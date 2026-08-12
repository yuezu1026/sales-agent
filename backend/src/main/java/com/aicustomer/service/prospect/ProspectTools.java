package com.aicustomer.service.prospect;

import com.aicustomer.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 潜客挖掘 Function Calling 工具（M2-2）
 * 通过 Spring AI @Tool 注解注册 search_company 工具，配合
 * MethodToolCallbackProvider 暴露给 ChatClient——将来支持自然语言挖掘
 * （如"帮我挖掘深圳的 SaaS 企业"）时，LLM 自主决定调用该工具。
 * 当前 MVP 由 ProspectController 直接调用同一实现（企业数据是结构化数据，
 * 直接返回 JSON 列表比经 LLM 中转更稳定）。
 */
@Component
public class ProspectTools {

    private static final Logger log = LoggerFactory.getLogger(ProspectTools.class);

    private final ProspectService prospectService;
    private final ObjectMapper objectMapper;

    public ProspectTools(ProspectService prospectService, ObjectMapper objectMapper) {
        this.prospectService = prospectService;
        this.objectMapper = objectMapper;
    }

    /**
     * 搜索企业潜客（挖掘候选）
     *
     * @param industry 行业关键词（如 SaaS、金融科技），可空
     * @param region   地区（如 深圳、上海），可空
     * @param scale    规模（如 50-200人），可空
     * @param keyword  公司名/行业模糊关键词，可空
     * @param limit    最大返回条数（默认 20）
     * @return 命中企业列表 JSON
     */
    @Tool(name = "search_company",
            description = "按行业/地区/规模/关键词搜索企业潜客候选（来自已配置的企业数据源，如企查查或内置演示库）。"
                    + "返回公司名、联系人、邮箱、电话、行业、地区、规模、官网等字段的 JSON 数组")
    public String searchCompany(
            @ToolParam(description = "行业关键词，如 SaaS、金融科技；可空", required = false) String industry,
            @ToolParam(description = "地区，如 深圳、上海；可空", required = false) String region,
            @ToolParam(description = "规模，如 50-200人、500-1000人；可空", required = false) String scale,
            @ToolParam(description = "公司名/行业模糊关键词；可空", required = false) String keyword,
            @ToolParam(description = "最大返回条数，默认 20", required = false) Integer limit) {
        try {
            var result = prospectService.search(
                    new com.aicustomer.dto.ProspectQuery(industry, region, scale, keyword),
                    limit != null && limit > 0 ? Math.min(limit, 50) : 20);
            return objectMapper.writeValueAsString(result);
        } catch (BizException e) {
            log.warn("search_company 业务错误: {}", e.getMessage());
            return "{\"error\":\"" + e.getMessage() + "\"}";
        } catch (Exception e) {
            log.warn("search_company 调用失败: {}", e.getMessage());
            return "{\"error\":\"数据源调用失败\"}";
        }
    }
}
