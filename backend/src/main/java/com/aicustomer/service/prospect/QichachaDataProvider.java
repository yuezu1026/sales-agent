package com.aicustomer.service.prospect;

import com.aicustomer.common.BizException;
import com.aicustomer.dto.ProspectCompany;
import com.aicustomer.dto.ProspectQuery;
import com.aicustomer.entity.DataSource;
import com.aicustomer.repository.DataSourceRepository;
import com.aicustomer.util.AesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 企查查数据源 Provider（data_source.type = qichacha，预留）
 * 真实对接骨架：数据源启用 + 配置 api_base_url / api_key 后，通过企查查开放平台 API
 * 检索企业并映射为 ProspectCompany。
 * 注意：企查查开放平台接口需企业认证与 token 签名，接入时按官方文档调整请求参数
 * 与响应字段映射（当前解析常见 JSON 结构，失败时给出明确提示）。
 */
@Component
public class QichachaDataProvider implements CompanyDataProvider {

    private static final Logger log = LoggerFactory.getLogger(QichachaDataProvider.class);

    private final DataSourceRepository dataSourceRepository;
    private final AesUtil aesUtil;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public QichachaDataProvider(DataSourceRepository dataSourceRepository, AesUtil aesUtil,
                                ObjectMapper objectMapper) {
        this.dataSourceRepository = dataSourceRepository;
        this.aesUtil = aesUtil;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
    }

    @Override
    public String type() {
        return "qichacha";
    }

    @Override
    public List<ProspectCompany> search(ProspectQuery query, int limit) {
        DataSource ds = dataSourceRepository.findByType(type())
                .orElseThrow(() -> BizException.badRequest("企查查数据源未配置，请先在数据源管理中配置"));
        if (!Boolean.TRUE.equals(ds.getEnabled())) {
            throw BizException.badRequest("企查查数据源未启用，请先在数据源管理中启用");
        }
        if (!StringUtils.hasText(ds.getApiKeyEncrypted())) {
            throw BizException.badRequest("企查查数据源未配置 API Key，请在数据源管理中填写");
        }
        String baseUrl = StringUtils.hasText(ds.getApiBaseUrl())
                ? ds.getApiBaseUrl().replaceAll("/+$", "")
                : "https://api.qcc.com";
        String apiKey = aesUtil.decrypt(ds.getApiKeyEncrypted());
        String keyword = query != null && StringUtils.hasText(query.keyword())
                ? query.keyword().trim()
                : query != null && StringUtils.hasText(query.industry())
                        ? query.industry().trim()
                        : "科技";

        String url = baseUrl + "/api/company/search?key=" + encode(apiKey)
                + "&keyword=" + encode(keyword) + "&pageSize=" + limit;
        log.info("企查查检索: {}", url);
        String body;
        try {
            body = restClient.get().uri(url).retrieve().body(String.class);
        } catch (Exception e) {
            throw BizException.badRequest("企查查接口调用失败：" + e.getMessage());
        }
        return parse(body);
    }

    private List<ProspectCompany> parse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            // 兼容常见结构：data.result / data.items / result.data / items
            JsonNode arr = findArray(root);
            List<ProspectCompany> result = new ArrayList<>();
            if (arr == null) {
                return result;
            }
            for (JsonNode node : arr) {
                String name = text(node, "name", "companyName", "entName", "company_name");
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                result.add(new ProspectCompany(
                        name,
                        text(node, "legalPerson", "contactName", "legal_person"),
                        text(node, "email", "contactEmail"),
                        text(node, "phone", "contactPhone", "tel"),
                        text(node, "industry", "industryName"),
                        text(node, "region", "province", "address"),
                        text(node, "scale", "employeeSize"),
                        text(node, "website", "webUrl"),
                        text(node, "address", "regAddress"),
                        type(),
                        text(node, "id", "companyId", "creditCode"),
                        false));
            }
            return result;
        } catch (Exception e) {
            log.warn("企查查响应解析失败: {}", e.getMessage());
            throw BizException.badRequest("企查查响应解析失败，请核对数据源接口文档（响应：" + safePreview(body) + "）");
        }
    }

    private static JsonNode findArray(JsonNode root) {
        for (String key : new String[]{"result", "data", "items"}) {
            JsonNode node = root.get(key);
            if (node != null) {
                if (node.isArray()) {
                    return node;
                }
                JsonNode inner = findArray(node);
                if (inner != null) {
                    return inner;
                }
            }
        }
        return null;
    }

    private static String text(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode v = node.get(key);
            if (v != null && !v.isNull() && v.asText().isBlank() == false) {
                return v.asText().trim();
            }
        }
        return null;
    }

    private static String safePreview(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 120 ? body.substring(0, 120) + "…" : body;
    }

    private static String encode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
