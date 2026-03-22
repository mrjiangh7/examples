package com.cloud.alibaba.ai.example.tools;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.Optional;

public class UserLocationTool implements Tool<UserLocationTool.Request, String> {

    public record Request(
            @JsonProperty(value = "query", required = true) @JsonPropertyDescription("The query to get user location. ") String query) {
    }

    @Override
    public ToolCallback toolCallback() {
        return FunctionToolCallback.builder("user_location", this)
                .description("Tool for getting user location. ")
                .inputType(Request.class)
                .build();
    }

    @Override
    public String apply(Request request, ToolContext toolContext) {
        // 从工具上下文中获取用户信息（若未注入则回退到 query）；同程旅行总部 -> 苏州，否则北京
        String userLocation = Optional.ofNullable(toolContext.getContext())
                .map(ctx -> ctx.get("userLocation"))
                .map(Object::toString)
                .filter(s -> !s.isBlank())
                .orElse(Optional.ofNullable(request.query()).orElse(""));
        if (userLocation.contains("同程旅行总部")) {
            return "苏州";
        } else {
            return "北京";
        }
    }
}
