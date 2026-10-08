package com.cmeim.hse.web;

import com.cmeim.hse.service.OverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final OverviewService overviewService;

    public ApiController(OverviewService overviewService) {
        this.overviewService = overviewService;
    }

    /**
     * @param limit     两张实时表最多返回多少条
     * @param days      开关机记录展示最近多少天, 0 表示全部历史
     * @param start     开关机记录起点, 形如 2026-10-01T00:00
     * @param end       开关机记录终点, 形如 2026-10-07T23:59
     * @param dataDays  实时数据展示最近多少天, 不传表示最近 30 天
     * @param dataStart 实时数据起点, 形如 2026-10-01T00:00
     * @param dataEnd   实时数据终点, 形如 2026-10-07T23:59
     * @param refresh   是否强制刷新开关机缓存
     */
    @GetMapping("/overview")
    public Map<String, Object> overview(@RequestParam(name = "limit", defaultValue = "10") int limit,
                                        @RequestParam(name = "days", required = false) Integer days,
                                        @RequestParam(name = "start", required = false) String start,
                                        @RequestParam(name = "end", required = false) String end,
                                        @RequestParam(name = "dataDays", required = false) Integer dataDays,
                                        @RequestParam(name = "dataStart", required = false) String dataStart,
                                        @RequestParam(name = "dataEnd", required = false) String dataEnd,
                                        @RequestParam(name = "refresh", defaultValue = "false") boolean refresh) {
        return overviewService.build(new OverviewService.Query(limit, refresh,
                days, OverviewService.parseTime(start), OverviewService.parseTime(end),
                dataDays, OverviewService.parseTime(dataStart), OverviewService.parseTime(dataEnd)));
    }
}
