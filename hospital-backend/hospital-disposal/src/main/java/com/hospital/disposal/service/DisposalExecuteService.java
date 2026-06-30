package com.hospital.disposal.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.execute.AbstractMedTechExecuteTemplate;
import com.hospital.common.execute.MedTechExecuteCoordinator;
import com.hospital.common.support.DisposalRecordComposer;
import com.hospital.common.support.MedTechSignSupport;
import com.hospital.disposal.dto.DisposalResultRequest;
import com.hospital.disposal.order.DisposalMedTechOrderCoordinator;
import com.hospital.disposal.repository.DisposalRequestRepository;
import com.hospital.disposal.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DisposalExecuteService extends AbstractMedTechExecuteTemplate {

    private final DisposalRequestRepository disposalRequestRepository;
    private final DisposalMedTechOrderCoordinator disposalMedTechOrderCoordinator;

    public Map<String, Object> listQueue(Integer status, int page, int pageSize) {
        int offset = Math.max(page - 1, 0) * pageSize;
        Integer queryStatus = status != null ? status : InspectionRequestStatus.PAID;
        return Map.of(
                "list", disposalRequestRepository.findQueue(queryStatus, offset, pageSize),
                "page", page,
                "pageSize", pageSize
        );
    }

    @Transactional
    public Map<String, Object> execute(Long disposalRequestId) {
        return executeOrder(disposalRequestId);
    }

    public Map<String, Object> getResultDetail(Long disposalRequestId) {
        Map<String, Object> context = disposalRequestRepository.findDisposalRecordContext(disposalRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "处置申请不存在"));
        requirePaidOrLater(context);
        return composeRecord(context, null, null);
    }

    @Transactional
    public Map<String, Object> saveResult(Long disposalRequestId, DisposalResultRequest request) {
        Long currentId = AuthContextHolder.require().getEmployeeId();
        Map<String, Object> locked = disposalRequestRepository.findByIdForUpdate(disposalRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "处置申请不存在"));

        int currentStatus = ((Number) locked.get("status")).intValue();
        boolean signAsReviewerOnly = Boolean.TRUE.equals(request.getSignAsReviewerOnly());
        assertCanSaveResult(currentStatus, signAsReviewerOnly);

        Map<String, Object> context = disposalRequestRepository.findDisposalRecordContext(disposalRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "处置申请不存在"));

        Long existingReporterId = context.get("resultInputId") != null
                ? ((Number) context.get("resultInputId")).longValue() : null;
        var sign = MedTechSignSupport.resolve(
                currentId,
                request.getSignAsReviewerOnly(),
                request.getPendingReview(),
                existingReporterId);

        String resultText = resolveResultText(request, context);
        if (!signAsReviewerOnly && resultText.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写处置过程或观察与结果");
        }

        int targetStatus = resolveSaveResultTarget(currentStatus, signAsReviewerOnly, sign.pendingReview());
        applySaveResultStatus(disposalRequestId, currentStatus, targetStatus);

        disposalRequestRepository.saveResultContent(
                disposalRequestId,
                sign.reporterId(),
                sign.reviewerId(),
                resultText,
                signAsReviewerOnly);

        Map<String, Object> result = new HashMap<>();
        result.put("disposalRequestId", disposalRequestId);
        result.put("status", targetStatus);
        result.put("resultText", resultText);
        return result;
    }

    @Override
    protected MedTechExecuteCoordinator coordinator() {
        return disposalMedTechOrderCoordinator;
    }

    @Override
    protected Long requireExecutorId() {
        return AuthContextHolder.require().getEmployeeId();
    }

    @Override
    protected String orderIdResultKey() {
        return "disposalRequestId";
    }

    private Map<String, Object> composeRecord(Map<String, Object> context, String processText, String outcomeText) {
        return DisposalRecordComposer.composeView(context, processText, outcomeText);
    }

    private void requirePaidOrLater(Map<String, Object> row) {
        int status = ((Number) row.get("status")).intValue();
        if (status < InspectionRequestStatus.PAID) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅已缴费及以后状态可查看记录");
        }
    }

    private String resolveResultText(DisposalResultRequest request, Map<String, Object> context) {
        if (Boolean.TRUE.equals(request.getSignAsReviewerOnly())) {
            return context.get("resultText") != null ? String.valueOf(context.get("resultText")).trim() : "";
        }
        if (request.getResultText() != null && !request.getResultText().isBlank()) {
            return request.getResultText().trim();
        }
        String process = request.getProcessText();
        String outcome = request.getOutcomeText();
        return DisposalRecordComposer.composeResultText(process, outcome).trim();
    }
}
