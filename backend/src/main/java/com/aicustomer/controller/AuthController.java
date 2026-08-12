package com.aicustomer.controller;

import com.aicustomer.common.ApiResponse;
import com.aicustomer.common.BizException;
import com.aicustomer.entity.LoginLog;
import com.aicustomer.entity.User;
import com.aicustomer.repository.LoginLogRepository;
import com.aicustomer.service.IpGeoService;
import com.aicustomer.service.UserService;
import com.aicustomer.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证接口：登录 / 当前用户 / 系统登录统计
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public static final String ATTR_USERNAME = "auth.username";

    /** 请求级租户上下文属性名（AuthInterceptor 写入，业务可读取） */
    public static final String ATTR_TENANT_ID = "auth.tenantId";

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final LoginLogRepository loginLogRepository;
    private final IpGeoService ipGeoService;

    public AuthController(UserService userService, JwtUtil jwtUtil, LoginLogRepository loginLogRepository,
                          IpGeoService ipGeoService) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.loginLogRepository = loginLogRepository;
        this.ipGeoService = ipGeoService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request,
                                                  HttpServletRequest httpRequest) {
        User user = userService.authenticate(request.username(), request.password());
        // M7.9 登录统计：记录登录日志 + 更新上次登录时间
        userService.recordLogin(user.getUsername());
        // M7.13：记录客户端真实 IP 并离线解析地理归属（失败不影响登录）
        String ip = clientIp(httpRequest);
        String geo = ipGeoService.resolve(ip);
        loginLogRepository.save(new LoginLog(user.getUsername(), ip, geo));
        String token = jwtUtil.generate(user.getUsername(), user.getTenantId());
        return ApiResponse.ok(Map.of(
                "token", token,
                "username", user.getUsername(),
                "displayName", user.getDisplayName(),
                "role", user.getRole(),
                "tenantId", user.getTenantId() == null ? 0L : user.getTenantId()
        ));
    }

    /**
     * SaaS 开放注册：创建独立租户 + 租户管理员，返回登录 token（注册即登录）
     */
    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request.username(), request.password(),
                request.displayName(), request.companyName());
        String token = jwtUtil.generate(user.getUsername(), user.getTenantId());
        return ApiResponse.ok(Map.of(
                "token", token,
                "username", user.getUsername(),
                "displayName", user.getDisplayName(),
                "role", user.getRole(),
                "tenantId", user.getTenantId()
        ));
    }

    /** 提取客户端真实 IP：优先 nginx 透传的 X-Forwarded-For 首段，其次 X-Real-IP，最后 socket 地址 */
    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String first = xff.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return request.getRemoteAddr();
    }

    /** 系统登录统计（免登录）：累计登录次数 / 今日登录次数 / 今日登录人数 */
    @GetMapping("/login-stats")
    public ApiResponse<LoginStats> loginStats() {
        // M7.12：显式按中国时区取“今日”，避免容器 UTC 导致北京时间 0-8 点不归零
        LocalDateTime todayStart = LocalDate.now(ZoneId.of("Asia/Shanghai")).atStartOfDay();
        long total = loginLogRepository.count();
        long today = loginLogRepository.countByLoginAtGreaterThanEqual(todayStart);
        long todayUsers = loginLogRepository.countDistinctUsernameAfter(todayStart);
        return ApiResponse.ok(new LoginStats(total, today, todayUsers));
    }

    public record LoginStats(long totalLogins, long todayLogins, long todayUsers) {
    }

    private static final DateTimeFormatter DF_DAY = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter DF_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * 系统登录次数趋势（M7.10 曲线图）：按日/周/月/年聚合登录次数。
     * range: daily=最近 14 天，weekly=最近 12 周（按周一），monthly=最近 12 个月，yearly=最近 5 年。
     * 无数据的桶补 0，保证曲线连续。
     */
    @GetMapping("/login-trend")
    public ApiResponse<LoginTrend> loginTrend(@RequestParam(defaultValue = "daily") String range) {
        // M7.12：按中国时区取“今天”，保证日/周/月/年桶边界正确
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        List<String> labels = new ArrayList<>();
        LocalDateTime queryStart;
        int bucketDays;
        switch (range) {
            case "weekly" -> {
                // 本周一为最后一个桶，向前取 12 周
                LocalDate monday = today.with(ChronoField.DAY_OF_WEEK, 1);
                LocalDate start = monday.minusWeeks(11);
                for (int i = 0; i < 12; i++) {
                    labels.add(start.plusWeeks(i).format(DF_DAY));
                }
                queryStart = start.atStartOfDay();
                bucketDays = 7;
            }
            case "monthly" -> {
                YearMonth startYm = YearMonth.from(today).minusMonths(11);
                for (int i = 0; i < 12; i++) {
                    labels.add(startYm.plusMonths(i).format(DF_MONTH));
                }
                queryStart = startYm.atDay(1).atStartOfDay();
                bucketDays = 0; // 月桶走 YearMonth 差值
            }
            case "yearly" -> {
                int startYear = today.getYear() - 4;
                for (int i = 0; i < 5; i++) {
                    labels.add(String.valueOf(startYear + i));
                }
                queryStart = LocalDate.of(startYear, 1, 1).atStartOfDay();
                bucketDays = 0; // 年桶走年份差值
            }
            case "daily" -> {
                LocalDate start = today.minusDays(13);
                for (int i = 0; i < 14; i++) {
                    labels.add(start.plusDays(i).format(DF_DAY));
                }
                queryStart = start.atStartOfDay();
                bucketDays = 1;
            }
            default -> throw BizException.badRequest("range 仅支持 daily / weekly / monthly / yearly");
        }

        long[] counts = new long[labels.size()];
        for (LoginLog log : loginLogRepository.findByLoginAtGreaterThanEqual(queryStart)) {
            LocalDate d = log.getLoginAt().toLocalDate();
            int idx;
            if (bucketDays == 0) {
                idx = range.equals("monthly")
                        ? (int) ChronoUnit.MONTHS.between(YearMonth.from(today).minusMonths(11), YearMonth.from(d))
                        : d.getYear() - (today.getYear() - 4);
            } else {
                idx = (int) ((d.toEpochDay() - queryStart.toLocalDate().toEpochDay()) / bucketDays);
            }
            if (idx >= 0 && idx < counts.length) {
                counts[idx]++;
            }
        }

        List<TrendPoint> points = new ArrayList<>(labels.size());
        for (int i = 0; i < labels.size(); i++) {
            points.add(new TrendPoint(labels.get(i), counts[i]));
        }
        return ApiResponse.ok(new LoginTrend(range, points));
    }

    public record LoginTrend(String range, List<TrendPoint> points) {
    }

    public record TrendPoint(String label, long count) {
    }

    /**
     * M7.13：访问者地理分布（工作台中国地图散点）。
     * 按 geo 串分组聚合后拆出省/市返回，前端按市（缺省回退省）定位散点。
     * 同一省市多次登录合并计数。
     */
    @GetMapping("/login-geo")
    public ApiResponse<LoginGeo> loginGeo() {
        Map<String, GeoPoint> merged = new HashMap<>();
        for (Object[] row : loginLogRepository.countByGeo()) {
            String geo = (String) row[0];
            long count = ((Number) row[1]).longValue();
            String province = IpGeoService.provinceOf(geo);
            String city = IpGeoService.cityOf(geo);
            if (province == null && city == null) {
                continue; // 海外/未知定位不上图
            }
            String key = (province == null ? "" : province) + "|" + (city == null ? "" : city);
            merged.merge(key, new GeoPoint(province, city, count),
                    (a, b) -> new GeoPoint(a.province(), a.city(), a.count() + b.count()));
        }
        return ApiResponse.ok(new LoginGeo(new ArrayList<>(merged.values())));
    }

    public record LoginGeo(List<GeoPoint> points) {
    }

    public record GeoPoint(String province, String city, long count) {
    }

    @GetMapping("/me")
    public ApiResponse<User> me(HttpServletRequest request) {
        String username = (String) request.getAttribute(ATTR_USERNAME);
        return ApiResponse.ok(userService.findByUsername(username));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                            HttpServletRequest httpRequest) {
        String username = (String) httpRequest.getAttribute(ATTR_USERNAME);
        userService.changePassword(username, request.oldPassword(), request.newPassword());
        return ApiResponse.ok(null);
    }

    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    public record RegisterRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password,
            String displayName,
            String companyName) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "原密码不能为空") String oldPassword,
            @NotBlank(message = "新密码不能为空") String newPassword) {
    }
}
