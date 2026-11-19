package com.msp.sqlseek;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KeysetSeekBuilderTest {

    @Test
    void shouldBuildExpandedDisjunctiveForMixedThreeColumnSort() {
        SeekQuerySegment segment = KeysetSeekBuilder.builder()
                .sortDesc("created_at")
                .sortAsc("priority")
                .sortDesc("id")
                .predicateStyle(PredicateStyle.EXPANDED_DISJUNCTIVE)
                .navigationDirection(NavigationDirection.NEXT_PAGE)
                .cursorValues("2026-11-19T00:00:00Z", 3, 1050L)
                .pageSize(25)
                .build();

        assertEquals(
                "(created_at < ? OR (created_at = ? AND priority > ?) OR (created_at = ? AND priority = ? AND id < ?))",
                segment.wherePredicate()
        );
        assertEquals("ORDER BY created_at DESC, priority ASC, id DESC", segment.orderByClause());
        assertEquals("LIMIT 25", segment.limitClause());
        assertEquals(
                List.of(
                        "2026-11-19T00:00:00Z",
                        "2026-11-19T00:00:00Z", 3,
                        "2026-11-19T00:00:00Z", 3, 1050L
                ),
                segment.bindParameters()
        );
        assertTrue(segment.hasWherePredicate());
    }

    @Test
    void shouldInvertSortDirectionsForPreviousPageInExpandedDisjunctive() {
        SeekQuerySegment segment = KeysetSeekBuilder.builder()
                .sortDesc("created_at")
                .sortAsc("priority")
                .sortDesc("id")
                .predicateStyle(PredicateStyle.EXPANDED_DISJUNCTIVE)
                .navigationDirection(NavigationDirection.PREVIOUS_PAGE)
                .cursorValues("2026-11-19T00:00:00Z", 3, 1050L)
                .pageSize(10)
                .build();

        assertEquals(
                "(created_at > ? OR (created_at = ? AND priority < ?) OR (created_at = ? AND priority = ? AND id > ?))",
                segment.wherePredicate()
        );
        assertEquals("ORDER BY created_at ASC, priority DESC, id ASC", segment.orderByClause());
        assertEquals("LIMIT 10", segment.limitClause());
        assertEquals(6, segment.bindParameters().size());
    }

    @Test
    void shouldBuildRowValueTupleForUniformDescAndAsc() {
        SeekQuerySegment nextDesc = KeysetSeekBuilder.builder()
                .sortDesc("created_at")
                .sortDesc("id")
                .predicateStyle(PredicateStyle.ROW_VALUE_TUPLE)
                .navigationDirection(NavigationDirection.NEXT_PAGE)
                .cursorValues("2026-11-19T00:00:00Z", 999L)
                .pageSize(50)
                .build();

        assertEquals("(created_at, id) < (?, ?)", nextDesc.wherePredicate());
        assertEquals("ORDER BY created_at DESC, id DESC", nextDesc.orderByClause());
        assertEquals("LIMIT 50", nextDesc.limitClause());
        assertEquals(List.of("2026-11-19T00:00:00Z", 999L), nextDesc.bindParameters());

        SeekQuerySegment prevDesc = KeysetSeekBuilder.builder()
                .sortDesc("created_at")
                .sortDesc("id")
                .predicateStyle(PredicateStyle.ROW_VALUE_TUPLE)
                .navigationDirection(NavigationDirection.PREVIOUS_PAGE)
                .cursorValues("2026-11-19T00:00:00Z", 999L)
                .pageSize(50)
                .build();

        assertEquals("(created_at, id) > (?, ?)", prevDesc.wherePredicate());
        assertEquals("ORDER BY created_at ASC, id ASC", prevDesc.orderByClause());
        assertEquals(List.of("2026-11-19T00:00:00Z", 999L), prevDesc.bindParameters());
    }

    @Test
    void shouldRejectRowValueTupleWhenSortDirectionsAreMixed() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                KeysetSeekBuilder.builder()
                        .sortDesc("created_at")
                        .sortAsc("id")
                        .predicateStyle(PredicateStyle.ROW_VALUE_TUPLE)
                        .cursorValues("2026-11-19T00:00:00Z", 42L)
                        .build()
        );
        assertTrue(ex.getMessage().contains("ROW_VALUE_TUPLE"));
    }

    @Test
    void shouldSupportFirstPageWithoutCursorValues() {
        SeekQuerySegment firstPage = KeysetSeekBuilder.builder()
                .sortDesc("u.created_at")
                .sortAsc("u.id")
                .pageSize(15)
                .build();

        assertFalse(firstPage.hasWherePredicate());
        assertEquals("", firstPage.wherePredicate());
        assertEquals("ORDER BY u.created_at DESC, u.id ASC", firstPage.orderByClause());
        assertEquals("LIMIT 15", firstPage.limitClause());
        assertTrue(firstPage.bindParameters().isEmpty());
        assertEquals("ORDER BY u.created_at DESC, u.id ASC LIMIT 15", firstPage.toSqlSuffix());
    }

    @Test
    void shouldValidateInputsAndBoundaryConditions() {
        // Empty sort specs
        assertThrows(IllegalArgumentException.class, () ->
                KeysetSeekBuilder.builder().build()
        );

        // Non-positive page size
        assertThrows(IllegalArgumentException.class, () ->
                KeysetSeekBuilder.builder()
                        .sortAsc("id")
                        .pageSize(0)
                        .build()
        );

        // Mismatched cursor count
        assertThrows(IllegalArgumentException.class, () ->
                KeysetSeekBuilder.builder()
                        .sortAsc("created_at")
                        .sortAsc("id")
                        .cursorValues(List.of("2026-01-01"))
                        .build()
        );

        // Null cursor element
        assertThrows(IllegalArgumentException.class, () ->
                KeysetSeekBuilder.builder()
                        .sortAsc("created_at")
                        .sortAsc("id")
                        .cursorValues(Arrays.asList("2026-01-01", null))
                        .build()
        );

        // Unsafe SQL injection identifier
        assertThrows(IllegalArgumentException.class, () ->
                SortSpec.asc("id; DROP TABLE users")
        );
    }
}
