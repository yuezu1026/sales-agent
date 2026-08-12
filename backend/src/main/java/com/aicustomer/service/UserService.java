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
import java.util.List;

/**
 * 用户服务：注册（建租户+租户管理员）、登录校验、当前用户、用户管理（仅超级管理员）
 * SaaS 化后：注册即开通独立租户，数据按 tenant_id 隔离
 */
@Service
public class UserService {

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

    /** 校验当前用户是否为超级管理员，否则 403 */
    public User requireAdmin(String username) {
        User user = findByUsername(username);
        if (!User.ROLE_ADMIN.equals(user.getRole())) {
            throw BizException.forbidden("无权限，仅超级管理员可操作");
        }
        return user;
    }

    /** 用户列表：租户管理员看本租户用户；平台超级管理员（无租户）看全部 */
    public List<User> listAll() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            return userRepository.findAll();
        }
        return userRepository.findByTenantId(tenantId);
    }

    /** 超级管理员创建操作员账号（同属当前租户；平台超级管理员创建的平台账号无租户） */
    public User createOperator(String operatorName, String password, String displayName) {
        if (operatorName == null || operatorName.isBlank()) {
            throw BizException.badRequest("用户名不能为空");
        }
        if (operatorName.length() < 3 || operatorName.length() > 32) {
            throw BizException.badRequest("用户名长度需 3-32 个字符");
        }
        if (password == null || password.length() < 8) {
            throw BizException.badRequest("密码至少 8 位");
        }
        if (userRepository.findByUsername(operatorName).isPresent()) {
            throw BizException.badRequest("用户名已存在");
        }
        User user = new User();
        user.setUsername(operatorName.trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName == null || displayName.isBlank() ? operatorName.trim() : displayName.trim());
        user.setRole(User.ROLE_OPERATOR);
        user.setStatus("active");
        user.setTenantId(TenantContext.get());
        return userRepository.save(user);
    }

    /** 超级管理员重置密码 */
    public void resetPassword(Long id, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw BizException.badRequest("新密码至少 8 位");
        }
        User user = findById(id);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /** 超级管理员启用/禁用账号（禁止禁用超级管理员自身） */
    public void setStatus(Long id, String status, String operatorName) {
        if (!"active".equals(status) && !"disabled".equals(status)) {
            throw BizException.badRequest("状态值不合法");
        }
        User user = findById(id);
        if (User.ROLE_ADMIN.equals(user.getRole())) {
            throw BizException.badRequest("超级管理员账号不可禁用");
        }
        if ("disabled".equals(status) && user.getUsername().equals(operatorName)) {
            throw BizException.badRequest("不能禁用当前登录账号");
        }
        user.setStatus(status);
        userRepository.save(user);
    }

    private User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("用户不存在"));
    }

    /**
     * 修改密码：校验旧密码 → 更新为 BCrypt 新哈希
     */
    public void changePassword(String username, String oldPassword, String newPassword) {
        User user = findByUsername(username);
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            // 原密码错误是业务校验失败，用 400（401 会被前端当作登录过期而登出）
            throw BizException.badRequest("原密码不正确");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
