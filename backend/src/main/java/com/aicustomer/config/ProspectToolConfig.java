package com.aicustomer.config;

import com.aicustomer.service.prospect.ProspectTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 潜客挖掘 Function Calling 工具注册（M2-2）
 * 将 search_company 工具暴露为 Spring AI ToolCallbackProvider——
 * 将来 ChatClient 注入该 provider 后，LLM 即可自主调用数据源挖掘企业
 * （自然语言挖掘，如"帮我挖掘深圳的 SaaS 企业"）。
 */
@Configuration
public class ProspectToolConfig {

    @Bean
    public ToolCallbackProvider prospectToolCallbackProvider(ProspectTools prospectTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(prospectTools)
                .build();
    }
}
