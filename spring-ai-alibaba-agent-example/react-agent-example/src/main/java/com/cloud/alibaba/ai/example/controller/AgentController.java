package com.cloud.alibaba.ai.example.controller;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class AgentController {

    private final ReactAgent reactAgent;

    private final Map<String, InterruptionMetadata> map = new ConcurrentHashMap<>();

    public AgentController(ReactAgent reactAgent) {
        this.reactAgent = reactAgent;
    }

    @GetMapping("/invoke")
    @ResponseBody
    public InvokeResponse invoke(@RequestParam("query") String query,
                       @RequestParam("threadId") String threadId
    ) throws Exception {
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(threadId).build();
        Object output = reactAgent.invokeAndGetOutput(query, runnableConfig).orElseThrow();
        if (output instanceof InterruptionMetadata metadata) {
            map.put(threadId, metadata);
            return new InvokeResponse(true, metadata.toolFeedbacks(), null);
        }
        if (output instanceof NodeOutput nodeOutput) {
            return new InvokeResponse(false, List.of(), extractAssistantMessage(nodeOutput));
        }
        throw new IllegalStateException("Unexpected invoke result type: " + output.getClass().getName());
    }

    private static String extractAssistantMessage(NodeOutput result) {
        OverAllState state = result.state();
        Optional<Object> output = state.value("output");
        if (output.isPresent()) {
            String s = String.valueOf(output.get());
            if (!s.isBlank()) {
                return s;
            }
        }
        Optional<List<AbstractMessage>> messages = state.value("messages");
        if (messages.isPresent()) {
            List<AbstractMessage> msgList = messages.get();
            for (int i = msgList.size() - 1; i >= 0; i--) {
                AbstractMessage m = msgList.get(i);
                if (m instanceof AssistantMessage am) {
                    String t = am.getText();
                    if (t != null && !t.isBlank()) {
                        return t;
                    }
                }
            }
        }
        return "（本轮已结束：未解析到助手文本回复，请查看日志或消息状态。）";
    }

    @PostMapping("/feedback")
    @ResponseBody
    public InvokeResponse feedback(@RequestBody List<Feedback> feedbacks,
                         @RequestParam("threadId") String threadId
    ) throws Exception {
        InterruptionMetadata metadata = map.get(threadId);
        if (metadata == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no metadata found");
        }
        if (metadata.toolFeedbacks().size() != feedbacks.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "feedback size not match");
        }

        InterruptionMetadata.Builder newBuilder = InterruptionMetadata.builder()
                .nodeId(metadata.node())
                .state(metadata.state());
        for (int i = 0; i < feedbacks.size(); i++) {
            var toolFeedback = metadata.toolFeedbacks().get(i);
            InterruptionMetadata.ToolFeedback.Builder editedFeedbackBuilder = InterruptionMetadata.ToolFeedback
                    .builder(toolFeedback);
            if(feedbacks.get(i).isApproved()) {
                editedFeedbackBuilder.result(InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED);
            } else {
                editedFeedbackBuilder.result(InterruptionMetadata.ToolFeedback.FeedbackResult.REJECTED)
                        .description(feedbacks.get(i).feedback());
            }
            newBuilder.addToolFeedback(editedFeedbackBuilder.build());
        }
        RunnableConfig resumeRunnableConfig = RunnableConfig.builder().threadId(threadId)
                .addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, newBuilder.build())
                .build();
        Object output = reactAgent.invokeAndGetOutput("", resumeRunnableConfig).orElseThrow();
        if (output instanceof InterruptionMetadata next) {
            map.put(threadId, next);
            return new InvokeResponse(true, next.toolFeedbacks(), null);
        }
        if (output instanceof NodeOutput nodeOutput) {
            map.remove(threadId);
            return new InvokeResponse(false, List.of(), extractAssistantMessage(nodeOutput));
        }
        throw new IllegalStateException("Unexpected feedback resume result type: " + output.getClass().getName());
    }

    /**
     * 流式调用。底层走 LLM stream，满足 qwen3.5-plus 等模型对 incremental_output 的要求。
     * 注意：框架侧 stream 暂不支持 HITL 恢复，需人工批准时请仍用 /invoke + /feedback。
     */
    @GetMapping(value = "/invoke/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public Flux<ServerSentEvent<String>> invokeStream(
            @RequestParam("query") String query,
            @RequestParam("threadId") String threadId) {
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(threadId).build();
        Map<String, Object> initialState = Map.of("messages", new UserMessage(query));
        Flux<NodeOutput> flux = reactAgent.getAndCompileGraph().stream(initialState, runnableConfig);
        return flux
                .filter(out -> !out.isSTART() && !out.isEND())
                .map(out -> {
                    if (out instanceof StreamingOutput so) {
                        return ServerSentEvent.builder(so.chunk()).build();
                    }
                    return ServerSentEvent.builder(out.toString()).build();
                })
                .filter(e -> e.data() != null && !e.data().isBlank());
    }

    @GetMapping
    public String index() {
        return "index";
    }
}
