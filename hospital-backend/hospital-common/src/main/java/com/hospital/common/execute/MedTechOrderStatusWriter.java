package com.hospital.common.execute;

/**
 * 医技医嘱 status 写库端口（lis / pacs / disposal 各 Repository 实现）。
 */
public interface MedTechOrderStatusWriter {

    java.util.Optional<java.util.Map<String, Object>> findByIdForUpdate(Long orderId);

    int markExecutedIfCurrent(Long orderId, int expectedFrom, Long executorId);

    int updateStatusIfCurrent(Long orderId, int expectedFrom, int newStatus);
}
