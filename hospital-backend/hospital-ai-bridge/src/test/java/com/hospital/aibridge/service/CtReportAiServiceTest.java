package com.hospital.aibridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.aibridge.config.AiProperties;
import com.hospital.aibridge.dto.HeadCtImpressionRequest;
import com.hospital.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CtReportAiServiceTest {

    private final CtReportAiService service = new CtReportAiService(
            new AiProperties(),
            new ObjectMapper(),
            new EmptyChatClientBuilderProvider());

    @Test
    void rejectsBlankFindings() {
        HeadCtImpressionRequest request = new HeadCtImpressionRequest();
        request.setFindingsText(" ");

        assertThatThrownBy(() -> service.generateHeadCtImpression(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CT 所见");
    }

    @Test
    void rejectsDisabledAi() {
        HeadCtImpressionRequest request = new HeadCtImpressionRequest();
        request.setFindingsText("颅脑CT平扫未见明显异常。");

        assertThatThrownBy(() -> service.generateHeadCtImpression(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未启用");
    }

    @Test
    void parsesJsonImpressionAndRemovesTitlePrefix() {
        String impression = service.parseImpression("""
                ```json
                {"impression":"诊断印象：颅脑CT平扫未见明确急性颅内异常征象。"}
                ```
                """);

        assertThat(impression).isEqualTo("颅脑CT平扫未见明确急性颅内异常征象。");
    }

    @Test
    void rejectsInvalidModelOutput() {
        assertThatThrownBy(() -> service.parseImpression("颅脑CT平扫未见异常"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("格式无效");
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
