package com.hospital.aibridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.aibridge.config.AiProperties;
import com.hospital.aibridge.domain.TriageMessage;
import com.hospital.aibridge.domain.TriageSession;
import com.hospital.aibridge.dto.AiTriageResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class SpringAiTriageClient {

    private static final String SYSTEM_PROMPT = """
            你是智慧云脑诊疗平台的 AI 分诊助手。
            你只能提供就诊分诊参考，不能做确诊，不能开药，不能给出具体治疗方案。
            你必须用中文回答。
            你必须只输出 JSON，不要输出 Markdown，不要解释 JSON。
            每次追问最多 1-2 个问题，避免一次性询问过多内容。
            如出现急症风险，建议立即前往急诊或拨打 120。
            最多推荐 1-3 个科室。
            不允许确诊疾病，不允许推荐药物、剂量或治疗方案，不允许承诺无需就医。
            JSON 字段固定为：
            {
              "reply": "给患者看的自然语言回复",
              "needMoreInfo": true,
              "needRegister": true,
              "emergency": false,
              "emergencyReason": "",
              "summary": "对症状的简短总结",
              "questions": ["继续追问的问题"],
              "departments": [
                { "name": "科室名称", "confidence": 0.8, "reason": "推荐理由" }
              ]
            }
            """;

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;

    public SpringAiTriageClient(
            AiProperties aiProperties,
            ObjectMapper objectMapper,
            ObjectProvider<ChatClient.Builder> chatClientBuilderProvider) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        this.chatClient = builder == null ? null : builder.build();
    }

    public Optional<AiTriageResult> analyze(TriageSession session, String latestMessage, boolean askMore) {
        if (!aiProperties.isEnabled() || chatClient == null) {
            return Optional.empty();
        }
        String userPrompt = """
                当前任务：%s。
                已有摘要：%s
                历史对话：
                %s
                患者最新输入：%s
                """.formatted(
                askMore ? "判断还缺少哪些关键信息，并生成 1-2 个追问问题" : "给出是否建议挂号和推荐科室",
                StringUtils.hasText(session.getSummary()) ? session.getSummary() : "暂无",
                history(session),
                latestMessage
        );

        try {
            String content = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();
            return Optional.of(objectMapper.readValue(cleanJson(content), AiTriageResult.class));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String history(TriageSession session) {
        return session.getMessages().stream()
                .map(this::formatMessage)
                .collect(Collectors.joining("\n"));
    }

    private String formatMessage(TriageMessage message) {
        return message.role() + ": " + message.content();
    }

    private String cleanJson(String content) {
        if (content == null) {
            return "{}";
        }
        String value = content.trim();
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return value.substring(start, end + 1);
        }
        return value;
    }
}
