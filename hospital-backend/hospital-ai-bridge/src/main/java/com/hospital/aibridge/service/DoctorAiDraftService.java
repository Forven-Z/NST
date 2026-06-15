package com.hospital.aibridge.service;

import com.hospital.aibridge.client.HisDoctorOrderClient;
import com.hospital.aibridge.domain.DoctorAiDraft;
import com.hospital.aibridge.dto.DiagnosisSuggestRequest;
import com.hospital.aibridge.dto.DoctorAiDraftRequest;
import com.hospital.aibridge.dto.DoctorAiDraftUpdateRequest;
import com.hospital.aibridge.repository.AiChatSessionRepository;
import com.hospital.aibridge.repository.AiCatalogRepository;
import com.hospital.aibridge.repository.AiPrescriptionDraftRepository;
import com.hospital.aibridge.repository.AiRegisterRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DoctorAiDraftService {

    private final DoctorAiAssistService aiAssistService;
    private final DoctorAiDraftStore draftStore;
    private final AiCatalogRepository catalogRepository;
    private final HisDoctorOrderClient hisDoctorOrderClient;
    private final AiPrescriptionDraftRepository prescriptionDraftRepository;
    private final AiChatSessionRepository chatSessionRepository;
    private final AiRegisterRepository registerRepository;
    private final AuthTokenService authTokenService;

    public DoctorAiDraftService(
            DoctorAiAssistService aiAssistService,
            DoctorAiDraftStore draftStore,
            AiCatalogRepository catalogRepository,
            HisDoctorOrderClient hisDoctorOrderClient,
            AiPrescriptionDraftRepository prescriptionDraftRepository,
            AiChatSessionRepository chatSessionRepository,
            AiRegisterRepository registerRepository,
            AuthTokenService authTokenService) {
        this.aiAssistService = aiAssistService;
        this.draftStore = draftStore;
        this.catalogRepository = catalogRepository;
        this.hisDoctorOrderClient = hisDoctorOrderClient;
        this.prescriptionDraftRepository = prescriptionDraftRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.registerRepository = registerRepository;
        this.authTokenService = authTokenService;
    }

    public Map<String, Object> saveMedicalRecord(Long registerId, Map<String, Object> record) {
        return draftStore.saveMedicalRecord(registerId, record);
    }

    public Map<String, Object> createClinicalDraft(Long registerId, String draftType) {
        DoctorAiDraftRequest request = new DoctorAiDraftRequest();
        request.setRegisterId(registerId);
        request.setDraftType(draftType);
        request.setMedicalRecord(recordRequest(registerId));
        request.setCandidates(clinicalDefaultCandidates(draftType));
        Map<String, Object> generated = aiAssistService.generateDraft(request);
        return persistGeneratedDraft(registerId, draftType, generated);
    }

    public Map<String, Object> createClinicalDraft(Map<String, Object> requestBody, String draftType) {
        return createClinicalDraft(requestBody, draftType, null);
    }

    public Map<String, Object> createClinicalDraft(Map<String, Object> requestBody, String draftType, String authorization) {
        Long registerId = registerId(requestBody);
        DoctorAiDraftRequest request = new DoctorAiDraftRequest();
        request.setRegisterId(registerId);
        request.setDraftType(draftType);
        request.setMedicalRecord(recordRequest(registerId, requestBody));
        request.setCandidates(candidates(requestBody, draftType));
        Map<String, Object> generated = aiAssistService.generateDraft(request);
        Map<String, Object> response = persistGeneratedDraft(registerId, draftType, generated);
        saveAiAuditSession("ASSISTANT", registerId, authorization, draftType + "_DRAFT", response);
        return response;
    }

    public Map<String, Object> createPrescriptionDraft(Long registerId) {
        return createPrescriptionDraft(registerId, null);
    }

    public Map<String, Object> createPrescriptionDraft(Long registerId, String authorization) {
        DoctorAiDraftRequest request = new DoctorAiDraftRequest();
        request.setRegisterId(registerId);
        request.setDraftType("PRESCRIPTION");
        request.setMedicalRecord(recordRequest(registerId));
        request.setCandidates(prescriptionCandidates());
        Map<String, Object> generated = aiAssistService.generateDraft(request);
        Map<String, Object> response = persistGeneratedDraft(registerId, "PRESCRIPTION", generated);
        Long doctorId = authTokenService.employeeId(authorization).orElse(null);
        if (doctorId != null) {
            DoctorAiDraft draft = draftStore.find(((Number) response.get("draftId")).longValue()).orElse(null);
            if (draft != null) {
                Long auditDraftId = prescriptionDraftRepository.insert(registerId, doctorId, response);
                draft.setAuditDraftId(auditDraftId);
                draftStore.save(draft);
                response = toResponse(draft);
            }
        }
        saveAiAuditSession("ASSISTANT", registerId, authorization, "PRESCRIPTION_DRAFT", response);
        return response;
    }

    public Map<String, Object> createPrescriptionDraft(Map<String, Object> requestBody, String authorization) {
        Long registerId = registerId(requestBody);
        DoctorAiDraftRequest request = new DoctorAiDraftRequest();
        request.setRegisterId(registerId);
        request.setDraftType("PRESCRIPTION");
        request.setMedicalRecord(recordRequest(registerId, requestBody));
        request.setCandidates(prescriptionCandidates());
        Map<String, Object> generated = aiAssistService.generateDraft(request);
        Map<String, Object> response = persistGeneratedDraft(registerId, "PRESCRIPTION", generated);
        Long doctorId = authTokenService.employeeId(authorization).orElse(null);
        if (doctorId != null) {
            DoctorAiDraft draft = draftStore.find(((Number) response.get("draftId")).longValue()).orElse(null);
            if (draft != null) {
                Long auditDraftId = prescriptionDraftRepository.insert(registerId, doctorId, response);
                draft.setAuditDraftId(auditDraftId);
                draftStore.save(draft);
                response = toResponse(draft);
            }
        }
        saveAiAuditSession("ASSISTANT", registerId, authorization, "PRESCRIPTION_DRAFT", response);
        return response;
    }

    public Map<String, Object> updateDraft(Long draftId, DoctorAiDraftUpdateRequest request) {
        DoctorAiDraft draft = draftStore.find(draftId)
                .orElseThrow(() -> new IllegalArgumentException("AI 草稿不存在"));
        if (StringUtils.hasText(request.getAiReason())) {
            draft.setAiReason(request.getAiReason());
        }
        if (request.getItems() != null) {
            draft.setEditedItems(request.getItems());
        }
        if (request.getFinalContent() != null) {
            draft.setFinalContent(request.getFinalContent());
        }
        if ("PRESCRIPTION".equals(draft.getDraftType()) && draft.getAuditDraftId() != null) {
            prescriptionDraftRepository.updateEditedContent(draft.getAuditDraftId(), toResponse(draft));
        }
        draftStore.save(draft);
        return toResponse(draft);
    }

    public Map<String, Object> confirmDraft(Long draftId, String authorization) {
        DoctorAiDraft draft = draftStore.find(draftId)
                .orElseThrow(() -> new IllegalArgumentException("AI 草稿不存在"));
        Map<String, Object> hisSubmitResult = hisDoctorOrderClient.submitDraft(
                draft.getRegisterId(),
                draft.getDraftType(),
                activeItems(draft),
                authorization);
        draft.setStatus(1);
        draft.setConfirmTime(OffsetDateTime.now());
        Map<String, Object> finalContent = new LinkedHashMap<>();
        finalContent.put("items", activeItems(draft));
        finalContent.put("confirmedByDoctor", true);
        finalContent.put("confirmTime", draft.getConfirmTime());
        finalContent.put("hisSubmitResult", hisSubmitResult);
        finalContent.put("safetyNotice", "AI 输出仅作为辅助建议，最终诊疗内容以医生确认为准。");
        draft.setFinalContent(finalContent);
        if ("PRESCRIPTION".equals(draft.getDraftType()) && draft.getAuditDraftId() != null) {
            prescriptionDraftRepository.markSubmitted(draft.getAuditDraftId(), finalContent);
        }
        draftStore.save(draft);

        Map<String, Object> response = toResponse(draft);
        response.put("hisSubmitResult", hisSubmitResult);
        saveAiAuditSession("ASSISTANT", draft.getRegisterId(), authorization, draft.getDraftType() + "_CONFIRM", response);
        response.put("message", "AI 草稿已确认，请由 HIS/医生端据此创建正式医嘱或处方。");
        return response;
    }

    private Map<String, Object> persistGeneratedDraft(Long registerId, String draftType, Map<String, Object> generated) {
        DoctorAiDraft draft = new DoctorAiDraft();
        draft.setRegisterId(registerId);
        draft.setDraftType(draftType);
        draft.setAiReason(String.valueOf(generated.getOrDefault("aiReason", "AI 已生成草稿，请医生核对后确认。")));
        draft.setOriginalItems(items(generated));
        draft.setEditedItems(items(generated));
        draft.setStatus(0);
        draftStore.save(draft);
        Map<String, Object> response = toResponse(draft);
        if (generated.containsKey("stub")) {
            response.put("stub", generated.get("stub"));
        }
        return response;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> items(Map<String, Object> generated) {
        Object value = generated.get("items");
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(it -> {
                        Map<String, Object> item = new LinkedHashMap<>((Map<String, Object>) it);
                        return item;
                    })
                    .toList();
        }
        return List.of();
    }

    private Map<String, Object> toResponse(DoctorAiDraft draft) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("draftId", draft.getId());
        response.put("auditDraftId", draft.getAuditDraftId());
        response.put("draftType", draft.getDraftType());
        response.put("registerId", draft.getRegisterId());
        response.put("aiReason", draft.getAiReason());
        response.put("items", activeItems(draft));
        response.put("originalItems", draft.getOriginalItems());
        response.put("finalContent", draft.getFinalContent());
        response.put("status", draft.getStatus());
        response.put("createTime", draft.getCreateTime());
        response.put("updateTime", draft.getUpdateTime());
        response.put("confirmTime", draft.getConfirmTime());
        return response;
    }

    private List<Map<String, Object>> activeItems(DoctorAiDraft draft) {
        return draft.getEditedItems() == null ? List.of() : draft.getEditedItems();
    }

    private DiagnosisSuggestRequest recordRequest(Long registerId) {
        DiagnosisSuggestRequest request = new DiagnosisSuggestRequest();
        request.setRegisterId(registerId);
        draftStore.findMedicalRecord(registerId).ifPresent(record -> {
            request.setReadme(text(record.get("readme")));
            request.setPresent(text(record.get("present")));
            request.setPresentTreat(text(record.get("presentTreat")));
            request.setHistory(text(record.get("history")));
            request.setAllergy(text(record.get("allergy")));
            request.setPhysique(text(record.get("physique")));
            request.setDiagnosis(text(record.get("diagnosis")));
            request.setCure(text(record.get("cure")));
            request.setCheckAdvice(text(record.get("checkAdvice")));
            request.setInspectionAdvice(text(record.get("inspectionAdvice")));
        });
        return request;
    }

    @SuppressWarnings("unchecked")
    private DiagnosisSuggestRequest recordRequest(Long registerId, Map<String, Object> requestBody) {
        DiagnosisSuggestRequest request = recordRequest(registerId);
        Map<String, Object> source = requestBody;
        Object nested = requestBody == null ? null : requestBody.get("medicalRecord");
        if (nested instanceof Map<?, ?> nestedMap) {
            source = (Map<String, Object>) nestedMap;
        }
        if (source == null) {
            return request;
        }
        applyRecordFields(request, source);
        return request;
    }

    private void applyRecordFields(DiagnosisSuggestRequest request, Map<String, Object> source) {
        if (source.containsKey("readme")) {
            request.setReadme(text(source.get("readme")));
        }
        if (source.containsKey("present")) {
            request.setPresent(text(source.get("present")));
        }
        if (source.containsKey("presentTreat")) {
            request.setPresentTreat(text(source.get("presentTreat")));
        }
        if (source.containsKey("history")) {
            request.setHistory(text(source.get("history")));
        }
        if (source.containsKey("allergy")) {
            request.setAllergy(text(source.get("allergy")));
        }
        if (source.containsKey("physique")) {
            request.setPhysique(text(source.get("physique")));
        }
        if (source.containsKey("diagnosis")) {
            request.setDiagnosis(text(source.get("diagnosis")));
        }
        if (source.containsKey("cure")) {
            request.setCure(text(source.get("cure")));
        }
        if (source.containsKey("checkAdvice")) {
            request.setCheckAdvice(text(source.get("checkAdvice")));
        }
        if (source.containsKey("inspectionAdvice")) {
            request.setInspectionAdvice(text(source.get("inspectionAdvice")));
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> candidates(Map<String, Object> requestBody, String draftType) {
        Object value = requestBody == null ? null : requestBody.get("candidates");
        if (value instanceof List<?> list) {
            List<Map<String, Object>> items = list.stream()
                    .filter(Map.class::isInstance)
                    .map(it -> {
                        Map<String, Object> item = new LinkedHashMap<>((Map<String, Object>) it);
                        return item;
                    })
                    .toList();
            if (!items.isEmpty()) {
                return items;
            }
        }
        return clinicalDefaultCandidates(draftType);
    }

    private Long registerId(Map<String, Object> requestBody) {
        Object value = requestBody == null ? null : requestBody.get("registerId");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        throw new IllegalArgumentException("registerId cannot be blank");
    }

    private List<Map<String, Object>> clinicalDefaultCandidates(String draftType) {
        List<Map<String, Object>> rows = catalogRepository.listMedicalTechnologies(draftType, 20);
        if (!rows.isEmpty()) {
            return rows;
        }
        return switch (draftType) {
            case "CHECK" -> List.of(
                    candidate(1L, "Head CT", "CHECK",
                            "Exclude clinically relevant intracranial or structural disease", "Head")
            );
            case "INSPECTION" -> List.of(
                    candidate(2L, "Blood routine", "INSPECTION",
                            "Screen infection, anemia and inflammatory response", "Blood")
            );
            case "DISPOSAL" -> List.of(
                    candidate(3L, "Gastric lavage", "DISPOSAL",
                            "Emergency disposal when clinically indicated", "")
            );
            default -> defaultCandidates(draftType);
        };
    }

    private List<Map<String, Object>> prescriptionCandidates() {
        List<Map<String, Object>> rows = catalogRepository.listDrugs(20);
        return rows.isEmpty() ? defaultCandidates("PRESCRIPTION") : rows;
    }

    public void saveAiAuditSession(String scene, Long registerId, String authorization,
                                   String action, Map<String, Object> payload) {
        try {
            Long doctorId = authTokenService.employeeId(authorization).orElse(null);
            Long patientId = authTokenService.patientId(authorization)
                    .orElseGet(() -> registerRepository.findPatientIdByRegisterId(registerId).orElse(null));
            chatSessionRepository.insertSession(scene, registerId, patientId, doctorId, List.of(
                    Map.of("role", "system", "content", action),
                    Map.of("role", "assistant", "content", payload)
            ));
        } catch (Exception ignored) {
            // Audit persistence must not block the outpatient workflow.
        }
    }

    private List<Map<String, Object>> defaultCandidates(String draftType) {
        return switch (draftType) {
            case "CHECK" -> List.of(
                    candidate(1L, "头部 CT", "CHECK", "排除相关器质性病变", "头部"),
                    candidate(3L, "胸部 CT", "CHECK", "评估胸肺情况", "胸部")
            );
            case "INSPECTION" -> List.of(
                    candidate(2L, "血常规", "INSPECTION", "感染、贫血等筛查", "血液"),
                    candidate(4L, "肝肾功能", "INSPECTION", "评估用药及基础状态", "血液")
            );
            case "DISPOSAL" -> List.of(
                    candidate(5L, "清创换药", "DISPOSAL", "局部处置", ""),
                    candidate(6L, "雾化吸入", "DISPOSAL", "缓解呼吸道症状", "")
            );
            case "PRESCRIPTION" -> List.of(
                    drug(1L, "阿莫西林胶囊", 1, "口服", "0.25g", "tid", 3, "青霉素过敏者禁用"),
                    drug(2L, "布洛芬缓释胶囊", 1, "口服", "0.3g", "bid", 3, "饭后服用")
            );
            default -> List.of();
        };
    }

    private Map<String, Object> candidate(Long id, String name, String techType, String purpose, String bodyPart) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("medicalTechnologyId", id);
        item.put("itemName", name);
        item.put("techType", techType);
        item.put("purpose", purpose);
        item.put("bodyPart", bodyPart);
        item.put("remark", "");
        return item;
    }

    private Map<String, Object> drug(Long id, String name, int quantity, String usageMethod,
                                     String dosage, String frequency, int days, String entrust) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("drugId", id);
        item.put("drugName", name);
        item.put("quantity", quantity);
        item.put("usageMethod", usageMethod);
        item.put("dosage", dosage);
        item.put("frequency", frequency);
        item.put("days", days);
        item.put("entrust", entrust);
        return item;
    }
}
