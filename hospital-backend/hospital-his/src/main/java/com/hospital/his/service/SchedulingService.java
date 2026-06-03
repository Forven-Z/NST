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
}
