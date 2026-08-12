package com.aicustomer.common;

import org.springframework.http.HttpStatus;

/**
 * 业务异常：携带 HTTP 状态码与业务错误码
 */
public class BizException extends RuntimeException {

    private final int status;
    private final int code;

    public BizException(int status, int code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static BizException badRequest(String message) {
        return new BizException(HttpStatus.BAD_REQUEST.value(), 400, message);
    }

    public static BizException unauthorized(String message) {
        return new BizException(HttpStatus.UNAUTHORIZED.value(), 401, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(HttpStatus.FORBIDDEN.value(), 403, message);
    }

    public static BizException notFound(String message) {
        return new BizException(HttpStatus.NOT_FOUND.value(), 404, message);
    }

    public int getStatus() {
        return status;
    }

    public int getCode() {
        return code;
    }
}
