package com.cybersixseven.platformapi.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UserRoleTests {

    @Test
    void onlyTeacherAndAdminAreStaff() {
        assertFalse(UserRole.STUDENT.isStaff());
        assertTrue(UserRole.TEACHER.isStaff());
        assertTrue(UserRole.ADMIN.isStaff());
    }
}
