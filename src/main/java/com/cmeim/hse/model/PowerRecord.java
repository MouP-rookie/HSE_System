package com.cmeim.hse.model;

/**
 * 一次开关机记录。
 * running 为 true 时 endTs/endTime 为空, 页面显示为"运行中"。
 * reason 说明这条记录的关机依据: 设备上报关机 / 数据中断（疑似断电）。
 */
public record PowerRecord(int seq,
                          long startTs,
                          String startTime,
                          Long endTs,
                          String endTime,
                          boolean running,
                          long durationSeconds,
                          String durationText,
                          String reason) {
}
