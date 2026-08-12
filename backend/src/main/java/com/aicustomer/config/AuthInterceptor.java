package com.aicustomer.config;

import com.aicustomer.common.ApiResponse;
import com.aicustomer.common.TenantContext;
import com.aicustomer.controller.AuthController;
import com.aicustomer.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * JWT 登录校验拦截器：解析 Authorization: Bearer <token>
 * 成功后将 username / tenantId 写入 request attribute，并设置租户上下文
 * 失败返回 401 统一响应体
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final JsonMapper jsonMapper;

    public AuthInterceptor(JwtUtil jwtUtil, JsonMapper jsonMapper) {
        this.jwtUtil = jwtUtil;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 放行 CORS 预检
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return reject(response, 401, "未登录或登录已过期");
        }

        String token = header.substring(7);
        String username = jwtUtil.parseUsername(token);
        if (username == null) {
            return reject(response, 401, "未登录或登录已过期");
        }
        Long tenantId = jwtUtil.parseTenantId(token);

        request.setAttribute(AuthController.ATTR_USERNAME, username);
        request.setAttribute(AuthController.ATTR_TENANT_ID, tenantId);
        TenantContext.set(tenantId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        // 请求结束清理租户上下文，防止线程池复用导致串租户
        TenantContext.clear();
    }

    private boolean reject(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(code);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(ApiResponse.error(code, message)));
        return false;
    }
}
