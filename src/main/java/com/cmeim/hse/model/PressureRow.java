package com.cmeim.hse.model;

import java.util.List;

/** 油烟治理系统实时数据行: 压差1 = 负压2 - 负压1, 压差2 = 负压3 - 负压2。 */
public record PressureRow(int seq,
                          String deviceName,
                          String diff1,
                          String diff2,
                          String collectTime,
                          String statusText,
                          List<RawItem> raw) {
}
