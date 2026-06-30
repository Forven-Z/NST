package com.hospital.pacs.order;

import com.hospital.common.execute.AbstractMedTechOrderCoordinator;
import com.hospital.common.execute.MedTechOrderStatusWriter;
import com.hospital.pacs.repository.CheckRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PacsMedTechOrderCoordinator extends AbstractMedTechOrderCoordinator {

    private final CheckRequestRepository checkRequestRepository;

    @Override
    protected MedTechOrderStatusWriter statusWriter() {
        return checkRequestRepository;
    }

    @Override
    protected String orderNotFoundMessage() {
        return "检查申请不存在";
    }

    @Override
    protected String executeMismatchHint() {
        return "仅已缴费申请可执行";
    }
}
