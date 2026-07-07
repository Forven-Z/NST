package com.hospital.pharmacy.service;

import com.hospital.common.auth.UserType;
import com.hospital.common.exception.BusinessException;
import com.hospital.pharmacy.dto.pharmacy.CreateDrugRequest;
import com.hospital.pharmacy.dto.pharmacy.UpdateDrugRequest;
import com.hospital.pharmacy.repository.DrugRepository;
import com.hospital.pharmacy.security.AuthContext;
import com.hospital.pharmacy.security.AuthContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacyDrugServiceTest {

    @Mock
    private DrugRepository drugRepository;

    @InjectMocks
    private PharmacyDrugService pharmacyDrugService;

    @BeforeEach
    void setPharmacistContext() {
        AuthContextHolder.set(AuthContext.builder()
                .userType(UserType.STAFF)
                .userId(1L)
                .employeeId(10L)
                .roles(List.of("PHARMACIST"))
                .build());
    }

    @AfterEach
    void clearContext() {
        AuthContextHolder.clear();
    }

    @Test
    void createDrug_rejectsBlankName() {
        CreateDrugRequest request = new CreateDrugRequest();
        request.setDrugName("   ");
        request.setRetailPrice(new BigDecimal("12.50"));
        request.setStockQty(100);

        assertThatThrownBy(() -> pharmacyDrugService.createDrug(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("药品名称不能为空");

        verify(drugRepository, never()).insertDrug(anyString(), anyString(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void updateDrug_rejectsEmptyPatch() {
        UpdateDrugRequest request = new UpdateDrugRequest();
        when(drugRepository.findByIdAny(1L)).thenReturn(Optional.of(Map.of("id", 1L, "disabled", false)));

        assertThatThrownBy(() -> pharmacyDrugService.updateDrug(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请至少修改一项");

        verify(drugRepository, never()).updateDrug(anyLong(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void disableDrug_rejectsAlreadyDisabled() {
        when(drugRepository.findByIdAny(2L))
                .thenReturn(Optional.of(Map.of("id", 2L, "disabled", true)));

        assertThatThrownBy(() -> pharmacyDrugService.disableDrug(2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("药品已停用");

        verify(drugRepository, never()).setDelmark(anyLong(), anyInt());
    }

    @Test
    void enableDrug_rejectsActiveDrug() {
        when(drugRepository.findByIdAny(3L))
                .thenReturn(Optional.of(Map.of("id", 3L, "disabled", false)));

        assertThatThrownBy(() -> pharmacyDrugService.enableDrug(3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("药品未停用");
    }

    @Test
    void createDrug_requiresPharmacistRole() {
        AuthContextHolder.clear();
        AuthContextHolder.set(AuthContext.builder()
                .userType(UserType.STAFF)
                .userId(2L)
                .employeeId(20L)
                .roles(List.of("DOCTOR"))
                .build());

        CreateDrugRequest request = new CreateDrugRequest();
        request.setDrugName("阿莫西林");
        request.setRetailPrice(new BigDecimal("8.00"));
        request.setStockQty(50);

        assertThatThrownBy(() -> pharmacyDrugService.createDrug(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PHARMACIST");
    }
}
