package com.aicustomer.mcp;

import com.aicustomer.common.BizException;
import com.aicustomer.entity.SystemConfig;
import com.aicustomer.repository.SystemConfigRepository;
import com.aicustomer.util.AesUtil;
import jakarta.mail.FetchProfile;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.FlagTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邮箱访问服务（M2-1.6 底层）：提供 IMAP 与 mock 两种 provider
 * - imap：真实邮箱（host/port/ssl/账号密码可配，适配任意邮箱服务商）
 * - mock：内置模拟客户回复邮件，无邮箱账号也可验证全链路（开发/演示/E2E）
 * provider 判定（M7.7）：环境变量 app.email.provider 显式设置优先；
 * 否则系统设置 system_config 配置了 imap.host → imap，未配置 → mock。
 * 暴露给 MCP 工具的领域对象：MailItem（JSON 序列化）
 */
@Service
public class EmailMailboxService {

    private static final Logger log = LoggerFactory.getLogger(EmailMailboxService.class);

    /** MCP 工具返回的邮件数据模型 */
    public record MailItem(long uid, String messageId, String fromAddress, String fromName,
                           String toAddress, String subject, String body,
                           LocalDateTime receivedAt, boolean isRead) {
    }

    /** 环境变量显式指定的 provider（imap/mock，空则按 system_config imap.host 自动判定） */
    private final String providerEnv;
    private final SystemConfigRepository configRepository;
    private final AesUtil aesUtil;

    /** mock 模式：uid → 是否已读（内存态，供 markRead 后列表联动） */
    private final Map<Long, Boolean> mockReadFlags = new ConcurrentHashMap<>();

    /** mock 模式：内置模拟客户回复邮件 */
    private static final List<MailItem> MOCK_MAILS = buildMockMails();

    public EmailMailboxService(@Value("${app.email.provider:}") String providerEnv,
                               SystemConfigRepository configRepository,
                               AesUtil aesUtil) {
        this.providerEnv = providerEnv;
        this.configRepository = configRepository;
        this.aesUtil = aesUtil;
    }

    /** IMAP 配置（system_config 读取，password 解密） */
    private record ImapConfig(String host, int port, boolean ssl, String username, String password) {
    }

    /** 是否使用 IMAP：环境变量显式指定优先；否则系统设置配置了 imap.host 即用 IMAP，未配置用 mock */
    private boolean isImap() {
        if (StringUtils.hasText(providerEnv)) {
            return "imap".equalsIgnoreCase(providerEnv);
        }
        return StringUtils.hasText(cfg("imap.host"));
    }

    private ImapConfig loadImapConfig() {
        String host = cfg("imap.host");
        String portStr = cfg("imap.port");
        String sslStr = cfg("imap.ssl");
        String username = cfg("imap.username");
        String passwordRaw = cfg("imap.password");
        if (!StringUtils.hasText(host)) {
            throw BizException.badRequest("未配置收件箱 IMAP 服务器，请在系统设置中配置 imap.host");
        }
        if (!StringUtils.hasText(username)) {
            throw BizException.badRequest("未配置收件箱账号，请在系统设置中配置 imap.username");
        }
        if (!StringUtils.hasText(passwordRaw)) {
            throw BizException.badRequest("未配置收件箱密码，请在系统设置中配置 imap.password");
        }
        int port = 993;
        if (StringUtils.hasText(portStr)) {
            try {
                port = Integer.parseInt(portStr.trim());
            } catch (NumberFormatException ignored) {
                // 非法端口回退 993
            }
        }
        boolean ssl = true;
        if (StringUtils.hasText(sslStr)) {
            ssl = !("false".equalsIgnoreCase(sslStr.trim()) || "0".equals(sslStr.trim()));
        }
        // 密码在系统设置保存时已 AES 加密，解密失败按明文兼容
        String password;
        try {
            password = aesUtil.decrypt(passwordRaw);
        } catch (Exception e) {
            password = passwordRaw;
        }
        return new ImapConfig(host, port, ssl, username, password);
    }

    private String cfg(String key) {
        return configRepository.findByConfigKey(key)
                .map(SystemConfig::getConfigValue)
                .orElse(null);
    }

    /** 列出邮件（文件夹 / 条数 / 只看未读 / 起始时间 / 发件人白名单 / 是否带正文），按接收时间倒序 */
    public List<MailItem> listEmails(String folder, int limit, boolean unreadOnly, LocalDateTime since,
                                     List<String> fromEmails, boolean includeBody) {
        if (!StringUtils.hasText(folder)) {
            folder = "INBOX";
        }
        if (limit <= 0) {
            limit = 20;
        }
        if (isImap()) {
            return listImap(folder, limit, unreadOnly, since, fromEmails, includeBody);
        }
        return listMock(folder, limit, unreadOnly, since, fromEmails);
    }

    /** 读取单封邮件正文 */
    public MailItem readEmail(long uid) {
        if (isImap()) {
            return readImap(uid);
        }
        return readMock(uid);
    }

    /** 批量读取多封邮件正文：一次 IMAP 连接 + getMessagesByUID 批量取，避免逐封重连 */
    public List<MailItem> readEmails(List<Long> uids) {
        if (uids == null || uids.isEmpty()) {
            return List.of();
        }
        if (isImap()) {
            return readImapBatch(uids);
        }
        return uids.stream().map(this::readMock).toList();
    }

    private List<MailItem> readImapBatch(List<Long> uids) {
        Store store = openStore();
        try {
            Folder folder = store.getFolder("INBOX");
            folder.open(Folder.READ_WRITE);
            try {
                long[] uidArr = uids.stream().mapToLong(Long::longValue).toArray();
                Message[] msgs = ((UIDFolder) folder).getMessagesByUID(uidArr);
                if (msgs.length == 0) {
                    return List.of();
                }
                // 预取元数据一次网络往返，正文逐封 getContent 复用同一连接
                FetchProfile fp = new FetchProfile();
                fp.add(FetchProfile.Item.ENVELOPE);
                fp.add(FetchProfile.Item.FLAGS);
                fp.add(UIDFolder.FetchProfileItem.UID);
                folder.fetch(msgs, fp);
                UIDFolder uf = (UIDFolder) folder;
                List<MailItem> items = new ArrayList<>(msgs.length);
                for (Message m : msgs) {
                    items.add(toMailItem(uf.getUID(m), m, false));
                }
                return items;
            } finally {
                folder.close(false);
            }
        } catch (Exception e) {
            log.warn("IMAP 批量读取失败: {}", e.getMessage());
            throw BizException.badRequest("读取邮件失败：" + e.getMessage());
        } finally {
            closeQuietly(store);
        }
    }

    /** 标记已读 / 未读 */
    public void markRead(long uid, boolean read) {
        if (isImap()) {
            markImapRead(uid, read);
            return;
        }
        mockReadFlags.put(uid, read);
    }

    // ==================== IMAP 实现 ====================

    private Store openStore() {
        ImapConfig conf = loadImapConfig();
        Properties props = new Properties();
        String protocol = conf.ssl() ? "imaps" : "imap";
        props.put("mail.store.protocol", protocol);
        if (conf.ssl()) {
            props.put("mail.imaps.ssl.trust", "*");
        }
        // 超时属性必须按实际协议前缀设置：imaps 用 mail.imaps.*，imap 用 mail.imap.*
        String p = conf.ssl() ? "mail.imaps" : "mail.imap";
        props.put(p + ".connectiontimeout", "10000");
        props.put(p + ".timeout", "15000");
        props.put(p + ".writetimeout", "15000");
        try {
            Session session = Session.getInstance(props);
            Store store = session.getStore(protocol);
            store.connect(conf.host(), conf.port(), conf.username(), conf.password());
            return store;
        } catch (Exception e) {
            log.warn("IMAP 连接失败: {}", e.getMessage());
            throw BizException.badRequest("邮箱连接失败：" + e.getMessage());
        }
    }

    private List<MailItem> listImap(String folderName, int limit, boolean unreadOnly, LocalDateTime since,
                                    List<String> fromEmails, boolean includeBody) {
        Store store = openStore();
        try {
            Folder folder = store.getFolder(folderName);
            folder.open(Folder.READ_WRITE);
            try {
                Message[] messages;
                jakarta.mail.search.SearchTerm term = null;
                if (unreadOnly) {
                    term = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
                }
                // 发件人白名单：只同步客户管理中存在的客户邮箱（多邮箱 OR）
                List<String> validFroms = fromEmails == null ? List.of()
                        : fromEmails.stream().filter(StringUtils::hasText).map(String::trim)
                                .filter(s -> !s.isBlank()).distinct().toList();
                if (!validFroms.isEmpty()) {
                    jakarta.mail.search.SearchTerm[] fromTerms = validFroms.stream()
                            .map(s -> (jakarta.mail.search.SearchTerm) new jakarta.mail.search.FromStringTerm(s))
                            .toArray(jakarta.mail.search.SearchTerm[]::new);
                    jakarta.mail.search.SearchTerm fromTerm = new jakarta.mail.search.OrTerm(fromTerms);
                    term = term == null ? fromTerm : new jakarta.mail.search.AndTerm(term, fromTerm);
                }
                if (term != null) {
                    // 服务器端 SEARCH 过滤（发件人/未读）
                    messages = folder.search(term);
                    // 命中数可能较多（客户邮箱匹配几十上百封），仍截断到最新 limit 封，避免响应过大/超时
                    if (messages.length > limit) {
                        messages = Arrays.copyOfRange(messages, messages.length - limit, messages.length);
                    }
                } else {
                    messages = folder.getMessages();
                    // 邮箱消息按 MSGN 升序（接收先后顺序），末尾即最新。
                    // 只取末尾 limit 封，避免对全部邮件逐个触发网络请求（大邮箱下会超时）。
                    int start = Math.max(0, messages.length - limit);
                    messages = Arrays.copyOfRange(messages, start, messages.length);
                }
                // 批量预取元数据（ENVELOPE/FLAGS/UID），一次网络往返，之后字段读取全部走本地缓存
                // 注：正文不批量预取（jakarta.mail 无 CONTENT FetchProfileItem），
                // 需要正文时在下方 toMailItem 内逐封 getContent，复用同一 IMAP 连接（无重连开销）
                FetchProfile fp = new FetchProfile();
                fp.add(FetchProfile.Item.ENVELOPE);
                fp.add(FetchProfile.Item.FLAGS);
                fp.add(UIDFolder.FetchProfileItem.UID);
                folder.fetch(messages, fp);
                UIDFolder uf = (UIDFolder) folder;
                List<MailItem> items = new ArrayList<>();
                // 倒序：最新在前
                for (int i = messages.length - 1; i >= 0; i--) {
                    Message m = messages[i];
                    items.add(toMailItem(uf.getUID(m), m, !includeBody));
                }
                return items;
            } finally {
                folder.close(false);
            }
        } catch (Exception e) {
            log.warn("IMAP 列表失败: {}", e.getMessage());
            throw BizException.badRequest("读取邮箱失败：" + e.getMessage());
        } finally {
            closeQuietly(store);
        }
    }

    private MailItem readImap(long uid) {
        Store store = openStore();
        try {
            Folder folder = store.getFolder("INBOX");
            folder.open(Folder.READ_WRITE);
            try {
                Message m = ((UIDFolder) folder).getMessageByUID(uid);
                if (m == null) {
                    throw BizException.notFound("邮件不存在（UID=" + uid + "）");
                }
                return toMailItem(uid, m, false);
            } finally {
                folder.close(false);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("IMAP 读取失败: {}", e.getMessage());
            throw BizException.badRequest("读取邮件失败：" + e.getMessage());
        } finally {
            closeQuietly(store);
        }
    }

    private void markImapRead(long uid, boolean read) {
        Store store = openStore();
        try {
            Folder folder = store.getFolder("INBOX");
            folder.open(Folder.READ_WRITE);
            try {
                Message m = ((UIDFolder) folder).getMessageByUID(uid);
                if (m != null) {
                    m.setFlag(Flags.Flag.SEEN, read);
                }
            } finally {
                folder.close(false);
            }
        } catch (Exception e) {
            log.warn("IMAP 标记失败: {}", e.getMessage());
            throw BizException.badRequest("标记邮件失败：" + e.getMessage());
        } finally {
            closeQuietly(store);
        }
    }

    private MailItem toMailItem(long uid, Message m, boolean skipBody) {
        try {
            String fromName = null;
            String fromAddress = null;
            if (m.getFrom() != null && m.getFrom().length > 0 && m.getFrom()[0] instanceof InternetAddress ia) {
                fromAddress = ia.getAddress();
                fromName = StringUtils.hasText(ia.getPersonal()) ? ia.getPersonal() : null;
            }
            String toAddress = null;
            if (m.getRecipients(Message.RecipientType.TO) != null
                    && m.getRecipients(Message.RecipientType.TO).length > 0
                    && m.getRecipients(Message.RecipientType.TO)[0] instanceof InternetAddress ia) {
                toAddress = ia.getAddress();
            }
            LocalDateTime receivedAt = safeReceivedAt(m);
            return new MailItem(uid,
                    m.getHeader("Message-ID") != null && m.getHeader("Message-ID").length > 0 ? m.getHeader("Message-ID")[0] : null,
                    fromAddress != null ? fromAddress : "",
                    fromName,
                    toAddress,
                    m.getSubject(),
                    skipBody ? null : extractBody(m),
                    receivedAt,
                    m.isSet(Flags.Flag.SEEN));
        } catch (Exception e) {
            log.warn("解析邮件失败 uid={}: {}", uid, e.getMessage());
            return new MailItem(uid, null, "", null, null, null, null, LocalDateTime.now(), false);
        }
    }

    private LocalDateTime safeReceivedAt(Message m) {
        try {
            // 优先取发送时间：ENVELOPE 已由 listImap 批量预取（本地缓存，不触发网络）。
            // getReceivedDate 对应 IMAP INTERNALDATE，未预取时会逐封发起网络请求（大邮箱下会超时）。
            java.util.Date d = m.getSentDate();
            if (d == null) {
                d = m.getReceivedDate();
            }
            return d != null ? LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault()) : LocalDateTime.now();
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    /** 递归提取正文：优先 text/plain，其次 text/html（去标签） */
    private String extractBody(Part part) {
        try {
            if (part.isMimeType("text/plain")) {
                Object c = part.getContent();
                return c == null ? null : String.valueOf(c);
            }
            if (part.isMimeType("text/html")) {
                Object c = part.getContent();
                if (c == null) {
                    return null;
                }
                return htmlToText(String.valueOf(c));
            }
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    String body = extractBody(mp.getBodyPart(i));
                    if (body != null && !body.isBlank()) {
                        return body;
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String htmlToText(String html) {
        String text = html.replaceAll("(?is)<(script|style).*?</\\1>", " ");
        text = text.replaceAll("(?is)<br\\s*/?>", "\n");
        text = text.replaceAll("(?is)</p>", "\n");
        text = text.replaceAll("(?is)<[^>]+>", " ");
        text = text.replaceAll("&nbsp;", " ").replaceAll("&amp;", "&").replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">").replaceAll("&quot;", "\"");
        return text.replaceAll("[ \\t]+", " ").replaceAll("\\n\\s*\\n+", "\n").trim();
    }

    private void closeQuietly(Store store) {
        try {
            if (store != null) {
                store.close();
            }
        } catch (Exception ignored) {
        }
    }

    // ==================== mock 实现 ====================

    private static List<MailItem> buildMockMails() {
        LocalDateTime now = LocalDateTime.now();
        return List.of(
                new MailItem(1L, "<mock-1@aicustomer.local>", "zhang@test.com", "张三",
                        "sales@aicustomer.local", "关于贵司产品的报价咨询",
                        "您好，我们在官网上看到了贵司的产品介绍，对 AI 获客方案很感兴趣。" +
                                "能否提供一份详细的报价单？我们的客户量在 2000 家左右，想了解具体的定价和部署方式。",
                        now.minusDays(1).minusHours(3), false),
                new MailItem(2L, "<mock-2@aicustomer.local>", "wang@new.com", "王五",
                        "sales@aicustomer.local", "Re: 合作意向跟进",
                        "感谢您的邮件。我们内部正在评估这套方案，预计下周三可以给出初步结论。" +
                                "届时如果方便，希望可以和您约一次线上演示。",
                        now.minusDays(2).minusHours(5), false),
                new MailItem(3L, "<mock-3@aicustomer.local>", "lilei@techcorp.cn", "李雷",
                        "sales@aicustomer.local", "关于价格的讨论",
                        "贵司的报价超出了我们当前的预算范围。如果价格方面有进一步优惠的空间，"
                                + "或者可以按年付费降低首年成本，我们可以继续谈。",
                        now.minusDays(3).minusHours(1), true),
                new MailItem(4L, "<mock-4@aicustomer.local>", "sales@globalsoft.com", "GlobalSoft 采购部",
                        "sales@aicustomer.local", "请提供详细技术方案",
                        "我们是一家出海 SaaS 公司，客户分布在北美和欧洲。想了解贵司方案是否支持多语言邮件、"
                                + "以及如何与现有 CRM 系统集成。请提供一份详细的技术方案说明。",
                        now.minusDays(4).minusHours(8), false),
                new MailItem(5L, "<mock-5@aicustomer.local>", "zhang@test.com", "张三",
                        "sales@aicustomer.local", "Re: 方案细节确认",
                        "方案看过了，整体比较满意。我们计划先在一个区域试点三个月，"
                                + "如果效果达标会推广到全部业务线。请帮忙准备试点合同。",
                        now.minusDays(5).minusHours(2), true));
    }

    private List<MailItem> listMock(String folder, int limit, boolean unreadOnly, LocalDateTime since,
                                    List<String> fromEmails) {
        List<String> validFroms = fromEmails == null ? List.of()
                : fromEmails.stream().map(String::trim).filter(s -> !s.isBlank())
                        .map(String::toLowerCase).distinct().toList();
        List<MailItem> items = new ArrayList<>();
        for (MailItem m : MOCK_MAILS) {
            boolean isRead = mockReadFlags.getOrDefault(m.uid(), m.isRead());
            MailItem item = new MailItem(m.uid(), m.messageId(), m.fromAddress(), m.fromName(),
                    m.toAddress(), m.subject(), m.body(), m.receivedAt(), isRead);
            if (unreadOnly && isRead) {
                continue;
            }
            if (since != null && item.receivedAt().isBefore(since)) {
                continue;
            }
            // 发件人白名单：只同步客户管理中存在的客户邮箱
            if (!validFroms.isEmpty() && m.fromAddress() != null
                    && !validFroms.contains(m.fromAddress().trim().toLowerCase())) {
                continue;
            }
            items.add(item);
        }
        items.sort(Comparator.comparing(MailItem::receivedAt).reversed());
        if (items.size() > limit) {
            return items.subList(0, limit);
        }
        return items;
    }

    private MailItem readMock(long uid) {
        return MOCK_MAILS.stream()
                .filter(m -> m.uid() == uid)
                .map(m -> new MailItem(m.uid(), m.messageId(), m.fromAddress(), m.fromName(),
                        m.toAddress(), m.subject(), m.body(), m.receivedAt(),
                        mockReadFlags.getOrDefault(m.uid(), m.isRead())))
                .findFirst()
                .orElseThrow(() -> BizException.notFound("邮件不存在（UID=" + uid + "）"));
    }
}
