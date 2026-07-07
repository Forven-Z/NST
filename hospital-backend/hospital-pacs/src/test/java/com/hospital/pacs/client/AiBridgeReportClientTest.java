package com.hospital.pacs.client;

import com.hospital.common.exception.BusinessException;
import com.hospital.pacs.config.AiBridgeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiBridgeReportClientTest {

    private AiBridgeReportClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        AiBridgeProperties properties = new AiBridgeProperties();
        properties.setBaseUrl("http://ai-bridge");
        client = new AiBridgeReportClient(properties, restTemplate);
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void returnsAiReportTextFromSuccessResponse() {
        server.expect(requestTo("http://ai-bridge/api/v1/ai/reports/imaging/impression"))
                .andExpect(jsonPath("$.findingsText").value("颅脑CT平扫未见明显异常。"))
                .andExpect(jsonPath("$.modality").value("CT_HEAD"))
                .andRespond(withSuccess("""
                        {
                          "code": 200,
                          "success": true,
                          "data": {
                            "aiReportText": "颅脑CT平扫未见明确急性颅内异常征象。",
                            "aiReportStatus": "READY"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        String result = client.generateImagingImpression(
                Map.of("itemName", "头部 CT"),
                Map.of("modality", "CT_HEAD"),
                "颅脑CT平扫未见明显异常。");

        assertThat(result).isEqualTo("颅脑CT平扫未见明确急性颅内异常征象。");
        server.verify();
    }

    @Test
    void propagatesAiBridgeFailure() {
        server.expect(requestTo("http://ai-bridge/api/v1/ai/reports/imaging/impression"))
                .andRespond(withSuccess("""
                        {
                          "code": 50301,
                          "success": false,
                          "message": "AI 报告生成服务未启用或未配置"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generateImagingImpression(Map.of(), null, "颅脑CT平扫未见明显异常。"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未启用");
        server.verify();
    }

    @Test
    void sendsLungOrTumorContextToGenericImagingEndpoint() {
        server.expect(requestTo("http://ai-bridge/api/v1/ai/reports/imaging/impression"))
                .andExpect(jsonPath("$.itemName").value("肺部 CT"))
                .andExpect(jsonPath("$.modality").value("CT_LUNG"))
                .andExpect(jsonPath("$.bodyPart").value("胸部"))
                .andRespond(withSuccess("""
                        {
                          "code": 200,
                          "success": true,
                          "data": {
                            "aiReportText": "双肺纹理增多，建议结合临床随访。",
                            "aiReportStatus": "READY"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        String result = client.generateImagingImpression(
                Map.of("itemName", "肺部 CT", "bodyPart", "胸部"),
                Map.of("modality", "CT_LUNG"),
                "双肺纹理增多。");

        assertThat(result).isEqualTo("双肺纹理增多，建议结合临床随访。");
        server.verify();
    }
}
