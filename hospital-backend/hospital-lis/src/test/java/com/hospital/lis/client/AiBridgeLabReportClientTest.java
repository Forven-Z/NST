package com.hospital.lis.client;

import com.hospital.common.exception.BusinessException;
import com.hospital.lis.config.AiBridgeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiBridgeLabReportClientTest {

    private AiBridgeLabReportClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        AiBridgeProperties properties = new AiBridgeProperties();
        properties.setBaseUrl("http://ai-bridge");
        client = new AiBridgeLabReportClient(properties, restTemplate);
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void returnsAiReportTextFromSuccessResponse() {
        server.expect(requestTo("http://ai-bridge/api/v1/ai/reports/lab/analysis"))
                .andExpect(jsonPath("$.itemName").value("血常规"))
                .andExpect(jsonPath("$.items[0].name").value("白细胞"))
                .andRespond(withSuccess("""
                        {
                          "code": 200,
                          "success": true,
                          "data": {
                            "aiReportText": "白细胞计数升高，提示可能存在感染或炎症反应。",
                            "aiReportStatus": "READY"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        String result = client.generateLabAnalysis(
                Map.of("itemName", "血常规"),
                List.of(Map.of("name", "白细胞", "result", "12.8", "unit", "×10^9/L", "flag", "H")));

        assertThat(result).isEqualTo("白细胞计数升高，提示可能存在感染或炎症反应。");
        server.verify();
    }

    @Test
    void propagatesAiBridgeFailure() {
        server.expect(requestTo("http://ai-bridge/api/v1/ai/reports/lab/analysis"))
                .andRespond(withSuccess("""
                        {
                          "code": 50301,
                          "success": false,
                          "message": "AI 检验报告生成服务未启用或未配置"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generateLabAnalysis(
                Map.of(),
                List.of(Map.of("name", "白细胞", "result", "12.8"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未启用");
        server.verify();
    }
}
