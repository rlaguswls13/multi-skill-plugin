package com.msp.inpartition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SqlInClausePartitioner Tests")
class SqlInClausePartitionerTest {

    @Nested
    @DisplayName("Positional parameter (?) partitioning")
    class PositionalTests {

        @Test
        @DisplayName("Returns '1 = 0' when collection is empty or null")
        void emptyOrNullCollection() {
            SqlInClause resNull = SqlInClausePartitioner.partition("id", null);
            assertEquals("1 = 0", resNull.sql());
            assertTrue(resNull.isEmpty());
            assertEquals(0, resNull.chunkCount());

            SqlInClause resEmpty = SqlInClausePartitioner.partition("id", Collections.emptyList());
            assertEquals("1 = 0", resEmpty.sql());
            assertTrue(resEmpty.isEmpty());
            assertEquals(0, resEmpty.chunkCount());
        }

        @Test
        @DisplayName("Generates single chunk IN clause when items fit in chunkSize")
        void singleChunk() {
            List<Integer> ids = List.of(10, 20, 30);
            SqlInClause clause = SqlInClausePartitioner.partition("user_id", ids, 5);

            assertEquals("user_id IN (?, ?, ?)", clause.sql());
            assertEquals(List.of(10, 20, 30), clause.parameters());
            assertEquals(1, clause.chunkCount());
        }

        @Test
        @DisplayName("Generates OR-combined chunks when items exceed chunkSize")
        void multipleChunks() {
            List<Integer> ids = List.of(1, 2, 3, 4, 5);
            SqlInClause clause = SqlInClausePartitioner.partition("user_id", ids, 2);

            assertEquals("(user_id IN (?, ?) OR user_id IN (?, ?) OR user_id IN (?))", clause.sql());
            assertEquals(List.of(1, 2, 3, 4, 5), clause.parameters());
            assertEquals(3, clause.chunkCount());
        }

        @Test
        @DisplayName("Generates exact multiple chunks when item count is divisible by chunkSize")
        void exactMultipleChunks() {
            List<Integer> ids = List.of(1, 2, 3, 4);
            SqlInClause clause = SqlInClausePartitioner.partition("user_id", ids, 2);

            assertEquals("(user_id IN (?, ?) OR user_id IN (?, ?))", clause.sql());
            assertEquals(List.of(1, 2, 3, 4), clause.parameters());
            assertEquals(2, clause.chunkCount());
        }

        @Test
        @DisplayName("Handles large collection with default chunk size of 1000")
        void largeCollectionDefaultChunk() {
            List<Integer> ids = IntStream.rangeClosed(1, 2500).boxed().toList();
            SqlInClause clause = SqlInClausePartitioner.partition("emp_id", ids);

            assertEquals(3, clause.chunkCount());
            assertEquals(2500, clause.parameters().size());
            assertTrue(clause.sql().startsWith("(emp_id IN (?"));
            assertTrue(clause.sql().endsWith("?))"));
        }
    }

    @Nested
    @DisplayName("Named parameter (:param) partitioning")
    class NamedParameterTests {

        @Test
        @DisplayName("Returns empty clause for null/empty collection")
        void emptyNamed() {
            NamedSqlInClause clause = SqlInClausePartitioner.partitionNamed("id", "id", null);
            assertEquals("1 = 0", clause.sql());
            assertTrue(clause.isEmpty());
            assertEquals(0, clause.chunkCount());
        }

        @Test
        @DisplayName("Generates single chunk named parameters")
        void singleChunkNamed() {
            List<String> codes = List.of("A", "B", "C");
            NamedSqlInClause clause = SqlInClausePartitioner.partitionNamed("code", "code", codes, 5);

            assertEquals("code IN (:code_0_0, :code_0_1, :code_0_2)", clause.sql());
            assertEquals(Map.of("code_0_0", "A", "code_0_1", "B", "code_0_2", "C"), clause.parameters());
            assertEquals(1, clause.chunkCount());
        }

        @Test
        @DisplayName("Generates multiple chunks with chunk and item indexes in param names")
        void multipleChunksNamed() {
            List<String> codes = List.of("A", "B", "C");
            NamedSqlInClause clause = SqlInClausePartitioner.partitionNamed("code", ":code", codes, 2);

            assertEquals("(code IN (:code_0_0, :code_0_1) OR code IN (:code_1_0))", clause.sql());
            assertEquals(Map.of("code_0_0", "A", "code_0_1", "B", "code_1_0", "C"), clause.parameters());
            assertEquals(2, clause.chunkCount());
        }
    }

    @Nested
    @DisplayName("Composite key partitioning")
    class CompositeKeyTests {

        @Test
        @DisplayName("Generates tuple IN-clause for single chunk")
        void compositeTupleSingleChunk() {
            List<String> cols = List.of("tenant_id", "user_id");
            List<CompositeKey> keys = List.of(
                    CompositeKey.of("T1", 100),
                    CompositeKey.of("T2", 200)
            );

            SqlInClause clause = SqlInClausePartitioner.partitionComposite(cols, keys, 5);
            assertEquals("(tenant_id, user_id) IN ((?, ?), (?, ?))", clause.sql());
            assertEquals(List.of("T1", 100, "T2", 200), clause.parameters());
            assertEquals(1, clause.chunkCount());
        }

        @Test
        @DisplayName("Generates tuple IN-clause for multiple chunks")
        void compositeTupleMultipleChunks() {
            List<String> cols = List.of("dept", "emp_no");
            List<CompositeKey> keys = List.of(
                    CompositeKey.of("IT", 1),
                    CompositeKey.of("IT", 2),
                    CompositeKey.of("HR", 3)
            );

            SqlInClause clause = SqlInClausePartitioner.partitionComposite(cols, keys, 2);
            assertEquals("((dept, emp_no) IN ((?, ?), (?, ?)) OR (dept, emp_no) IN ((?, ?)))", clause.sql());
            assertEquals(List.of("IT", 1, "IT", 2, "HR", 3), clause.parameters());
            assertEquals(2, clause.chunkCount());
        }

        @Test
        @DisplayName("Generates disjunctive composite key fallback syntax")
        void compositeDisjunctive() {
            List<String> cols = List.of("tenant_id", "role");
            List<CompositeKey> keys = List.of(
                    CompositeKey.of("T1", "ADMIN"),
                    CompositeKey.of("T2", "MEMBER")
            );

            SqlInClause clause = SqlInClausePartitioner.partitionCompositeDisjunctive(cols, keys, 2);
            assertEquals("((tenant_id = ? AND role = ?) OR (tenant_id = ? AND role = ?))", clause.sql());
            assertEquals(List.of("T1", "ADMIN", "T2", "MEMBER"), clause.parameters());
        }

        @Test
        @DisplayName("Throws exception on composite key column size mismatch")
        void compositeKeySizeMismatch() {
            List<String> cols = List.of("tenant_id", "user_id");
            List<CompositeKey> keys = List.of(CompositeKey.of("T1", 100, "EXTRA"));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    SqlInClausePartitioner.partitionComposite(cols, keys, 10));
            assertTrue(ex.getMessage().contains("expected 2 columns"));
        }
    }

    @Nested
    @DisplayName("Validation and error conditions")
    class ValidationTests {

        @Test
        void invalidColumnThrows() {
            assertThrows(NullPointerException.class, () -> SqlInClausePartitioner.partition(null, List.of(1)));
            assertThrows(IllegalArgumentException.class, () -> SqlInClausePartitioner.partition("   ", List.of(1)));
        }

        @Test
        void invalidChunkSizeThrows() {
            assertThrows(IllegalArgumentException.class, () -> SqlInClausePartitioner.partition("id", List.of(1), 0));
            assertThrows(IllegalArgumentException.class, () -> SqlInClausePartitioner.partition("id", List.of(1), -5));
        }

        @Test
        void invalidParamPrefixThrows() {
            assertThrows(IllegalArgumentException.class, () -> SqlInClausePartitioner.partitionNamed("id", "", List.of(1)));
            assertThrows(IllegalArgumentException.class, () -> SqlInClausePartitioner.partitionNamed("id", "::", List.of(1)));
        }
    }
}
