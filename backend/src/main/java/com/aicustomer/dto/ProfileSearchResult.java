package com.aicustomer.dto;

import com.aicustomer.entity.CustomerProfile;

/**
 * 画像语义检索结果（画像 + 相似度 0~1）
 */
public record ProfileSearchResult(CustomerProfile profile, double score) {
}
