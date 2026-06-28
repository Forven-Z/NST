package com.hospital.management.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.management.dto.BatchUpsertChangeItem;
import com.hospital.management.dto.BatchUpsertRequest;
import com.hospital.management.dto.CopyWeekRequest;
import com.hospital.management.repository.EmployeeRepository;
import com.hospital.management.repository.SchedulingRepository;
import com.hospital.management.repository.SchedulingTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulingWeekServiceTest {

    @Mock
    private SchedulingRepository schedulingRepository;
    @Mock
    private SchedulingTemplateRepository templateRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private LeaveRequestService leaveRequestService;

    @InjectMocks
    private SchedulingWeekService schedulingWeekService;

    @Test
    void copyWeek_skipsOccupiedSlots() {
        LocalDate sourceStart = LocalDate.of(2026, 7, 6);
        LocalDate targetStart = LocalDate.of(2026, 7, 13);
        when(schedulingRepository.listWeekByDept(1L, sourceStart, sourceStart.plusDays(6)))
                .thenReturn(List.of(Map.of(
                        "employeeId", 1L,
                        "workDate", sourceStart,
                        "noonType", 1,
                        "registLevelId", 1L,
                        "totalQuota", 30
                )));
        when(schedulingRepository.existsActiveSlot(1L, targetStart, 1)).thenReturn(true);

        CopyWeekRequest request = new CopyWeekRequest();
        request.setDeptId(1L);
        request.setSourceWeekStart(sourceStart);
        request.setTargetWeekStart(targetStart);

        Map<String, Object> result = schedulingWeekService.copyWeek(request);

        assertEquals(0, result.get("created"));
        assertEquals(1, result.get("skipped"));
        verify(schedulingRepository, never()).insert(any(), any(), any(), any(), any());
    }

    @Test
    void batchUpsert_rejectsPastDate() {
        BatchUpsertChangeItem change = new BatchUpsertChangeItem();
        change.setEmployeeId(1L);
        change.setWorkDate(LocalDate.now().minusDays(1));
        change.setNoonType(1);
        change.setRegistLevelId(1L);

        BatchUpsertRequest request = new BatchUpsertRequest();
        request.setDeptId(1L);
        request.setWeekStart(LocalDate.now());
        request.setChanges(List.of(change));

        assertThrows(BusinessException.class, () -> schedulingWeekService.batchUpsert(request));
    }

    @Test
    void getWeekGrid_alignsWeekStartToMonday() {
        LocalDate wednesday = LocalDate.of(2026, 7, 8);
        LocalDate monday = LocalDate.of(2026, 7, 6);
        when(employeeRepository.listEmployees(null, 1L, null, 0, 1, 0, 200)).thenReturn(List.of());
        when(schedulingRepository.listWeekByDept(eq(1L), eq(monday), eq(monday.plusDays(6))))
                .thenReturn(List.of());
        when(templateRepository.hasEnabledForDept(1L)).thenReturn(false);

        Map<String, Object> result = schedulingWeekService.getWeekGrid(1L, wednesday);

        assertEquals(monday, result.get("weekStart"));
    }
}
