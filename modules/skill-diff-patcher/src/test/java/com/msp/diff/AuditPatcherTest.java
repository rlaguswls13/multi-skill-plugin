package com.msp.diff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuditPatcher and Patch/Rollback Unit Tests")
class AuditPatcherTest {

    @Test
    @DisplayName("Formats audit log strings correctly")
    void testAuditLogFormatting() {
        DiffResult diff = new DiffResult(List.of(
                new FieldChange(ChangeType.MODIFIED, "address.city", "Seoul", "Busan"),
                new FieldChange(ChangeType.ADDED, "role", null, "ADMIN"),
                new FieldChange(ChangeType.REMOVED, "token", "xyz-123", null)
        ));

        List<String> logs = AuditPatcher.formatAuditLogs(diff);
        assertEquals(3, logs.size());
        assertEquals("[address.city] 'Seoul' -> 'Busan'", logs.get(0));
        assertEquals("[role] ADDED: 'ADMIN'", logs.get(1));
        assertEquals("[token] REMOVED: 'xyz-123'", logs.get(2));
    }

    @Test
    @DisplayName("Produces valid RFC 6902 JSON patch operations")
    void testJsonPatch() {
        DiffResult diff = new DiffResult(List.of(
                new FieldChange(ChangeType.MODIFIED, "user.email", "old@ex.com", "new@ex.com"),
                new FieldChange(ChangeType.ADDED, "items[0].price", null, 99.9),
                new FieldChange(ChangeType.REMOVED, "active", true, null)
        ));

        List<Map<String, Object>> patches = AuditPatcher.toJsonPatch(diff);
        assertEquals(3, patches.size());

        assertEquals("replace", patches.get(0).get("op"));
        assertEquals("/user/email", patches.get(0).get("path"));
        assertEquals("new@ex.com", patches.get(0).get("value"));

        assertEquals("add", patches.get(1).get("op"));
        assertEquals("/items/0/price", patches.get(1).get("path"));
        assertEquals(99.9, patches.get(1).get("value"));

        assertEquals("remove", patches.get(2).get("op"));
        assertEquals("/active", patches.get(2).get("path"));
    }

    @Test
    @DisplayName("Applies patch forward and rolls back changes backward")
    void testApplyAndRollback() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("name", "Original Name");
        state.put("profile", new LinkedHashMap<>(Map.of("level", 1)));

        // Create diff that:
        // - modifies "name"
        // - adds "profile.vip"
        // - adds "department"
        DiffResult diff = new DiffResult(List.of(
                new FieldChange(ChangeType.MODIFIED, "name", "Original Name", "Patched Name"),
                new FieldChange(ChangeType.ADDED, "profile.vip", null, true),
                new FieldChange(ChangeType.ADDED, "department", null, "AI Lab")
        ));

        // 1. Apply forward
        AuditPatcher.apply(state, diff);
        assertEquals("Patched Name", state.get("name"));
        assertEquals("AI Lab", state.get("department"));
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) state.get("profile");
        assertEquals(true, profile.get("vip"));

        // 2. Rollback backward
        AuditPatcher.rollback(state, diff);
        assertEquals("Original Name", state.get("name"));
        assertNull(state.get("department"));
        assertNull(profile.get("vip"));
    }

    @Test
    @DisplayName("DiffPatcher facade delegates seamlessly")
    void testFacade() {
        Map<String, Object> a = Map.of("x", 1);
        Map<String, Object> b = Map.of("x", 2);

        DiffResult diff = DiffPatcher.diff(a, b);
        assertTrue(diff.hasChanges());
        assertEquals(1, DiffPatcher.formatAuditLogs(diff).size());
        assertEquals(1, DiffPatcher.toJsonPatch(diff).size());
    }
}
