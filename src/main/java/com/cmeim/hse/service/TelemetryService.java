package com.cmeim.hse.service;

import com.cmeim.hse.iot.IotProperties;
import com.cmeim.hse.iot.ThingsBoardClient;
import com.cmeim.hse.model.PowerRecord;
import com.cmeim.hse.model.PressureRow;
import com.cmeim.hse.model.RawItem;
import com.cmeim.hse.model.ThRow;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

@Service
public class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String KEY_STATUS = "status";
    private static final String KEY_CODE = "code";
    private static final String KEY_P1 = "负压1号机";
    private static final String KEY_P2 = "负压2号机";
    private static final String KEY_P3 = "负压3号机";
    private static final String KEY_P4 = "负压4号机";
    private static final String KEY_TEMP = "温度";
    private static final String KEY_HUMIDITY = "湿度";
    private static final String KEY_COLLECT = "CollectionTime";

    private static final String REASON_REPORTED = "设备上报关机";
    private static final String REASON_GAP = "数据中断";
    private static final String REASON_RUNNING = "设备运行中";

    private final ThingsBoardClient client;
    private final IotProperties props;

    private volatile List<PowerRecord> powerCache = List.of();
    private volatile long powerCacheAt = 0L;

    public TelemetryService(ThingsBoardClient client, IotProperties props) {
        this.client = client;
        this.props = props;
    }

    /**
     * 开机时间取一次运行开始的数据点, 关机时间按下面的优先级确定:
     * 1. status=False 或 status=5 属于设备主动上报的关机, 以该时间点为准(优先于数据中断)。
     * 2. 相邻数据点间隔超过阈值(默认 20 分钟)视为数据中断, 以中断前最后一个数据点作为关机时间,
     *    因为数采模块与设备共用电源时, 设备关机后数采模块一起断电, 关机期间没有数据。
     *
     * @param startTs 展示范围起点(含), null 表示不限
     * @param endTs   展示范围终点(含), null 表示不限
     */
    public List<PowerRecord> powerRecords(boolean forceRefresh, Long startTs, Long endTs) {
        List<PowerRecord> all = allPowerRecords(forceRefresh);
        long now = System.currentTimeMillis();
        Long from = startTs;
        Long to = endTs;
        List<PowerRecord> filtered = new ArrayList<>();
        int seq = 1;
        for (PowerRecord r : all) {
            long recordEnd = r.endTs() != null ? r.endTs() : now;
            if (from != null && recordEnd < from) {
                continue;
            }
            if (to != null && r.startTs() > to) {
                continue;
            }
            filtered.add(new PowerRecord(seq++, r.startTs(), r.startTime(), r.endTs(), r.endTime(),
                    r.running(), r.durationSeconds(), r.durationText(), r.reason()));
        }
        return filtered;
    }

    private List<PowerRecord> allPowerRecords(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        long ttl = Math.max(0, props.getPowerCacheSeconds()) * 1000L;
        if (!forceRefresh && powerCacheAt > 0 && now - powerCacheAt < ttl) {
            return powerCache;
        }

        long startTs = parseStart(props.getPowerHistoryStart());
        JsonNode data = client.timeseries(props.getPressure().getId(), List.of(KEY_STATUS),
                startTs, now, "NONE", props.getQueryLimit(), "ASC");
        List<Point> points = series(data, KEY_STATUS);

        long gapMs = Math.max(1, props.getGapMinutes()) * 60_000L;
        List<PowerRecord> chronological = new ArrayList<>();
        Long openedAt = null;
        Point prev = null;
        for (Point p : points) {
            Boolean on = parseOn(p.value());
            boolean reportedOff = Boolean.FALSE.equals(on);
            boolean interrupted = prev != null && openedAt != null && (p.ts() - prev.ts()) > gapMs;

            if (openedAt != null && reportedOff) {
                addIfMeaningful(chronological, openedAt, p.ts(), REASON_REPORTED);
                openedAt = null;
            } else if (openedAt != null && interrupted) {
                addIfMeaningful(chronological, openedAt, prev.ts(), REASON_GAP);
                openedAt = null;
            }
            if (Boolean.TRUE.equals(on) && openedAt == null) {
                openedAt = p.ts();
            }
            prev = p;
        }
        if (openedAt != null) {
            chronological.add(runningRecord(openedAt, now));
        }

        Collections.reverse(chronological);
        List<PowerRecord> result = new ArrayList<>(chronological.size());
        int seq = 1;
        for (PowerRecord r : chronological) {
            result.add(new PowerRecord(seq++, r.startTs(), r.startTime(), r.endTs(), r.endTime(),
                    r.running(), r.durationSeconds(), r.durationText(), r.reason()));
        }
        powerCache = List.copyOf(result);
        powerCacheAt = now;
        return powerCache;
    }

    /** 油烟治理系统实时数据, 包含压差计算和原始字段。 */
    public List<PressureRow> pressureRows(int limit, Long startTs, Long endTs) {
        IotProperties.Device device = props.getPressure();
        Window window = window(startTs, endTs);
        List<String> keys = List.of(KEY_CODE, KEY_STATUS, KEY_P1, KEY_P2, KEY_P3, KEY_P4);
        JsonNode data = client.timeseries(device.getId(), keys, window.from(), window.to(), "NONE", limit, "DESC");

        Map<String, NavigableMap<Long, String>> maps = new LinkedHashMap<>();
        for (String key : keys) {
            NavigableMap<Long, String> map = new TreeMap<>();
            for (Point p : series(data, key)) {
                map.put(p.ts(), p.value());
            }
            maps.put(key, map);
        }

        List<PressureRow> rows = new ArrayList<>();
        int seq = 1;
        for (Long ts : maps.get(KEY_P1).descendingKeySet()) {
            String v1 = maps.get(KEY_P1).get(ts);
            String v2 = nearestValue(maps.get(KEY_P2), ts);
            String v3 = nearestValue(maps.get(KEY_P3), ts);
            String status = nearestValue(maps.get(KEY_STATUS), ts);
            List<RawItem> raw = new ArrayList<>();
            for (String key : keys) {
                Map.Entry<Long, String> entry = maps.get(key).floorEntry(ts);
                if (entry != null) {
                    raw.add(new RawItem(key, entry.getValue(), formatTime(entry.getKey())));
                }
            }
            rows.add(new PressureRow(seq++, device.getLabel(),
                    subtract(v1, v2), subtract(v2, v3),
                    formatTime(ts), statusText(status), raw));
            if (rows.size() >= limit) {
                break;
            }
        }
        return rows;
    }

    /** 温湿度传感器实时数据, 直接展示原始采集值。 */
    public List<ThRow> thRows(int limit, Long startTs, Long endTs) {
        IotProperties.Device device = props.getTh();
        Window window = window(startTs, endTs);
        List<String> keys = List.of(KEY_CODE, KEY_COLLECT, KEY_TEMP, KEY_HUMIDITY);
        JsonNode data = client.timeseries(device.getId(), keys, window.from(), window.to(), "NONE", limit, "DESC");

        Map<String, NavigableMap<Long, String>> maps = new LinkedHashMap<>();
        for (String key : keys) {
            NavigableMap<Long, String> map = new TreeMap<>();
            for (Point p : series(data, key)) {
                map.put(p.ts(), p.value());
            }
            maps.put(key, map);
        }

        List<ThRow> rows = new ArrayList<>();
        int seq = 1;
        for (Long ts : maps.get(KEY_TEMP).descendingKeySet()) {
            String collectTime = maps.get(KEY_COLLECT).get(ts);
            if (collectTime == null || collectTime.isBlank()) {
                collectTime = formatTime(ts);
            }
            List<RawItem> raw = new ArrayList<>();
            for (String key : keys) {
                Map.Entry<Long, String> entry = maps.get(key).floorEntry(ts);
                if (entry != null) {
                    raw.add(new RawItem(key, entry.getValue(), formatTime(entry.getKey())));
                }
            }
            rows.add(new ThRow(seq++, device.getLabel(),
                    maps.get(KEY_TEMP).get(ts), maps.get(KEY_HUMIDITY).get(ts), collectTime, raw));
            if (rows.size() >= limit) {
                break;
            }
        }
        return rows;
    }

    /**
     * 关机时间与开机时间相差不足 1 秒时, 页面上会显示成 0天0小时0分0秒, 属于无意义的脏数据, 直接丢弃。
     */
    private static void addIfMeaningful(List<PowerRecord> target, long startTs, long endTs, String reason) {
        if (endTs - startTs >= 1000L) {
            target.add(closedRecord(startTs, endTs, reason));
        }
    }

    private static PowerRecord closedRecord(long startTs, long endTs, String reason) {
        long seconds = Math.max(0, (endTs - startTs) / 1000L);
        return new PowerRecord(0, startTs, formatTime(startTs), endTs, formatTime(endTs),
                false, seconds, durationText(seconds), reason);
    }

    private PowerRecord runningRecord(long startTs, long now) {
        long seconds = Math.max(0, (now - startTs) / 1000L);
        return new PowerRecord(0, startTs, formatTime(startTs), null, null,
                true, seconds, durationText(seconds), REASON_RUNNING);
    }

    /** 统一输出 x天x小时x分x秒。 */
    public static String durationText(long seconds) {
        long safe = Math.max(0, seconds);
        long days = safe / 86400L;
        long hours = safe % 86400L / 3600L;
        long minutes = safe % 3600L / 60L;
        long secs = safe % 60L;
        return days + "天" + hours + "小时" + minutes + "分" + secs + "秒";
    }

    private static String statusText(String value) {
        Boolean on = parseOn(value);
        if (on == null) {
            return "未知";
        }
        return on ? "运行中" : "已停机";
    }

    /** True 为开机; False 与 5 为关机; 其他取值不改变开关机状态。 */
    private static Boolean parseOn(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        if ("true".equalsIgnoreCase(v)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(v) || "5".equals(v)) {
            return Boolean.FALSE;
        }
        log.warn("收到无法识别的 status 取值: {}", v);
        return null;
    }

    /**
     * 两张实时表的取数窗口: 没传时间范围时取最近 30 天, 起止顺序颠倒直接报错。
     */
    private static Window window(Long startTs, Long endTs) {
        long to = endTs != null ? endTs : System.currentTimeMillis();
        long from = startTs != null ? startTs : to - 30L * 24 * 3600 * 1000;
        if (from > to) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }
        return new Window(from, to);
    }

    private record Window(long from, long to) {
    }

    private static String nearestValue(NavigableMap<Long, String> map, long ts) {
        Map.Entry<Long, String> entry = map.floorEntry(ts);
        return entry == null ? null : entry.getValue();
    }

    /** 压差 = b - a, 保留两位小数; 任一为空时返回 null。 */
    private static String subtract(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(b.trim()).subtract(new BigDecimal(a.trim()))
                    .setScale(2, RoundingMode.HALF_UP).toPlainString();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<Point> series(JsonNode data, String key) {
        JsonNode arr = data.path(key);
        List<Point> points = new ArrayList<>();
        if (arr.isArray()) {
            for (JsonNode node : arr) {
                points.add(new Point(node.path("ts").asLong(), node.path("value").asText()));
            }
        }
        points.sort((x, y) -> Long.compare(x.ts(), y.ts()));
        return points;
    }

    private static long parseStart(String text) {
        try {
            return OffsetDateTime.parse(text).toInstant().toEpochMilli();
        } catch (Exception e) {
            return Instant.parse("2025-07-09T00:00:00Z").toEpochMilli();
        }
    }

    private static String formatTime(long ts) {
        return TIME_FMT.withZone(ZONE).format(Instant.ofEpochMilli(ts));
    }

    private record Point(long ts, String value) {
    }
}
