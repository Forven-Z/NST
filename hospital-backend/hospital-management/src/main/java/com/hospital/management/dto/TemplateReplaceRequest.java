package com.hospital.management.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class TemplateReplaceRequest {
    @NotEmpty
    private List<TemplateSlotItem> slots;
}
