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

    /** 用户列表（M8.6）：系统管理员看平台管理员；租户管理员看本租户（排除自己）；普通用户只看自己 */
    @GetMapping
    public ApiResponse<List<UserService.UserVO>> list(HttpServletRequest request) {
        String operator = currentUsername(request);
        userService.findByUsername(operator); // 仅登录校验，任意已登录角色可查（内容按角色分派）
        return ApiResponse.ok(userService.listAll(operator));
    }

    /** 所有用户列表（只读，仅系统管理员）：所有租户所有用户，含租户名，供「所有用户管理」视图 */
    @GetMapping("/all")
    public ApiResponse<List<UserService.UserVO>> listAllUsers(HttpServletRequest request) {
        userService.requireSystemAdmin(currentUsername(request));
        return ApiResponse.ok(userService.listAllUsers());
    }

    /** 创建用户：系统管理员可创建系统管理员/普通用户；普通管理员仅能创建本租户普通用户 */
    @PostMapping
    public ApiResponse<User> create(@Valid @RequestBody CreateUserRequest req,
                                    HttpServletRequest request) {
        String operator = currentUsername(request);
        userService.requireAdmin(operator);
        User user = userService.createUser(req.username(), req.password(), req.displayName(), req.role(), operator);
        return ApiResponse.ok(user);
    }

    /** 重置密码 */
    @PutMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                           @Valid @RequestBody ResetPasswordRequest req,
                                           HttpServletRequest request) {
        String operator = currentUsername(request);
        userService.requireAdmin(operator);
        userService.resetPassword(id, req.newPassword(), operator);
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
            String displayName,
            /** 目标角色：admin=系统管理员（仅系统管理员可指定）；缺省/operator=普通用户 */
            String role) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "新密码不能为空") @Size(min = 8, message = "新密码至少 8 位") String newPassword) {
    }

    public record SetStatusRequest(@NotBlank String status) {
    }
}
