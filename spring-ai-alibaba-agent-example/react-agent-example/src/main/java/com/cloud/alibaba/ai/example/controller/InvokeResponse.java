package com.cloud.alibaba.ai.example.controller;

import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;

import java.util.List;

/**
 * {@code invokeAndGetOutput} 可能返回需人工反馈的 {@link InterruptionMetadata}，也可能直接返回完成的 {@link com.alibaba.cloud.ai.graph.NodeOutput}。
 */
public record InvokeResponse(boolean requiresHumanFeedback, List<InterruptionMetadata.ToolFeedback> toolFeedbacks,
        String assistantMessage) {
}
