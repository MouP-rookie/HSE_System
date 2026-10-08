package com.cmeim.hse.model;

import java.util.List;

/** 温湿度传感器实时数据行。 */
public record ThRow(int seq,
                    String deviceName,
                    String temperature,
                    String humidity,
                    String collectTime,
                    List<RawItem> raw) {
}
