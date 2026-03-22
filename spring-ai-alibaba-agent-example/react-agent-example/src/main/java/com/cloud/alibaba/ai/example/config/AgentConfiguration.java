package com.cloud.alibaba.ai.example.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.cloud.alibaba.ai.example.interceptor.LogToolInterceptor;
import com.cloud.alibaba.ai.example.tools.FileReadTool;
import com.cloud.alibaba.ai.example.tools.FileWriteTool;
import com.cloud.alibaba.ai.example.tools.UserLocationTool;
import com.cloud.alibaba.ai.example.tools.WeatherTool;

@Configuration
public class AgentConfiguration {

    private final ChatModel chatModel;

    public AgentConfiguration(@Qualifier("dashScopeChatModel") ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Bean
    public ReactAgent reactAgent() throws GraphStateException {
        return ReactAgent.builder()
                .name("agent")
                .description("This is a react agent")
                .model(chatModel)
                .saver(new MemorySaver())
                .tools(
                        new FileReadTool().toolCallback(),
                        new FileWriteTool().toolCallback(),
                        new WeatherTool().toolCallback(),
                        new UserLocationTool().toolCallback())
                .hooks(HumanInTheLoopHook.builder()
                        .approvalOn("file_write", "Write File should be approved")
                        .approvalOn("get_weather", "Weather should be approved")
                        .approvalOn("user_location", "User location should be approved")
                        .build())
                .interceptors(new LogToolInterceptor())
                .build();
    }
}
