package com.hospital.pacs.security;

import com.hospital.common.auth.UserType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AuthContext {

    private final Long employeeId;
    private final List<String> roles;

    public boolean isStaff() {
        return true;
    }
}
