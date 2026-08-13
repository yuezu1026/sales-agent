package com.aicustomer.controller;

import com.aicustomer.common.ApiResponse;
import com.aicustomer.entity.Tenant;
import com.aicustomer.entity.User;
import com.aicustomer.repository.TenantRepository;
import com.aicustomer.repository.UserRepository;
import com.aicustomer.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 租户管理接口（仅系统管理员，平台级）
 * M8.3：免费试用阶段仅提供只读列表（租户名/管理员/套餐/状态/用户数/创建/到期），不做停用/启用操作。
 */
@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    public TenantController(TenantRepository tenantRepository, UserRepository userRepository,
                            UserService userService) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    /** 租户列表（仅系统管理员） */
    @GetMapping
    public ApiResponse<List<TenantVO>> list(HttpServletRequest request) {
        userService.requireSystemAdmin(currentUsername(request));
        List<Tenant> tenants = tenantRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        if (tenants.isEmpty()) {
            return ApiResponse.ok(List.of());
        }
        // 用户数：一次性聚合，避免 N+1
        Map<Long, Long> counts = userRepository.countGroupByTenantId().stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        // 租户管理员用户名：批量查，容忍 owner 为 null / 用户已删除
        // 注意：ownerNames 必须用 HashMap（不可用 Map.of），否则 get(null) 会抛 NPE
        Set<Long> ownerIds = tenants.stream().map(Tenant::getOwnerUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> ownerNames = new HashMap<>();
        if (!ownerIds.isEmpty()) {
            ownerNames.putAll(userRepository.findAllById(ownerIds).stream()
                    .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a)));
        }
        return ApiResponse.ok(tenants.stream()
                .map(t -> new TenantVO(t.getId(), t.getName(), t.getOwnerUserId(),
                        ownerNames.get(t.getOwnerUserId()), t.getPlan(), t.getStatus(),
                        t.getCreatedAt(), t.getExpireAt(), counts.getOrDefault(t.getId(), 0L)))
                .toList());
    }

    private String currentUsername(HttpServletRequest request) {
        return (String) request.getAttribute(AuthController.ATTR_USERNAME);
    }

    public record TenantVO(Long id, String name, Long ownerUserId, String ownerUsername,
                           String plan, String status, LocalDateTime createdAt,
                           LocalDateTime expireAt, long userCount) {
    }
}
