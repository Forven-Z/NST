package com.hospital.his.dto.doctor;

import lombok.Data;

import java.util.List;

@Data
public class MedicalRecordSaveRequest {

    private String readme;
    private String present;
    private String presentTreat;
    private String history;
    private String allergy;
    private String physique;
    private String diagnosis;
    private String cure;
    private String checkAdvice;
    private String inspectionAdvice;

    /** 扁平 ID 列表（兼容）；首个为主要诊断，其余为次要诊断 */
    private List<Long> diseaseIds;

    /** 结构化疾病关联；优先于 diseaseIds */
    private List<DiseaseEntry> diseaseEntries;

    @Data
    public static class DiseaseEntry {

        private Long diseaseId;
        /** 1=主要诊断 2=次要诊断 */
        private Integer diseaseType;
    }
}
