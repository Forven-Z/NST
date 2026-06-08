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

import java.time.LocalDate;
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
                        p.getBirthDate(), p.getIdCard(), p.getPhone(), p.getAddress(), 0, false, null, null, null)))
                .orElse(List.of());
        List<Map<String, Object>> family = familyRepository.listByOwner(ownerId);
        Map<String, Object> result = new HashMap<>();
        result.put("list", mergeList(self, family));
        return result;
    }

    @Transactional
    public Map<String, Object> addMember(AddFamilyMemberRequest request) {
        Long ownerId = AuthContextHolder.require().getPatientId();
        boolean noIdCard = Boolean.TRUE.equals(request.getNoIdCard());
        validateAddRequest(request, noIdCard, ownerId);

        String phone = noIdCard ? null : blankToNull(request.getPhone());
        patientRepository.assertPhoneAvailable(phone, null);

        String idCard = noIdCard ? null : blankToNull(request.getIdCard());
        String address = blankToNull(request.getAddress());
        Integer gender = request.getGender() != null ? request.getGender() : 1;
        LocalDate birthDate = request.getBirthDate();

        Long memberId;
        if (noIdCard) {
            String mrn = BizNoGenerator.medicalRecordNo();
            memberId = patientRepository.insertFamilyPatient(mrn, request.getRealName(), gender,
                    birthDate, null, null, address);
        } else {
            memberId = patientRepository.findPatientIdByIdCard(idCard).orElseGet(() -> {
                String mrn = BizNoGenerator.medicalRecordNo();
                return patientRepository.insertFamilyPatient(mrn, request.getRealName(), gender,
                        birthDate, idCard, phone, address);
            });
        }

        if (memberId.equals(ownerId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "本人已在就诊人列表中");
        }
        patientRepository.assertPhoneAvailable(phone, memberId);
        patientRepository.updateProfile(memberId, request.getRealName(), gender,
                birthDate, phone, idCard, address, null);

        int relation = normalizeRelationType(request.getRelationType(), noIdCard);
        String guardianName = noIdCard ? blankToNull(request.getGuardianName()) : null;
        String guardianIdCard = noIdCard ? blankToNull(request.getGuardianIdCard()) : null;
        String guardianPhone = noIdCard ? blankToNull(request.getGuardianPhone()) : null;
        familyRepository.insertLink(ownerId, memberId, relation, noIdCard,
                guardianName, guardianIdCard, guardianPhone);

        Map<String, Object> row = new HashMap<>();
        row.put("memberPatientId", memberId);
        row.put("realName", request.getRealName());
        row.put("medicalRecordNo", patientRepository.findMedicalRecordNo(memberId));
        row.put("gender", gender);
        row.put("birthDate", birthDate != null ? birthDate.toString() : null);
        row.put("idCard", idCard);
        row.put("phone", phone);
        row.put("address", address);
        row.put("relationType", relation);
        row.put("noIdCard", noIdCard);
        if (noIdCard) {
            row.put("guardianName", guardianName);
            row.put("guardianIdCard", guardianIdCard);
            row.put("guardianPhone", guardianPhone);
        }
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

    private void validateAddRequest(AddFamilyMemberRequest request, boolean noIdCard, Long ownerId) {
        if (request.getGender() == null || (request.getGender() != 1 && request.getGender() != 2)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择性别");
        }
        if (request.getBirthDate() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择出生日期");
        }
        if (noIdCard) {
            if (isNotBlank(request.getIdCard()) || isNotBlank(request.getPhone())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "无身份证患儿不应填写身份证号或手机号");
            }
            if (!isNotBlank(request.getGuardianName()) || !isNotBlank(request.getGuardianIdCard())
                    || !isNotBlank(request.getGuardianPhone())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写陪诊人姓名、身份证与手机号");
            }
            String guardianId = request.getGuardianIdCard().trim();
            if (guardianId.length() != 18) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "陪诊人身份证号格式不正确");
            }
            String guardianPhone = request.getGuardianPhone().trim();
            if (!guardianPhone.matches("^1\\d{10}$")) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "陪诊人手机号格式不正确");
            }
            var owner = patientRepository.findProfileById(ownerId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "账号档案不存在"));
            if (owner.getIdCard() == null || !owner.getIdCard().equalsIgnoreCase(guardianId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "陪诊人须为当前账号本人（身份证与本人档案一致）");
            }
            return;
        }
        String idCard = blankToNull(request.getIdCard());
        if (idCard == null || idCard.length() != 18) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写18位身份证号");
        }
        String phone = blankToNull(request.getPhone());
        if (phone != null && !phone.matches("^1\\d{10}$")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号格式不正确");
        }
    }

    private Map<String, Object> selfRow(Long id, String name, String mrn, Integer gender, LocalDate birthDate,
                                        String idCard, String phone, String address, int relation,
                                        boolean noIdCard, String guardianName, String guardianIdCard,
                                        String guardianPhone) {
        Map<String, Object> row = new HashMap<>();
        row.put("memberPatientId", id);
        row.put("realName", name);
        row.put("medicalRecordNo", mrn);
        row.put("gender", gender);
        row.put("birthDate", birthDate != null ? birthDate.toString() : null);
        row.put("idCard", idCard);
        row.put("phone", phone);
        row.put("address", address);
        row.put("relationType", relation);
        row.put("noIdCard", noIdCard);
        row.put("guardianName", guardianName);
        row.put("guardianIdCard", guardianIdCard);
        row.put("guardianPhone", guardianPhone);
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

    /** 0本人 1父母 2配偶 3子女 4其他；缺省与历史值 6 均归并为 4；无身份证患儿默认子女 */
    private static int normalizeRelationType(Integer relationType, boolean noIdCard) {
        if (relationType == null || relationType == 6) {
            return noIdCard ? 3 : 4;
        }
        if (relationType < 0 || relationType > 4) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的关系类型");
        }
        return relationType;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
