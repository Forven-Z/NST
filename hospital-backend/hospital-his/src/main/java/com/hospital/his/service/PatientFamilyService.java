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
        int relation = request.getRelationType() != null ? request.getRelationType() : 4;
        familyRepository.insertLink(ownerId, memberId, relation);
        Map<String, Object> row = new HashMap<>();
        row.put("memberPatientId", memberId);
        row.put("realName", request.getRealName());
        row.put("medicalRecordNo", patientRepository.findMedicalRecordNo(memberId));
        row.put("relationType", relation);
        return row;
    }

    public void assertCanRegisterFor(Long memberPatientId) {
        Long ownerId = AuthContextHolder.require().getPatientId();
        if (!familyRepository.isLinked(ownerId, memberPatientId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权为该就诊人挂号");
        }
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
}
