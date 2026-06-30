package com.hospital.disposal.order;

import com.hospital.common.execute.AbstractMedTechOrderCoordinator;
import com.hospital.common.execute.MedTechOrderStatusWriter;
import com.hospital.disposal.repository.DisposalRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DisposalMedTechOrderCoordinator extends AbstractMedTechOrderCoordinator {

    private final DisposalRequestRepository disposalRequestRepository;

    @Override
    protected MedTechOrderStatusWriter statusWriter() {
        return disposalRequestRepository;
    }

    @Override
    protected String orderNotFoundMessage() {
        return "处置申请不存在";
    }

    @Override
    protected String executeMismatchHint() {
        return "仅已缴费申请可执行";
    }
}
