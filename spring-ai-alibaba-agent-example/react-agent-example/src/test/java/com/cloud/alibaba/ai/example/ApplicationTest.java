package com.cloud.alibaba.ai.example;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;
import java.util.UUID;

@SpringBootTest
public class ApplicationTest {

    @Autowired
    ReactAgent agent;

    @Test
    void testReactAgent() throws Exception {
        String threadId = UUID.randomUUID().toString();
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(threadId).build();

        Object firstResult = agent.invokeAndGetOutput("帮我使用Python完成Socket编程实例代码，需要有服务端和客户端两份代码。", runnableConfig)
                .orElseThrow();

        if (firstResult instanceof InterruptionMetadata interruptionMetadata) {
            // 智能体因需人工批准而中断，提交反馈后恢复执行
            InterruptionMetadata feedbackMetadata = feedback(interruptionMetadata);
            secondCall(threadId, feedbackMetadata);
        } else if (firstResult instanceof NodeOutput nodeOutput) {
            // 智能体未触发需批准的工具，直接完成
            System.out.println(nodeOutput);
        } else {
            throw new AssertionError(
                    "Unexpected result type: " + (firstResult != null ? firstResult.getClass().getName() : "null"));
        }
    }

    private InterruptionMetadata feedback(InterruptionMetadata interruptionMetadata) {
        InterruptionMetadata.Builder newBuilder = InterruptionMetadata.builder()
                .nodeId(interruptionMetadata.node())
                .state(interruptionMetadata.state());

        interruptionMetadata.toolFeedbacks().forEach(toolFeedback -> {
            InterruptionMetadata.ToolFeedback editedFeedback = InterruptionMetadata.ToolFeedback
                    .builder(toolFeedback)
                    .result(InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED)
                    .build();
            newBuilder.addToolFeedback(editedFeedback);
        });

        return newBuilder.build();
    }

    private void secondCall(String threadId, InterruptionMetadata feedbackMetadata) throws GraphRunnerException {
        RunnableConfig resumeRunnableConfig = RunnableConfig.builder().threadId(threadId)
                .addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, feedbackMetadata)
                .build();
        Optional<NodeOutput> result = agent.invokeAndGetOutput("", resumeRunnableConfig);
        NodeOutput finalOutput = result.orElseThrow();
        System.out.println(finalOutput);
    }

}
