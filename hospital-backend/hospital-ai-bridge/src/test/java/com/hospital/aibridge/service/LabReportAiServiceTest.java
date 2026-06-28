package com.hospital.aibridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.aibridge.config.AiProperties;
import com.hospital.aibridge.dto.LabAnalysisRequest;
import com.hospital.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LabReportAiServiceTest {

    private final LabReportAiService service = new LabReportAiService(
            new AiProperties(),
            new ObjectMapper(),
            new EmptyChatClientBuilderProvider());

    @Test
    void rejectsEmptyItems() {
        LabAnalysisRequest request = new LabAnalysisRequest();
        request.setItems(List.of());

        assertThatThrownBy(() -> service.generateLabAnalysis(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("检验项目");
    }

    @Test
    void rejectsDisabledAi() {
        LabAnalysisRequest request = new LabAnalysisRequest();
        request.setItems(List.of(Map.of("name", "白细胞", "result", "12.8", "flag", "H")));

        assertThatThrownBy(() -> service.generateLabAnalysis(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未启用");
    }

    @Test
    void parsesJsonAnalysisAndRemovesTitlePrefix() {
        String analysis = service.parseAnalysis("""
                ```json
                {"analysis":"诊断分析：白细胞计数升高，提示可能存在感染或炎症反应。"}
                ```
                """);

        assertThat(analysis).isEqualTo("白细胞计数升高，提示可能存在感染或炎症反应。");
    }

    @Test
    void rejectsInvalidModelOutput() {
        assertThatThrownBy(() -> service.parseAnalysis("白细胞计数升高"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("格式无效");
    }

    @Test
    void rejectsMissingAnalysisField() {
        assertThatThrownBy(() -> service.parseAnalysis("{\"impression\":\"白细胞计数升高\"}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("为空");
    }

    private static final class EmptyChatClientBuilderProvider
            implements ObjectProvider<org.springframework.ai.chat.client.ChatClient.Builder> {

        @Override
        public org.springframework.ai.chat.client.ChatClient.Builder getObject(Object... args) {
            return null;
        }

        @Override
        public org.springframework.ai.chat.client.ChatClient.Builder getIfAvailable() {
            return null;
        }

        @Override
        public org.springframework.ai.chat.client.ChatClient.Builder getIfUnique() {
            return null;
        }

        @Override
        public org.springframework.ai.chat.client.ChatClient.Builder getObject() {
            return null;
        }
    }
}
