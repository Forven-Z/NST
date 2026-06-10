package com.hospital.his.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SchedulingService {

    private final com.hospital.his.repository.SchedulingRepository schedulingRepository;

    public Map<String, Object> listSchedules(Long deptId, LocalDate workDate, Integer noonType, Long registLevelId) {
        LocalDate queryDate = workDate != null ? workDate : LocalDate.now();
        List<Map<String, Object>> list = schedulingRepository.findAvailableSchedules(
                deptId, queryDate, noonType, registLevelId);
        return Map.of("list", list);
    }

    public Map<String, Object> listRegistrarSchedules(Long deptId, Long employeeId, Long registLevelId,
                                                      LocalDate workDate) {
        LocalDate fromDate = workDate != null ? workDate : LocalDate.now();
        LocalDate toDate = fromDate.plusDays(6);
        List<Map<String, Object>> raw = schedulingRepository.findRegistrarSchedules(
                deptId, employeeId, registLevelId, fromDate, toDate);
        List<Map<String, Object>> list = raw.stream().map(this::enrichRegistrarScheduleRow).toList();
        return Map.of("list", list);
    }

    private Map<String, Object> enrichRegistrarScheduleRow(Map<String, Object> row) {
        int noonType = ((Number) row.get("noonType")).intValue();
        row.put("noonLabel", noonType == 1 ? "上午" : "下午");
        row.put("timeRange", noonType == 1 ? "08:00-12:00" : "13:00-17:00");
        return row;
    }
}
