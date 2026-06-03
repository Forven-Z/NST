package com.hospital.auth.domain;

/**
 * 员工登录联表查询结果：sys_user + employee + department。
 */
public record StaffAccount(
        Long userId,
        String username,
        String passwordHash,
        String userType,
        Integer status,
        Long employeeId,
        String realName,
        String roleType,
        Long deptId,
        String deptName
) {
}
