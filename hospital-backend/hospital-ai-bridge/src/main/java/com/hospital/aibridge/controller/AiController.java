package com.hospital.aibridge.controller;

import com.hospital.common.Result;
import com.hospital.aibridge.dto.AssistantStreamRequest;
import com.hospital.aibridge.dto.DiagnosisSuggestRequest;
import com.hospital.aibridge.dto.DoctorAiDraftRequest;
import com.hospital.aibridge.dto.TriageBindRegisterRequest;
import com.hospital.aibridge.dto.TriageChatRequest;
import com.hospital.aibridge.dto.TriageChatResponse;
import com.hospital.aibridge.repository.AiChatSessionRepository;
import com.hospital.aibridge.service.AiStubService;
import com.hospital.aibridge.service.DoctorAiAssistService;
import com.hospital.aibridge.service.TriageChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiStubService aiStubService;
    private final TriageChatService triageChatService;
    private final DoctorAiAssistService doctorAiAssistService;
    private final AiChatSessionRepository aiChatSessionRepository;

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(Map.of(
                "status", "UP",
                "service", "hospital-ai-bridge",
                "stub", true
        ));
    }

    @PostMapping("/triage/chat")
    public Result<TriageChatResponse> triageChat(@RequestBody TriageChatRequest request) {
        return Result.success(triageChatService.chat(request));
    }

    @PostMapping("/triage/sessions/{sessionNo}/bind-register")
    public Result<Map<String, Object>> bindTriageRegister(
            @PathVariable String sessionNo,
            @RequestBody TriageBindRegisterRequest request) {
        int updated = aiChatSessionRepository.bindRegister(sessionNo, request.getRegisterId());
        return Result.success(Map.of(
                "sessionNo", sessionNo,
                "registerId", request.getRegisterId(),
                "bound", updated > 0,
                "updated", updated
        ));
    }

    @PostMapping("/assistant/stream")
    public Result<Map<String, Object>> assistantStream(@RequestBody AssistantStreamRequest request) {
        // TODO: switch to a real text/event-stream endpoint after the frontend is ready for SSE.
        return Result.success(aiStubService.assistantStream(request.getRegisterId(), request.getMessage()));
    }

    @PostMapping("/diagnosis/suggest")
    public Result<Map<String, Object>> diagnosisSuggest(@RequestBody DiagnosisSuggestRequest request) {
        return Result.success(doctorAiAssistService.diagnosisSuggest(request));
    }

    @PostMapping("/doctor/drafts")
    public Result<Map<String, Object>> generateDoctorDraft(@RequestBody DoctorAiDraftRequest request) {
        return Result.success(doctorAiAssistService.generateDraft(request));
    }
}
