package com.cmeim.hse;

import com.cmeim.hse.service.OverviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 自检模式: 不启动 Web 容器, 直接把页面使用的数据打印到控制台。
 * 用法: java -jar target/hse-iot-demo-0.0.1-SNAPSHOT.jar --spring.profiles.active=dump --spring.main.web-application-type=none
 *      [--dump.days=0] [--dump.limit=5] [--dump.start=2026-10-01T00:00] [--dump.end=2026-10-07T23:59]
 *      [--dump.dataDays=7] [--dump.dataStart=2026-10-01T00:00] [--dump.dataEnd=2026-10-07T23:59] [--dump.out=dump.json]
 * dump.out 会以 UTF-8 写出, 避免 Windows 控制台编码把中文显示成乱码。
 */
@Component
@Profile("dump")
public class DumpRunner implements ApplicationRunner {

    private final OverviewService overviewService;
    private final ObjectMapper mapper;

    public DumpRunner(OverviewService overviewService, ObjectMapper mapper) {
        this.overviewService = overviewService;
        this.mapper = mapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Integer days = option(args, "dump.days") == null ? null : Integer.valueOf(option(args, "dump.days"));
        int limit = option(args, "dump.limit") == null ? 5 : Integer.parseInt(option(args, "dump.limit"));
        Integer dataDays = option(args, "dump.dataDays") == null ? null : Integer.valueOf(option(args, "dump.dataDays"));
        Map<String, Object> body = overviewService.build(new OverviewService.Query(limit, true,
                days,
                OverviewService.parseTime(option(args, "dump.start")),
                OverviewService.parseTime(option(args, "dump.end")),
                dataDays,
                OverviewService.parseTime(option(args, "dump.dataStart")),
                OverviewService.parseTime(option(args, "dump.dataEnd"))));
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(body);

        String out = option(args, "dump.out");
        if (out != null && !out.isBlank()) {
            Path path = Path.of(out).toAbsolutePath();
            Files.writeString(path, json, StandardCharsets.UTF_8);
            System.out.println("dump written -> " + path);
        }
        System.out.println("=== DUMP BEGIN ===");
        System.out.println(json);
        System.out.println("=== DUMP END ===");
    }

    private static String option(ApplicationArguments args, String name) {
        if (!args.containsOption(name) || args.getOptionValues(name).isEmpty()) {
            return null;
        }
        return args.getOptionValues(name).get(0);
    }
}