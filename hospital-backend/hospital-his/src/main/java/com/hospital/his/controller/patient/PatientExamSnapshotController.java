package com.hospital.his.controller.patient;

import com.hospital.his.service.PatientExamSnapshotService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/patient/reports/exam")
@RequiredArgsConstructor
public class PatientExamSnapshotController {

    private final PatientExamSnapshotService patientExamSnapshotService;

    @GetMapping("/{checkRequestId}/snapshot/{plane}")
    public void snapshot(
            @PathVariable Long checkRequestId,
            @PathVariable String plane,
            HttpServletResponse response) throws Exception {
        patientExamSnapshotService.streamSnapshot(checkRequestId, plane, response);
    }
}
