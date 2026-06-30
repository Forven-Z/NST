package com.hospital.his.dto.doctor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class UpdatePrescriptionRequest {

    @NotEmpty
    @Valid
    private List<CreatePrescriptionRequest.PrescriptionItemRequest> items;
}
