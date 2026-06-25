package com.hospital.disposal.service;

import com.hospital.disposal.dto.DisposalResultRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class DisposalExecuteServiceTest {

    @InjectMocks
    private DisposalExecuteService disposalExecuteService;

    @Test
    void resolveResultText_composesProcessAndOutcomeSegments() throws Exception {
        DisposalResultRequest request = new DisposalResultRequest();
        request.setProcessText("左侧卧位洗胃，出入量记录完整");
        request.setOutcomeText("患者生命体征平稳，未诉明显不适");

        Method method = DisposalExecuteService.class.getDeclaredMethod(
                "resolveResultText", DisposalResultRequest.class);
        method.setAccessible(true);
        String text = (String) method.invoke(disposalExecuteService, request);

        assertTrue(text.contains("【处置过程】"));
        assertTrue(text.contains("左侧卧位洗胃"));
        assertTrue(text.contains("【观察与结果】"));
        assertTrue(text.contains("生命体征平稳"));
    }
}
