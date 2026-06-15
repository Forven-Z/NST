package com.hospital.aibridge.service;

import com.hospital.aibridge.config.AiProperties;
import com.hospital.aibridge.domain.TriageSession;
import com.hospital.aibridge.domain.TriageStage;
import com.hospital.aibridge.dto.TriageChatResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class TriageRuleEngine {

    private static final List<String> EMERGENCY_KEYWORDS = List.of(
            "胸痛", "胸闷", "呼吸困难", "喘不上气", "昏迷", "意识不清", "抽搐", "偏瘫",
            "口角歪斜", "说话不清", "大出血", "剧烈头痛", "休克", "严重过敏", "吞咽困难",
            "高热不退", "孕妇腹痛", "黑便", "呕血", "自杀", "服毒"
    );

    private static final List<String> DURATION_KEYWORDS = List.of(
            "今天", "昨天", "刚刚", "小时", "天", "周", "月", "年", "半天", "两天", "三天"
    );

    private static final List<String> SEVERITY_KEYWORDS = List.of(
            "轻微", "严重", "剧烈", "加重", "缓解", "反复", "持续", "一点", "很痛", "难受"
    );

    private final AiProperties aiProperties;

    public TriageRuleEngine(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    public TriageChatResponse emergencyResponse(TriageSession session, String message) {
        String hit = findEmergencyKeyword(message);
        if (hit == null) {
            return null;
        }
        String reason = "描述中包含急症风险信号：" + hit;
        session.setStage(TriageStage.EMERGENCY);
        session.setSummary(composeSummary(session, message));
        return TriageChatResponse.builder()
                .sessionId(session.getSessionId())
                .reply("你描述的症状可能存在急症风险，建议立即前往急诊或拨打 120，不建议仅通过线上分诊处理。")
                .stage(TriageStage.EMERGENCY)
                .needMoreInfo(false)
                .needRegister(false)
                .emergency(true)
                .emergencyReason(reason)
                .summary(session.getSummary())
                .safetyNotice(aiProperties.getSafetyNotice())
                .build();
    }

    public boolean shouldAskMore(TriageSession session, String message) {
        if (session.getRound() <= 1) {
            return true;
        }
        if (session.getRound() >= aiProperties.getMaxRounds()) {
            return false;
        }
        String allText = (session.getSummary() + " " + message).toLowerCase(Locale.ROOT);
        return !containsAny(allText, DURATION_KEYWORDS) || !containsAny(allText, SEVERITY_KEYWORDS);
    }

    public List<String> fallbackQuestions(String message) {
        List<String> questions = new ArrayList<>();
        if (!containsAny(message, DURATION_KEYWORDS)) {
            questions.add("这个症状大概持续多久了？");
        }
        if (!containsAny(message, SEVERITY_KEYWORDS)) {
            questions.add("症状严重程度如何，是持续加重还是有缓解？");
        }
        questions.add("是否伴有发热、呕吐、胸闷、呼吸困难或明显乏力？");
        return questions.stream().distinct().limit(2).toList();
    }

    public String composeSummary(TriageSession session, String latestMessage) {
        if (StringUtils.hasText(session.getSummary())) {
            return session.getSummary() + "；补充：" + latestMessage;
        }
        return "患者描述：" + latestMessage;
    }

    private String findEmergencyKeyword(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        return EMERGENCY_KEYWORDS.stream()
                .filter(message::contains)
                .findFirst()
                .orElse(null);
    }

    private boolean containsAny(String text, List<String> keywords) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return keywords.stream().anyMatch(text::contains);
        
    }
}
