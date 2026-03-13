package com.fpoly.duan.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum UserRole {
    USER(0, "ROLE_USER"),
    STAFF(1, "ROLE_STAFF"),
    ADMIN(2, "ROLE_ADMIN"),
    SUPER_ADMIN(3, "ROLE_SUPER_ADMIN");

    private final int value;
    private final String authority;

    public static UserRole fromValue(int value) {
        for (UserRole role : values()) {
            if (role.value == value) {
                return role;
            }
        }
        return USER;
    }
}
