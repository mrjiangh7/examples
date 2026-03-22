package com.cloud.alibaba.ai.example.tools;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.Optional;

public class WeatherTool implements Tool<WeatherTool.Request, String> {
    @Override
    public ToolCallback toolCallback() {
        return FunctionToolCallback.builder("get_weather", this)
                .description("Get the current weather for a specified location.")
                .inputType(Request.class)
                .build();
    }

    @Override
    public String apply(Request request, ToolContext toolContext) {
        // 优先从工具上下文取用户位置；未注入时用入参 location，避免 Map.get 为 null 时 toString NPE
        String userLocation = Optional.ofNullable(toolContext.getContext())
                .map(ctx -> ctx.get("userLocation"))
                .map(Object::toString)
                .filter(s -> !s.isBlank())
                .orElse(Optional.ofNullable(request.location()).orElse(""));
        if (userLocation.contains("苏州")) {
            return "苏州的天气小雨，温度15度，湿度60%";
        } else {
            return "北京的天气晴朗，温度25度，湿度10%";
        }
    }

    public record Request(
            @JsonProperty(value = "location", required = true) @JsonPropertyDescription("The location to get weather information. ") String location) {
    }
}
