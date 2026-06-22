package com.hospital.his.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.dto.patient.PatientLoginRequest;
import com.hospital.his.repository.PatientRepository;
import com.hospital.his.util.BizNoGenerator;
import com.hospital.his.util.IdCardUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 登录档案落库（独立事务，提交后再由 auth 签发 Token）。
 */
@Service
@RequiredArgsConstructor
public class PatientLoginPersistence {

    private final PatientRepository patientRepository;

    record UpsertResult(Long patientId, boolean isNewPatient) {
    }

    @Transactional
    public UpsertResult upsert(PatientLoginRequest request, String phone, String idCard, String address) {
        Optional<Long> byPhone = patientRepository.findPatientIdByPhone(phone);
        Optional<Long> byIdCard = patientRepository.findPatientIdByIdCard(idCard);

        Long patientId;
        boolean isNew = false;
        if (byPhone.isPresent() && byIdCard.isPresent()) {
            if (!byPhone.get().equals(byIdCard.get())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号与身份证不属于同一患者档案");
            }
            patientId = byPhone.get();
        } else if (byPhone.isPresent()) {
            patientId = byPhone.get();
        } else if (byIdCard.isPresent()) {
            patientId = byIdCard.get();
            patientRepository.assertPhoneAvailable(phone, patientId);
        } else {
            isNew = true;
            String mrn = BizNoGenerator.medicalRecordNo();
            patientId = patientRepository.insertFamilyPatient(
                    mrn, request.getRealName().trim(), request.getGender(),
                    request.getBirthDate(), IdCardUtils.resolveAge(null, request.getBirthDate()),
                    idCard, phone, address);
        }

        patientRepository.updateProfile(patientId, request.getRealName().trim(), request.getGender(),
                request.getBirthDate(), IdCardUtils.resolveAge(null, request.getBirthDate()),
                phone, idCard, address, null);

        return new UpsertResult(patientId, isNew);
    }

    @Transactional
    public UpsertResult upsertForWindow(String patientName, Integer gender, LocalDate birthDate, Integer age,
                                        String phone, String idCard, String address,
                                        Long settleCategoryId) {
        String normalizedPhone = StringUtils.hasText(phone) ? phone.trim() : null;
        String normalizedIdCard = IdCardUtils.normalizeIdCard(idCard);

        if (!StringUtils.hasText(normalizedPhone) || !StringUtils.hasText(normalizedIdCard)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写身份证号和手机号");
        }

        LocalDate resolvedBirthDate = IdCardUtils.resolveBirthDate(birthDate, age, normalizedIdCard);
        Integer resolvedAge = IdCardUtils.resolveAge(age, resolvedBirthDate);

        Optional<Long> byPhone = normalizedPhone != null
                ? patientRepository.findPatientIdByPhone(normalizedPhone) : Optional.empty();
        Optional<Long> byIdCard = normalizedIdCard != null
                ? patientRepository.findPatientIdByIdCard(normalizedIdCard) : Optional.empty();

        Long patientId;
        boolean isNew = false;
        if (byPhone.isPresent() && byIdCard.isPresent()) {
            if (!byPhone.get().equals(byIdCard.get())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号与身份证不属于同一患者档案");
            }
            patientId = byPhone.get();
        } else if (byPhone.isPresent()) {
            patientId = byPhone.get();
        } else if (byIdCard.isPresent()) {
            patientId = byIdCard.get();
            if (normalizedPhone != null) {
                patientRepository.assertPhoneAvailable(normalizedPhone, patientId);
            }
        } else {
            isNew = true;
            String mrn = BizNoGenerator.medicalRecordNo();
            patientId = patientRepository.insertFamilyPatient(
                    mrn, patientName.trim(), gender, resolvedBirthDate, resolvedAge,
                    normalizedIdCard, normalizedPhone, address);
        }

        if (normalizedPhone != null) {
            patientRepository.assertPhoneAvailable(normalizedPhone, patientId);
        }

        patientRepository.updateProfile(patientId, patientName.trim(), gender, resolvedBirthDate, resolvedAge,
                normalizedPhone, normalizedIdCard, address, settleCategoryId);

        return new UpsertResult(patientId, isNew);
    }
}
