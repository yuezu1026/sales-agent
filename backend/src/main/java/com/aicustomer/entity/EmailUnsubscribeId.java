package com.aicustomer.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * 退订黑名单复合主键：(tenant_id, email)
 */
public class EmailUnsubscribeId implements Serializable {

    private Long tenantId;
    private String email;

    public EmailUnsubscribeId() {
    }

    public EmailUnsubscribeId(Long tenantId, String email) {
        this.tenantId = tenantId;
        this.email = email;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EmailUnsubscribeId that)) {
            return false;
        }
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(email, that.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, email);
    }
}
