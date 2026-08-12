package com.aicustomer.dto;

/**
 * 挖掘命中的潜客候选（数据源返回，未入库）
 * sourceType + sourceId：入库时用于去重（与 lead 表 uk_lead_source 对齐）
 * inLibrary：是否已在本系统客户库（前端置灰提示，避免重复入库）
 */
public record ProspectCompany(
        String companyName,
        String contactName,
        String contactEmail,
        String contactPhone,
        String industry,
        String region,
        String scale,
        String website,
        String address,
        String sourceType,
        String sourceId,
        boolean inLibrary) {
}
