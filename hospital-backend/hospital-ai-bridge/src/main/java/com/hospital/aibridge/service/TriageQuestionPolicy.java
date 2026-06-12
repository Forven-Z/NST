package com.hospital.aibridge.service;

import com.hospital.aibridge.config.AiProperties;
import com.hospital.aibridge.domain.TriageSession;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
public class TriageQuestionPolicy {

    private static final List<String> UNKNOWN_INTENTS = List.of("不知道", "不清楚", "先推荐", "直接推荐", "随便", "不确定");
    private static final List<String> DURATION_KEYWORDS = List.of(
            "刚刚", "今天", "昨天", "前天", "小时", "分钟", "天", "周", "月", "年", "半天", "一天", "两天", "三天", "一周", "两周"
    );
    private static final List<String> SEVERITY_KEYWORDS = List.of(
            "轻微", "轻度", "一般", "明显", "严重", "剧烈", "很痛", "特别痛", "加重", "缓解", "持续", "反复"
    );
    private static final List<String> BODY_PART_KEYWORDS = List.of(
            "头", "胸", "腹", "肚子", "胃", "咽", "喉咙", "鼻", "眼", "耳", "牙", "腰", "背", "腿", "手", "皮肤"
    );
    private static final List<String> COMPANION_KEYWORDS = List.of(
            "发热", "发烧", "咳嗽", "呕吐", "恶心", "腹泻", "胸闷", "呼吸困难", "乏力", "头晕", "皮疹", "瘙痒"
    );
    private static final List<String> SPECIAL_GROUP_KEYWORDS = List.of(
            "儿童", "小孩", "宝宝", "孕妇", "怀孕", "老人", "老年", "高血压", "糖尿病", "心脏病"
    );
    private static final List<String> SYMPTOM_KEYWORDS = List.of(
            "痛", "疼", "发热", "发烧", "咳嗽", "腹泻", "呕吐", "恶心", "头晕", "皮疹", "瘙痒", "胸闷", "乏力", "不舒服"
    );

    private final AiProperties aiProperties;

    public TriageQuestionPolicy(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    public boolean isInitialMessage(String message) {
        return !StringUtils.hasText(message);
    }

    public boolean wantsRecommendation(String text) {
        return containsAny(text, UNKNOWN_INTENTS);
    }

    public boolean shouldRecommend(TriageSession session, String fullText) {
        if (wantsRecommendation(fullText)) {
            return true;
        }
        if (session.getRound() >= aiProperties.getMaxFollowUpRounds()) {
            return true;
        }
        List<QuestionDimension> missing = missingDimensions(fullText);
        return !missing.contains(QuestionDimension.MAIN_SYMPTOM)
                && !missing.contains(QuestionDimension.DURATION)
                && !missing.contains(QuestionDimension.SEVERITY);
    }

    public List<String> buildQuestions(String fullText) {
        List<QuestionDimension> missing = missingDimensions(fullText);
        List<String> questions = new ArrayList<>();
        for (QuestionDimension dimension : missing) {
            switch (dimension) {
                case MAIN_SYMPTOM -> questions.add("请简单描述你最主要的不适症状。");
                case DURATION -> questions.add("这个症状大概持续多久了？");
                case SEVERITY -> questions.add("症状程度是轻微、明显还是非常剧烈？");
                case COMPANIONS -> questions.add("是否伴有发热、呕吐、胸闷、呼吸困难或明显乏力？");
                case BODY_PART -> questions.add("不适主要出现在哪个部位？");
                case SPECIAL_GROUP -> questions.add("患者是否为儿童、孕妇、老人，或有高血压、糖尿病等基础病？");
                default -> {
                }
            }
            if (questions.size() >= 2) {
                break;
            }
        }
        if (questions.isEmpty()) {
            questions.add("还有其他伴随症状需要补充吗？");
        }
        return questions;
    }

    public List<String> quickRepliesForQuestions(List<String> questions) {
        String joined = String.join(" ", questions);
        if (joined.contains("持续多久")) {
            return List.of("今天刚出现", "两三天", "一周以上", "不清楚");
        }
        if (joined.contains("轻微") || joined.contains("剧烈")) {
            return List.of("轻微", "明显", "非常剧烈", "不清楚");
        }
        if (joined.contains("伴有")) {
            return List.of("有", "没有", "不清楚");
        }
        return List.of("发热咳嗽", "头痛头晕", "腹痛腹泻", "胸闷胸痛", "皮疹瘙痒", "其他不适");
    }

    public List<String> initialQuickReplies() {
        return List.of("发热咳嗽", "头痛头晕", "腹痛腹泻", "胸闷胸痛", "皮疹瘙痒", "其他不适");
    }

    private List<QuestionDimension> missingDimensions(String text) {
        List<QuestionDimension> missing = new ArrayList<>();
        if (!containsAny(text, SYMPTOM_KEYWORDS)) {
            missing.add(QuestionDimension.MAIN_SYMPTOM);
        }
        if (!containsAny(text, DURATION_KEYWORDS)) {
            missing.add(QuestionDimension.DURATION);
        }
        if (!containsAny(text, SEVERITY_KEYWORDS)) {
            missing.add(QuestionDimension.SEVERITY);
        }
        if (!containsAny(text, COMPANION_KEYWORDS)) {
            missing.add(QuestionDimension.COMPANIONS);
        }
        if (!containsAny(text, BODY_PART_KEYWORDS)) {
            missing.add(QuestionDimension.BODY_PART);
        }
        if (!containsAny(text, SPECIAL_GROUP_KEYWORDS)) {
            missing.add(QuestionDimension.SPECIAL_GROUP);
        }
        return missing;
    }

    private boolean containsAny(String text, List<String> keywords) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return keywords.stream().anyMatch(text::contains);
    }

    private enum QuestionDimension {
        MAIN_SYMPTOM,
        DURATION,
        SEVERITY,
        COMPANIONS,
        BODY_PART,
        SPECIAL_GROUP
    }
}
