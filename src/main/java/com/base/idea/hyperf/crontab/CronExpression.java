package com.base.idea.hyperf.crontab;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Cron 规则解析与下次执行时间计算，语义对齐 Hyperf\Crontab\Parser：
 *
 * <ul>
 *   <li>6 段：秒 分 时 日 月 周；5 段时秒固定为 0</li>
 *   <li>段语法：{@code *}、{@code *&#47;n}、{@code a-b}、{@code a-b/n}、逗号列表、数字；
 *       不支持名称（JAN/MON）与 {@code ?}（Hyperf 的 isValid 同样不允许）</li>
 *   <li>周日=0，范围 0-6（Hyperf 不允许 7）；日与周是 AND 关系（同 Hyperf parse()，非标准 cron 的 OR）</li>
 * </ul>
 */
public class CronExpression {

    private final int[] seconds;
    private final int[] minutes;
    private final int[] hours;
    private final int[] days;
    private final int[] months;
    private final int[] weeks;

    private CronExpression(int[] seconds, int[] minutes, int[] hours, int[] days, int[] months, int[] weeks) {
        this.seconds = seconds;
        this.minutes = minutes;
        this.hours = hours;
        this.days = days;
        this.months = months;
        this.weeks = weeks;
    }

    /** 解析规则，无法解析（段数不对/段为空/语法不支持）返回 null */
    public static @Nullable CronExpression parse(@NotNull String rule) {
        String[] parts = rule.trim().split("\\s+");
        if (parts.length != 5 && parts.length != 6) {
            return null;
        }
        int i = 0;
        int[] seconds = parts.length == 6 ? parseSegment(parts[i++], 0, 59) : new int[]{0};
        int[] minutes = parseSegment(parts[i++], 0, 59);
        int[] hours = parseSegment(parts[i++], 0, 23);
        int[] days = parseSegment(parts[i++], 1, 31);
        int[] months = parseSegment(parts[i++], 1, 12);
        int[] weeks = parseSegment(parts[i], 0, 6);
        if (seconds == null || minutes == null || hours == null || days == null || months == null || weeks == null) {
            return null;
        }
        return new CronExpression(seconds, minutes, hours, days, months, weeks);
    }

    /** 与 Hyperf Parser::parseSegment 对齐：解析单段为升序去重的取值集合；空集返回 null（包级可见供单测） */
    static int @Nullable [] parseSegment(@NotNull String segment, int min, int max) {
        TreeSet<Integer> result = new TreeSet<>();
        if (!collectSegment(segment, min, max, result)) {
            return null;
        }
        if (result.isEmpty()) {
            return null;
        }
        int[] values = new int[result.size()];
        int i = 0;
        for (int v : result) {
            values[i++] = v;
        }
        return values;
    }

    /** @return false 表示语法不支持（非数字/步长非法），调用方视为解析失败 */
    private static boolean collectSegment(@NotNull String segment, int min, int max, @NotNull TreeSet<Integer> result) {
        if ("*".equals(segment)) {
            for (int i = min; i <= max; i++) {
                result.add(i);
            }
            return true;
        }
        if (segment.contains(",")) {
            for (String piece : segment.split(",", -1)) {
                // 同 Hyperf：空段跳过（如 '10,14,,15,'）
                if (piece.trim().isEmpty()) {
                    continue;
                }
                // 同 Hyperf：段内含 '-' 时整段递归（piece 是裸数字也走递归无妨）
                if (piece.contains("/") || segment.contains("-")) {
                    if (!collectSegment(piece, min, max, result)) {
                        return false;
                    }
                    continue;
                }
                Integer value = parseInt(piece);
                if (value == null) {
                    return false;
                }
                if (value >= min && value <= max) {
                    result.add(value);
                }
            }
            return true;
        }
        if (segment.contains("/")) {
            String[] parts = segment.split("/", -1);
            if (parts.length != 2) {
                return false;
            }
            Integer step = parseInt(parts[1]);
            if (step == null || step <= 0) {
                return false;
            }
            int lo = min;
            int hi = max;
            String base = parts[0];
            if (base.contains("-")) {
                String[] range = base.split("-", -1);
                if (range.length != 2) {
                    return false;
                }
                Integer rMin = parseInt(range[0]);
                Integer rMax = parseInt(range[1]);
                if (rMin == null || rMax == null) {
                    return false;
                }
                // 同 Hyperf：只在收紧时生效
                if (rMin > lo) {
                    lo = rMin;
                }
                if (rMax < hi) {
                    hi = rMax;
                }
            } else if (!base.isEmpty() && !"*".equals(base)) {
                // Hyperf 对 '5/2' 这类写法从字段最小值起步，保持 parity
                Integer baseValue = parseInt(base);
                if (baseValue == null) {
                    return false;
                }
            }
            for (int i = lo; i <= hi; i += step) {
                result.add(i);
            }
            return true;
        }
        if (segment.contains("-")) {
            return collectSegment(segment + "/1", min, max, result);
        }
        Integer value = parseInt(segment);
        if (value == null) {
            return false;
        }
        if (value >= min && value <= max) {
            result.add(value);
        }
        return true;
    }

    private static @Nullable Integer parseInt(@NotNull String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从 from 之后的最近 count 次执行时间（本地时区）。
     * 按天遍历（日/月/周 AND 命中），命中天再展开 时/分/秒；最多向后找 4 年（覆盖 2 月 29 日）。
     */
    public @NotNull List<LocalDateTime> nextRuns(@NotNull LocalDateTime from, int count) {
        List<LocalDateTime> result = new ArrayList<>();
        LocalDate day = from.toLocalDate();
        LocalDate end = day.plusYears(4);
        while (result.size() < count && !day.isAfter(end)) {
            if (contains(months, day.getMonthValue())
                    && contains(days, day.getDayOfMonth())
                    && contains(weeks, day.getDayOfWeek().getValue() % 7)) {
                for (int hour : hours) {
                    for (int minute : minutes) {
                        for (int second : seconds) {
                            LocalDateTime time = day.atTime(hour, minute, second);
                            if (time.isAfter(from)) {
                                result.add(time);
                                if (result.size() == count) {
                                    return result;
                                }
                            }
                        }
                    }
                }
            }
            day = day.plusDays(1);
        }
        return result;
    }

    private static boolean contains(int @NotNull [] values, int value) {
        for (int v : values) {
            if (v == value) {
                return true;
            }
        }
        return false;
    }
}
