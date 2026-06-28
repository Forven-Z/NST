package com.hospital.management.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class BatchUpsertRequest {
    @NotNull
    private Long deptId;
    @NotNull
    private LocalDate weekStart;
    @NotEmpty
    private List<BatchUpsertChangeItem> changes;
}
