package com.hospital.common.order;

import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MedTechOrderSaveResultSupportTest {

    @Test
    void resolveNextStatus_fromPaidSingleSign() {
        int next = MedTechOrderSaveResultSupport.resolveNextStatus(
                InspectionRequestStatus.PAID, false, false);

        assertEquals(InspectionRequestStatus.RESULT_READY, next);
    }

    @Test
    void resolveNextStatus_fromPaidPendingReview() {
        int next = MedTechOrderSaveResultSupport.resolveNextStatus(
                InspectionRequestStatus.PAID, false, true);

        assertEquals(InspectionRequestStatus.EXECUTED, next);
    }

    @Test
    void resolveNextStatus_fromExecutedPendingReview() {
        int next = MedTechOrderSaveResultSupport.resolveNextStatus(
                InspectionRequestStatus.EXECUTED, false, true);

        assertEquals(InspectionRequestStatus.EXECUTED, next);
    }

    @Test
    void resolveNextStatus_reviewerOnlyFromExecuted() {
        int next = MedTechOrderSaveResultSupport.resolveNextStatus(
                InspectionRequestStatus.EXECUTED, true, false);

        assertEquals(InspectionRequestStatus.RESULT_READY, next);
    }

    @Test
    void assertCanSaveResult_rejectsOrderedState() {
        assertThrows(BusinessException.class,
                () -> MedTechOrderSaveResultSupport.assertCanSaveResult(
                        InspectionRequestStatus.ORDERED, false));
    }

    @Test
    void assertCanSaveResult_reviewerOnlyBeforeExecuted() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> MedTechOrderSaveResultSupport.assertCanSaveResult(
                        InspectionRequestStatus.PAID, true));
        assertEquals("报告尚未录入，无法审核", ex.getMessage());
    }
}
