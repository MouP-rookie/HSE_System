package com.cmeim.hse.service;

import com.cmeim.hse.iot.IotProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class OverviewService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /** 指定时间范围时两张实时表最多返回的条数 */
    private static final int MAX_DATA_ROWS = 2000;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TelemetryService telemetry;
    private final IotProperties props;

    public OverviewService(TelemetryService telemetry, IotProperties props) {
        this.telemetry = telemetry;
        this.props = props;
    }

    /**
     * 一次查询的全部入参。
     *
     * @param limit      两张实时表最多返回多少条
     * @param refresh    是否强制刷新开关机缓存
     * @param powerDays  开关机记录展示最近多少天, 0 = 全部历史, null = 用配置值
     * @param powerStart 开关机记录自定义起点(毫秒), 与 powerEnd 任一不为 null 时 powerDays 失效
     * @param powerEnd   开关机记录自定义终点(毫秒)
     * @param dataDays   两张实时表展示最近多少天, null 或 0 表示默认最近 30 天
     * @param dataStart  两张实时表自定义起点(毫秒), 与 dataEnd 任一不为 null 时 dataDays 失效
     * @param dataEnd    两张实时表自定义终点(毫秒)
     */
    public record Query(int limit, boolean refresh,
                        Integer powerDays, Long powerStart, Long powerEnd,
                        Integer dataDays, Long dataStart, Long dataEnd) {
    }

    public Map<String, Object> build(Query query) {
        int rowLimit = Math.min(Math.max(query.limit(), 1), MAX_DATA_ROWS);
        long now = System.currentTimeMillis();

        int powerDays = query.powerDays() != null ? query.powerDays() : props.getPowerDisplayDays();
        Long powerFrom = query.powerStart();
        Long powerTo = query.powerEnd();
        if (powerFrom == null && powerTo == null && powerDays > 0) {
            powerFrom = now - powerDays * 24L * 3600 * 1000;
        }

        Long dataFrom = query.dataStart();
        Long dataTo = query.dataEnd();
        if (dataFrom == null && dataTo == null && query.dataDays() != null && query.dataDays() > 0) {
            dataFrom = now - query.dataDays() * 24L * 3600 * 1000;
        }
        // 指定了时间范围就返回范围内全部数据点(上限 2000), 不再受"实时条数"限制, 否则选了范围也看不出变化
        int dataLimit = (dataFrom != null || dataTo != null) ? MAX_DATA_ROWS : rowLimit;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("generatedAt", TIME_FMT.withZone(ZONE).format(Instant.now()));
        body.put("source", props.getBaseUrl());
        body.put("power", block(props.getPressure(), telemetry.powerRecords(query.refresh(), powerFrom, powerTo)));
        body.put("pressure", block(props.getPressure(), telemetry.pressureRows(dataLimit, dataFrom, dataTo)));
        body.put("th", block(props.getTh(), telemetry.thRows(dataLimit, dataFrom, dataTo)));
        return body;
    }

    /**
     * 解析页面传来的时间, 支持 2026-10-07T00:00、2026-10-07 00:00、2026-10-07T00:00:00, 一律按北京时间解释。
     * 空字符串返回 null, 表示该端不限。
     */
    public static Long parseTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim().replace(' ', 'T');
        try {
            return LocalDateTime.parse(value).atZone(ZONE).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("时间格式不正确, 应形如 2026-10-07T00:00 : " + text);
        }
    }

    private static Map<String, Object> block(IotProperties.Device device, Object rows) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("deviceId", device.getId());
        block.put("deviceName", device.getName());
        block.put("deviceLabel", device.getLabel());
        block.put("rows", rows);
        return block;
    }
}
