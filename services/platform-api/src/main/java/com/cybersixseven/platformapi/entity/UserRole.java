package com.cybersixseven.platformapi.entity;

public enum UserRole {
    STUDENT,
    TEACHER,
    ADMIN;

    public boolean isStaff() {
        return this == TEACHER || this == ADMIN;
    }
}
