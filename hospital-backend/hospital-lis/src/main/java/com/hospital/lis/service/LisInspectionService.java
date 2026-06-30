package com.hospital.lis.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.execute.AbstractMedTechExecuteTemplate;
import com.hospital.common.execute.MedTechExecuteCoordinator;
import com.hospital.common.support.LabReportComposer;
import com.hospital.common.support.LabReportItemTemplates;
import com.hospital.common.support.MedTechSignSupport;
import com.hospital.lis.client.AiBridgeLabReportClient;
import com.hospital.lis.dto.InspectionResultRequest;
import com.hospital.lis.order.LisMedTechOrderCoordinator;
import com.hospital.lis.repository.InspectionRequestRepository;
import com.hospital.lis.repository.InspectionResultItemRepository;
import com.hospital.lis.security.AuthContextHolder;
import com.hospital.lis.support.LisAiReportCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class LisInspectionService extends AbstractMedTechExecuteTemplate {

    private final InspectionRequestRepository inspectionRequestRepository;
    private final InspectionResultItemRepository inspectionResultItemRepository;
    private final LisMedTechOrderCoordinator lisMedTechOrderCoordinator;
    private final LisAiReportCache lisAiReportCache;
    private final AiBridgeLabReportClient aiBridgeLabReportClient;

    @Transactional
    public Map<String, Object> listQueue(Integer status, int page, int pageSize) {
        autoAssignPaidRequests();
        int offset = Math.max(page - 1, 0) * pageSize;
        Integer queryStatus = status != null ? status : InspectionRequestStatus.PAID;
        Long executorId = queueExecutorFilter();
        return Map.of(
                "list", inspectionRequestRepository.findQueue(queryStatus, executorId, offset, pageSize),
                "page", page,
                "pageSize", pageSize
        );
    }

    @Transactional
    public Map<String, Object> execute(Long inspectionRequestId) {
        return executeOrder(inspectionRequestId);
    }

    @Override
    protected void onAfterExecute(Long orderId) {
        seedInstrumentItemsIfAbsent(orderId);
    }

    public Map<String, Object> getResultDetail(Long inspectionRequestId) {
        Map<String, Object> context = inspectionRequestRepository.findLabReportContext(inspectionRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检验申请不存在"));
        requirePaidOrLater(context);
        return enrichLabReport(context);
    }

    public Map<String, Object> generateAiReport(Long inspectionRequestId) {
        Map<String, Object> context = inspectionRequestRepository.findLabReportContext(inspectionRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检验申请不存在"));
        requirePaidOrLater(context);

        List<Map<String, Object>> items = loadItems(inspectionRequestId, (String) context.get("itemName"));
        String aiText = aiBridgeLabReportClient.generateLabAnalysis(context, items);
        lisAiReportCache.put(inspectionRequestId, aiText, "READY");
        return enrichLabReport(context);
    }

    @Transactional
    public Map<String, Object> saveResult(Long inspectionRequestId, InspectionResultRequest request) {
        Long currentId = AuthContextHolder.require().getEmployeeId();
        Map<String, Object> locked = inspectionRequestRepository.findByIdForUpdate(inspectionRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检验申请不存在"));

        int currentStatus = ((Number) locked.get("status")).intValue();
        boolean signAsReviewerOnly = Boolean.TRUE.equals(request.getSignAsReviewerOnly());
        assertCanSaveResult(currentStatus, signAsReviewerOnly);

        Map<String, Object> context = inspectionRequestRepository.findLabReportContext(inspectionRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检验申请不存在"));
        List<Map<String, Object>> items = loadItems(inspectionRequestId, (String) context.get("itemName"));

        Long existingReporterId = context.get("resultInputId") != null
                ? ((Number) context.get("resultInputId")).longValue() : null;
        var sign = MedTechSignSupport.resolve(
                currentId,
                request.getSignAsReviewerOnly(),
                request.getPendingReview(),
                existingReporterId);

        String resultText = resolveResultText(request, context, items);
        if (!signAsReviewerOnly && resultText.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请生成 AI 报告或填写检验医师意见");
        }

        int targetStatus = resolveSaveResultTarget(currentStatus, signAsReviewerOnly, sign.pendingReview());
        applySaveResultStatus(inspectionRequestId, currentStatus, targetStatus);

        inspectionRequestRepository.saveResultContent(
                inspectionRequestId,
                sign.reporterId(),
                sign.reviewerId(),
                resultText,
                signAsReviewerOnly);
        lisAiReportCache.evict(inspectionRequestId);

        Map<String, Object> result = new HashMap<>();
        result.put("inspectionRequestId", inspectionRequestId);
        result.put("status", targetStatus);
        result.put("resultText", resultText);
        return result;
    }

    @Override
    protected MedTechExecuteCoordinator coordinator() {
        return lisMedTechOrderCoordinator;
    }

    @Override
    protected Long requireExecutorId() {
        return AuthContextHolder.require().getEmployeeId();
    }

    @Override
    protected String orderIdResultKey() {
        return "inspectionRequestId";
    }

    private Map<String, Object> enrichLabReport(Map<String, Object> context) {
        Long id = ((Number) context.get("inspectionRequestId")).longValue();
        String itemName = (String) context.get("itemName");
        List<Map<String, Object>> items = loadItems(id, itemName);

        String resultText = context.get("resultText") != null ? String.valueOf(context.get("resultText")) : "";
        var parsed = LabReportComposer.parsePublishedText(resultText);

        String aiReportText = parsed.aiReportText();
        String doctorReportText = parsed.doctorReportText();
        String aiReportStatus = "PENDING";

        if (aiReportText.isBlank()) {
            LisAiReportCache.Entry cached = lisAiReportCache.get(id);
            if (cached != null) {
                aiReportText = cached.aiReportText();
                aiReportStatus = cached.aiReportStatus();
            }
        } else {
            aiReportStatus = "READY";
        }

        return LabReportComposer.composeView(context, items, aiReportText, doctorReportText, aiReportStatus);
    }

    private String resolveResultText(InspectionResultRequest request, Map<String, Object> context,
                                     List<Map<String, Object>> items) {
        if (Boolean.TRUE.equals(request.getSignAsReviewerOnly())) {
            return context.get("resultText") != null ? String.valueOf(context.get("resultText")).trim() : "";
        }
        if (request.getResultText() != null && !request.getResultText().isBlank()) {
            return request.getResultText().trim();
        }

        String ai = request.getAiReportText();
        String doctor = request.getDoctorReportText();
        if (ai == null || ai.isBlank()) {
            Long id = ((Number) context.get("inspectionRequestId")).longValue();
            LisAiReportCache.Entry cached = lisAiReportCache.get(id);
            if (cached != null) {
                ai = cached.aiReportText();
            }
        }
        return LabReportComposer.composeResultText(items, ai, doctor);
    }

    private List<Map<String, Object>> loadItems(Long inspectionRequestId, String itemName) {
        List<Map<String, Object>> items = inspectionResultItemRepository.findByRequestId(inspectionRequestId);
        if (items.isEmpty()) {
            return LabReportItemTemplates.defaultItemsFor(itemName);
        }
        return items;
    }

    private void seedInstrumentItemsIfAbsent(Long inspectionRequestId) {
        if (inspectionResultItemRepository.countByRequestId(inspectionRequestId) > 0) {
            return;
        }
        Map<String, Object> context = inspectionRequestRepository.findLabReportContext(inspectionRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检验申请不存在"));
        inspectionResultItemRepository.insertItems(
                inspectionRequestId,
                LabReportItemTemplates.defaultItemsFor((String) context.get("itemName"))
        );
    }

    private void requirePaidOrLater(Map<String, Object> row) {
        int status = ((Number) row.get("status")).intValue();
        if (status < InspectionRequestStatus.PAID) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅已缴费及以后状态可查看报告");
        }
    }

    private void autoAssignPaidRequests() {
        List<Long> requestIds = inspectionRequestRepository.findUnassignedPaidIdsForUpdate();
        if (requestIds.isEmpty()) {
            return;
        }

        List<Map<String, Object>> doctors = inspectionRequestRepository.findDoctorLoads("LAB_DOCTOR");
        if (doctors.isEmpty()) {
            return;
        }

        for (Long requestId : requestIds) {
            Map<String, Object> doctor = doctors.get(0);
            Long doctorId = ((Number) doctor.get("employeeId")).longValue();
            inspectionRequestRepository.assignExecutorIfUnassigned(requestId, doctorId);
            doctor.put("loadCount", ((Number) doctor.get("loadCount")).intValue() + 1);
            sortDoctorsByLoad(doctors);
        }
    }

    private Long queueExecutorFilter() {
        var context = AuthContextHolder.require();
        if (context.getRoles() != null && context.getRoles().contains("ADMIN")) {
            return null;
        }
        return context.getEmployeeId();
    }

    private void sortDoctorsByLoad(List<Map<String, Object>> doctors) {
        doctors.sort((left, right) -> {
            int byLoad = Integer.compare(
                    ((Number) left.get("loadCount")).intValue(),
                    ((Number) right.get("loadCount")).intValue());
            if (byLoad != 0) {
                return byLoad;
            }
            return Long.compare(
                    ((Number) left.get("employeeId")).longValue(),
                    ((Number) right.get("employeeId")).longValue());
        });
    }
}
