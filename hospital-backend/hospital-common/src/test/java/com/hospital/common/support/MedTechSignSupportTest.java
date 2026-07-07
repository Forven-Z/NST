package com.hospital.common.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MedTechSignSupportTest {

    @Test
    void singleSign_assignsSameReporterAndReviewer() {
        MedTechSignSupport.SignAssignment assignment =
                MedTechSignSupport.resolve(100L, false, false, null);

        assertThat(assignment.reporterId()).isEqualTo(100L);
        assertThat(assignment.reviewerId()).isEqualTo(100L);
        assertThat(assignment.pendingReview()).isFalse();
    }

    @Test
    void pendingReview_onlySetsReporter() {
        MedTechSignSupport.SignAssignment assignment =
                MedTechSignSupport.resolve(200L, false, true, null);

        assertThat(assignment.reporterId()).isEqualTo(200L);
        assertThat(assignment.reviewerId()).isNull();
        assertThat(assignment.pendingReview()).isTrue();
    }

    @Test
    void reviewerOnly_keepsReporterAndSetsReviewer() {
        MedTechSignSupport.SignAssignment assignment =
                MedTechSignSupport.resolve(300L, true, false, 100L);

        assertThat(assignment.reporterId()).isEqualTo(100L);
        assertThat(assignment.reviewerId()).isEqualTo(300L);
        assertThat(assignment.pendingReview()).isFalse();
    }

    @Test
    void rejectsNullEmployeeId() {
        assertThatThrownBy(() -> MedTechSignSupport.resolve(null, false, false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("职员 ID");
    }

    @Test
    void reviewerOnly_requiresExistingReporter() {
        assertThatThrownBy(() -> MedTechSignSupport.resolve(300L, true, false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无法审核签阅");
    }
}
