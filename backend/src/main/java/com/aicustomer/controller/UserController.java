package com.aicustomer.controller;

import com.aicustomer.common.ApiResponse;
import com.aicustomer.entity.User;
import com.aicustomer.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理接口（仅超级管理员 admin）
 * 普通操作员账号由超级管理员创建；操作员无系统设置权限
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 用户列表 */
    @GetMapping
    public ApiResponse<List<User>> list(HttpServletRequest request) {
        userService.requireAdmin(currentUsername(request));
        return ApiResponse.ok(userService.listAll());
    }

    /** 创建操作员账号（仅限 operator 角色） */
    @PostMapping
    public ApiResponse<User> create(@Valid @RequestBody CreateUserRequest req,
                                    HttpServletRequest request) {
        userService.requireAdmin(currentUsername(request));
        User user = userService.createOperator(req.username(), req.password(), req.displayName());
        return ApiResponse.ok(user);
    }

    /** 重置密码 */
    @PutMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                           @Valid @RequestBody ResetPasswordRequest req,
                                           HttpServletRequest request) {
        userService.requireAdmin(currentUsername(request));
        userService.resetPassword(id, req.newPassword());
        return ApiResponse.ok(null);
    }

    /** 启用 / 禁用账号 */
    @PutMapping("/{id}/status")
    public ApiResponse<Void> setStatus(@PathVariable Long id,
                                       @RequestBody SetStatusRequest req,
                                       HttpServletRequest request) {
        String operator = currentUsername(request);
        userService.requireAdmin(operator);
        userService.setStatus(id, req.status(), operator);
        return ApiResponse.ok(null);
    }

    private String currentUsername(HttpServletRequest request) {
        return (String) request.getAttribute(AuthController.ATTR_USERNAME);
    }

    public record CreateUserRequest(
            @NotBlank(message = "用户名不能为空") @Size(min = 3, max = 32, message = "用户名长度需 3-32 个字符") String username,
            @NotBlank(message = "密码不能为空") @Size(min = 8, message = "密码至少 8 位") String password,
            String displayName) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "新密码不能为空") @Size(min = 8, message = "新密码至少 8 位") String newPassword) {
    }

    public record SetStatusRequest(@NotBlank String status) {
    }
}
