package com.msp.sqlwindow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WindowFunctionsTest {

    @Test
    @DisplayName("Empty OVER clause generates valid SQL")
    void testEmptyOver() {
        WindowExpression expr = WindowFunctions.rowNumber().over().build();
        assertEquals("ROW_NUMBER() OVER ()", expr.toSql());
        assertEquals("ROW_NUMBER() OVER ()", expr.toString());
    }

    @Test
    @DisplayName("ROW_NUMBER with PARTITION BY, ORDER BY and AS alias")
    void testRowNumberWithPartitionAndOrder() {
        WindowExpression expr = WindowFunctions.rowNumber()
                .over()
                .partitionBy("department_id")
                .orderBy(OrderSpec.desc("salary"))
                .as("dept_rank");

        assertEquals("ROW_NUMBER() OVER (PARTITION BY department_id ORDER BY salary DESC) AS dept_rank", expr.toSql());
        assertEquals("dept_rank", expr.getAlias().orElse(null));
    }

    @Test
    @DisplayName("RANK and DENSE_RANK with multi-column partition and order")
    void testRankAndDenseRank() {
        WindowExpression rankExpr = WindowFunctions.rank()
                .over()
                .partitionBy("region", "country")
                .orderBy(OrderSpec.descNullsLast("sales"))
                .as("sales_rank");

        assertEquals("RANK() OVER (PARTITION BY region, country ORDER BY sales DESC NULLS LAST) AS sales_rank", rankExpr.toSql());

        WindowExpression denseRankExpr = WindowFunctions.denseRank()
                .over()
                .partitionBy(List.of("dept"))
                .orderBy("name", SortDirection.ASC, NullsOrder.NULLS_FIRST)
                .build();

        assertEquals("DENSE_RANK() OVER (PARTITION BY dept ORDER BY name ASC NULLS FIRST)", denseRankExpr.toSql());
    }

    @Test
    @DisplayName("NTILE, PERCENT_RANK and CUME_DIST")
    void testRankingVariants() {
        WindowExpression ntileExpr = WindowFunctions.ntile(4)
                .over()
                .orderBy("score")
                .as("quartile");
        assertEquals("NTILE(4) OVER (ORDER BY score ASC) AS quartile", ntileExpr.toSql());

        WindowExpression pctExpr = WindowFunctions.percentRank()
                .over()
                .orderByDesc("score")
                .build();
        assertEquals("PERCENT_RANK() OVER (ORDER BY score DESC)", pctExpr.toSql());

        WindowExpression cumeExpr = WindowFunctions.cumeDist()
                .over()
                .orderBy("score")
                .build();
        assertEquals("CUME_DIST() OVER (ORDER BY score ASC)", cumeExpr.toSql());

        assertThrows(IllegalArgumentException.class, () -> WindowFunctions.ntile(0));
        assertThrows(IllegalArgumentException.class, () -> WindowFunctions.ntile(-1));
    }

    @Test
    @DisplayName("LEAD and LAG with offset and default values")
    void testLeadAndLag() {
        WindowExpression leadDefault = WindowFunctions.lead("salary", 1, 0)
                .over()
                .partitionBy("department_id")
                .orderBy("hire_date")
                .as("next_salary");
        assertEquals("LEAD(salary, 1, 0) OVER (PARTITION BY department_id ORDER BY hire_date ASC) AS next_salary", leadDefault.toSql());

        WindowExpression leadStringDefault = WindowFunctions.lead("status", 2, "UNKNOWN")
                .over()
                .orderBy("event_time")
                .build();
        assertEquals("LEAD(status, 2, 'UNKNOWN') OVER (ORDER BY event_time ASC)", leadStringDefault.toSql());

        WindowExpression lagSimple = WindowFunctions.lag("price")
                .over()
                .partitionBy("item_id")
                .orderBy("ts")
                .build();
        assertEquals("LAG(price) OVER (PARTITION BY item_id ORDER BY ts ASC)", lagSimple.toSql());

        WindowExpression lagOffset = WindowFunctions.lag("price", 3)
                .over()
                .orderBy("ts")
                .build();
        assertEquals("LAG(price, 3) OVER (ORDER BY ts ASC)", lagOffset.toSql());
    }

    @Test
    @DisplayName("FIRST_VALUE, LAST_VALUE, and NTH_VALUE")
    void testValueFunctions() {
        WindowExpression firstVal = WindowFunctions.firstValue("salary")
                .over()
                .partitionBy("dept_id")
                .orderByDesc("salary")
                .build();
        assertEquals("FIRST_VALUE(salary) OVER (PARTITION BY dept_id ORDER BY salary DESC)", firstVal.toSql());

        WindowExpression lastVal = WindowFunctions.lastValue("salary")
                .over()
                .partitionBy("dept_id")
                .orderBy("salary")
                .build();
        assertEquals("LAST_VALUE(salary) OVER (PARTITION BY dept_id ORDER BY salary ASC)", lastVal.toSql());

        WindowExpression nthVal = WindowFunctions.nthValue("salary", 2)
                .over()
                .partitionBy("dept_id")
                .orderByDesc("salary")
                .build();
        assertEquals("NTH_VALUE(salary, 2) OVER (PARTITION BY dept_id ORDER BY salary DESC)", nthVal.toSql());

        assertThrows(IllegalArgumentException.class, () -> WindowFunctions.nthValue("salary", 0));
    }

    @Test
    @DisplayName("Aggregate window functions: SUM, AVG, COUNT, MIN, MAX")
    void testAggregateFunctions() {
        WindowExpression sumExpr = WindowFunctions.sum("amount")
                .over()
                .partitionBy("customer_id")
                .orderBy("order_date")
                .rowsBetween(FrameBound.unboundedPreceding(), FrameBound.currentRow())
                .as("running_total");
        assertEquals("SUM(amount) OVER (PARTITION BY customer_id ORDER BY order_date ASC ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running_total", sumExpr.toSql());

        WindowExpression avgExpr = WindowFunctions.avg("rating")
                .over()
                .partitionBy("category")
                .build();
        assertEquals("AVG(rating) OVER (PARTITION BY category)", avgExpr.toSql());

        WindowExpression countExpr = WindowFunctions.count("order_id")
                .over()
                .partitionBy("customer_id")
                .build();
        assertEquals("COUNT(order_id) OVER (PARTITION BY customer_id)", countExpr.toSql());

        WindowExpression countStarExpr = WindowFunctions.count()
                .over()
                .partitionBy("dept")
                .build();
        assertEquals("COUNT(*) OVER (PARTITION BY dept)", countStarExpr.toSql());

        WindowExpression minExpr = WindowFunctions.min("latency")
                .over()
                .partitionBy("endpoint")
                .build();
        assertEquals("MIN(latency) OVER (PARTITION BY endpoint)", minExpr.toSql());

        WindowExpression maxExpr = WindowFunctions.max("latency")
                .over()
                .partitionBy("endpoint")
                .build();
        assertEquals("MAX(latency) OVER (PARTITION BY endpoint)", maxExpr.toSql());
    }

    @Test
    @DisplayName("Frame clauses: ROWS, RANGE, GROUPS and EXCLUDE")
    void testFrameClauses() {
        WindowExpression rowsExpr = WindowFunctions.avg("sales")
                .over()
                .orderBy("day")
                .rowsBetween(FrameBound.preceding(2), FrameBound.following(2), FrameExclusion.EXCLUDE_CURRENT_ROW)
                .build();
        assertEquals("AVG(sales) OVER (ORDER BY day ASC ROWS BETWEEN 2 PRECEDING AND 2 FOLLOWING EXCLUDE CURRENT ROW)", rowsExpr.toSql());

        WindowExpression rangeExpr = WindowFunctions.sum("amount")
                .over()
                .orderBy("val")
                .rangeBetween(FrameBound.unboundedPreceding(), FrameBound.unboundedFollowing(), FrameExclusion.EXCLUDE_TIES)
                .build();
        assertEquals("SUM(amount) OVER (ORDER BY val ASC RANGE BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING EXCLUDE TIES)", rangeExpr.toSql());

        WindowExpression groupsExpr = WindowFunctions.count()
                .over()
                .orderBy("val")
                .groupsBetween(FrameBound.currentRow(), FrameBound.following(3), FrameExclusion.EXCLUDE_GROUP)
                .build();
        assertEquals("COUNT(*) OVER (ORDER BY val ASC GROUPS BETWEEN CURRENT ROW AND 3 FOLLOWING EXCLUDE GROUP)", groupsExpr.toSql());

        assertThrows(IllegalArgumentException.class, () -> FrameBound.preceding(-1));
        assertThrows(IllegalArgumentException.class, () -> FrameBound.following(-5));
    }

    @Test
    @DisplayName("Named window specifications")
    void testNamedWindow() {
        WindowExpression expr = WindowFunctions.rowNumber().over("w").as("rn");
        assertEquals("ROW_NUMBER() OVER w AS rn", expr.toSql());

        WindowSpec spec = WindowSpec.named("my_window");
        WindowExpression expr2 = WindowFunctions.rank().over(spec);
        assertEquals("RANK() OVER my_window", expr2.toSql());
    }

    @Test
    @DisplayName("Reusable WindowSpec across multiple function calls")
    void testReusableWindowSpec() {
        WindowSpec deptSpec = WindowSpec.builder()
                .partitionBy("department_id")
                .orderBy(OrderSpec.desc("salary"))
                .build();

        WindowExpression rank = WindowFunctions.rank().over(deptSpec).as("dept_rank");
        WindowExpression denseRank = WindowFunctions.denseRank().over(deptSpec).as("dept_dense_rank");

        assertEquals("RANK() OVER (PARTITION BY department_id ORDER BY salary DESC) AS dept_rank", rank.toSql());
        assertEquals("DENSE_RANK() OVER (PARTITION BY department_id ORDER BY salary DESC) AS dept_dense_rank", denseRank.toSql());
    }

    @Test
    @DisplayName("Custom function calls")
    void testCustomFunction() {
        WindowExpression expr = WindowFunctions.custom("MY_ANALYTIC_FUNC", "col1", "col2", "42")
                .over()
                .partitionBy("grp")
                .orderBy("id")
                .build();

        assertEquals("MY_ANALYTIC_FUNC(col1, col2, 42) OVER (PARTITION BY grp ORDER BY id ASC)", expr.toSql());
    }
}
