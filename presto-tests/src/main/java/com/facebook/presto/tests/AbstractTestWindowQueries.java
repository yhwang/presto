/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.tests;

import com.facebook.presto.testing.MaterializedResult;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import static com.facebook.presto.common.type.BigintType.BIGINT;
import static com.facebook.presto.common.type.DoubleType.DOUBLE;
import static com.facebook.presto.common.type.VarcharType.VARCHAR;
import static com.facebook.presto.common.type.VarcharType.createVarcharType;
import static com.facebook.presto.testing.MaterializedResult.resultBuilder;
import static com.facebook.presto.testing.assertions.Assert.assertEquals;
import static com.facebook.presto.tests.QueryAssertions.assertEqualsIgnoreOrder;
import static com.facebook.presto.tests.StructuralTestUtil.mapType;

public abstract class AbstractTestWindowQueries
        extends AbstractTestQueryFramework
{
    @Test
    public void testRowFieldAccessorInWindowFunction()
    {
        assertQuery("SELECT a.col0, " +
                        "SUM(a.col1[1].col1) OVER(PARTITION BY a.col2.col0), " +
                        "SUM(a.col2.col1) OVER(PARTITION BY a.col2.col0) FROM " +
                        "(VALUES " +
                        "ROW(CAST(ROW(1.0, ARRAY[row(31, 14.5E0), row(12, 4.2E0)], row(3, 4.0E0))  AS ROW(col0 double, col1 array(ROW(col0 integer, col1 double)), col2 row(col0 integer, col1 double)))), " +
                        "ROW(CAST(ROW(2.2, ARRAY[row(41, 13.1E0), row(32, 4.2E0)], row(6, 6.0E0))  AS ROW(col0 double, col1 array(ROW(col0 integer, col1 double)), col2 row(col0 integer, col1 double)))), " +
                        "ROW(CAST(ROW(2.2, ARRAY[row(41, 17.1E0), row(45, 4.2E0)], row(7, 16.0E0)) AS ROW(col0 double, col1 array(ROW(col0 integer, col1 double)), col2 row(col0 integer, col1 double)))), " +
                        "ROW(CAST(ROW(2.2, ARRAY[row(41, 13.1E0), row(32, 4.2E0)], row(6, 6.0E0))  AS ROW(col0 double, col1 array(ROW(col0 integer, col1 double)), col2 row(col0 integer, col1 double)))), " +
                        "ROW(CAST(ROW(3.1, ARRAY[row(41, 13.1E0), row(32, 4.2E0)], row(6, 6.0E0))  AS ROW(col0 double, col1 array(ROW(col0 integer, col1 double)), col2 row(col0 integer, col1 double))))) t(a) ",
                "SELECT * FROM VALUES (1.0, 14.5, 4.0), (2.2, 39.3, 18.0), (2.2, 39.3, 18.0), (2.2, 17.1, 16.0), (3.1, 39.3, 18.0)");

        assertQuery("SELECT a.col1[1].col0, " +
                        "SUM(a.col0) OVER(PARTITION BY a.col1[1].col0), " +
                        "SUM(a.col1[1].col1) OVER(PARTITION BY a.col1[1].col0), " +
                        "SUM(a.col2.col1) OVER(PARTITION BY a.col1[1].col0) FROM " +
                        "(VALUES " +
                        "ROW(CAST(ROW(1.0, ARRAY[row(31, 14.5E0), row(12, 4.2E0)], row(3, 4.0E0)) AS ROW(col0 double, col1 array(row(col0 integer, col1 double)), col2 row(col0 integer, col1 double)))), " +
                        "ROW(CAST(ROW(3.1, ARRAY[row(41, 13.1E0), row(32, 4.2E0)], row(6, 6.0E0)) AS ROW(col0 double, col1 array(row(col0 integer, col1 double)), col2 row(col0 integer, col1 double)))), " +
                        "ROW(CAST(ROW(2.2, ARRAY[row(31, 14.2E0), row(22, 5.2E0)], row(5, 4.0E0)) AS ROW(col0 double, col1 array(row(col0 integer, col1 double)), col2 row(col0 integer, col1 double))))) t(a) " +
                        "WHERE a.col1[2].col1 > a.col2.col0",
                "SELECT * FROM VALUES (31, 3.2, 28.7, 8.0), (31, 3.2, 28.7, 8.0)");
    }

    @Test
    public void testDistinctWindow()
    {
        MaterializedResult actual = computeActual(
                "SELECT RANK() OVER (PARTITION BY orderdate ORDER BY COUNT(DISTINCT clerk)) rnk " +
                        "FROM orders " +
                        "GROUP BY orderdate, custkey " +
                        "ORDER BY rnk " +
                        "LIMIT 1");
        MaterializedResult expected = resultBuilder(getSession(), BIGINT).row(1L).build();
        assertEquals(actual, expected);
    }

    @Test
    public void testGroupingInWindowFunction()
    {
        assertQuery(
                "SELECT orderkey, custkey, sum(totalprice), grouping(orderkey)+grouping(custkey) AS g, " +
                        "       rank() OVER (PARTITION BY grouping(orderkey)+grouping(custkey), " +
                        "       CASE WHEN grouping(orderkey) = 0 THEN custkey END ORDER BY orderkey ASC) AS r " +
                        "FROM orders " +
                        "GROUP BY ROLLUP (orderkey, custkey) " +
                        "ORDER BY orderkey, custkey " +
                        "LIMIT 10",
                "VALUES (1, 370, 172799.49, 0, 1), " +
                        "       (1, NULL, 172799.49, 1, 1), " +
                        "       (2, 781, 38426.09, 0, 1), " +
                        "       (2, NULL, 38426.09, 1, 2), " +
                        "       (3, 1234, 205654.30, 0, 1), " +
                        "       (3, NULL, 205654.30, 1, 3), " +
                        "       (4, 1369, 56000.91, 0, 1), " +
                        "       (4, NULL, 56000.91, 1, 4), " +
                        "       (5, 445, 105367.67, 0, 1), " +
                        "       (5, NULL, 105367.67, 1, 5)");
    }

    @Test
    public void testWindowImplicitCoercion()
    {
        assertQueryOrdered(
                "SELECT orderkey, 1e0 / row_number() OVER (ORDER BY orderkey) FROM orders ORDER BY orderkey LIMIT 2",
                "VALUES (1, 1.0), (2, 0.5)");
    }

    @Test
    public void testWindowsSameOrdering()
    {
        MaterializedResult actual = computeActual("SELECT " +
                "sum(quantity) OVER(PARTITION BY suppkey ORDER BY orderkey)," +
                "min(tax) OVER(PARTITION BY suppkey ORDER BY shipdate)" +
                "FROM lineitem " +
                "ORDER BY 1 " +
                "LIMIT 10");

        MaterializedResult expected = resultBuilder(getSession(), DOUBLE, DOUBLE)
                .row(1.0, 0.0)
                .row(2.0, 0.0)
                .row(2.0, 0.0)
                .row(3.0, 0.0)
                .row(3.0, 0.0)
                .row(4.0, 0.0)
                .row(4.0, 0.0)
                .row(5.0, 0.0)
                .row(5.0, 0.0)
                .row(5.0, 0.0)
                .build();

        assertEquals(actual, expected);
    }

    @Test
    public void testWindowsPrefixPartitioning()
    {
        MaterializedResult actual = computeActual("SELECT " +
                "max(tax) OVER(PARTITION BY suppkey, tax ORDER BY receiptdate)," +
                "sum(quantity) OVER(PARTITION BY suppkey ORDER BY orderkey)" +
                "FROM lineitem " +
                "ORDER BY 2, 1 " +
                "LIMIT 10");

        MaterializedResult expected = resultBuilder(getSession(), DOUBLE, DOUBLE)
                .row(0.06, 1.0)
                .row(0.02, 2.0)
                .row(0.06, 2.0)
                .row(0.02, 3.0)
                .row(0.08, 3.0)
                .row(0.03, 4.0)
                .row(0.03, 4.0)
                .row(0.02, 5.0)
                .row(0.03, 5.0)
                .row(0.07, 5.0)
                .build();

        assertEquals(actual, expected);
    }

    @Test
    public void testWindowsDifferentPartitions()
    {
        MaterializedResult actual = computeActual("SELECT " +
                "sum(quantity) OVER(PARTITION BY suppkey ORDER BY orderkey)," +
                "count(discount) OVER(PARTITION BY partkey ORDER BY receiptdate)," +
                "min(tax) OVER(PARTITION BY suppkey, tax ORDER BY receiptdate)" +
                "FROM lineitem " +
                "ORDER BY 1, 2 " +
                "LIMIT 10");

        MaterializedResult expected = resultBuilder(getSession(), DOUBLE, BIGINT, DOUBLE)
                .row(1.0, 10L, 0.06)
                .row(2.0, 4L, 0.06)
                .row(2.0, 16L, 0.02)
                .row(3.0, 3L, 0.08)
                .row(3.0, 38L, 0.02)
                .row(4.0, 10L, 0.03)
                .row(4.0, 10L, 0.03)
                .row(5.0, 9L, 0.03)
                .row(5.0, 13L, 0.07)
                .row(5.0, 15L, 0.02)
                .build();

        assertEquals(actual, expected);
    }

    @Test
    public void testWindowsConstantExpression()
    {
        assertQueryOrdered(
                "SELECT " +
                        "sum(size) OVER(PARTITION BY type ORDER BY brand)," +
                        "lag(partkey, 1) OVER(PARTITION BY type ORDER BY name)" +
                        "FROM part " +
                        "ORDER BY 1, 2 " +
                        "LIMIT 10",
                "VALUES " +
                        "(1, 315), " +
                        "(1, 881), " +
                        "(1, 1009), " +
                        "(3, 1087), " +
                        "(3, 1187), " +
                        "(3, 1529), " +
                        "(4, 969), " +
                        "(5, 151), " +
                        "(5, 505), " +
                        "(5, 872)");
    }

    @Test
    public void testDependentWindows()
    {
        // For such query as below generated plan has two adjacent window nodes where second depends on output of first.

        String sql = "WITH " +
                "t1 AS (" +
                "SELECT extendedprice FROM lineitem ORDER BY orderkey, partkey LIMIT 2)," +
                "t2 AS (" +
                "SELECT extendedprice, sum(extendedprice) OVER() AS x FROM t1)," +
                "t3 AS (" +
                "SELECT max(x) OVER() FROM t2) " +
                "SELECT * FROM t3";

        assertQuery(sql, "VALUES 59645.36, 59645.36");
    }

    @Test
    public void testWindowFunctionWithoutParameters()
    {
        MaterializedResult actual = computeActual("SELECT count() over(partition by custkey) FROM orders WHERE custkey < 3 ORDER BY custkey");

        MaterializedResult expected = resultBuilder(getSession(), BIGINT)
                .row(9L)
                .row(9L)
                .row(9L)
                .row(9L)
                .row(9L)
                .row(9L)
                .row(9L)
                .row(9L)
                .row(9L)
                .row(10L)
                .row(10L)
                .row(10L)
                .row(10L)
                .row(10L)
                .row(10L)
                .row(10L)
                .row(10L)
                .row(10L)
                .row(10L)
                .build();

        assertEquals(actual, expected);
    }

    @Test
    public void testWindowFunctionWithImplicitCoercion()
    {
        assertQuery("SELECT *, 1.0 * sum(x) OVER () FROM (VALUES 1) t(x)", "SELECT 1, 1.0");
    }

    @Test
    public void testWindowFunctionsExpressions()
    {
        assertQueryOrdered(
                "SELECT orderkey, orderstatus " +
                        ", row_number() OVER (ORDER BY orderkey * 2) * " +
                        "  row_number() OVER (ORDER BY orderkey DESC) + 100 " +
                        "FROM (SELECT * FROM orders ORDER BY orderkey LIMIT 10) x " +
                        "ORDER BY orderkey LIMIT 5",
                "VALUES " +
                        "(1, 'O', 110), " +
                        "(2, 'O', 118), " +
                        "(3, 'F', 124), " +
                        "(4, 'O', 128), " +
                        "(5, 'F', 130)");
    }

    @Test
    public void testWindowFunctionsFromAggregate()
    {
        MaterializedResult actual = computeActual("" +
                "SELECT * FROM (\n" +
                "  SELECT orderstatus, clerk, sales\n" +
                "  , rank() OVER (PARTITION BY x.orderstatus ORDER BY sales DESC) rnk\n" +
                "  FROM (\n" +
                "    SELECT orderstatus, clerk, sum(totalprice) sales\n" +
                "    FROM orders\n" +
                "    GROUP BY orderstatus, clerk\n" +
                "   ) x\n" +
                ") x\n" +
                "WHERE rnk <= 2\n" +
                "ORDER BY orderstatus, rnk");

        MaterializedResult expected = resultBuilder(getSession(), VARCHAR, VARCHAR, DOUBLE, BIGINT)
                .row("F", "Clerk#000000090", 2784836.61, 1L)
                .row("F", "Clerk#000000084", 2674447.15, 2L)
                .row("O", "Clerk#000000500", 2569878.29, 1L)
                .row("O", "Clerk#000000050", 2500162.92, 2L)
                .row("P", "Clerk#000000071", 841820.99, 1L)
                .row("P", "Clerk#000001000", 643679.49, 2L)
                .build();

        assertEquals(actual.getMaterializedRows(), expected.getMaterializedRows());
    }

    @Test
    public void testOrderByWindowFunction()
    {
        assertQueryOrdered(
                "SELECT orderkey, row_number() OVER (ORDER BY orderkey) " +
                        "FROM (SELECT * FROM orders ORDER BY orderkey LIMIT 10) " +
                        "ORDER BY 2 DESC " +
                        "LIMIT 5",
                "VALUES (34, 10), " +
                        "(33, 9), " +
                        "(32, 8), " +
                        "(7, 7), " +
                        "(6, 6)");
    }

    @Test
    public void testSameWindowFunctionsTwoCoerces()
    {
        MaterializedResult actual = computeActual("" +
                "SELECT 12.0E0 * row_number() OVER ()/row_number() OVER(),\n" +
                "row_number() OVER()\n" +
                "FROM (SELECT * FROM orders ORDER BY orderkey LIMIT 10)\n" +
                "ORDER BY 2 DESC\n" +
                "LIMIT 5");

        MaterializedResult expected = resultBuilder(getSession(), DOUBLE, BIGINT)
                .row(12.0, 10L)
                .row(12.0, 9L)
                .row(12.0, 8L)
                .row(12.0, 7L)
                .row(12.0, 6L)
                .build();

        assertEquals(actual, expected);

        actual = computeActual("" +
                "SELECT (MAX(x.a) OVER () - x.a) * 100.0E0 / MAX(x.a) OVER ()\n" +
                "FROM (VALUES 1, 2, 3, 4) x(a)");

        expected = resultBuilder(getSession(), DOUBLE)
                .row(75.0)
                .row(50.0)
                .row(25.0)
                .row(0.0)
                .build();

        assertEquals(actual, expected);
    }

    @Test
    public void testWindowMapAgg()
    {
        MaterializedResult actual = computeActual("" +
                "SELECT map_agg(orderkey, orderpriority) OVER(PARTITION BY orderstatus) FROM\n" +
                "(SELECT * FROM orders ORDER BY orderkey LIMIT 5) t");
        MaterializedResult expected = resultBuilder(getSession(), mapType(BIGINT, createVarcharType(1)))
                .row(ImmutableMap.of(1L, "5-LOW", 2L, "1-URGENT", 4L, "5-LOW"))
                .row(ImmutableMap.of(1L, "5-LOW", 2L, "1-URGENT", 4L, "5-LOW"))
                .row(ImmutableMap.of(1L, "5-LOW", 2L, "1-URGENT", 4L, "5-LOW"))
                .row(ImmutableMap.of(3L, "5-LOW", 5L, "5-LOW"))
                .row(ImmutableMap.of(3L, "5-LOW", 5L, "5-LOW"))
                .build();
        assertEqualsIgnoreOrder(actual.getMaterializedRows(), expected.getMaterializedRows());
    }

    @Test
    public void testWindowPropertyDerivation()
    {
        assertQuery(
                "SELECT orderstatus, orderkey, " +
                        "SUM(s) OVER (PARTITION BY orderstatus), " +
                        "SUM(s) OVER (PARTITION BY orderstatus, orderkey), " +
                        "SUM(s) OVER (PARTITION BY orderstatus ORDER BY orderkey), " +
                        "SUM(s) OVER (ORDER BY orderstatus, orderkey) " +
                        "FROM ( " +
                        "   SELECT orderkey, orderstatus, SUM(orderkey) OVER (ORDER BY orderstatus, orderkey) s " +
                        "   FROM ( " +
                        "       SELECT * FROM orders ORDER BY orderkey LIMIT 10 " +
                        "   ) " +
                        ")",
                "VALUES " +
                        "('F', 3, 72, 3, 3, 3), " +
                        "('F', 5, 72, 8, 11, 11), " +
                        "('F', 6, 72, 14, 25, 25), " +
                        "('F', 33, 72, 47, 72, 72), " +
                        "('O', 1, 433, 48, 48, 120), " +
                        "('O', 2, 433, 50, 98, 170), " +
                        "('O', 4, 433, 54, 152, 224), " +
                        "('O', 7, 433, 61, 213, 285), " +
                        "('O', 32, 433, 93, 306, 378), " +
                        "('O', 34, 433, 127, 433, 505)");
    }

    @Test
    public void testWindowFunctionWithGroupBy()
    {
        MaterializedResult actual = computeActual("" +
                "SELECT *, rank() OVER (PARTITION BY x)\n" +
                "FROM (SELECT 'foo' x)\n" +
                "GROUP BY 1");

        MaterializedResult expected = resultBuilder(getSession(), createVarcharType(3), BIGINT)
                .row("foo", 1L)
                .build();

        assertEquals(actual, expected);
    }

    @Test
    public void testPartialPrePartitionedWindowFunction()
    {
        assertQueryOrdered("" +
                        "SELECT orderkey, COUNT(*) OVER (PARTITION BY orderkey, custkey) " +
                        "FROM (SELECT * FROM orders ORDER BY orderkey LIMIT 10) " +
                        "ORDER BY orderkey LIMIT 5",
                "VALUES (1, 1), " +
                        "(2, 1), " +
                        "(3, 1), " +
                        "(4, 1), " +
                        "(5, 1)");
    }

    @Test
    public void testFullPrePartitionedWindowFunction()
    {
        assertQueryOrdered(
                "SELECT orderkey, COUNT(*) OVER (PARTITION BY orderkey) " +
                        "FROM (SELECT * FROM orders ORDER BY orderkey LIMIT 10) " +
                        "ORDER BY orderkey LIMIT 5",
                "VALUES (1, 1), (2, 1), (3, 1), (4, 1), (5, 1)");
    }

    @Test
    public void testPartialPreSortedWindowFunction()
    {
        assertQueryOrdered(
                "SELECT orderkey, COUNT(*) OVER (ORDER BY orderkey, custkey) " +
                        "FROM (SELECT * FROM orders ORDER BY orderkey LIMIT 10) " +
                        "ORDER BY orderkey LIMIT 5",
                "VALUES (1, 1), " +
                        "(2, 2), " +
                        "(3, 3), " +
                        "(4, 4), " +
                        "(5, 5)");
    }

    @Test
    public void testFullPreSortedWindowFunction()
    {
        assertQueryOrdered(
                "SELECT orderkey, COUNT(*) OVER (ORDER BY orderkey) " +
                        "FROM (SELECT * FROM orders ORDER BY orderkey LIMIT 10) " +
                        "ORDER BY orderkey LIMIT 5",
                "VALUES (1, 1), (2, 2), (3, 3), (4, 4), (5, 5)");
    }

    @Test
    public void testFullyPartitionedAndPartiallySortedWindowFunction()
    {
        assertQueryOrdered(
                "SELECT orderkey, custkey, orderPriority, COUNT(*) OVER (PARTITION BY orderkey ORDER BY custkey, orderPriority) " +
                        "FROM (SELECT * FROM orders ORDER BY orderkey, custkey LIMIT 10) " +
                        "ORDER BY orderkey LIMIT 5",
                "VALUES (1, 370, '5-LOW', 1), " +
                        "(2, 781, '1-URGENT', 1), " +
                        "(3, 1234, '5-LOW', 1), " +
                        "(4, 1369, '5-LOW', 1), " +
                        "(5, 445, '5-LOW', 1)");
    }

    @Test
    public void testFullyPartitionedAndFullySortedWindowFunction()
    {
        assertQueryOrdered(
                "SELECT orderkey, custkey, COUNT(*) OVER (PARTITION BY orderkey ORDER BY custkey) " +
                        "FROM (SELECT * FROM orders ORDER BY orderkey, custkey LIMIT 10) " +
                        "ORDER BY orderkey LIMIT 5",
                "VALUES (1, 370, 1), " +
                        "(2, 781, 1), " +
                        "(3, 1234, 1), " +
                        "(4, 1369, 1), " +
                        "(5, 445, 1)");
    }

    @Test
    public void testOrderByWindowFunctionWithNulls()
    {
        // Nulls first
        assertQueryOrdered(
                "SELECT orderkey, row_number() OVER (ORDER BY nullif(orderkey, 3) NULLS FIRST) " +
                        "FROM (SELECT * FROM orders ORDER BY orderkey LIMIT 10) " +
                        "ORDER BY 2 ASC " +
                        "LIMIT 5",
                "VALUES (3, 1), " +
                        "(1, 2), " +
                        "(2, 3), " +
                        "(4, 4)," +
                        "(5, 5)");

        // Nulls last
        String nullsLastExpected = "VALUES (3, 10), " +
                "(34, 9), " +
                "(33, 8), " +
                "(32, 7), " +
                "(7, 6)";
        assertQueryOrdered(
                "SELECT orderkey, row_number() OVER (ORDER BY nullif(orderkey, 3) NULLS LAST) " +
                        "FROM (SELECT * FROM orders ORDER BY orderkey LIMIT 10) " +
                        "ORDER BY 2 DESC " +
                        "LIMIT 5",
                nullsLastExpected);

        // and nulls last should be the default
        assertQueryOrdered(
                "SELECT orderkey, row_number() OVER (ORDER BY nullif(orderkey, 3)) " +
                        "FROM (SELECT * FROM orders ORDER BY orderkey LIMIT 10) " +
                        "ORDER BY 2 DESC " +
                        "LIMIT 5",
                nullsLastExpected);
    }

    @Test
    public void testValueWindowFunctions()
    {
        assertQueryOrdered(
                "SELECT * FROM ( " +
                        "  SELECT orderkey, orderstatus " +
                        "    , first_value(orderkey + 1000) OVER (PARTITION BY orderstatus ORDER BY orderkey) fvalue " +
                        "    , nth_value(orderkey + 1000, 2) OVER (PARTITION BY orderstatus ORDER BY orderkey " +
                        "        ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) nvalue " +
                        "    FROM (SELECT * FROM orders ORDER BY orderkey LIMIT 10) x " +
                        "  ) x " +
                        "ORDER BY orderkey LIMIT 5",
                "VALUES " +
                        "(1, 'O', 1001, 1002), " +
                        "(2, 'O', 1001, 1002), " +
                        "(3, 'F', 1003, 1005), " +
                        "(4, 'O', 1001, 1002), " +
                        "(5, 'F', 1003, 1005)");
    }

    @Test
    public void testWindowFrames()
    {
        MaterializedResult actual = computeActual("SELECT * FROM (\n" +
                "  SELECT orderkey, orderstatus\n" +
                "    , sum(orderkey + 1000) OVER (PARTITION BY orderstatus ORDER BY orderkey\n" +
                "        ROWS BETWEEN mod(custkey, 2) PRECEDING AND custkey / 500 FOLLOWING)\n" +
                "    FROM (SELECT * FROM orders ORDER BY orderkey LIMIT 10) x\n" +
                "  ) x\n" +
                "ORDER BY orderkey LIMIT 5");

        MaterializedResult expected = resultBuilder(getSession(), BIGINT, VARCHAR, BIGINT)
                .row(1L, "O", 1001L)
                .row(2L, "O", 3007L)
                .row(3L, "F", 3014L)
                .row(4L, "O", 4045L)
                .row(5L, "F", 2008L)
                .build();

        assertEquals(actual.getMaterializedRows(), expected.getMaterializedRows());
    }

    @Test
    public void testWindowNoChannels()
    {
        MaterializedResult actual = computeActual("SELECT rank() OVER ()\n" +
                "FROM (SELECT * FROM orders LIMIT 10)\n" +
                "LIMIT 3");

        MaterializedResult expected = resultBuilder(getSession(), BIGINT)
                .row(1L)
                .row(1L)
                .row(1L)
                .build();

        assertEquals(actual, expected);
    }

    @Test
    public void testInvalidWindowFunction()
    {
        assertQueryFails("SELECT abs(x) OVER ()\n" +
                        "FROM (VALUES (1), (2), (3)) t(x)",
                "line 1:1: Not a window function: abs");
    }

    @Test
    public void testDuplicateColumnsInWindowOrderByClause()
    {
        MaterializedResult actual = computeActual("SELECT a, row_number() OVER (ORDER BY a ASC, a DESC) FROM (VALUES 3, 2, 1) t(a)");

        MaterializedResult expected = resultBuilder(getSession(), BIGINT, BIGINT)
                .row(1, 1L)
                .row(2, 2L)
                .row(3, 3L)
                .build();

        assertEqualsIgnoreOrder(actual, expected);
    }

    @Test
    public void testMultipleInstancesOfWindowFunction()
    {
        assertQueryOrdered(
                "SELECT a, b, c, " +
                        "lag(c, 1) RESPECT NULLS OVER (PARTITION BY b ORDER BY a), " +
                        "lag(c, 1) IGNORE NULLS OVER (PARTITION BY b ORDER BY a) " +
                        "FROM ( VALUES " +
                        "(1, 'A', 'a'), " +
                        "(2, 'A', NULL), " +
                        "(3, 'A', 'c'), " +
                        "(4, 'A', NULL), " +
                        "(5, 'A', 'e'), " +
                        "(6, 'A', NULL)" +
                        ") t(a, b, c)",
                "VALUES " +
                        "(1, 'A', 'a', null, null), " +
                        "(2, 'A', null, 'a', 'a'), " +
                        "(3, 'A', 'c', null, 'a'), " +
                        "(4, 'A', null, 'c', 'c'), " +
                        "(5, 'A', 'e', null, 'c'), " +
                        "(6, 'A', null, 'e', 'e')");

        assertQueryOrdered(
                "SELECT a, b, c, " +
                        "lag(c, 1) IGNORE NULLS OVER (PARTITION BY b ORDER BY a), " +
                        "lag(c, 1) RESPECT NULLS OVER (PARTITION BY b ORDER BY a) " +
                        "FROM ( VALUES " +
                        "(1, 'A', 'a'), " +
                        "(2, 'A', NULL), " +
                        "(3, 'A', 'c'), " +
                        "(4, 'A', NULL), " +
                        "(5, 'A', 'e'), " +
                        "(6, 'A', NULL)" +
                        ") t(a, b, c)",
                "VALUES " +
                        "(1, 'A', 'a', null, null), " +
                        "(2, 'A', null, 'a', 'a'), " +
                        "(3, 'A', 'c', 'a', null), " +
                        "(4, 'A', null, 'c', 'c'), " +
                        "(5, 'A', 'e', 'c', null), " +
                        "(6, 'A', null, 'e', 'e')");
    }

    @Test
    public void testNullsSortKey()
    {
        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS FIRST RANGE BETWEEN 1 PRECEDING AND 1 FOLLOWING) " +
                        "FROM (VALUES 1, 2, 3, null, null, 2, 1, null, null) T(a)",
                "VALUES " +
                        "ARRAY[null, null, null, null], " +
                        "ARRAY[null, null, null, null], " +
                        "ARRAY[null, null, null, null], " +
                        "ARRAY[null, null, null, null], " +
                        "ARRAY[1, 1, 2, 2], " +
                        "ARRAY[1, 1, 2, 2], " +
                        "ARRAY[1, 1, 2, 2, 3], " +
                        "ARRAY[1, 1, 2, 2, 3], " +
                        "ARRAY[2, 2, 3]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS LAST RANGE BETWEEN 1 PRECEDING AND 1 FOLLOWING) " +
                        "FROM (VALUES 1, 2, 3, null, null, 2, 1, null, null) T(a)",
                "VALUES " +
                        "ARRAY[1, 1, 2, 2], " +
                        "ARRAY[1, 1, 2, 2], " +
                        "ARRAY[1, 1, 2, 2, 3], " +
                        "ARRAY[1, 1, 2, 2, 3], " +
                        "ARRAY[2, 2, 3], " +
                        "ARRAY[null, null, null, null], " +
                        "ARRAY[null, null, null, null], " +
                        "ARRAY[null, null, null, null], " +
                        "ARRAY[null, null, null, null]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a DESC NULLS FIRST RANGE BETWEEN 1 PRECEDING AND 1 FOLLOWING) " +
                        "FROM (VALUES 1, 2, 3, null, null, 2, 1, null, null) T(a)",
                "VALUES " +
                        "ARRAY[null, null, null, null], " +
                        "ARRAY[null, null, null, null], " +
                        "ARRAY[null, null, null, null], " +
                        "ARRAY[null, null, null, null], " +
                        "ARRAY[3, 2, 2], " +
                        "ARRAY[3, 2, 2, 1, 1], " +
                        "ARRAY[3, 2, 2, 1, 1], " +
                        "ARRAY[2, 2, 1, 1], " +
                        "ARRAY[2, 2, 1, 1]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a DESC NULLS LAST RANGE BETWEEN 1 PRECEDING AND 1 FOLLOWING) " +
                        "FROM (VALUES 1, 2, 3, null, null, 2, 1, null, null) T(a)",
                "VALUES " +
                        "ARRAY[3, 2, 2], " +
                        "ARRAY[3, 2, 2, 1, 1], " +
                        "ARRAY[3, 2, 2, 1, 1], " +
                        "ARRAY[2, 2, 1, 1], " +
                        "ARRAY[2, 2, 1, 1], " +
                        "ARRAY[null, null, null, null], " +
                        "ARRAY[null, null, null, null], " +
                        "ARRAY[null, null, null, null], " +
                        "ARRAY[null, null, null, null]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS LAST RANGE BETWEEN 1 PRECEDING AND UNBOUNDED FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2) T(a)",
                "VALUES " +
                        "ARRAY[1, 2, null, null], " +
                        "ARRAY[1, 2, null, null], " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS LAST RANGE BETWEEN UNBOUNDED PRECEDING AND 1 FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2) T(a)",
                "VALUES " +
                        "ARRAY[1, 2], " +
                        "ARRAY[1, 2], " +
                        "ARRAY[1, 2, null, null], " +
                        "ARRAY[1, 2, null, null]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS FIRST RANGE BETWEEN UNBOUNDED PRECEDING AND 1 FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2) T(a)",
                "VALUES " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null, 1, 2], " +
                        "ARRAY[null, null, 1, 2]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS FIRST RANGE BETWEEN 1 PRECEDING AND UNBOUNDED FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2) T(a)",
                "VALUES " +
                        "ARRAY[null, null, 1, 2], " +
                        "ARRAY[null, null, 1, 2], " +
                        "ARRAY[1, 2], " +
                        "ARRAY[1, 2]");
    }

    @Test
    public void testNoValueFrameBounds()
    {
        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS FIRST RANGE BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[null, null, 1, 1, 2], " +
                        "ARRAY[null, null, 1, 1, 2], " +
                        "ARRAY[null, null, 1, 1, 2], " +
                        "ARRAY[null, null, 1, 1, 2], " +
                        "ARRAY[null, null, 1, 1, 2]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS FIRST RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null, 1, 1], " +
                        "ARRAY[null, null, 1, 1], " +
                        "ARRAY[null, null, 1, 1, 2]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS FIRST RANGE BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[null, null, 1, 1, 2], " +
                        "ARRAY[null, null, 1, 1, 2], " +
                        "ARRAY[1, 1, 2], " +
                        "ARRAY[1, 1, 2], " +
                        "ARRAY[2]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS FIRST RANGE BETWEEN CURRENT ROW AND CURRENT ROW) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null], " +
                        "ARRAY[1, 1], " +
                        "ARRAY[1, 1], " +
                        "ARRAY[2]");
    }

    @Test
    public void testMixedTypeFrameBoundsAscendingNullsFirst()
    {
        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS FIRST RANGE BETWEEN UNBOUNDED PRECEDING AND 0.5 PRECEDING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null, 1, 1]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS FIRST RANGE BETWEEN UNBOUNDED PRECEDING AND 1.5 FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null, 1, 1, 2], " +
                        "ARRAY[null, null, 1, 1, 2], " +
                        "ARRAY[null, null, 1, 1, 2]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS FIRST RANGE BETWEEN CURRENT ROW AND 1.5 FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null], " +
                        "ARRAY[1, 1, 2], " +
                        "ARRAY[1, 1, 2], " +
                        "ARRAY[2]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS FIRST RANGE BETWEEN 1.5 PRECEDING AND CURRENT ROW) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null], " +
                        "ARRAY[1, 1], " +
                        "ARRAY[1, 1], " +
                        "ARRAY[1, 1, 2]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS FIRST RANGE BETWEEN 0.5 PRECEDING AND UNBOUNDED FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[null, null, 1, 1, 2], " +
                        "ARRAY[null, null, 1, 1, 2], " +
                        "ARRAY[1, 1, 2], " +
                        "ARRAY[1, 1, 2], " +
                        "ARRAY[2]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS FIRST RANGE BETWEEN 0.5 FOLLOWING AND UNBOUNDED FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[null, null, 1, 1, 2], " +
                        "ARRAY[null, null, 1, 1, 2], " +
                        "ARRAY[2], " +
                        "ARRAY[2], " +
                        "null");
    }

    @Test
    public void testMixedTypeFrameBoundsAscendingNullsLast()
    {
        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS LAST RANGE BETWEEN UNBOUNDED PRECEDING AND 0.5 PRECEDING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "null, " +
                        "null, " +
                        "ARRAY[1, 1], " +
                        "ARRAY[1, 1, 2, null, null], " +
                        "ARRAY[1, 1, 2, null, null]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS LAST RANGE BETWEEN UNBOUNDED PRECEDING AND 1.5 FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[1, 1, 2], " +
                        "ARRAY[1, 1, 2], " +
                        "ARRAY[1, 1, 2], " +
                        "ARRAY[1, 1, 2, null, null], " +
                        "ARRAY[1, 1, 2, null, null]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS LAST RANGE BETWEEN CURRENT ROW AND 1.5 FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[1, 1, 2], " +
                        "ARRAY[1, 1, 2], " +
                        "ARRAY[2], " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS LAST RANGE BETWEEN 1.5 PRECEDING AND CURRENT ROW) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[1, 1], " +
                        "ARRAY[1, 1], " +
                        "ARRAY[1, 1, 2], " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS LAST RANGE BETWEEN 0.5 PRECEDING AND UNBOUNDED FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[1, 1, 2, null, null], " +
                        "ARRAY[1, 1, 2, null, null], " +
                        "ARRAY[2, null, null], " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS LAST RANGE BETWEEN 0.5 FOLLOWING AND UNBOUNDED FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[2, null, null], " +
                        "ARRAY[2, null, null], " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null]");
    }

    @Test
    public void testMixedTypeFrameBoundsDescendingNullsFirst()
    {
        assertQuery("SELECT array_agg(a) OVER(ORDER BY a DESC NULLS FIRST RANGE BETWEEN UNBOUNDED PRECEDING AND 0.5 PRECEDING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null, 2], " +
                        "ARRAY[null, null, 2]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a DESC NULLS FIRST RANGE BETWEEN UNBOUNDED PRECEDING AND 0.5 FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null, 2], " +
                        "ARRAY[null, null, 2, 1, 1], " +
                        "ARRAY[null, null, 2, 1, 1]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a DESC NULLS FIRST RANGE BETWEEN CURRENT ROW AND 1.5 FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null], " +
                        "ARRAY[2, 1, 1], " +
                        "ARRAY[1, 1], " +
                        "ARRAY[1, 1]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a DESC NULLS FIRST RANGE BETWEEN 1.5 PRECEDING AND CURRENT ROW) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null], " +
                        "ARRAY[2], " +
                        "ARRAY[2, 1, 1], " +
                        "ARRAY[2, 1, 1]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a DESC NULLS FIRST RANGE BETWEEN 1.5 PRECEDING AND UNBOUNDED FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[null, null, 2, 1, 1], " +
                        "ARRAY[null, null, 2, 1, 1], " +
                        "ARRAY[2, 1, 1], " +
                        "ARRAY[2, 1, 1], " +
                        "ARRAY[2, 1, 1]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a DESC NULLS FIRST RANGE BETWEEN 1.5 FOLLOWING AND UNBOUNDED FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[null, null, 2, 1, 1], " +
                        "ARRAY[null, null, 2, 1, 1], " +
                        "null, " +
                        "null, " +
                        "null");
    }

    @Test
    public void testMixedTypeFrameBoundsDescendingNullsLast()
    {
        assertQuery("SELECT array_agg(a) OVER(ORDER BY a DESC NULLS LAST RANGE BETWEEN UNBOUNDED PRECEDING AND 0.5 PRECEDING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "null, " +
                        "ARRAY[2], " +
                        "ARRAY[2], " +
                        "ARRAY[2, 1, 1, null, null], " +
                        "ARRAY[2, 1, 1, null, null]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a DESC NULLS LAST RANGE BETWEEN UNBOUNDED PRECEDING AND 1.5 FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[2, 1, 1], " +
                        "ARRAY[2, 1, 1], " +
                        "ARRAY[2, 1, 1], " +
                        "ARRAY[2, 1, 1, null, null], " +
                        "ARRAY[2, 1, 1, null, null]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a DESC NULLS LAST RANGE BETWEEN CURRENT ROW AND 1.5 FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[2, 1, 1], " +
                        "ARRAY[1, 1], " +
                        "ARRAY[1, 1], " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a DESC NULLS LAST RANGE BETWEEN 0.5 PRECEDING AND CURRENT ROW) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[2], " +
                        "ARRAY[1, 1], " +
                        "ARRAY[1, 1], " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a DESC NULLS LAST RANGE BETWEEN 0.5 PRECEDING AND UNBOUNDED FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[2, 1, 1, null, null], " +
                        "ARRAY[1, 1, null, null], " +
                        "ARRAY[1, 1, null, null], " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a DESC NULLS LAST RANGE BETWEEN 1.5 FOLLOWING AND UNBOUNDED FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[cast(null as integer), cast(null as integer)], " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null]");
    }

    @Test
    public void testEmptyInput()
    {
        assertQuery("SELECT array_agg(a) OVER(ORDER BY a DESC NULLS LAST RANGE BETWEEN 1 PRECEDING AND 1 FOLLOWING) " +
                        "FROM (SELECT 1 WHERE false) T(a)",
                "SELECT ARRAY[1] WHERE false");
        assertQuery("SELECT array_agg(a) OVER(ORDER BY a DESC NULLS LAST RANGE UNBOUNDED PRECEDING) " +
                        "FROM (SELECT 1 WHERE false) T(a)",
                "SELECT ARRAY[1] WHERE false");
    }

    @Test
    public void testEmptyFrameIntegralBounds()
    {
        assertQuery("SELECT array_agg(a) OVER(ORDER BY a DESC NULLS LAST RANGE BETWEEN 1 PRECEDING AND 10 PRECEDING) " +
                        "FROM (VALUES 1, 2, 3, null, null, 2, 1, null, null) T(a)",
                "VALUES " +
                        "CAST(null AS array), " +
                        "null, " +
                        "null, " +
                        "null, " +
                        "null, " +
                        "ARRAY[null, null, null, null], " +
                        "ARRAY[null, null, null, null], " +
                        "ARRAY[null, null, null, null], " +
                        "ARRAY[null, null, null, null]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a DESC NULLS LAST RANGE BETWEEN 10 FOLLOWING AND 1 FOLLOWING) " +
                        "FROM (VALUES 1, 2, 3, null, null, 2, 1, null, null) T(a)",
                "VALUES " +
                        "CAST(null AS array), " +
                        "null, " +
                        "null, " +
                        "null, " +
                        "null, " +
                        "ARRAY[null, null, null, null], " +
                        "ARRAY[null, null, null, null], " +
                        "ARRAY[null, null, null, null], " +
                        "ARRAY[null, null, null, null]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a RANGE BETWEEN 2 PRECEDING AND 1 PRECEDING) " +
                        "FROM (VALUES 1, 2) T(a)",
                "VALUES " +
                        "null, " +
                        "ARRAY[1]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a NULLS FIRST RANGE BETWEEN 2 PRECEDING AND 1 PRECEDING) " +
                        "FROM (VALUES null, 1, 2) T(a)",
                "VALUES " +
                        "ARRAY[null], " +
                        "null, " +
                        "ARRAY[1]");
    }

    @Test
    public void testEmptyFrameMixedBounds()
    {
        assertQuery("SELECT array_agg(a) OVER(ORDER BY a RANGE BETWEEN 0.5 FOLLOWING AND 1.5 FOLLOWING) " +
                        "FROM (VALUES 1, 2, 4) T(a)",
                "VALUES " +
                        "ARRAY[2], " +
                        "null, " +
                        "null");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a RANGE BETWEEN 1 FOLLOWING AND 2 FOLLOWING) " +
                        "FROM (VALUES 1.0, 1.1) T(a)",
                "VALUES " +
                        "CAST(null AS array), " +
                        "null");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a NULLS LAST RANGE BETWEEN 1 FOLLOWING AND 2 FOLLOWING) " +
                        "FROM (VALUES 1.0, 1.1, null) T(a)",
                "VALUES " +
                        "CAST(null AS array), " +
                        "null, " +
                        "ARRAY[null]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a RANGE BETWEEN 2 PRECEDING AND 1 PRECEDING) " +
                        "FROM (VALUES 1.0, 1.1) T(a)",
                "VALUES " +
                        "CAST(null AS array), " +
                        "null");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a NULLS FIRST RANGE BETWEEN 2 PRECEDING AND 1 PRECEDING) " +
                        "FROM (VALUES null, 1.0, 1.1) T(a)",
                "VALUES " +
                        "ARRAY[cast(null as decimal(2,1))], " +
                        "null, " +
                        "null");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a NULLS FIRST RANGE BETWEEN 2 PRECEDING AND 1.5 PRECEDING) " +
                        "FROM (VALUES null, 1, 2) T(a)",
                "VALUES " +
                        "ARRAY[cast(null as integer)], " +
                        "null, " +
                        "null");
    }

    @Test
    public void testOnlyNulls()
    {
        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS LAST RANGE BETWEEN 1 FOLLOWING AND 2 FOLLOWING) " +
                        "FROM (VALUES CAST(null AS integer), null, null) T(a)",
                "VALUES " +
                        "ARRAY[cast(null as integer), cast(null as integer), cast(null as integer)], " +
                        "ARRAY[null, null, null], " +
                        "ARRAY[null, null, null]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS LAST RANGE BETWEEN 2 PRECEDING AND 1 PRECEDING) " +
                        "FROM (VALUES CAST(null AS integer), null, null) T(a)",
                "VALUES " +
                        "ARRAY[cast(null as integer), cast(null as integer), cast(null as integer)], " +
                        "ARRAY[null, null, null], " +
                        "ARRAY[null, null, null]");
    }

    @Test
    public void testAllPartitionSameValues()
    {
        assertQuery("SELECT array_agg(a) OVER(ORDER BY a RANGE BETWEEN 1 FOLLOWING AND 2 FOLLOWING) " +
                        "FROM (VALUES 1, 1, 1) T(a)",
                "VALUES " +
                        "CAST(null AS array), " +
                        "null, " +
                        "null");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a RANGE BETWEEN 2 PRECEDING AND 1 PRECEDING) " +
                        "FROM (VALUES 1, 1, 1) T(a)",
                "VALUES " +
                        "CAST(null AS array), " +
                        "null, " +
                        "null");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a RANGE BETWEEN 1 PRECEDING AND 1 FOLLOWING) " +
                        "FROM (VALUES 1, 1, 1) T(a)",
                "VALUES " +
                        "ARRAY[1, 1, 1], " +
                        "ARRAY[1, 1, 1], " +
                        "ARRAY[1, 1, 1]");
    }

    @Test
    public void testZeroOffset()
    {
        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS LAST RANGE BETWEEN 0 PRECEDING AND 0 FOLLOWING) " +
                        "FROM (VALUES 1, 2, 1, null) T(a)",
                "VALUES " +
                        "ARRAY[1, 1], " +
                        "ARRAY[1, 1], " +
                        "ARRAY[2], " +
                        "ARRAY[null]");
    }

    @Test
    public void testNonConstantOffset()
    {
        assertQuery("SELECT array_agg(a) OVER(ORDER BY a RANGE BETWEEN x * 10 PRECEDING AND y / 10.0 FOLLOWING) " +
                        "FROM (VALUES (1, 0.1, 10), (2, 0.2, 20), (4, 0.4, 40)) T(a, x, y)",
                "VALUES " +
                        "ARRAY[1, 2], " +
                        "ARRAY[1, 2, 4], " +
                        "ARRAY[1, 2, 4]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a RANGE BETWEEN x * 10 PRECEDING AND y / 10.0 FOLLOWING) " +
                        "FROM (VALUES (1, 0.1, 10), (2, 0.2, 20), (4, 0.4, 40), (null, 0.5, 50)) T(a, x, y)",
                "VALUES " +
                        "ARRAY[1, 2], " +
                        "ARRAY[1, 2, 4], " +
                        "ARRAY[1, 2, 4], " +
                        "ARRAY[null]");
    }

    @Test
    public void testInvalidOffset()
    {
        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a ASC RANGE x PRECEDING) " +
                        "FROM (VALUES (1, 0.1), (2, -0.2)) T(a, x)",
                "Window frame offset value must not be negative or null");

        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a ASC RANGE BETWEEN 1 PRECEDING AND x FOLLOWING) " +
                        "FROM (VALUES (1, 0.1), (2, -0.2)) T(a, x)",
                "Window frame offset value must not be negative or null");

        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a DESC RANGE x PRECEDING) " +
                        "FROM (VALUES (1, 0.1), (2, -0.2)) T(a, x)",
                "Window frame offset value must not be negative or null");

        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a DESC RANGE BETWEEN 1 PRECEDING AND x FOLLOWING) " +
                        "FROM (VALUES (1, 0.1), (2, -0.2)) T(a, x)",
                "Window frame offset value must not be negative or null");

        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a DESC RANGE x PRECEDING) " +
                        "FROM (VALUES (1, 0.1), (2, null)) T(a, x)",
                "Window frame offset value must not be negative or null");

        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a DESC RANGE BETWEEN 1 PRECEDING AND x FOLLOWING) " +
                        "FROM (VALUES (1, 0.1), (2, null)) T(a, x)",
                "Window frame offset value must not be negative or null");

        // fail if offset is invalid for null sort key
        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a DESC RANGE BETWEEN 1 PRECEDING AND x FOLLOWING) " +
                        "FROM (VALUES (1, 0.1), (null, null)) T(a, x)",
                "Window frame offset value must not be negative or null");

        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a DESC RANGE BETWEEN 1 PRECEDING AND x FOLLOWING) " +
                        "FROM (VALUES (1, 0.1), (null, -0.1)) T(a, x)",
                "Window frame offset value must not be negative or null");

        // test invalid offset of different types
        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a RANGE x PRECEDING) " +
                        "FROM (VALUES (1, BIGINT '-1')) T(a, x)",
                "Window frame offset value must not be negative or null");

        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a RANGE x PRECEDING) " +
                        "FROM (VALUES (1, INTEGER '-1')) T(a, x)",
                "Window frame offset value must not be negative or null");

        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a RANGE x PRECEDING) " +
                        "FROM (VALUES (SMALLINT '1', SMALLINT '-1')) T(a, x)",
                "Window frame offset value must not be negative or null");

        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a RANGE x PRECEDING) " +
                        "FROM (VALUES (TINYINT '1', TINYINT '-1')) T(a, x)",
                "Window frame offset value must not be negative or null");

        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a RANGE x PRECEDING) " +
                        "FROM (VALUES (1, -1.1e0)) T(a, x)",
                "Window frame offset value must not be negative or null");

        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a RANGE x PRECEDING) " +
                        "FROM (VALUES (1, REAL '-1.1')) T(a, x)",
                "Window frame offset value must not be negative or null");

        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a RANGE x PRECEDING) " +
                        "FROM (VALUES (1, -1.0001)) T(a, x)",
                "Window frame offset value must not be negative or null");

        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a RANGE x PRECEDING) " +
                        "FROM (VALUES (DATE '2001-01-31', INTERVAL '-1' YEAR)) T(a, x)",
                "Window frame offset value must not be negative or null");

        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a RANGE x PRECEDING) " +
                        "FROM (VALUES (DATE '2001-01-31', INTERVAL '-1' MONTH)) T(a, x)",
                "Window frame offset value must not be negative or null");

        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a RANGE x PRECEDING) " +
                        "FROM (VALUES (DATE '2001-01-31', INTERVAL '-1' DAY)) T(a, x)",
                "Window frame offset value must not be negative or null");

        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a RANGE x PRECEDING) " +
                        "FROM (VALUES (DATE '2001-01-31', INTERVAL '-1' HOUR)) T(a, x)",
                "Window frame offset value must not be negative or null");

        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a RANGE x PRECEDING) " +
                        "FROM (VALUES (DATE '2001-01-31', INTERVAL '-1' MINUTE)) T(a, x)",
                "Window frame offset value must not be negative or null");

        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a RANGE x PRECEDING) " +
                        "FROM (VALUES (DATE '2001-01-31', INTERVAL '-1' SECOND)) T(a, x)",
                "Window frame offset value must not be negative or null");
    }

    @Test
    public void testWindowPartitioning()
    {
        assertQuery("SELECT a, p, array_agg(a) OVER(PARTITION BY p ORDER BY a ASC NULLS FIRST RANGE BETWEEN 0.5 PRECEDING AND 1 FOLLOWING) " +
                        "FROM (VALUES (1, 'x'), (2, 'x'), (null, 'x'), (null, 'y'), (2, 'y')) T(a, p)",
                "VALUES " +
                        "(null, 'x', ARRAY[null]), " +
                        "(1,    'x', ARRAY[1, 2]), " +
                        "(2,    'x', ARRAY[2]), " +
                        "(null, 'y', ARRAY[null]), " +
                        "(2,    'y', ARRAY[2])");

        assertQuery("SELECT a, p, array_agg(a) OVER(PARTITION BY p ORDER BY a ASC NULLS FIRST RANGE BETWEEN 0.5 PRECEDING AND 1 FOLLOWING) " +
                        "FROM (VALUES (1, 'x'), (2, 'x'), (null, 'x'), (null, 'y'), (2, 'y'), (null, null), (null, null), (1, null)) T(a, p)",
                "VALUES " +
                        "(null, null, ARRAY[null, null]), " +
                        "(null, null, ARRAY[null, null]), " +
                        "(1,    null, ARRAY[1]), " +
                        "(null, 'x', ARRAY[null]), " +
                        "(1,    'x', ARRAY[1, 2]), " +
                        "(2,    'x', ARRAY[2]), " +
                        "(null, 'y', ARRAY[null]), " +
                        "(2,    'y', ARRAY[2])");
    }

    @Test
    public void testTypes()
    {
        assertQuery("SELECT array_agg(a) OVER(ORDER BY a RANGE BETWEEN DOUBLE '0.5' PRECEDING AND TINYINT '1' FOLLOWING) " +
                        "FROM (VALUES 1, null, 2) T(a)",
                "VALUES " +
                        "ARRAY[1, 2], " +
                        "ARRAY[2], " +
                        "ARRAY[null]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a RANGE BETWEEN 0.5 PRECEDING AND 1.000 FOLLOWING) " +
                        "FROM (VALUES REAL '1', null, 2) T(a)",
                "VALUES " +
                        "ARRAY[CAST('1' AS REAL), CAST('2' AS REAL)], " +
                        "ARRAY[CAST('2' AS REAL)], " +
                        "ARRAY[null]");

        assertQuery("SELECT x, array_agg(x) OVER(ORDER BY x DESC RANGE BETWEEN interval '1' month PRECEDING AND interval '1' month FOLLOWING) " +
                        "FROM (VALUES DATE '2001-01-31', DATE '2001-08-25', DATE '2001-09-25', DATE '2001-09-26') T(x)",
                "VALUES " +
                        "(DATE '2001-09-26', ARRAY[DATE '2001-09-26', DATE '2001-09-25']), " +
                        "(DATE '2001-09-25', ARRAY[DATE '2001-09-26', DATE '2001-09-25', DATE '2001-08-25']), " +
                        "(DATE '2001-08-25', ARRAY[DATE '2001-09-25', DATE '2001-08-25']), " +
                        "(DATE '2001-01-31', ARRAY[DATE '2001-01-31'])");

        // January 31 + 1 month sets the frame bound to the last day of February. March 1 is out of range.
        assertQuery("SELECT x, array_agg(x) OVER(ORDER BY x RANGE BETWEEN CURRENT ROW AND interval '1' month FOLLOWING) " +
                        "FROM (VALUES DATE '2001-01-31', DATE '2001-02-28', DATE '2001-03-01') T(x)",
                "VALUES " +
                        "(DATE '2001-01-31', ARRAY[DATE '2001-01-31', DATE '2001-02-28']), " +
                        "(DATE '2001-02-28', ARRAY[DATE '2001-02-28', DATE '2001-03-01']), " +
                        "(DATE '2001-03-01', ARRAY[DATE '2001-03-01'])");

        // H2 and Presto has some type conversion problem for Interval type, hence use the same query runner for this query
        assertQueryWithSameQueryRunner("SELECT x, array_agg(x) OVER(ORDER BY x RANGE BETWEEN interval '1' year PRECEDING AND interval '1' month FOLLOWING) " +
                        "FROM (VALUES " +
                        "INTERVAL '1' month, " +
                        "INTERVAL '2' month, " +
                        "INTERVAL '5' year) T(x)",
                "VALUES " +
                        "(INTERVAL '1' month, ARRAY[INTERVAL '1' month, INTERVAL '2' month]), " +
                        "(INTERVAL '2' month, ARRAY[INTERVAL '1' month, INTERVAL '2' month]), " +
                        "(INTERVAL '5' year, ARRAY[INTERVAL '5' year])");
    }

    @Test
    public void testMultipleWindowFunctions()
    {
        assertQuery("SELECT x, array_agg(date) OVER(ORDER BY x RANGE BETWEEN 1 PRECEDING AND 1 FOLLOWING), avg(number) OVER(ORDER BY x RANGE BETWEEN 1 PRECEDING AND 1 FOLLOWING) " +
                        "FROM (VALUES " +
                        "(2, DATE '2222-01-01', 4.4), " +
                        "(1, DATE '1111-01-01', 2.2), " +
                        "(3, DATE '3333-01-01', 6.6)) T(x, date, number)",
                "VALUES " +
                        "(1, ARRAY[DATE '1111-01-01', DATE '2222-01-01'], 3.3), " +
                        "(2, ARRAY[DATE '1111-01-01', DATE '2222-01-01', DATE '3333-01-01'], 4.4), " +
                        "(3, ARRAY[DATE '2222-01-01', DATE '3333-01-01'], 5.5)");

        assertQuery("SELECT x, array_agg(a) OVER(ORDER BY x RANGE BETWEEN 2 PRECEDING AND CURRENT ROW), array_agg(a) OVER(ORDER BY x RANGE BETWEEN CURRENT ROW AND 2 FOLLOWING) " +
                        "FROM (VALUES " +
                        "(1.0, 1), " +
                        "(2.0, 2), " +
                        "(3.0, 3), " +
                        "(4.0, 4), " +
                        "(5.0, 5), " +
                        "(6.0, 6)) T(x, a)",
                "VALUES " +
                        "(1.0, ARRAY[1], ARRAY[1, 2, 3]), " +
                        "(2.0, ARRAY[1, 2], ARRAY[2, 3, 4]), " +
                        "(3.0, ARRAY[1, 2, 3], ARRAY[3, 4, 5]), " +
                        "(4.0, ARRAY[2, 3, 4], ARRAY[4, 5, 6]), " +
                        "(5.0, ARRAY[3, 4, 5], ARRAY[5, 6]), " +
                        "(6.0, ARRAY[4, 5, 6], ARRAY[6])");
    }

    @Test
    public void testConstantOffset()
    {
        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS FIRST GROUPS BETWEEN 1 PRECEDING AND 2 FOLLOWING) " +
                        "FROM (VALUES 3, 3, 3, 2, 2, 1, null, null) T(a)",
                "VALUES " +
                        "ARRAY[null, null, 1, 2, 2], " +
                        "ARRAY[null, null, 1, 2, 2], " +
                        "ARRAY[null, null, 1, 2, 2, 3, 3, 3], " +
                        "ARRAY[1, 2, 2, 3, 3, 3], " +
                        "ARRAY[1, 2, 2, 3, 3, 3], " +
                        "ARRAY[2, 2, 3, 3, 3], " +
                        "ARRAY[2, 2, 3, 3, 3], " +
                        "ARRAY[2, 2, 3, 3, 3]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS FIRST GROUPS CURRENT ROW) " +
                        "FROM (VALUES 3, 3, 3, 2, 2, 1, null, null) T(a)",
                "VALUES " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null], " +
                        "ARRAY[1], " +
                        "ARRAY[2, 2], " +
                        "ARRAY[2, 2], " +
                        "ARRAY[3, 3, 3], " +
                        "ARRAY[3, 3, 3], " +
                        "ARRAY[3, 3, 3]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS FIRST GROUPS BETWEEN 0 PRECEDING AND 0 FOLLOWING) " +
                        "FROM (VALUES 3, 3, 3, 2, 2, 1, null, null) T(a)",
                "VALUES " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null], " +
                        "ARRAY[1], " +
                        "ARRAY[2, 2], " +
                        "ARRAY[2, 2], " +
                        "ARRAY[3, 3, 3], " +
                        "ARRAY[3, 3, 3], " +
                        "ARRAY[3, 3, 3]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS FIRST GROUPS BETWEEN 1 FOLLOWING AND 2 FOLLOWING) " +
                        "FROM (VALUES 3, 3, 3, 2, 2, 1, null, null) T(a)",
                "VALUES " +
                        "ARRAY[1, 2, 2], " +
                        "ARRAY[1, 2, 2], " +
                        "ARRAY[2, 2, 3, 3, 3], " +
                        "ARRAY[3, 3, 3], " +
                        "ARRAY[3, 3, 3], " +
                        "null, " +
                        "null, " +
                        "null");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS FIRST GROUPS BETWEEN 2 PRECEDING AND 1 PRECEDING) " +
                        "FROM (VALUES 3, 3, 3, 2, 2, 1, null, null) T(a)",
                "VALUES " +
                        "null, " +
                        "null, " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null, 1], " +
                        "ARRAY[null, null, 1], " +
                        "ARRAY[1, 2, 2], " +
                        "ARRAY[1, 2, 2], " +
                        "ARRAY[1, 2, 2]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS FIRST GROUPS BETWEEN 2 FOLLOWING AND 1 FOLLOWING) " +
                        "FROM (VALUES 3, 3, 3, 2, 2, 1, null, null) T(a)",
                "VALUES " +
                        "CAST(null AS array), " +
                        "null, " +
                        "null, " +
                        "null, " +
                        "null, " +
                        "null, " +
                        "null, " +
                        "null");
    }

    @Test
    public void testNoValueFrameBoundsGroup()
    {
        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS FIRST GROUPS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[null, null, 1, 1, 2], " +
                        "ARRAY[null, null, 1, 1, 2], " +
                        "ARRAY[null, null, 1, 1, 2], " +
                        "ARRAY[null, null, 1, 1, 2], " +
                        "ARRAY[null, null, 1, 1, 2]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS FIRST GROUPS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null, 1, 1], " +
                        "ARRAY[null, null, 1, 1], " +
                        "ARRAY[null, null, 1, 1, 2]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS FIRST GROUPS BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[null, null, 1, 1, 2], " +
                        "ARRAY[null, null, 1, 1, 2], " +
                        "ARRAY[1, 1, 2], " +
                        "ARRAY[1, 1, 2], " +
                        "ARRAY[2]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS FIRST GROUPS BETWEEN CURRENT ROW AND CURRENT ROW) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null], " +
                        "ARRAY[1, 1], " +
                        "ARRAY[1, 1], " +
                        "ARRAY[2]");
    }

    @Test
    public void testMixedTypeFrameBounds()
    {
        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS LAST GROUPS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "null, " +
                        "null, " +
                        "ARRAY[1, 1], " +
                        "ARRAY[1, 1, 2], " +
                        "ARRAY[1, 1, 2]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS LAST GROUPS BETWEEN UNBOUNDED PRECEDING AND 1 FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[1, 1, 2], " +
                        "ARRAY[1, 1, 2], " +
                        "ARRAY[1, 1, 2, null, null], " +
                        "ARRAY[1, 1, 2, null, null], " +
                        "ARRAY[1, 1, 2, null, null]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS LAST GROUPS BETWEEN CURRENT ROW AND 1 FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[1, 1, 2], " +
                        "ARRAY[1, 1, 2], " +
                        "ARRAY[2, null, null], " +
                        "ARRAY[null, null], " +
                        "ARRAY[null, null]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS LAST GROUPS BETWEEN 1 PRECEDING AND CURRENT ROW) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[1, 1], " +
                        "ARRAY[1, 1], " +
                        "ARRAY[1, 1, 2], " +
                        "ARRAY[2, null, null], " +
                        "ARRAY[2, null, null]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS LAST GROUPS BETWEEN 1 PRECEDING AND UNBOUNDED FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[1, 1, 2, null, null], " +
                        "ARRAY[1, 1, 2, null, null], " +
                        "ARRAY[1, 1, 2, null, null], " +
                        "ARRAY[2, null, null], " +
                        "ARRAY[2, null, null]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS LAST GROUPS BETWEEN 1 FOLLOWING AND UNBOUNDED FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "ARRAY[2, null, null], " +
                        "ARRAY[2, null, null], " +
                        "ARRAY[null, null], " +
                        "null, " +
                        "null");
    }

    @Test
    public void testEmptyFrameGroup()
    {
        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS LAST GROUPS BETWEEN 90 PRECEDING AND 100 PRECEDING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "CAST(null AS array), " +
                        "null, " +
                        "null, " +
                        "null, " +
                        "null");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a ASC NULLS LAST GROUPS BETWEEN 100 FOLLOWING AND 90 FOLLOWING) " +
                        "FROM (VALUES 1, null, null, 2, 1) T(a)",
                "VALUES " +
                        "CAST(null AS array), " +
                        "null, " +
                        "null, " +
                        "null, " +
                        "null");
    }

    @Test
    public void testNonConstantOffsetGroup()
    {
        assertQuery("SELECT array_agg(a) OVER(ORDER BY a GROUPS BETWEEN x PRECEDING AND y FOLLOWING) " +
                        "FROM (VALUES ('a', 1, 1), ('b', 2, 0), ('c', 0, 3)) T(a, x, y)",
                "VALUES " +
                        "ARRAY['a', 'b'], " +
                        "ARRAY['a', 'b'], " +
                        "ARRAY['c']");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a GROUPS BETWEEN x FOLLOWING AND y FOLLOWING) " +
                        "FROM (VALUES ('a', 1, 1), ('b', 2, 0), ('c', 3, 3), ('d', 0, 0)) T(a, x, y)",
                "VALUES " +
                        "ARRAY['b'], " +
                        "null, " +
                        "null, " +
                        "ARRAY['d']");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a GROUPS BETWEEN x PRECEDING AND y PRECEDING) " +
                        "FROM (VALUES ('a', 1, 1), ('b', 0, 2), ('c', 2, 1), ('d', 0, 2)) T(a, x, y)",
                "VALUES " +
                        "null, " +
                        "null, " +
                        "ARRAY['a', 'b'], " +
                        "null");
    }

    @Test
    public void testEmptyInputGroup()
    {
        assertQueryReturnsEmptyResult("SELECT array_agg(a) OVER(ORDER BY a GROUPS BETWEEN 1 PRECEDING AND 1 FOLLOWING) " +
                "FROM (SELECT 1 WHERE false) T(a)");
        assertQueryReturnsEmptyResult("SELECT array_agg(a) OVER(ORDER BY a GROUPS UNBOUNDED PRECEDING)" +
                "FROM (SELECT 1 WHERE false) T(a)");
    }

    @Test
    public void testOnlyNullsGroup()
    {
        assertQuery("SELECT array_agg(a) OVER(ORDER BY a GROUPS BETWEEN 1 PRECEDING AND 2 FOLLOWING) " +
                        "FROM (VALUES CAST(null AS integer), null, null) T(a)",
                "VALUES " +
                        "ARRAY[cast(null as integer), cast(null as integer), cast(null as integer)], " +
                        "ARRAY[null, null, null], " +
                        "ARRAY[null, null, null]");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a GROUPS BETWEEN 1 FOLLOWING AND 2 FOLLOWING) " +
                        "FROM (VALUES CAST(null AS integer), null, null) T(a)",
                "VALUES " +
                        "CAST(null AS array), " +
                        "null, " +
                        "null");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a GROUPS BETWEEN 2 PRECEDING AND 1 PRECEDING) " +
                        "FROM (VALUES CAST(null AS integer), null, null) T(a)",
                "VALUES " +
                        "CAST(null AS array), " +
                        "null, " +
                        "null");
    }

    @Test
    public void testAllPartitionSameValuesGroup()
    {
        assertQuery("SELECT array_agg(a) OVER(ORDER BY a GROUPS BETWEEN 1 FOLLOWING AND 2 FOLLOWING) " +
                        "FROM (VALUES 'a', 'a', 'a') T(a)",
                "VALUES " +
                        "CAST(null AS array), " +
                        "null, " +
                        "null");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a GROUPS BETWEEN 2 PRECEDING AND 1 PRECEDING) " +
                        "FROM (VALUES 'a', 'a', 'a') T(a)",
                "VALUES " +
                        "CAST(null AS array), " +
                        "null, " +
                        "null");

        assertQuery("SELECT array_agg(a) OVER(ORDER BY a GROUPS BETWEEN 1 PRECEDING AND 1 FOLLOWING) " +
                        "FROM (VALUES 'a', 'a', 'a') T(a)",
                "VALUES " +
                        "ARRAY['a', 'a', 'a'], " +
                        "ARRAY['a', 'a', 'a'], " +
                        "ARRAY['a', 'a', 'a']");

        // test frame bounds at partition bounds
        assertQuery("SELECT array_agg(a) OVER(ORDER BY a GROUPS BETWEEN 10 PRECEDING AND 10 FOLLOWING) " +
                        "FROM (VALUES 'a', 'a', 'a') T(a)",
                "VALUES " +
                        "ARRAY['a', 'a', 'a'], " +
                        "ARRAY['a', 'a', 'a'], " +
                        "ARRAY['a', 'a', 'a']");
    }

    @Test
    public void testInvalidOffsetGroup()
    {
        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a ASC GROUPS x PRECEDING) " +
                        "FROM (VALUES (1, 1), (2, -2)) T(a, x)",
                "Window frame -2 offset must not be negative");
        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a ASC GROUPS x PRECEDING) " +
                        "FROM (VALUES (1, 1), (2, -2)) T(a, x)",
                "Window frame -2 offset must not be negative");

        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a ASC GROUPS BETWEEN 1 PRECEDING AND x FOLLOWING) " +
                        "FROM (VALUES (1, 1), (2, -2)) T(a, x)",
                "Window frame -2 offset must not be negative");

        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a DESC GROUPS x PRECEDING) " +
                        "FROM (VALUES (1, 1), (2, -2)) T(a, x)",
                "Window frame -2 offset must not be negative");

        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a DESC GROUPS BETWEEN 1 PRECEDING AND x FOLLOWING) " +
                        "FROM (VALUES (1, 1), (2, -2)) T(a, x)",
                "Window frame -2 offset must not be negative");

        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a DESC GROUPS x PRECEDING) " +
                        "FROM (VALUES (1, 1), (2, null)) T(a, x)",
                "Window frame starting offset must not be null");

        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a DESC GROUPS BETWEEN 1 PRECEDING AND x FOLLOWING) " +
                        "FROM (VALUES (1, 1), (2, null)) T(a, x)",
                "Window frame ending offset must not be null");

        // fail if offset is invalid for null sort key
        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a DESC GROUPS BETWEEN 1 PRECEDING AND x FOLLOWING) " +
                        "FROM (VALUES (1, 1), (null, null)) T(a, x)",
                "Window frame ending offset must not be null");

        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a DESC GROUPS BETWEEN 1 PRECEDING AND x FOLLOWING) " +
                        "FROM (VALUES (1, 1), (null, -1)) T(a, x)",
                "Window frame -1 offset must not be negative");

        // test invalid offset of different types
        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a GROUPS x PRECEDING) " +
                        "FROM (VALUES (1, BIGINT '-1')) T(a, x)",
                "Window frame -1 offset must not be negative");

        assertQueryFails("SELECT array_agg(a) OVER(ORDER BY a GROUPS x PRECEDING) " +
                        "FROM (VALUES (1, INTEGER '-1')) T(a, x)",
                "Window frame -1 offset must not be negative");
    }

    @Test
    public void testWindowPartitioningGroup()
    {
        assertQuery("SELECT a, p, array_agg(a) OVER(PARTITION BY p ORDER BY a ASC NULLS FIRST GROUPS BETWEEN 1 PRECEDING AND 1 FOLLOWING) " +
                        "FROM (VALUES (1, 'x'), (2, 'x'), (null, 'x'), (null, 'y'), (2, 'y')) T(a, p)",
                "VALUES " +
                        "(null, 'x', ARRAY[null, 1]), " +
                        "(1,    'x', ARRAY[null, 1, 2]), " +
                        "(2,    'x', ARRAY[1, 2]), " +
                        "(null, 'y', ARRAY[null, 2]), " +
                        "(2,    'y', ARRAY[null, 2])");

        assertQuery("SELECT a, p, array_agg(a) OVER(PARTITION BY p ORDER BY a ASC NULLS FIRST GROUPS BETWEEN 0 PRECEDING AND 1 FOLLOWING) " +
                        "FROM (VALUES (1, 'x'), (2, 'x'), (null, 'x'), (null, 'y'), (2, 'y'), (null, null), (null, null), (1, null)) T(a, p)",
                "VALUES " +
                        "(null, null, ARRAY[null, null, 1]), " +
                        "(null, null, ARRAY[null, null, 1]), " +
                        "(1,    null, ARRAY[1]), " +
                        "(null, 'x', ARRAY[null, 1]), " +
                        "(1,    'x', ARRAY[1, 2]), " +
                        "(2,    'x', ARRAY[2]), " +
                        "(null, 'y', ARRAY[null, 2]), " +
                        "(2,    'y', ARRAY[2])");
    }

    @Test
    public void testMultipleWindowFunctionsGroup()
    {
        // two functions with frame type GROUPS
        assertQuery("SELECT x, array_agg(date) OVER(ORDER BY x GROUPS BETWEEN 1 PRECEDING AND 1 PRECEDING), avg(number) OVER(ORDER BY x GROUPS BETWEEN 1 FOLLOWING AND 1 FOLLOWING) " +
                        "FROM (VALUES " +
                        "(2, DATE '2222-01-01', 4.4), " +
                        "(1, DATE '1111-01-01', 2.2), " +
                        "(3, DATE '3333-01-01', 6.6)) T(x, date, number)",
                "VALUES " +
                        "(1, null, 4.4), " +
                        "(2, ARRAY[DATE '1111-01-01'], 6.6), " +
                        "(3, ARRAY[DATE '2222-01-01'], null)");

        // three functions with different frame types
        assertQuery("SELECT " +
                        "x, " +
                        "array_agg(a) OVER(ORDER BY x RANGE BETWEEN 2 PRECEDING AND CURRENT ROW), " +
                        "array_agg(a) OVER(ORDER BY x GROUPS BETWEEN 1 FOLLOWING AND 2 FOLLOWING), " +
                        "array_agg(a) OVER(ORDER BY x ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) " +
                        "FROM (VALUES " +
                        "(1.0, 1), " +
                        "(2.0, 2), " +
                        "(3.0, 3), " +
                        "(4.0, 4), " +
                        "(5.0, 5), " +
                        "(6.0, 6)) T(x, a)",
                "VALUES " +
                        "(1.0, ARRAY[1], ARRAY[2, 3], ARRAY[1]), " +
                        "(2.0, ARRAY[1, 2], ARRAY[3, 4], ARRAY[1, 2]), " +
                        "(3.0, ARRAY[1, 2, 3], ARRAY[4, 5], ARRAY[2, 3]), " +
                        "(4.0, ARRAY[2, 3, 4], ARRAY[5, 6], ARRAY[3, 4]), " +
                        "(5.0, ARRAY[3, 4, 5], ARRAY[6], ARRAY[4, 5]), " +
                        "(6.0, ARRAY[4, 5, 6], null, ARRAY[5, 6])");
    }
}
