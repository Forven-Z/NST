package com.hospital.management.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.management.repository.LeaveRequestRepository;
import com.hospital.management.repository.SchedulingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveRequestServiceTest {

    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private SchedulingRepository schedulingRepository;
    @InjectMocks
    private LeaveRequestService leaveRequestService;

    @Test
    void submitLeave_rejectsWhenScheduleExpired() {
        when(schedulingRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(Map.of(
                "schedulingId", 1L,
                "employeeId", 1L,
                "publishStatus", 1,
                "workDate", LocalDate.now().minusDays(1)
        )));
        assertThrows(BusinessException.class,
                () -> leaveRequestService.submitLeave(1L, 1L, "病假"));
    }
}
