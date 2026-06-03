package com.hospital.management.controller;

import com.hospital.common.Result;
import com.hospital.management.service.DictReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminDictController {

    private final DictReadService dictReadService;

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(Map.of("status", "UP", "service", "hospital-management"));
    }

    @GetMapping("/departments")
    public Result<Map<String, Object>> departments(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(dictReadService.listDepartments(keyword, page, pageSize));
    }

    @GetMapping("/departments/{id}")
    public Result<Map<String, Object>> department(@PathVariable Long id) {
        return Result.success(dictReadService.getDepartment(id));
    }

    @GetMapping("/regist-levels")
    public Result<Map<String, Object>> registLevels(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(dictReadService.listRegistLevels(keyword, page, pageSize));
    }

    @GetMapping("/settle-categories")
    public Result<Map<String, Object>> settleCategories(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(dictReadService.listSettleCategories(keyword, page, pageSize));
    }

    @GetMapping("/medical-technologies")
    public Result<Map<String, Object>> medicalTechnologies(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String techType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(dictReadService.listMedicalTechnologies(keyword, techType, page, pageSize));
    }

    @GetMapping("/drugs")
    public Result<Map<String, Object>> drugs(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(dictReadService.listDrugs(keyword, page, pageSize));
    }

    @GetMapping("/diseases")
    public Result<Map<String, Object>> diseases(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(dictReadService.listDiseases(keyword, page, pageSize));
    }
}
