package com.hospital.disposal.service;

import com.hospital.disposal.dto.DisposalResultRequest;
import com.hospital.disposal.repository.DisposalRequestRepository;
import com.hospital.disposal.support.DisposalAiReportCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class DisposalExecuteServiceTest {

    @Mock
    private DisposalRequestRepository disposalRequestRepository;
    @Mock
    private DisposalAiReportCache disposalAiReportCache;
    @InjectMocks
    private DisposalExecuteService disposalExecuteService;

    @Test
    void resolveResultText_composesAiAndDoctorSegments() throws Exception {
        DisposalResultRequest request = new DisposalResultRequest();
        request.setAiReportText("AI 处置摘要");
        request.setDoctorReportText("患者耐受良好");

        Method method = DisposalExecuteService.class.getDeclaredMethod(
                "resolveResultText", DisposalResultRequest.class);
        method.setAccessible(true);
        String text = (String) method.invoke(disposalExecuteService, request);

        assertTrue(text.contains("AI：AI 处置摘要"));
        assertTrue(text.contains("医师：患者耐受良好"));
    }
}
