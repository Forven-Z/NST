package com.hospital.management.service;

import com.hospital.management.repository.DictRepository;
import com.hospital.management.repository.EmployeeRepository;
import com.hospital.management.repository.LeaveRequestRepository;
import com.hospital.management.repository.SchedulingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulingAiSuggestServiceTest {

    @Mock
    private SchedulingService schedulingService;
    @Mock
    private LeaveRequestService leaveRequestService;
    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private SchedulingRepository schedulingRepository;
    @Mock
    private DictRepository dictRepository;

    @InjectMocks
    private SchedulingAiSuggestService service;

    @Test
    void suggestWeek_generatesRegularSixDaysAndExpertThreeHalfDays() {
        LocalDate targetDate = LocalDate.now().plusDays(14);
        LocalDate monday = targetDate.minusDays((targetDate.getDayOfWeek().getValue() + 6) % 7);
        mockRegistLevels();
        when(employeeRepository.listEmployees(null, 1L, "OUTPATIENT_DOCTOR", 0, 1, 0, 200))
                .thenReturn(List.of(
                        doctor(1L, "张医生", "主治医师"),
                        doctor(2L, "李医生", "主治医师"),
                        doctor(3L, "陈教授", "主任医师")
                ));
        when(schedulingRepository.listWeekByDept(1L, monday, monday.plusDays(6))).thenReturn(List.of());

        Map<String, Object> result = service.suggest(1L, monday, "WEEK");
        List<Map<String, Object>> changes = changes(result);

        assertEquals(27, changes.size());
        assertEquals(12, countByDoctor(changes, 1L));
        assertEquals(12, countByDoctor(changes, 2L));
        assertEquals(3, countByDoctor(changes, 3L));
        assertTrue(changes.stream().anyMatch(row -> monday.plusDays(6).equals(row.get("workDate"))));
        assertEveryHalfDayCovered(changes, monday);
    }

    @Test
    void suggestWeek_skipsExistingDoctorSlot() {
        LocalDate targetDate = LocalDate.now().plusDays(14);
        LocalDate monday = targetDate.minusDays((targetDate.getDayOfWeek().getValue() + 6) % 7);
        mockRegistLevels();
        when(employeeRepository.listEmployees(null, 1L, "OUTPATIENT_DOCTOR", 0, 1, 0, 200))
                .thenReturn(List.of(doctor(1L, "张医生", "主治医师")));
        when(schedulingRepository.listWeekByDept(1L, monday, monday.plusDays(6)))
                .thenReturn(List.of(schedule(99L, 1L, monday.plusDays(1), 1)));

        Map<String, Object> result = service.suggest(1L, monday, "WEEK");
        List<Map<String, Object>> changes = changes(result);

        assertFalse(changes.stream().anyMatch(row ->
                row.get("employeeId").equals(1L)
                        && row.get("workDate").equals(monday.plusDays(1))
                        && row.get("noonType").equals(1)
        ));
    }

    @Test
    void suggestWeek_appliesCustomRulesText() {
        LocalDate targetDate = LocalDate.now().plusDays(14);
        LocalDate monday = targetDate.minusDays((targetDate.getDayOfWeek().getValue() + 6) % 7);
        mockRegistLevels();
        when(employeeRepository.listEmployees(null, 1L, "OUTPATIENT_DOCTOR", 0, 1, 0, 200))
                .thenReturn(List.of(
                        doctor(1L, "doctor-a", "GENERAL"),
                        doctor(2L, "doctor-b", "EXPERT")
                ));
        when(schedulingRepository.listWeekByDept(1L, monday, monday.plusDays(6)))
                .thenReturn(weekdayCoverage(monday));

        Map<String, Object> result = service.suggest(
                1L,
                monday,
                "WEEK",
                """
                regular weekly 4 halfdays quota 25.
                expert weekly 2 halfdays quota 10.
                weekend off.
                """
        );
        List<Map<String, Object>> changes = changes(result);

        assertEquals(6, changes.size());
        assertEquals(4, countByDoctor(changes, 1L));
        assertEquals(2, countByDoctor(changes, 2L));
        assertTrue(changes.stream().noneMatch(row -> ((LocalDate) row.get("workDate")).getDayOfWeek().getValue() >= 6));
        assertTrue(changes.stream().filter(row -> row.get("employeeId").equals(1L))
                .allMatch(row -> row.get("totalQuota").equals(25)));
        assertTrue(changes.stream().filter(row -> row.get("employeeId").equals(2L))
                .allMatch(row -> row.get("totalQuota").equals(10)));
    }

    @Test
    void suggestSubstitutes_doesNotRecommendOriginalOrBusyDoctor() {
        LocalDate date = LocalDate.now().plusDays(7);
        when(schedulingService.list(1L, null, null, null)).thenReturn(Map.of("list", List.of(
                schedule(10L, 1L, date, 1),
                schedule(11L, 2L, date, 1)
        )));
        when(leaveRequestService.listAdmin(1)).thenReturn(Map.of("list", List.of(
                Map.of("leaveRequestId", 20L, "schedulingId", 10L, "employeeId", 1L, "deptId", 1L)
        )));
        when(employeeRepository.listEmployees(null, 1L, "OUTPATIENT_DOCTOR", 0, 1, 0, 200))
                .thenReturn(List.of(
                        doctor(1L, "张医生", "主治医师"),
                        doctor(2L, "李医生", "主治医师"),
                        doctor(3L, "王医生", "主治医师")
                ));

        Map<String, Object> result = service.suggest(1L, date, "SUBSTITUTE");
        List<Map<String, Object>> suggestions = suggestions(result);
        Long employeeId = (Long) ((Map<?, ?>) suggestions.get(0).get("proposedSchedule")).get("employeeId");

        assertEquals(3L, employeeId);
    }

    @Test
    void suggestSubstitutes_doesNotCrossRecommendBetweenRegularAndExpert() {
        LocalDate date = LocalDate.now().plusDays(7);
        when(schedulingService.list(1L, null, null, null)).thenReturn(Map.of("list", List.of(
                expertSchedule(10L, 1L, date, 1)
        )));
        when(leaveRequestService.listAdmin(1)).thenReturn(Map.of("list", List.of(
                Map.of("leaveRequestId", 20L, "schedulingId", 10L, "employeeId", 1L, "deptId", 1L)
        )));
        when(employeeRepository.listEmployees(null, 1L, "OUTPATIENT_DOCTOR", 0, 1, 0, 200))
                .thenReturn(List.of(
                        doctor(1L, "专家甲", "主任医师"),
                        doctor(2L, "普通乙", "主治医师")
                ));

        Map<String, Object> result = service.suggest(1L, date, "SUBSTITUTE");
        List<Map<String, Object>> suggestions = suggestions(result);
        Map<?, ?> proposed = (Map<?, ?>) suggestions.get(0).get("proposedSchedule");

        assertNull(proposed.get("employeeId"));
        assertFalse((Boolean) suggestions.get(0).get("replaceable"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> changes(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("changes");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> suggestions(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("suggestions");
    }

    private long countByDoctor(List<Map<String, Object>> changes, Long employeeId) {
        return changes.stream().filter(row -> employeeId.equals(row.get("employeeId"))).count();
    }

    private void assertEveryHalfDayCovered(List<Map<String, Object>> changes, LocalDate monday) {
        Set<String> covered = changes.stream()
                .map(row -> row.get("workDate") + "|" + row.get("noonType"))
                .collect(Collectors.toSet());
        for (int day = 0; day < 7; day++) {
            assertTrue(covered.contains(monday.plusDays(day) + "|1"));
            assertTrue(covered.contains(monday.plusDays(day) + "|2"));
        }
    }

    private void mockRegistLevels() {
        when(dictRepository.listRegistLevels(null, 0, 100)).thenReturn(List.of(
                Map.of("id", 1L, "levelCode", "NORMAL"),
                Map.of("id", 2L, "levelCode", "EXPERT")
        ));
    }

    private Map<String, Object> doctor(Long employeeId, String name, String title) {
        return Map.of(
                "employeeId", employeeId,
                "realName", name,
                "title", title,
                "deptId", 1L,
                "roleType", "OUTPATIENT_DOCTOR",
                "delmark", 0
        );
    }

    private Map<String, Object> schedule(Long schedulingId, Long employeeId, LocalDate workDate, int noonType) {
        return Map.<String, Object>ofEntries(
                Map.entry("schedulingId", schedulingId),
                Map.entry("deptId", 1L),
                Map.entry("employeeId", employeeId),
                Map.entry("employeeName", "医生" + employeeId),
                Map.entry("workDate", workDate),
                Map.entry("noonType", noonType),
                Map.entry("noonLabel", noonType == 1 ? "上午" : "下午"),
                Map.entry("totalQuota", 30),
                Map.entry("usedQuota", 0),
                Map.entry("publishStatus", 1)
        );
    }

    private List<Map<String, Object>> weekdayCoverage(LocalDate monday) {
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        long id = 100L;
        for (int day = 0; day < 5; day++) {
            for (int noonType : List.of(1, 2)) {
                rows.add(schedule(id++, 99L, monday.plusDays(day), noonType));
            }
        }
        return rows;
    }

    private Map<String, Object> expertSchedule(Long schedulingId, Long employeeId, LocalDate workDate, int noonType) {
        return Map.<String, Object>ofEntries(
                Map.entry("schedulingId", schedulingId),
                Map.entry("deptId", 1L),
                Map.entry("employeeId", employeeId),
                Map.entry("employeeName", "专家医生" + employeeId),
                Map.entry("workDate", workDate),
                Map.entry("noonType", noonType),
                Map.entry("noonLabel", noonType == 1 ? "上午" : "下午"),
                Map.entry("registLevelId", 2L),
                Map.entry("registLevelName", "专家号"),
                Map.entry("totalQuota", 20),
                Map.entry("usedQuota", 0),
                Map.entry("publishStatus", 1)
        );
    }
}
