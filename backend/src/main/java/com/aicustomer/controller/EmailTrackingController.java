package com.aicustomer.controller;

import com.aicustomer.entity.EmailSendLog;
import com.aicustomer.repository.EmailSendLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDateTime;

/**
 * 邮件打开/点击追踪端点（M4-6，公开——收件人无登录态，WebConfig JWT exclude /api/track/**）：
 * <ul>
 *   <li>GET /api/track/open/{logId} —— 1px 透明 GIF，收件人加载即记首次打开时间（幂等）；</li>
 *   <li>GET /api/track/click/{logId}?url=xxx —— 记录首次点击时间后 302 跳转原链接（幂等）。</li>
 * </ul>
 * 只记录首次（opened_at/clicked_at 为空时写入），重复请求不覆盖。
 */
@RestController
@RequestMapping("/api/track")
public class EmailTrackingController {

    private static final Logger log = LoggerFactory.getLogger(EmailTrackingController.class);

    /** 1x1 透明 GIF（43 字节） */
    private static final byte[] PIXEL_1X1_GIF = new byte[]{
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00,
            (byte) 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            0x21, (byte) 0xF9, 0x04, 0x01, 0x00, 0x00, 0x00, 0x00,
            0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            0x02, 0x02, 0x44, 0x01, 0x00, 0x3B
    };

    private final EmailSendLogRepository emailSendLogRepository;

    public EmailTrackingController(EmailSendLogRepository emailSendLogRepository) {
        this.emailSendLogRepository = emailSendLogRepository;
    }

    /** 打开追踪：返回 1x1 透明 GIF，首次加载记 opened_at（幂等） */
    @GetMapping(value = "/open/{logId}", produces = MediaType.IMAGE_GIF_VALUE)
    public ResponseEntity<byte[]> trackOpen(@PathVariable Long logId,
                                            @RequestParam(value = "tenantId", required = false) Long tenantId) {
        markFirst(emailSendLogRepository.findByTenantIdAndId(resolveTenant(tenantId), logId).orElse(null), "opened_at");
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(PIXEL_1X1_GIF);
    }

    /** 点击追踪：首次点击记 clicked_at 后 302 跳转原链接（仅允许 http/https，防开放重定向） */
    @GetMapping("/click/{logId}")
    public ResponseEntity<?> trackClick(@PathVariable Long logId,
                                        @RequestParam("url") String url,
                                        @RequestParam(value = "tenantId", required = false) Long tenantId) {
        String target = url == null ? null : url.trim();
        if (!StringUtils.hasText(target)
                || !(target.startsWith("http://") || target.startsWith("https://"))) {
            return ResponseEntity.badRequest().body("{\"status\":\"invalid\",\"message\":\"追踪链接无效\"}");
        }
        markFirst(emailSendLogRepository.findByTenantIdAndId(resolveTenant(tenantId), logId).orElse(null), "clicked_at");
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target))
                .build();
    }

    /** 追踪链接未带租户（旧版邮件）时回退默认租户 1 */
    private Long resolveTenant(Long tenantId) {
        return tenantId == null ? 1L : tenantId;
    }

    /** 幂等标记首次时间：字段为空才写入（并发下 update 覆盖写，取最早一次） */
    private void markFirst(EmailSendLog entry, String field) {
        if (entry == null) {
            return;
        }
        boolean changed = false;
        if ("opened_at".equals(field) && entry.getOpenedAt() == null) {
            entry.setOpenedAt(LocalDateTime.now());
            changed = true;
        } else if ("clicked_at".equals(field) && entry.getClickedAt() == null) {
            entry.setClickedAt(LocalDateTime.now());
            changed = true;
        }
        if (changed) {
            try {
                emailSendLogRepository.save(entry);
                log.info("邮件追踪[{}]: logId={}", field, entry.getId());
            } catch (Exception e) {
                log.warn("邮件追踪[{}] 落库失败: logId={}, err={}", field, entry.getId(), e.getMessage());
            }
        }
    }
}
