package com.hospital.management.service;

import com.hospital.management.dto.TemplateReplaceRequest;
import com.hospital.management.dto.TemplateSlotItem;
import com.hospital.management.repository.EmployeeRepository;
import com.hospital.management.repository.SchedulingTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulingTemplateServiceTest {

    @Mock
    private SchedulingTemplateRepository templateRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @InjectMocks
    private SchedulingTemplateService service;

    @Test
    void replaceTemplate_fillsDefaultQuotaWhenMissing() {
        when(employeeRepository.existsActive(1L)).thenReturn(true);
        when(employeeRepository.findRoleType(1L)).thenReturn(Optional.of("OUTPATIENT_DOCTOR"));
        when(templateRepository.listByEmployee(1L)).thenReturn(List.of());

        TemplateReplaceRequest req = new TemplateReplaceRequest();
        TemplateSlotItem slot = new TemplateSlotItem();
        slot.setWeekday(1);
        slot.setNoonType(1);
        slot.setRegistLevelId(2L);
        slot.setEnabled(true);
        req.setSlots(List.of(slot));

        service.replaceTemplate(1L, req);

        verify(templateRepository).replaceForEmployee(eq(1L), argThat(list ->
                list.size() == 1 && Integer.valueOf(15).equals(list.get(0).get("totalQuota"))));
    }
}
