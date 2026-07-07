package com.hospital.patient.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class IdCardUtilsTest {

    private static final String VALID_ID = "110101199003075516";

    @Test
    void normalizeIdCard_trimsAndUppercases() {
        assertThat(IdCardUtils.normalizeIdCard(" 11010119900307551x "))
                .isEqualTo("11010119900307551X");
    }

    @Test
    void normalizeIdCard_blankReturnsNull() {
        assertThat(IdCardUtils.normalizeIdCard("  ")).isNull();
    }

    @Test
    void parseBirthDate_validId() {
        assertThat(IdCardUtils.parseBirthDate(VALID_ID)).isEqualTo(LocalDate.of(1990, 3, 7));
    }

    @Test
    void parseBirthDate_invalidFormat() {
        assertThat(IdCardUtils.parseBirthDate("123456")).isNull();
    }

    @Test
    void resolveBirthDate_prefersExplicitBirthDate() {
        LocalDate birthDate = LocalDate.of(1988, 1, 1);

        assertThat(IdCardUtils.resolveBirthDate(birthDate, 30, VALID_ID)).isEqualTo(birthDate);
    }

    @Test
    void resolveBirthDate_fallsBackToIdCard() {
        assertThat(IdCardUtils.resolveBirthDate(null, null, VALID_ID))
                .isEqualTo(LocalDate.of(1990, 3, 7));
    }

    @Test
    void resolveBirthDate_fallsBackToAge() {
        LocalDate birthDate = IdCardUtils.resolveBirthDate(null, 25, null);

        assertThat(birthDate).isEqualTo(LocalDate.now().minusYears(25));
    }

    @Test
    void resolveAge_prefersFormAge() {
        assertThat(IdCardUtils.resolveAge(40, LocalDate.of(1990, 1, 1))).isEqualTo(40);
    }

    @Test
    void resolveAge_fromBirthDate() {
        Integer age = IdCardUtils.resolveAge(null, LocalDate.now().minusYears(30));

        assertThat(age).isEqualTo(30);
    }
}
