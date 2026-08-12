package com.aicustomer.service.prospect;

import com.aicustomer.dto.ProspectCompany;
import com.aicustomer.dto.ProspectQuery;

import java.util.List;

/**
 * 潜客数据源 Provider（M2-2）——Function Calling 对接的外部能力抽象。
 * 每个实现对应一种企业数据源：
 * - MockCompanyDataProvider：内置演示数据（默认，无 API Key 可验证全链路）
 * - QichachaDataProvider：企查查真实 API（预留，需配置 base_url + api_key）
 * 挖掘服务（ProspectService）按数据源配置选择 Provider，将来自然语言挖掘
 * 可把同一能力注册为 Spring AI @Tool 供 LLM 自主调用。
 */
public interface CompanyDataProvider {

    /** 数据源类型标识（与 data_source.type 对应） */
    String type();

    /** 按条件搜索企业（无条件时返回示例数据，最多 limit 条） */
    List<ProspectCompany> search(ProspectQuery query, int limit);
}
