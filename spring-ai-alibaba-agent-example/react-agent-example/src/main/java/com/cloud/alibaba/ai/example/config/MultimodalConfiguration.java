package com.cloud.alibaba.ai.example.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 多模态 ChatModel 配置。仅在 app.agent.multimodal=true 时启用。
 * 多模态模型（如 qwen3.5-plus）要求流式调用时 incremental_output 才生效，请配合 /invoke/stream 使用。
 */
@Configuration
@ConditionalOnProperty(name = "app.agent.multimodal", havingValue = "true")
public class MultimodalConfiguration {

    private static final String MULTIMODAL_PATH = "/api/v1/services/aigc/multimodal-generation/generation";

    @Bean("multimodalChatModel")
    public ChatModel multimodalChatModel(
            @Value("${spring.ai.dashscope.api-key}") String apiKey,
            @Value("${spring.ai.dashscope.base-url:}") String baseUrl,
            @Value("${spring.ai.dashscope.chat.options.model}") String model) {

        var apiBuilder = DashScopeApi.builder().apiKey(apiKey);
        if (StringUtils.hasText(baseUrl)) {
            apiBuilder.baseUrl(baseUrl);
        }
        apiBuilder.completionsPath(MULTIMODAL_PATH);
        DashScopeApi api = apiBuilder.build();

        return DashScopeChatModel.builder()
                .dashScopeApi(api)
                .defaultOptions(DashScopeChatOptions.builder()
                        .model(model)
                        .multiModel(true)
                        .incrementalOutput(true)  // qwen3.5-plus 等模型要求必须为 true，否则报 This model only supports incremental_output set to True
                        .build())
                .build();
    }
}
