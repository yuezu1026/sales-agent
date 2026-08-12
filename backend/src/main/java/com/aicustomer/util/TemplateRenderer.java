package com.aicustomer.util;

import com.aicustomer.entity.Lead;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 邮件模板占位符渲染器（M3-2 补充）：
 * 把文本中的 {xxx} 占位符替换为 Lead 实际字段值；空字段 → 空串；未识别占位符原样保留
 * （如 {email} 由 appendUnsubscribe 处理退订 URL）。
 * 调用时机：保存草稿时（草稿箱看到替换后的真实内容）+ 发送时（兜底幂等替换）。
 * 支持变量：{companyName} {contactName} {contactEmail} {phone} {contactPhone} {gender}
 * {industry} {region} {scale} {website} {address} {date}（当天日期） {year}（当前年份）
 */
public final class TemplateRenderer {

    private TemplateRenderer() {
    }

    public static String render(String text, Lead lead) {
        if (text == null || !text.contains("{")) {
            return text;
        }
        String s = text;
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("companyName", nz(lead.getCompanyName()));
        vars.put("contactName", nz(lead.getContactName()));
        vars.put("contactEmail", nz(lead.getContactEmail()));
        vars.put("phone", nz(lead.getContactPhone()));
        vars.put("contactPhone", nz(lead.getContactPhone())); // phone 别名（用户直觉写法）
        vars.put("gender", nz(lead.getGender()));
        vars.put("industry", nz(lead.getIndustry()));
        vars.put("region", nz(lead.getRegion()));
        vars.put("scale", nz(lead.getScale()));
        vars.put("website", nz(lead.getWebsite()));
        vars.put("address", nz(lead.getAddress()));
        vars.put("date", LocalDate.now().toString());
        vars.put("year", String.valueOf(LocalDate.now().getYear()));
        for (Map.Entry<String, String> e : vars.entrySet()) {
            s = s.replace("{" + e.getKey() + "}", e.getValue());
        }
        return s;
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }
}
