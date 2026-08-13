package com.aicustomer.service;

import com.aicustomer.common.BizException;
import com.aicustomer.common.TenantContext;
import com.aicustomer.common.TenantDefaults;
import com.aicustomer.entity.DataSource;
import com.aicustomer.entity.PromptTemplate;
import com.aicustomer.entity.SystemConfig;
import com.aicustomer.entity.Tenant;
import com.aicustomer.entity.User;
import com.aicustomer.repository.DataSourceRepository;
import com.aicustomer.repository.PromptTemplateRepository;
import com.aicustomer.repository.SystemConfigRepository;
import com.aicustomer.repository.TenantRepository;
import com.aicustomer.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户服务：注册（建租户+租户管理员）、登录校验、当前用户、用户管理（仅超级管理员）
 * SaaS 化后：注册即开通独立租户，数据按 tenant_id 隔离
 */
@Service
public class UserService {

    /** 演示默认账号（公开密码）：禁止任何方式修改密码，含本人 */
    public static final String DEFAULT_ADMIN_USERNAME = "admin";

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final DataSourceRepository dataSourceRepository;
    private final PromptTemplateRepository promptTemplateRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository, TenantRepository tenantRepository,
                       SystemConfigRepository systemConfigRepository, DataSourceRepository dataSourceRepository,
                       PromptTemplateRepository promptTemplateRepository) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.promptTemplateRepository = promptTemplateRepository;
    }

    /**
     * 开放注册：创建独立租户 + 租户管理员 + 初始化默认配置/数据源/Prompt 模板
     */
    @Transactional
    public User register(String username, String password, String displayName, String companyName) {
        if (!StringUtils.hasText(username)) {
            throw BizException.badRequest("用户名不能为空");
        }
        if (username.trim().length() < 3 || username.trim().length() > 32) {
            throw BizException.badRequest("用户名长度需 3-32 个字符");
        }
        if (!username.trim().matches("[a-zA-Z0-9_]+")) {
            throw BizException.badRequest("用户名仅支持字母、数字、下划线");
        }
        if (password == null || password.length() < 8) {
            throw BizException.badRequest("密码至少 8 位");
        }
        if (userRepository.findByUsername(username.trim()).isPresent()) {
            throw BizException.badRequest("用户名已存在");
        }

        // 1) 创建租户
        Tenant tenant = new Tenant();
        tenant.setName(StringUtils.hasText(companyName) ? companyName.trim() : username.trim());
        tenant.setPlan("free");
        tenant.setStatus("active");
        tenant = tenantRepository.save(tenant);

        // 2) 创建租户管理员
        User user = new User();
        user.setUsername(username.trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(StringUtils.hasText(displayName) ? displayName.trim() : username.trim());
        user.setRole(User.ROLE_ADMIN);
        user.setStatus("active");
        user.setTenantId(tenant.getId());
        user = userRepository.save(user);

        // 3) 回填租户 owner
        tenant.setOwnerUserId(user.getId());
        tenantRepository.save(tenant);

        // 4) 初始化租户默认数据（开箱即用）
        initTenantDefaults(tenant.getId());

        return user;
    }

    /** 新租户初始化：system_config 默认项 + 默认数据源 + 默认 Prompt 模板 */
    private void initTenantDefaults(Long tenantId) {
        for (var def : TenantDefaults.DEFAULT_CONFIGS) {
            if (systemConfigRepository.findByTenantIdAndConfigKey(tenantId, def.get("key")).isEmpty()) {
                SystemConfig c = new SystemConfig();
                c.setTenantId(tenantId);
                c.setConfigKey(def.get("key"));
                c.setDescription(def.get("description"));
                systemConfigRepository.save(c);
            }
        }
        for (var ds : TenantDefaults.DEFAULT_DATA_SOURCES) {
            if (dataSourceRepository.findByTenantIdAndType(tenantId, ds.type()).isEmpty()) {
                DataSource d = new DataSource();
                d.setTenantId(tenantId);
                d.setName(ds.name());
                d.setType(ds.type());
                d.setApiBaseUrl(ds.apiBaseUrl());
                d.setEnabled(ds.enabled());
                dataSourceRepository.save(d);
            }
        }
        for (var pt : TenantDefaults.DEFAULT_PROMPTS) {
            if (promptTemplateRepository.findByTenantIdAndScene(tenantId, pt.scene()).isEmpty()) {
                PromptTemplate p = new PromptTemplate();
                p.setTenantId(tenantId);
                p.setScene(pt.scene());
                p.setName(pt.name());
                p.setContent(pt.content());
                p.setVersion(1);
                p.setEnabled(true);
                promptTemplateRepository.save(p);
            }
        }
    }

    public User authenticate(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> BizException.unauthorized("用户名或密码错误"));
        if (!"active".equals(user.getStatus())) {
            throw BizException.forbidden("账号已被禁用，请联系管理员");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw BizException.unauthorized("用户名或密码错误");
        }
        return user;
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> BizException.unauthorized("用户不存在"));
    }

    /** 记录登录：更新上次登录时间（M7.9 登录统计） */
    public void recordLogin(String username) {
        User user = findByUsername(username);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
    }

    /**
     * 校验当前用户是否为管理员（系统管理员或普通管理员），否则 403
     * 系统管理员 = role=admin 且无租户；普通管理员 = role=admin 且属于某租户
     */
    public User requireAdmin(String username) {
        User user = findByUsername(username);
        if (!User.ROLE_ADMIN.equals(user.getRole())) {
            throw BizException.forbidden("无权限，仅管理员可操作");
        }
        return user;
    }

    /** 校验当前用户是否为系统管理员（平台级，无租户），否则 403 */
    public User requireSystemAdmin(String username) {
        User user = findByUsername(username);
        if (!user.isSystemAdmin()) {
            throw BizException.forbidden("无权限，仅系统管理员可操作");
        }
        return user;
    }

    /** 用户列表视图：含租户名（平台视角展示用） */
    public record UserVO(Long id, String username, String displayName, String role,
                         Long tenantId, String tenantName, String status,
                         LocalDateTime createdAt, LocalDateTime lastLoginAt) {
    }

    /**
     * 用户列表：
     * - 系统管理员（无租户上下文）→ 仅平台系统管理员账号（role=admin 且无租户）—— 普通用户/普通管理员由各租户管理员管理
     * - 普通管理员 → 本租户用户
     */
    public List<UserVO> listAll() {
        Long tenantId = TenantContext.get();
        List<User> users = (tenantId == null)
                ? userRepository.findByRoleAndTenantIdIsNull(User.ROLE_ADMIN)
                : userRepository.findByTenantId(tenantId);
        if (users.isEmpty()) {
            return List.of();
        }
        // 批量查租户名（平台视角需要展示用户归属的租户）
        // 注意：平台级账号（tenant_id 为 NULL，如初始 admin）无租户，get(null) 需容忍
        Set<Long> ids = users.stream().map(User::getTenantId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = ids.isEmpty() ? new HashMap<>()
                : tenantRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Tenant::getId, Tenant::getName, (a, b) -> a));
        return users.stream()
                .map(u -> new UserVO(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(),
                        u.getTenantId(), names.get(u.getTenantId()), u.getStatus(),
                        u.getCreatedAt(), u.getLastLoginAt()))
                .toList();
    }

    /**
     * 创建用户（管理员操作）：
     * - 超级管理员（平台级）→ 只能创建超级管理员（role=admin，无租户），不能创建普通管理员/普通用户
     * - 普通管理员（租户级）→ 只能创建本租户的普通管理员（role=admin）或普通用户（role=operator）
     */
    public User createUser(String username, String password, String displayName, String role, String operatorName) {
        if (username == null || username.isBlank()) {
            throw BizException.badRequest("用户名不能为空");
        }
        if (username.trim().length() < 3 || username.trim().length() > 32) {
            throw BizException.badRequest("用户名长度需 3-32 个字符");
        }
        if (!username.trim().matches("[a-zA-Z0-9_]+")) {
            throw BizException.badRequest("用户名仅支持字母、数字、下划线");
        }
        if (password == null || password.length() < 8) {
            throw BizException.badRequest("密码至少 8 位");
        }
        if (userRepository.findByUsername(username.trim()).isPresent()) {
            throw BizException.badRequest("用户名已存在");
        }

        User operator = findByUsername(operatorName);
        boolean sysAdmin = operator.isSystemAdmin();

        // 目标角色与归属（M8.1 角色创建权限矩阵）：
        // 超级管理员 → 只能创建超级管理员（平台级，无租户）
        // 租户管理员 → 创建本租户普通管理员/普通用户（归属当前租户）
        String targetRole;
        Long targetTenantId;
        if (sysAdmin) {
            if (StringUtils.hasText(role) && !User.ROLE_ADMIN.equals(role.trim())) {
                throw BizException.forbidden("无权限，超级管理员只能创建超级管理员");
            }
            targetRole = User.ROLE_ADMIN;
            targetTenantId = null;
        } else {
            targetRole = (StringUtils.hasText(role) && User.ROLE_ADMIN.equals(role.trim()))
                    ? User.ROLE_ADMIN : User.ROLE_OPERATOR;
            targetTenantId = TenantContext.require();
        }

        User user = new User();
        user.setUsername(username.trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName == null || displayName.isBlank() ? username.trim() : displayName.trim());
        user.setRole(targetRole);
        user.setStatus("active");
        user.setTenantId(targetTenantId);
        return userRepository.save(user);
    }

    /**
     * 管理员重置密码：
     * - 演示默认账号 admin 禁止重置（含本人操作）→ 400
     * - 任何人不能重置自己的密码（自己的密码通过个人设置修改）→ 400
     * - 系统管理员：只能重置其他系统管理员（平台账号）；租户用户由租户管理员管理 → 403
     * - 普通管理员：可重置本租户任意账号（普通用户/普通管理员），租户隔离由 findByIdWithinScope 保证
     */
    public void resetPassword(Long id, String newPassword, String operatorName) {
        if (newPassword == null || newPassword.length() < 8) {
            throw BizException.badRequest("新密码至少 8 位");
        }
        User operator = findByUsername(operatorName);
        User target = findByIdWithinScope(id, operator);
        if (DEFAULT_ADMIN_USERNAME.equals(target.getUsername())) {
            throw BizException.badRequest("演示默认账号 admin 禁止修改密码");
        }
        if (target.getUsername().equals(operatorName)) {
            throw BizException.badRequest("不能重置当前登录账号密码，请通过个人设置修改");
        }
        if (operator.isSystemAdmin()) {
            if (!target.isSystemAdmin()) {
                throw BizException.forbidden("无权限，租户用户由租户管理员管理");
            }
        } else if (target.isSystemAdmin()) {
            throw BizException.forbidden("无权限，不能重置系统管理员密码");
        }
        target.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(target);
    }

    /**
     * 管理员启用/禁用账号：
     * - 系统管理员：仅能管理平台账号；但系统管理员账号不可禁用（含自己）→ 400；
     *   租户用户由租户管理员管理，系统管理员无权操作 → 403
     * - 普通管理员：可启停本租户任意账号（普通用户/普通管理员），但不能禁用自己；租户隔离由 findByIdWithinScope 保证
     */
    public void setStatus(Long id, String status, String operatorName) {
        if (!"active".equals(status) && !"disabled".equals(status)) {
            throw BizException.badRequest("状态值不合法");
        }
        User operator = findByUsername(operatorName);
        User target = findByIdWithinScope(id, operator);
        if (operator.isSystemAdmin()) {
            if (!target.isSystemAdmin()) {
                throw BizException.forbidden("无权限，租户用户由租户管理员管理");
            }
            // 平台账号只有系统管理员，系统管理员账号一律不可禁用
            throw BizException.badRequest("系统管理员账号不可禁用");
        }
        if (target.isSystemAdmin()) {
            throw BizException.badRequest("系统管理员账号不可禁用");
        }
        if ("disabled".equals(status) && target.getUsername().equals(operatorName)) {
            throw BizException.badRequest("不能禁用当前登录账号");
        }
        target.setStatus(status);
        userRepository.save(target);
    }

    /**
     * 按操作者范围查找用户（租户隔离，防越权）：
     * - 系统管理员可操作任意用户
     * - 普通管理员只能操作本租户用户
     */
    private User findByIdWithinScope(Long id, User operator) {
        if (operator.isSystemAdmin()) {
            return userRepository.findById(id)
                    .orElseThrow(() -> BizException.notFound("用户不存在"));
        }
        return userRepository.findByIdAndTenantId(id, operator.getTenantId())
                .orElseThrow(() -> BizException.notFound("用户不存在"));
    }

    /**
     * 当前用户完整资料（含公司名称，从租户名填充）：me 接口使用
     */
    public User getProfile(String username) {
        User user = findByUsername(username);
        if (user.getTenantId() != null) {
            tenantRepository.findById(user.getTenantId())
                    .ifPresent(t -> user.setCompanyName(t.getName()));
        }
        return user;
    }

    /**
     * 修改本人资料：显示名称/邮箱/微信/电话/公司名称
     * 仅能改自己的资料（username 由 token 身份决定）；公司名称更新租户名（租户级），
     * 且公司名称只能由本租户的管理员修改（普通用户/平台管理员提交则拒绝）
     */
    @Transactional
    public void updateProfile(String username, String displayName, String email, String wechat,
                              String phone, String companyName) {
        User user = findByUsername(username);
        String dn = StringUtils.hasText(displayName) ? displayName.trim() : null;
        if (!StringUtils.hasText(dn)) {
            throw BizException.badRequest("显示名称不能为空");
        }
        if (dn.length() > 64) {
            throw BizException.badRequest("显示名称不能超过 64 个字符");
        }
        String em = StringUtils.hasText(email) ? email.trim() : null;
        if (em != null && em.length() > 128) {
            throw BizException.badRequest("邮箱地址过长");
        }
        if (em != null && !em.matches("^[^@\\s]+@[^@\\s]+$")) {
            throw BizException.badRequest("邮箱地址格式不正确");
        }
        String wc = StringUtils.hasText(wechat) ? wechat.trim() : null;
        if (wc != null && wc.length() > 64) {
            throw BizException.badRequest("微信号过长");
        }
        String ph = StringUtils.hasText(phone) ? phone.trim() : null;
        if (ph != null && ph.length() > 32) {
            throw BizException.badRequest("电话号码过长");
        }
        user.setDisplayName(dn);
        user.setEmail(em);
        user.setWechat(wc);
        user.setPhone(ph);
        userRepository.save(user);
        // 公司名称（租户级）：仅租户管理员可修改；普通用户/平台管理员提交则拒绝
        boolean wantCompany = StringUtils.hasText(companyName);
        if (wantCompany && !user.isTenantAdmin()) {
            throw BizException.badRequest("仅租户管理员可修改公司名称");
        }
        if (wantCompany) {
            String cn = companyName.trim();
            if (cn.length() > 128) {
                throw BizException.badRequest("公司名称不能超过 128 个字符");
            }
            String finalCn = cn;
            tenantRepository.findById(user.getTenantId()).ifPresent(t -> {
                t.setName(finalCn);
                tenantRepository.save(t);
            });
        }
    }

    /**
     * 修改密码：校验旧密码 → 更新为 BCrypt 新哈希
     * 演示默认账号 admin 禁止修改（公开密码）
     */
    public void changePassword(String username, String oldPassword, String newPassword) {
        User user = findByUsername(username);
        if (DEFAULT_ADMIN_USERNAME.equals(user.getUsername())) {
            throw BizException.badRequest("演示默认账号 admin 禁止修改密码");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            // 原密码错误是业务校验失败，用 400（401 会被前端当作登录过期而登出）
            throw BizException.badRequest("原密码不正确");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
