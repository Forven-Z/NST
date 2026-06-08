package com.hospital.disposal.security;

import com.hospital.common.auth.UserType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AuthContext {

    private final UserType userType;
    private final Long userId;
    private final Long employeeId;
    private final List<String> roles;

    public boolean isStaff() {
        return UserType.STAFF == userType || UserType.ADMIN == userType;
    }
}
