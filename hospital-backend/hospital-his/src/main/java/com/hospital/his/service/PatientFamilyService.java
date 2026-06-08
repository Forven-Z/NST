package com.hospital.his.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.dto.patient.AddFamilyMemberRequest;
import com.hospital.his.repository.PatientFamilyRepository;
import com.hospital.his.repository.PatientRepository;
import com.hospital.his.security.AuthContextHolder;
import com.hospital.his.util.BizNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PatientFamilyService {

    private final PatientFamilyRepository familyRepository;
    private final PatientRepository patientRepository;

    public Map<String, Object> listMembers() {
        Long ownerId = AuthContextHolder.require().getPatientId();
        List<Map<String, Object>> self = patientRepository.findProfileById(ownerId)
                .map(p -> List.of(selfRow(ownerId, p.getRealName(), p.getMedicalRecordNo(), p.getGender(),
                        p.getIdCard(), p.getPhone(), 0)))
                .orElse(List.of());
        List<Map<String, Object>> family = familyRepository.listByOwner(ownerId);
        Map<String, Object> result = new HashMap<>();
        result.put("list", mergeList(self, family));
        return result;
    }

    @Transactional
    public Map<String, Object> addMember(AddFamilyMemberRequest request) {
        Long ownerId = AuthContextHolder.require().getPatientId();
        Long memberId = patientRepository.findPatientIdByIdCard(request.getIdCard()).orElseGet(() -> {
            String mrn = BizNoGenerator.medicalRecordNo();
            return patientRepository.insertFamilyPatient(
                    mrn, request.getRealName(), request.getGender(),
                    request.getIdCard(), request.getPhone());
        });
        if (memberId.equals(ownerId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "本人已在就诊人列表中");
        }
        patientRepository.updateProfile(memberId, request.getRealName(), request.getGender(),
                null, request.getPhone(), request.getIdCard(), null, null);
        int relation = normalizeRelationType(request.getRelationType());
        familyRepository.insertLink(ownerId, memberId, relation);
        Map<String, Object> row = new HashMap<>();
        row.put("memberPatientId", memberId);
        row.put("realName", request.getRealName());
        row.put("medicalRecordNo", patientRepository.findMedicalRecordNo(memberId));
        row.put("relationType", relation);
        return row;
    }

    /**
     * 方案 A：JWT {@code patientId} = 操作者（微信绑定）；{@code visitPatientId} = 当前就诊人（首页切换，可不传）。
     */
    public Long resolveVisitPatientId(Long visitPatientId) {
        Long operatorId = AuthContextHolder.require().getPatientId();
        if (visitPatientId == null || visitPatientId.equals(operatorId)) {
            return operatorId;
        }
        if (!familyRepository.isLinked(operatorId, visitPatientId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权以该就诊人身份操作");
        }
        return visitPatientId;
    }

    public void assertCanAccessVisitPatient(Long visitPatientId) {
        resolveVisitPatientId(visitPatientId);
    }

    public boolean canAccessVisitPatient(Long operatorPatientId, Long visitPatientId) {
        if (visitPatientId == null || operatorPatientId.equals(visitPatientId)) {
            return true;
        }
        return familyRepository.isLinked(operatorPatientId, visitPatientId);
    }

    public void assertCanRegisterFor(Long memberPatientId) {
        assertCanAccessVisitPatient(memberPatientId);
    }

    private Map<String, Object> selfRow(Long id, String name, String mrn, Integer gender,
                                        String idCard, String phone, int relation) {
        Map<String, Object> row = new HashMap<>();
        row.put("memberPatientId", id);
        row.put("realName", name);
        row.put("medicalRecordNo", mrn);
        row.put("gender", gender);
        row.put("idCard", idCard);
        row.put("phone", phone);
        row.put("relationType", relation);
        row.put("isSelf", true);
        return row;
    }

    private List<Map<String, Object>> mergeList(List<Map<String, Object>> self, List<Map<String, Object>> family) {
        java.util.ArrayList<Map<String, Object>> list = new java.util.ArrayList<>(self);
        for (Map<String, Object> row : family) {
            row.put("isSelf", false);
            list.add(row);
        }
        return list;
    }

    /** 0本人 1父母 2配偶 3子女 4其他；缺省与历史值 6 均归并为 4 */
    private static int normalizeRelationType(Integer relationType) {
        if (relationType == null || relationType == 6) {
            return 4;
        }
        if (relationType < 0 || relationType > 4) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的关系类型");
        }
        return relationType;
    }
}
