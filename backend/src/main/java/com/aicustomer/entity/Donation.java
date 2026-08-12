package com.aicustomer.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 捐助记录（表 donations）：捐助拾客 Shike 开源项目的开发开销。
 * 金额以分为单位存储（amount_cents），避免浮点误差。
 */
@Entity
@Table(name = "donations")
public class Donation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 捐赠人（账号/昵称），选填，空则匿名用户 */
    @Column(name = "donor", nullable = false, length = 64)
    private String donor = "匿名用户";

    /** 金额（分） */
    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    /** 支付渠道：alipay / wechat */
    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDonor() {
        return donor;
    }

    public void setDonor(String donor) {
        this.donor = donor;
    }

    public Long getAmountCents() {
        return amountCents;
    }

    public void setAmountCents(Long amountCents) {
        this.amountCents = amountCents;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
