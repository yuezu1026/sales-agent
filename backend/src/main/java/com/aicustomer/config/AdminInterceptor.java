package com.aicustomer.config;

import com.aicustomer.common.ApiResponse;
import com.aicustomer.controller.AuthController;
import com.aicustomer.entity.User;
import com.aicustomer.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 角色权限拦截器：仅超级管理员（admin）可访问用户管理 / 系统设置
 */
@Component
public class AdminInterceptor implements HandlerInterceptor {

    private final UserService userService;
    private final JsonMapper jsonMapper;

    public AdminInterceptor(UserService userService, JsonMapper jsonMapper) {
        this.userService = userService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // M8.6：用户列表接口 GET /api/users 放行任意已登录用户（内容由 UserService 按角色分派：
        // 系统管理员看平台管理员、租户管理员看本租户（不含自己）、operator 只看自己）。
        // 其余 /api/users/** 写操作（创建/重置密码/启停）及 /api/config/** 仍要求管理员。
        if ("GET".equalsIgnoreCase(request.getMethod())
                && "/api/users".equals(request.getRequestURI())) {
            return true;
        }
        String username = (String) request.getAttribute(AuthController.ATTR_USERNAME);
        try {
            User user = userService.findByUsername(username);
            if (!User.ROLE_ADMIN.equals(user.getRole())) {
                return reject(response, "无权限，仅超级管理员可访问");
            }
            return true;
        } catch (Exception e) {
            return reject(response, "无权限，仅超级管理员可访问");
        }
    }

    private boolean reject(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(ApiResponse.error(403, message)));
        return false;
    }
}
