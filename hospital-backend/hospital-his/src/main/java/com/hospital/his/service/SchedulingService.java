package com.hospital.his.service;

import com.hospital.his.support.NoonTypeSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
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

    public Map<String, Object> listRegistrarSchedules(Long deptId, Long employeeId, Long registLevelId) {
        // 窗口挂号：固定当天 + 当前午别及以后（上午含下午/晚上，下午含晚上，晚上仅晚上）
        LocalDate queryDate = LocalDate.now();
        int noonType = NoonTypeSupport.resolveCurrentNoonType(LocalTime.now());
        List<Map<String, Object>> raw = schedulingRepository.findRegistrarSchedules(
                deptId, employeeId, registLevelId, queryDate, queryDate, noonType);
        List<Map<String, Object>> list = raw.stream().map(this::enrichRegistrarScheduleRow).toList();
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("workDate", queryDate);
        result.put("noonType", noonType);
        result.put("noonLabel", NoonTypeSupport.label(noonType));
        return result;
    }

    private Map<String, Object> enrichRegistrarScheduleRow(Map<String, Object> row) {
        int noonType = ((Number) row.get("noonType")).intValue();
        row.put("noonLabel", NoonTypeSupport.label(noonType));
        row.put("timeRange", NoonTypeSupport.timeRange(noonType));
        return row;
    }
}
