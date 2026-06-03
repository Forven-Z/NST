package com.hospital.common.auth;

/**
 * JWT 载荷中的用户类型，Gateway 与各业务服务据此区分患者 / 医护 / 管理员。
 */
public enum UserType {

    PATIENT,
    STAFF,
    ADMIN
}
