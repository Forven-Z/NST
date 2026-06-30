package com.hospital.aibridge.service;

import com.hospital.aibridge.config.AiProperties;
import com.hospital.aibridge.domain.TriageSession;
import com.hospital.aibridge.domain.TriageStage;
import com.hospital.aibridge.dto.AiTriageResult;
import com.hospital.aibridge.dto.DepartmentRecommendation;
import com.hospital.aibridge.dto.TriageChatRequest;
import com.hospital.aibridge.dto.TriageChatResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class TriageChatService {

    private final TriageSessionStore sessionStore;
    private final TriageRuleEngine ruleEngine;
    private final DepartmentRecommendService departmentRecommendService;
    private final SpringAiTriageClient springAiTriageClient;
    private final AiProperties aiProperties;
    private final TriageQuestionPolicy questionPolicy;
    private final TriageResponseSanitizer responseSanitizer;

    public TriageChatService(
            TriageSessionStore sessionStore,
            TriageRuleEngine ruleEngine,
            DepartmentRecommendService departmentRecommendService,
            SpringAiTriageClient springAiTriageClient,
            AiProperties aiProperties,
            TriageQuestionPolicy questionPolicy,
            TriageResponseSanitizer responseSanitizer) {
        this.sessionStore = sessionStore;
        this.ruleEngine = ruleEngine;
        this.departmentRecommendService = departmentRecommendService;
        this.springAiTriageClient = springAiTriageClient;
        this.aiProperties = aiProperties;
        this.questionPolicy = questionPolicy;
        this.responseSanitizer = responseSanitizer;
    }

    public TriageChatResponse chat(TriageChatRequest request) {
        TriageSession session = sessionStore.getOrCreate(request.getSessionId(), request.getPatientId());
        if (questionPolicy.isInitialMessage(request.getMessage())) {
            TriageChatResponse response = initialResponse(session);
            response = responseSanitizer.sanitize(response);
            session.setStage(response.getStage());
            sessionStore.addMessage(session, "assistant", response.getReply());
            return response;
        }

        String message = request.getMessage().trim();
        sessionStore.addMessage(session, "user", message);

        TriageChatResponse emergencyResponse = ruleEngine.emergencyResponse(session, message);
        if (emergencyResponse != null) {
            emergencyResponse = responseSanitizer.sanitize(emergencyResponse);
            sessionStore.addMessage(session, "assistant", emergencyResponse.getReply());
            return emergencyResponse;
        }

        String fullText = buildFullText(session);
        boolean forceRecommendation = reachedMaxFollowUpRounds(session);
        Optional<AiTriageResult> aiResult = springAiTriageClient.analyze(session, message, forceRecommendation);
        TriageChatResponse response = aiResult
                .map(result -> buildFromAi(session, fullText, result, forceRecommendation))
                .orElseGet(() -> buildFallback(session, message,
                        !forceRecommendation && !questionPolicy.shouldRecommend(session, fullText)));
        response = responseSanitizer.sanitize(response);

        session.setStage(response.getStage());
        session.setSummary(response.getSummary());
        session.setRecommendedDepartments(response.getRecommendedDepartments());
        sessionStore.addMessage(session, "assistant", response.getReply());
        return response;
    }

    private TriageChatResponse initialResponse(TriageSession session) {
        return TriageChatResponse.builder()
                .sessionId(session.getSessionId())
                .reply("请简单描述你的主要不适，例如：头痛两天、咳嗽发热、腹痛腹泻、皮疹瘙痒等。")
                .stage(TriageStage.ASKING)
                .needMoreInfo(true)
                .needRegister(null)
                .emergency(false)
                .emergencyReason("")
                .summary("")
                .askedQuestions(List.of("请简单描述你的主要不适。"))
                .quickReplies(questionPolicy.initialQuickReplies())
                .safetyNotice(aiProperties.getSafetyNotice())
                .build();
    }

    private TriageChatResponse buildFromAi(TriageSession session, String fullText, AiTriageResult aiResult, boolean forceRecommendation) {
        boolean emergency = Boolean.TRUE.equals(aiResult.getEmergency());
        if (emergency) {
            return TriageChatResponse.builder()
                    .sessionId(session.getSessionId())
                    .reply(defaultText(aiResult.getReply(), "你描述的症状可能存在急症风险，建议立即前往急诊或拨打 120。"))
                    .stage(TriageStage.EMERGENCY)
                    .needMoreInfo(false)
                    .needRegister(false)
                    .emergency(true)
                    .emergencyReason(defaultText(aiResult.getEmergencyReason(), "AI 识别到急症风险信号"))
                    .summary(defaultText(aiResult.getSummary(), fullText))
                    .safetyNotice(aiProperties.getSafetyNotice())
                    .build();
        }

        if (!forceRecommendation && Boolean.TRUE.equals(aiResult.getNeedMoreInfo())) {
            List<String> questions = firstQuestions(aiResult.getQuestions(), fullText);
            String reply = defaultText(aiResult.getReply(), String.join(" ", questions));
            return TriageChatResponse.builder()
                    .sessionId(session.getSessionId())
                    .reply(reply)
                    .stage(TriageStage.ASKING)
                    .needMoreInfo(true)
                    .needRegister(null)
                    .emergency(false)
                    .emergencyReason("")
                    .summary(defaultText(aiResult.getSummary(), fullText))
                    .askedQuestions(questions)
                    .quickReplies(questionPolicy.quickRepliesForQuestions(questions))
                    .safetyNotice(aiProperties.getSafetyNotice())
                    .build();
        }

        boolean needRegister = forceRecommendation || !Boolean.FALSE.equals(aiResult.getNeedRegister());
        List<DepartmentRecommendation> departments = needRegister
                ? departmentRecommendService.normalizeAiDepartments(aiResult.getDepartments(), fullText)
                : List.of();
        return TriageChatResponse.builder()
                .sessionId(session.getSessionId())
                .reply(defaultText(aiResult.getReply(), "根据你提供的信息，建议选择推荐科室进行挂号，由医生进一步评估。"))
                .stage(needRegister ? TriageStage.RECOMMENDED : TriageStage.NO_REGISTER)
                .needMoreInfo(false)
                .needRegister(needRegister)
                .emergency(false)
                .emergencyReason("")
                .summary(defaultText(aiResult.getSummary(), fullText))
                .recommendedDepartments(departments)
                .safetyNotice(aiProperties.getSafetyNotice())
                .build();
    }

    private TriageChatResponse buildFallback(TriageSession session, String message, boolean askMore) {
        String summary = ruleEngine.composeSummary(session, message);
        if (askMore) {
            List<String> questions = questionPolicy.buildQuestions(buildFullText(session));
            return TriageChatResponse.builder()
                    .sessionId(session.getSessionId())
                    .reply(String.join(" ", questions))
                    .stage(TriageStage.ASKING)
                    .needMoreInfo(true)
                    .needRegister(null)
                    .emergency(false)
                    .emergencyReason("")
                    .summary(summary)
                    .askedQuestions(questions)
                    .quickReplies(questionPolicy.quickRepliesForQuestions(questions))
                    .safetyNotice(aiProperties.getSafetyNotice())
                    .build();
        }
        List<DepartmentRecommendation> departments = departmentRecommendService.recommendByRules(summary);
        return TriageChatResponse.builder()
                .sessionId(session.getSessionId())
                .reply("根据你提供的信息，建议先选择推荐科室挂号，由医生进一步评估。")
                .stage(TriageStage.RECOMMENDED)
                .needMoreInfo(false)
                .needRegister(true)
                .emergency(false)
                .emergencyReason("")
                .summary(summary)
                .recommendedDepartments(departments)
                .safetyNotice(aiProperties.getSafetyNotice())
                .build();
    }

    private List<String> firstQuestions(List<String> aiQuestions, String message) {
        List<String> questions = new ArrayList<>();
        if (aiQuestions != null) {
            aiQuestions.stream()
                    .filter(StringUtils::hasText)
                    .limit(2)
                    .forEach(questions::add);
        }
        if (questions.isEmpty()) {
            questions.addAll(questionPolicy.buildQuestions(message));
        }
        return questions.stream().distinct().limit(2).toList();
    }

    private String buildFullText(TriageSession session) {
        String messages = session.getMessages().stream()
                .filter(message -> "user".equals(message.role()))
                .map(message -> message.content())
                .filter(StringUtils::hasText)
                .reduce("", (left, right) -> left + " " + right)
                .trim();
        if (StringUtils.hasText(session.getSummary())) {
            return session.getSummary() + "；" + messages;
        }
        return messages;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private boolean reachedMaxFollowUpRounds(TriageSession session) {
        return session.getRound() >= aiProperties.getMaxFollowUpRounds();
    }
}
