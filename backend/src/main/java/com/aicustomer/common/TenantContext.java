package com.aicustomer.common;

/**
 * 租户上下文（ThreadLocal）：请求线程内保存当前租户 id。
 * AuthInterceptor 在 preHandle 设置、afterCompletion 清理；
 * Service/Repository 通过 {@link #get()} 读取，实现数据按租户隔离。
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long tenantId) {
        CURRENT.set(tenantId);
    }

    /** 当前请求的租户 id；平台级账号（如平台管理员）为 null */
    public static Long get() {
        return CURRENT.get();
    }

    /** 当前租户 id，平台级账号访问业务接口时抛 400 */
    public static Long require() {
        Long tenantId = CURRENT.get();
        if (tenantId == null) {
            throw BizException.badRequest("当前账号无租户上下文，请使用注册的租户账号操作");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
