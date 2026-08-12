package com.aicustomer.dto;

/**
 * 潜客挖掘条件（M2-2）：行业 / 地区 / 规模 / 关键词
 * 可空字段不筛选；关键词模糊匹配公司名与行业
 */
public record ProspectQuery(String industry, String region, String scale, String keyword) {

    public boolean hasCondition() {
        return isNotBlank(industry) || isNotBlank(region) || isNotBlank(scale) || isNotBlank(keyword);
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
