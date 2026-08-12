package com.aicustomer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@EnableScheduling
@SpringBootApplication
public class AiCustomerApplication {

    static {
        // M7.12 时区修复：强制 JVM 默认时区为中国时区（容器 TZ 缺失时兜底）。
        // 保证 LocalDate.now() 的“今日”口径与 Hibernate 解释 LocalDateTime→TIMESTAMPTZ 同时区，自洽一致。
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
    }

    public static void main(String[] args) {
        SpringApplication.run(AiCustomerApplication.class, args);
    }
}
