package com.msp.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BitmaskPermissionTest {

    @Test
    @DisplayName("Evaluate grant, revoke, and has operations correctly")
    void testBasicPermissionOperations() {
        BitmaskPermission perm = BitmaskPermission.of(PermissionFlag.READ, PermissionFlag.WRITE);

        assertTrue(perm.has(PermissionFlag.READ));
        assertTrue(perm.has(PermissionFlag.WRITE));
        assertFalse(perm.has(PermissionFlag.DELETE));

        // Grant DELETE
        perm.grant(PermissionFlag.DELETE);
        assertTrue(perm.has(PermissionFlag.DELETE));

        // Revoke READ
        perm.revoke(PermissionFlag.READ);
        assertFalse(perm.has(PermissionFlag.READ));
        assertTrue(perm.has(PermissionFlag.WRITE));
        assertTrue(perm.has(PermissionFlag.DELETE));
    }

    @Test
    @DisplayName("Static multi-flag hasAll and hasAny evaluation")
    void testMultiFlagEvaluation() {
        long mask = BitmaskPermission.grant(0L, PermissionFlag.READ.mask(), PermissionFlag.UPDATE.mask());

        assertTrue(BitmaskPermission.hasAll(mask, PermissionFlag.READ.mask(), PermissionFlag.UPDATE.mask()));
        assertFalse(BitmaskPermission.hasAll(mask, PermissionFlag.READ.mask(), PermissionFlag.DELETE.mask()));

        assertTrue(BitmaskPermission.hasAny(mask, PermissionFlag.DELETE.mask(), PermissionFlag.READ.mask()));
        assertFalse(BitmaskPermission.hasAny(mask, PermissionFlag.DELETE.mask(), PermissionFlag.ADMIN.mask()));
    }

    @Test
    @DisplayName("Toggle bitmask switches permission on and off")
    void testToggle() {
        BitmaskPermission perm = new BitmaskPermission();
        assertFalse(perm.has(PermissionFlag.ADMIN));

        perm.toggle(PermissionFlag.ADMIN);
        assertTrue(perm.has(PermissionFlag.ADMIN));

        perm.toggle(PermissionFlag.ADMIN);
        assertFalse(perm.has(PermissionFlag.ADMIN));
    }

    @Test
    @DisplayName("Describe returns matched enum names")
    void testDescribe() {
        BitmaskPermission perm = BitmaskPermission.of(PermissionFlag.READ, PermissionFlag.AUDIT);
        List<String> names = perm.describe();

        assertEquals(List.of("READ", "AUDIT"), names);
    }
}
