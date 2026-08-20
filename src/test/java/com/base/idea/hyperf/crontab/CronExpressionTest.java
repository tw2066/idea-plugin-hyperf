package com.base.idea.hyperf.crontab;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * {@link CronExpression} 单测，用例移植自 hyperf/crontab 的
 * ParserTest / ParserCronNumberTest（src/crontab/tests），
 * 语义差异处（nextRuns 严格大于 from、分钟对齐到 0 秒）按本类语义断言。
 */
public class CronExpressionTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ---------- parseSegment（对应 ParserCronNumberTest） ----------

    @Test
    public void testSegmentStar() {
        int[] expected = new int[60];
        for (int i = 0; i < 60; i++) {
            expected[i] = i;
        }
        assertArrayEquals(expected, CronExpression.parseSegment("*", 0, 59));
    }

    @Test
    public void testSegmentStep() {
        assertArrayEquals(new int[]{0, 11, 22, 33, 44, 55}, CronExpression.parseSegment("*/11", 0, 59));
        assertArrayEquals(new int[]{0, 11, 22, 33}, CronExpression.parseSegment("0-40/11", 0, 59));
        assertArrayEquals(new int[]{2, 13}, CronExpression.parseSegment("2-40/11", 0, 23));
        assertArrayEquals(new int[]{2, 5, 8}, CronExpression.parseSegment("2-10/3", 0, 11));
    }

    @Test
    public void testSegmentSingleAndList() {
        assertArrayEquals(new int[]{11}, CronExpression.parseSegment("11", 0, 59));
        assertArrayEquals(new int[]{11, 12, 13}, CronExpression.parseSegment("11,12,13", 0, 59));
        // 空段跳过（Hyperf: '10,14,,15,'）
        assertArrayEquals(new int[]{10, 14, 15}, CronExpression.parseSegment("10,14,,15,", 0, 59));
        // 混合 range 与带步长 range（Hyperf 的段内含 '-' 整段递归规则）
        assertArrayEquals(new int[]{10, 11, 12, 14, 15}, CronExpression.parseSegment("10-12,14-15/1", 0, 59));
    }

    @Test
    public void testSegmentInvalid() {
        assertNull(CronExpression.parseSegment("a", 0, 59));
        assertNull(CronExpression.parseSegment("*/0", 0, 59));
        assertNull(CronExpression.parseSegment("?", 0, 59));
        // 超出字段范围被丢弃后为空集 → null（Hyperf 中 week=7 永不触发）
        assertNull(CronExpression.parseSegment("7", 0, 6));
    }

    // ---------- parse 合法性（对应 ParserTest::testIsValid/testIsInvalid） ----------

    @Test
    public void testParseValid() {
        String[] rules = {
                "* * * * *",
                "* * * * * *",
                "*/11 * * * * *",
                "10-15/1 * * * * *",
                "10-12/1,14-15/1 * * * * *",
                "10,14,,15, * * * * *",
                "10-15/1 10-12/1 10 * * *",
        };
        for (String rule : rules) {
            assertNotNull("should parse: " + rule, CronExpression.parse(rule));
        }
    }

    @Test
    public void testParseInvalid() {
        assertNull(CronExpression.parse("* * *"));
        assertNull(CronExpression.parse("* * * * * * *"));
        assertNull(CronExpression.parse("JAN * * * *"));
        assertNull(CronExpression.parse("? * * * *"));
    }

    // ---------- nextRuns（对应 ParserTest 各 parse 用例） ----------

    @Test
    public void testSecondLevel() {
        // Hyperf: '*/11 * * * * *' 从 2019-06-21 01:47:00 起 6 次
        assertEquals(
                List.of("2019-06-21 01:47:00", "2019-06-21 01:47:11", "2019-06-21 01:47:22",
                        "2019-06-21 01:47:33", "2019-06-21 01:47:44", "2019-06-21 01:47:55"),
                next("*/11 * * * * *", LocalDateTime.of(2019, 6, 21, 1, 46, 59), 6));
    }

    @Test
    public void testSecondLevelAcrossMinute() {
        // 语义差异：nextRuns 严格大于 from 且分钟对齐 0 秒
        // （Hyperf parse 是 startTime+second 的非对齐结果），这里按本类语义断言
        assertEquals(
                List.of("2019-06-21 01:48:00", "2019-06-21 01:48:11", "2019-06-21 01:48:22",
                        "2019-06-21 01:48:33", "2019-06-21 01:48:44", "2019-06-21 01:48:55"),
                next("*/11 * * * * *", LocalDateTime.of(2019, 6, 21, 1, 47, 55), 6));
    }

    @Test
    public void testSecondLevelBetween() {
        assertEquals(
                List.of("2020-06-10 09:58:10", "2020-06-10 09:58:11", "2020-06-10 09:58:12",
                        "2020-06-10 09:58:13", "2020-06-10 09:58:14", "2020-06-10 09:58:15"),
                next("10-15/1 * * * * *", LocalDateTime.of(2020, 6, 10, 9, 58, 9), 6));
    }

    @Test
    public void testSecondLevelForComma() {
        assertEquals(
                List.of("2020-06-10 09:58:10", "2020-06-10 09:58:11", "2020-06-10 09:58:12",
                        "2020-06-10 09:58:14", "2020-06-10 09:58:15"),
                next("10-12/1,14-15/1 * * * * *", LocalDateTime.of(2020, 6, 10, 9, 58, 9), 5));
        // 不带步长的混合写法
        assertEquals(
                List.of("2020-06-10 09:58:10", "2020-06-10 09:58:11", "2020-06-10 09:58:12",
                        "2020-06-10 09:58:14", "2020-06-10 09:58:15"),
                next("10-12,14-15/1 * * * * *", LocalDateTime.of(2020, 6, 10, 9, 58, 9), 5));
    }

    @Test
    public void testSecondLevelWithEmptyPiece() {
        // Hyperf 只看当前分钟返回 3 个；nextRuns 会跨分钟继续，取前 3 个断言
        assertEquals(
                List.of("2020-06-10 09:58:10", "2020-06-10 09:58:14", "2020-06-10 09:58:15"),
                next("10,14,,15, * * * * *", LocalDateTime.of(2020, 6, 10, 9, 58, 9), 3));
    }

    @Test
    public void testMinuteLevelBetween() {
        // sec 10-15, min 10-12, hour 10（Hyperf ParserTest::testParseMinuteLevelBetween）
        assertEquals(
                List.of("2020-06-10 10:10:10", "2020-06-10 10:10:11", "2020-06-10 10:10:12",
                        "2020-06-10 10:10:13", "2020-06-10 10:10:14", "2020-06-10 10:10:15"),
                next("10-15/1 10-12/1 10 * * *", LocalDateTime.of(2020, 6, 10, 10, 9, 59), 6));
        // 过了 10:12:15 后，下一次是次日 10:10:10
        assertEquals(
                List.of("2020-06-11 10:10:10"),
                next("10-15/1 10-12/1 10 * * *", LocalDateTime.of(2020, 6, 10, 10, 12, 15), 1));
    }

    @Test
    public void testMinuteLevel() {
        // 5 段：秒固定 0（Hyperf ParserTest::testParseMinuteLevel）
        assertEquals(
                List.of("2019-06-21 01:33:00", "2019-06-21 01:44:00", "2019-06-21 01:55:00",
                        "2019-06-21 02:00:00", "2019-06-21 02:11:00"),
                next("*/11 * * * *", LocalDateTime.of(2019, 6, 21, 1, 32, 59), 5));
    }

    @Test
    public void testFromIsExclusive() {
        assertEquals(
                List.of("2026-08-21 00:00:00"),
                next("0 0 * * *", LocalDateTime.of(2026, 8, 20, 0, 0, 0), 1));
    }

    @Test
    public void testDayOfWeekSundayIsZero() {
        // 2026-08-20 是周四，下一个周日是 2026-08-23
        assertEquals(
                List.of("2026-08-23 00:00:00"),
                next("0 0 * * 0", LocalDateTime.of(2026, 8, 20, 0, 0, 0), 1));
    }

    @Test
    public void testDayAndWeekAreAnded() {
        // 日/周 AND（Hyperf 语义，非标准 cron 的 OR）：1 号且周日 → 2026-11-01（周日）
        assertEquals(
                List.of("2026-11-01 00:00:00"),
                next("0 0 1 * 0", LocalDateTime.of(2026, 8, 20, 0, 0, 0), 1));
    }

    @Test
    public void testLeapDay() {
        // 2 月 29 日：2026/2027 非闰年，下一个是 2028-02-29（验证 4 年遍历窗口）
        List<String> runs = next("0 0 29 2 *", LocalDateTime.of(2026, 8, 20, 0, 0, 0), 5);
        assertEquals(List.of("2028-02-29 00:00:00"), runs);
    }

    // ---------- helpers ----------

    private static List<String> next(String rule, LocalDateTime from, int count) {
        CronExpression expression = CronExpression.parse(rule);
        assertNotNull("parse failed: " + rule, expression);
        List<String> result = new ArrayList<>();
        for (LocalDateTime time : expression.nextRuns(from, count)) {
            result.add(time.format(FMT));
        }
        return result;
    }
}
