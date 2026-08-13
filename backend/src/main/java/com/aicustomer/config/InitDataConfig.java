package com.aicustomer.config;

import com.aicustomer.entity.Tenant;
import com.aicustomer.entity.User;
import com.aicustomer.repository.TenantRepository;
import com.aicustomer.repository.UserRepository;
import com.aicustomer.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 初始化数据：首次启动创建演示账号（幂等）
 * - admin / Admin@123456：平台级系统管理员
 * - demo_admin / Demo@123456：演示租户管理员（演示租户「演示租户」）
 * - demo_user / Demo@123456：演示租户普通用户
 * <p>
 * 演示租户独立创建，与真实注册租户完全隔离；演示账号公开密码，禁止修改（见 UserService.DEMO_ACCOUNTS）。
 */
@Configuration
public class InitDataConfig {

    private static final Logger log = LoggerFactory.getLogger(InitDataConfig.class);

    /** 演示租户名称（幂等键） */
    public static final String DEMO_TENANT_NAME = "演示租户";

    @Bean
    public CommandLineRunner initAdmin(UserRepository userRepository, TenantRepository tenantRepository,
                                       UserService userService) {
        return args -> {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            if (userRepository.findByUsername("admin").isEmpty()) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPasswordHash(encoder.encode("Admin@123456"));
                admin.setDisplayName("系统管理员");
                admin.setRole(User.ROLE_ADMIN);
                userRepository.save(admin);
                log.info("已创建初始管理员账号 admin（默认密码 Admin@123456，请及时修改）");
            }

            // 演示租户 + 演示账号（幂等）：租户管理员 + 普通用户
            createDemoAccounts(userRepository, tenantRepository, userService, encoder);
        };
    }

    /** 幂等创建演示租户及两个演示账号：缺哪个建哪个，已存在则跳过 */
    private void createDemoAccounts(UserRepository userRepository, TenantRepository tenantRepository,
                                    UserService userService, BCryptPasswordEncoder encoder) {
        User demoAdmin = userRepository.findByUsername("demo_admin").orElse(null);
        User demoUser = userRepository.findByUsername("demo_user").orElse(null);

        // 1) 演示租户：已存在则复用，否则新建
        Tenant demoTenant = null;
        if (demoAdmin == null || demoUser == null) {
            for (Tenant t : tenantRepository.findAll()) {
                if (DEMO_TENANT_NAME.equals(t.getName())) {
                    demoTenant = t;
                    break;
                }
            }
            if (demoTenant == null) {
                demoTenant = new Tenant();
                demoTenant.setName(DEMO_TENANT_NAME);
                demoTenant.setPlan("free");
                demoTenant.setStatus("active");
                demoTenant = tenantRepository.save(demoTenant);
            }
        }

        // 2) 演示租户管理员
        if (demoAdmin == null) {
            demoAdmin = new User();
            demoAdmin.setUsername("demo_admin");
            demoAdmin.setPasswordHash(encoder.encode("Demo@123456"));
            demoAdmin.setDisplayName("演示租户管理员");
            demoAdmin.setRole(User.ROLE_ADMIN);
            demoAdmin.setTenantId(demoTenant.getId());
            demoAdmin = userRepository.save(demoAdmin);
            demoTenant.setOwnerUserId(demoAdmin.getId());
            tenantRepository.save(demoTenant);
            log.info("已创建演示租户管理员 demo_admin（默认密码 Demo@123456，租户：{}）", DEMO_TENANT_NAME);
        }

        // 3) 演示租户普通用户
        if (demoUser == null) {
            demoUser = new User();
            demoUser.setUsername("demo_user");
            demoUser.setPasswordHash(encoder.encode("Demo@123456"));
            demoUser.setDisplayName("演示普通用户");
            demoUser.setRole(User.ROLE_OPERATOR);
            demoUser.setTenantId(demoTenant.getId());
            userRepository.save(demoUser);
            log.info("已创建演示租户普通用户 demo_user（默认密码 Demo@123456，租户：{}）", DEMO_TENANT_NAME);
        }

        // 4) 演示租户默认数据（开箱即用，与注册流程一致）：新建租户时初始化一次
        if (demoTenant != null && demoTenant.getOwnerUserId() != null) {
            userService.initTenantDefaults(demoTenant.getId());
        }
    }
}
