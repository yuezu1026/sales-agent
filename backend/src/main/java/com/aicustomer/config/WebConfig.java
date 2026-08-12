package com.aicustomer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：拦截器注册 + CORS（本地联调跨域）
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AdminInterceptor adminInterceptor;

    public WebConfig(AuthInterceptor authInterceptor, AdminInterceptor adminInterceptor) {
        this.authInterceptor = authInterceptor;
        this.adminInterceptor = adminInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 登录校验：排除健康检查、登录、注册、退订接口（邮件收件人无登录态）、邮件追踪（收件人无登录态）
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/health/**",
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/auth/login-stats",
                        "/api/unsubscribe",
                        "/api/track/**"
                );

        // 仅超级管理员：用户管理 + 系统设置
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/users/**", "/api/config/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
