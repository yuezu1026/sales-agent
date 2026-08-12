package com.aicustomer.config;

import com.aicustomer.entity.User;
import com.aicustomer.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 初始化数据：首次启动创建 admin 账号（幂等）
 */
@Configuration
public class InitDataConfig {

    private static final Logger log = LoggerFactory.getLogger(InitDataConfig.class);

    @Bean
    public CommandLineRunner initAdmin(UserRepository userRepository) {
        return args -> {
            if (userRepository.findByUsername("admin").isEmpty()) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPasswordHash(new BCryptPasswordEncoder().encode("Admin@123456"));
                admin.setDisplayName("系统管理员");
                admin.setRole("admin");
                userRepository.save(admin);
                log.info("已创建初始管理员账号 admin（默认密码 Admin@123456，请及时修改）");
            }
        };
    }
}
