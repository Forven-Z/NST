package com.hospital.lis.order;

import com.hospital.common.execute.AbstractMedTechOrderCoordinator;
import com.hospital.common.execute.MedTechOrderStatusWriter;
import com.hospital.lis.repository.InspectionRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LisMedTechOrderCoordinator extends AbstractMedTechOrderCoordinator {

    private final InspectionRequestRepository inspectionRequestRepository;

    @Override
    protected MedTechOrderStatusWriter statusWriter() {
        return inspectionRequestRepository;
    }

    @Override
    protected String orderNotFoundMessage() {
        return "检验申请不存在";
    }

    @Override
    protected String executeMismatchHint() {
        return "仅已缴费申请可执行";
    }
}
