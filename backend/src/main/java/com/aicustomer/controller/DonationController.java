package com.aicustomer.controller;

import com.aicustomer.common.ApiResponse;
import com.aicustomer.common.BizException;
import com.aicustomer.entity.Donation;
import com.aicustomer.repository.DonationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 捐助拾客 Shike（开源项目开发开销）：
 * - POST /api/donations        创建捐助记录（公开免登录，演示环境模拟支付）
 * - GET  /api/donations        捐助记录分页 + 总额（公开免登录）
 * 真实支付（支付宝/微信）接入预留：channel 字段区分渠道，后续可替换为下单+回调流程。
 */
@RestController
@RequestMapping("/api/donations")
public class DonationController {

    private final DonationRepository donationRepository;

    public DonationController(DonationRepository donationRepository) {
        this.donationRepository = donationRepository;
    }

    /** 创建捐助记录：金额（分）1 元~10 万元，渠道 alipay/wechat，捐赠人选填（空=匿名用户） */
    @PostMapping
    public ApiResponse<Donation> donate(@RequestBody Map<String, Object> body) {
        Long amountCents = toLong(body.get("amountCents"));
        if (amountCents == null || amountCents < 100 || amountCents > 10_000_000) {
            throw BizException.badRequest("捐助金额需在 1 元 ~ 100000 元之间");
        }
        String channel = body.get("channel") == null ? "" : String.valueOf(body.get("channel")).trim();
        if (!"alipay".equals(channel) && !"wechat".equals(channel)) {
            throw BizException.badRequest("支付渠道不正确");
        }
        String donor = body.get("donor") == null ? "" : String.valueOf(body.get("donor")).trim();
        if (donor.length() > 64) {
            throw BizException.badRequest("捐赠人名称过长（最多 64 字符）");
        }

        Donation record = new Donation();
        record.setAmountCents(amountCents);
        record.setChannel(channel);
        record.setDonor(StringUtils.hasText(donor) ? donor : "匿名用户");
        return ApiResponse.ok(donationRepository.save(record));
    }

    /** 捐助记录：总额 + 分页列表（每页 10 条，按时间倒序） */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(value = "page", defaultValue = "0") int page,
                                                 @RequestParam(value = "size", defaultValue = "10") int size) {
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        Page<Donation> result = donationRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(p, s));
        long totalCents = donationRepository.findAll().stream()
                .mapToLong(Donation::getAmountCents).sum();
        Map<String, Object> data = new HashMap<>();
        data.put("totalCents", totalCents);
        data.put("page", p);
        data.put("totalPages", result.getTotalPages());
        data.put("items", result.getContent());
        return ApiResponse.ok(data);
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        try {
            if (v instanceof Number n) return n.longValue();
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
