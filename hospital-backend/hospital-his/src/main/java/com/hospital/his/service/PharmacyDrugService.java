package com.hospital.his.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.dto.pharmacy.CreateDrugRequest;
import com.hospital.his.dto.pharmacy.UpdateDrugRequest;
import com.hospital.his.repository.DrugRepository;
import com.hospital.his.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PharmacyDrugService {

    private final DrugRepository drugRepository;

    public Map<String, Object> listDrugs(String keyword, boolean includeDisabled, int page, int pageSize) {
        requirePharmacist();
        int offset = Math.max(page - 1, 0) * pageSize;
        return Map.of(
                "list", drugRepository.listDrugsForPharmacy(keyword, includeDisabled, offset, pageSize),
                "page", page,
                "pageSize", pageSize
        );
    }

    @Transactional
    public Map<String, Object> createDrug(CreateDrugRequest request) {
        requirePharmacist();
        String name = request.getDrugName().trim();
        if (name.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "药品名称不能为空");
        }
        String drugCode = drugRepository.nextDrugCode();
        long id = drugRepository.insertDrug(
                drugCode,
                name,
                blankToNull(request.getDrugFormat()),
                blankToNull(request.getDrugDosage()),
                blankToNull(request.getDrugType()),
                blankToNull(request.getUnit()),
                request.getRetailPrice(),
                request.getStockQty()
        );
        return drugRepository.findByIdAny(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "创建药品失败"));
    }

    @Transactional
    public Map<String, Object> updateDrug(Long id, UpdateDrugRequest request) {
        requirePharmacist();
        ensureExists(id);
        if (request.getDrugName() != null && request.getDrugName().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "药品名称不能为空");
        }
        if (request.getDrugName() == null && request.getRetailPrice() == null && request.getStockQty() == null
                && request.getDrugFormat() == null && request.getDrugDosage() == null
                && request.getDrugType() == null && request.getUnit() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请至少修改一项");
        }
        drugRepository.updateDrug(
                id,
                request.getDrugName() != null ? request.getDrugName().trim() : null,
                blankToNull(request.getDrugFormat()),
                blankToNull(request.getDrugDosage()),
                blankToNull(request.getDrugType()),
                blankToNull(request.getUnit()),
                request.getRetailPrice(),
                request.getStockQty()
        );
        return drugRepository.findByIdAny(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "药品不存在"));
    }

    @Transactional
    public Map<String, Object> disableDrug(Long id) {
        requirePharmacist();
        Map<String, Object> drug = ensureExists(id);
        if (Boolean.TRUE.equals(drug.get("disabled"))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "药品已停用");
        }
        drugRepository.setDelmark(id, 1);
        return drugRepository.findByIdAny(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "药品不存在"));
    }

    @Transactional
    public Map<String, Object> enableDrug(Long id) {
        requirePharmacist();
        Map<String, Object> drug = ensureExists(id);
        if (!Boolean.TRUE.equals(drug.get("disabled"))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "药品未停用");
        }
        drugRepository.setDelmark(id, 0);
        return drugRepository.findByIdAny(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "药品不存在"));
    }

    private Map<String, Object> ensureExists(Long id) {
        return drugRepository.findByIdAny(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "药品不存在"));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void requirePharmacist() {
        var context = AuthContextHolder.require();
        if (context.getEmployeeId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要药师身份");
        }
        List<String> roles = context.getRoles();
        if (roles != null && (roles.contains("PHARMACIST") || roles.contains("ADMIN"))) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "需要药师角色 PHARMACIST");
    }
}
