package com.hospital.his.controller.doctor;

import com.hospital.common.Result;
import com.hospital.his.service.DoctorPatientVisitOrderResultService;
import com.hospital.his.service.VisitRecordQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/doctor/patients")
@RequiredArgsConstructor
public class DoctorPatientVisitController {

    private final VisitRecordQueryService visitRecordQueryService;
    private final DoctorPatientVisitOrderResultService orderResultService;

    @GetMapping("/{patientId}/visits")
    public Result<Map<String, Object>> listVisits(
            @PathVariable Long patientId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(visitRecordQueryService.listVisitsForDoctor(patientId, page, pageSize));
    }

    @GetMapping("/{patientId}/visits/{registerId}/hub")
    public Result<Map<String, Object>> getVisitHub(
            @PathVariable Long patientId,
            @PathVariable Long registerId) {
        return Result.success(visitRecordQueryService.getVisitHubForDoctor(patientId, registerId));
    }

    @GetMapping("/{patientId}/order-results/{kind}/{requestId}")
    public Result<Map<String, Object>> getOrderResult(
            @PathVariable Long patientId,
            @PathVariable String kind,
            @PathVariable Long requestId) {
        return Result.success(orderResultService.getOrderResult(patientId, kind, requestId));
    }
}
