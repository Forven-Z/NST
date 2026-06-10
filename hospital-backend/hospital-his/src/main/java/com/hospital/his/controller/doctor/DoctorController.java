package com.hospital.his.controller.doctor;

import com.hospital.common.Result;
import com.hospital.his.dto.doctor.MedicalRecordSaveRequest;
import com.hospital.his.service.DoctorDictQueryService;
import com.hospital.his.service.DoctorMedicalRecordService;
import com.hospital.his.service.DoctorQueueService;
import com.hospital.his.service.RegisterOrdersService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/doctor")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorQueueService doctorQueueService;
    private final DoctorMedicalRecordService doctorMedicalRecordService;
    private final RegisterOrdersService registerOrdersService;
    private final DoctorDictQueryService doctorDictQueryService;

    @GetMapping("/queues")
    public Result<Map<String, Object>> listQueue(
            @RequestParam(required = false) Integer visitState,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(doctorQueueService.listQueue(visitState, keyword, page, pageSize));
    }

    @PostMapping("/call/{registerId}")
    public Result<Map<String, Object>> callPatient(@PathVariable Long registerId) {
        return Result.success(doctorQueueService.callPatient(registerId));
    }

    @GetMapping("/medical-records/{registerId}")
    public Result<Map<String, Object>> getMedicalRecord(@PathVariable Long registerId) {
        return Result.success(doctorMedicalRecordService.getMedicalRecord(registerId));
    }

    @PutMapping("/medical-records/{registerId}")
    public Result<Map<String, Object>> saveMedicalRecord(
            @PathVariable Long registerId,
            @RequestBody MedicalRecordSaveRequest request) {
        return Result.success(doctorMedicalRecordService.saveMedicalRecord(registerId, request));
    }

    @GetMapping("/registers/{registerId}/orders")
    public Result<Map<String, Object>> getRegisterOrders(@PathVariable Long registerId) {
        return Result.success(registerOrdersService.getOrdersForDoctor(registerId));
    }

    @GetMapping("/diseases")
    public Result<Map<String, Object>> listDiseases(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        return Result.success(doctorDictQueryService.listDiseases(keyword, page, pageSize));
    }
}
