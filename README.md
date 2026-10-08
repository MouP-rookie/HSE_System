# 东港废气处理环境监测看板（Demo）

展示两台 ThingsBoard 设备的数据：

| 设备 | 设备代码 | 展示内容 |
| --- | --- | --- |
| 淬火线油烟治理系统 | CZDG-985-42 | 开关机记录、实时检测数据（压差1 / 压差2） |
| 废气处理温湿度传感器 | CZDG-TH | 温度、湿度实时数据 |

技术栈：Spring Boot 3.5（Java 21 编译，本机 Java 25 运行）+ Vue 3（global 构建，无需 npm）。

## 启动

双击 `start.cmd`，或在项目根目录执行：

```
mvn -B spring-boot:run
```

浏览器打开 <http://localhost:8080>。

仓库里不含 IoT 平台账号密码：请在项目根目录创建 `application-local.yml`（已被 `.gitignore` 忽略，不会提交），或设置环境变量 `IOT_USERNAME` / `IOT_PASSWORD`：

```yaml
iot:
  username: 你的平台账号
  password: 你的平台密码
```

首次构建需要联网下载依赖。项目内的 `maven-settings.xml` 通过 `.mvn/maven.config` 自动生效，把中央仓库指向 `repo1.maven.org`（本机访问 `repo.maven.apache.org` 会 SSL 失败）。

## 页面结构

1. 淬火线油烟治理系统 · 开关机记录：序号、开机时间、关机时间、运行时间。正在运行的记录关机时间显示「运行中」，运行时间按秒跳动。工具栏的「历史范围」可切换近 7 / 30 / 90 / 180 天、全部历史或自定义时间段（选「自定义时间段」后填开始/结束时间再点查询），右侧实时显示当前表格实际覆盖的数据范围。
2. 淬火线油烟治理系统 · 实时检测数据：序号、设备名称、压差1、压差2、采集时间、设备状态、操作（详情弹窗显示原始字段）。工具栏的「时间范围」可切换最近数据 / 近 1 / 3 / 7 / 30 天 / 自定义时间段，右侧显示当前表格实际覆盖的数据范围；选定时间范围后返回该范围内全部数据点（上限 2000 条），此时右上角「实时条数」自动置灰。
3. 废气处理温湿度传感器 · 实时数据：序号、设备名称、温度、湿度、采集时间、操作（详情弹窗显示原始字段）。筛选方式与上一张表相同，两张实时表共用同一个时间范围。

页面每 10 秒自动刷新一次，右上角可切换实时数据的显示条数（1 / 10 / 20 / 50 / 100 / 500，即每张实时表最多显示多少条）并手动刷新。想看某个时间段的完整数据，直接在「时间范围」里选预设区间或自定义时间段即可。

## 开关机判定规则

| 数据情况 | 判定 |
| --- | --- |
| `status = True` | 开机 |
| `status = False` 或 `status = 5` | 设备上报关机，优先于数据中断判定 |
| 相邻数据点间隔超过 `iot.gap-minutes`（默认 20 分钟） | 关机（设备上报关机优先；否则按数据中断处理：数采模块与设备共用电源，设备关机后数采一起断电，关机期间没有数据） |

关机时间取中断前最后一个数据点的时间，开机时间取恢复后的第一个数据点时间，采样间隔约 5 分钟，因此时间精度为 ±5 分钟。设备主动上报关机时以该上报点为准。运行时间不足 1 秒（页面显示成 0天0小时0分0秒）的记录属于无效数据，已在推导阶段直接丢弃，不计入表格。

## 接口

```
GET /api/overview?limit=10&days=0&start=2026-10-01T00:00&end=2026-10-07T23:59&dataStart=2026-10-06T00:00&dataEnd=2026-10-06T23:59&refresh=false
```

返回开关机记录、油烟治理实时数据、温湿度实时数据三部分。

开关机记录：`days` 控制展示范围（0 = 全部历史）；`start` / `end` 为自定义时间段（北京时间，形如 `2026-10-01T00:00`），只要传了其中一个就以它为准、忽略 `days`，筛选规则是「与时间段有重叠的开关机记录」。

两张实时表：`dataDays` 控制展示最近多少天（不传或 0 = 默认最近 30 天）；`dataStart` / `dataEnd` 为自定义时间段，传了就以它为准、忽略 `dataDays`；两张表共用同一个时间范围。只要指定了时间范围（`dataDays` 或 `dataStart` / `dataEnd`），`limit` 即失效，返回该范围内全部数据点，上限 2000 条。

`limit` 控制每张实时表最多返回多少条（上限 2000），仅在未指定时间范围时生效。`refresh=true` 强制刷新开关机缓存（默认缓存 120 秒，因为开关机推导需要拉取全量 status 历史）。

## 配置项（application.yml）

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `iot.base-url` | https://iot.cme-im.com | ThingsBoard 地址 |
| `iot.username` / `iot.password` | 无，需本地提供 | 取自项目根目录的 `application-local.yml` 或环境变量 `IOT_USERNAME` / `IOT_PASSWORD`，账号密码不入库 |
| `iot.power-history-start` | 2025-07-09T00:00:00+08:00 | 开关机推导的数据起点 |
| `iot.power-display-days` | 0 | 开关机表默认展示最近多少天，0 表示全部历史（页面上的「历史范围」可临时覆盖） |
| `iot.gap-minutes` | 20 | 超过该分钟数的数据中断视为一次关机 |
| `iot.power-cache-seconds` | 120 | 开关机结果缓存时长 |
| `iot.query-limit` | 200000 | 单次遥测查询的数据点上限 |

## 自检模式

不启动 Web 容器，直接把页面使用的数据打印到控制台，便于排查取数问题：

```
java -jar target/hse-iot-demo-0.0.1-SNAPSHOT.jar --spring.profiles.active=dump --spring.main.web-application-type=none --dump.days=0 --dump.limit=5 --dump.start=2026-10-01T00:00 --dump.end=2026-10-07T23:59 --dump.dataStart=2026-10-06T00:00 --dump.dataEnd=2026-10-06T23:59 --dump.out=dump.json
```

## 实现说明

- 后端用 `X-Authorization: Bearer <jwt>` 调用 ThingsBoard，账号登录后缓存 token，按 JWT 的 `exp` 自动续期，遇到 401/403 会重新登录并重试一次。
- `ThingsBoardClient` 显式使用 `SimpleClientHttpRequestFactory`（HttpURLConnection）。部分受限环境里 JDK 自带 HttpClient / 内嵌 Tomcat 初始化回环管道会失败，改用该实现可避免这一问题。
- 两张实时表的「设备名称」显示 ThingsBoard 里的设备中文标签（`label`）：淬火线油烟治理系统 / 废气处理温湿度传感器，不使用设备编号（`CZDG-985-42` / `CZDG-TH`）；原始编号仍可在详情弹窗的 `code` 字段里查看。
- 压差1 = 负压2号机 − 负压1号机，压差2 = 负压3号机 − 负压2号机，保留两位小数；负压4号机为异常值（约 -989），不参与计算，仅在详情弹窗原样展示。
- 时间统一按北京时间（Asia/Shanghai）展示。
