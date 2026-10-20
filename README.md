# 流量消耗器 · Android

参考 [uu6/llxhq](https://github.com/uu6/llxhq/)（网页版流量消耗器）重写的原生 Android 版本。
纯 Android Framework 实现，**不依赖 AndroidX / Material**，APK 仅约 190 KB，无任何危险权限。

- 包名：`cn.llxhq.consumer`
- 版本：1.0.0（versionCode 1）
- minSdk 24（Android 7.0）/ targetSdk 34（Android 14）
- 产物：`build-output/llxhq-traffic-consumer.apk`

---

## 功能

### 1. 下载链接管理（自动记忆）
- 内置 **56 个节点**，按分组展示（移植自网页版节点表）：
  通用大文件（5）、中国移动（6）、中国电信（4）、中国联通（2）、百度（2）、阿里系（4）、腾讯（2）、
  京东（1）、网易（5）、蓝绿大厂（3）、字节跳动（5）、哔哩哔哩（1）、华为（1）、锤子（1）、
  拼多多（2）、国内其他大厂（6）、海外优选（6）。
- 链接页顶部有**搜索框**（按名称 / 域名 / 分组过滤，160ms 防抖），50+ 节点不用一路滑。
- 支持**自定义添加**任意 http(s) 直链，保存到 `SharedPreferences`，重启后仍在。
- 选中的链接自动保存，下次打开直接沿用上次选择；进入链接页会自动滚到当前选中项。
- 自定义项可删除；删除当前选中项时自动回落到第一个内置节点。

### 2. 流量消耗
- 多线程并发拉取直链并丢弃数据，线程数 **10 档可选（持久化）**：
  `1 / 2 / 4 / 8 / 16` / `32 / 64 / 128 / 256 / 512`，分两行排列。
- 可设置目标流量（MB，0 为不限），达到目标自动停止并提示。
- 实时显示：本次消耗、实时速度、运行时长、请求次数、目标完成百分比、**累计消耗**。
- 消耗中保持屏幕常亮（避免息屏被系统挂起网络），退出页面即停止。

#### 累计消耗（自安装以来）
- 独立于「本次消耗」的长期计数器，持久化在 SharedPreferences，重启 App / 换节点 / 重装前都不会丢。
- 增量每 3 s 落盘一次（`onPause`、`onDestroy`、停止时也会立即落盘），避免每秒写磁盘；
  代价是进程被强杀时最多丢失最后约 3 s 的计数。
- 点击「累计消耗」那一行可以查看并重置（重置只清零累计，不影响本次会话）。

#### 高并发防护
512 线程在部分机型上会被系统拒绝，因此做了三层兜底：

1. 线程数 ≥ 256 时单线程读缓冲降到 8 KB（≥ 64 时 16 KB），512 × 8 KB ≈ 4 MB，不会撑爆堆；
2. 线程数 ≥ 32 时线程栈压到 512 KB，降低虚拟内存与内核栈压力；
3. 创建线程失败（`pthread_create` / OOM）时立即停止创建，用已成功的线程继续跑；
   若一个都没创建成功，则回滚到停止状态并提示「系统拒绝创建线程，请降低并发数」。

### 3. 流量统计口径（准确性）
网页版按「文件大小 × 循环次数」估算，遇到限速、断流、重连就会失真。本版改为**实测字节数**：

1. **正文**：`InputStream.read()` 返回的每一个字节都累加，无论是完整下完还是中途停止；
2. **报文头**：每个请求的响应头（字段名 + 值 + CRLF）按实际长度累加，另加状态行开销；
3. 每个线程独立累加，`synchronized` 保证计数不丢；
4. 首页再用系统 `TrafficStats.getUidRxBytes/getUidTxBytes` 做**交叉校验**，实时显示「校核偏差」，正常应在 ±2% 以内（Ping 与 DNS 流量也算在系统侧，所以通常略偏负）。

### 4. Ping 模块
- 默认目标 `qq.com`，内置 `baidu.com`、`taobao.com`、`aliyun.com`、`114.114.114.114`、`github.com`。
- 首页卡片**每秒刷新一次**延迟，用绿 / 蓝 / 红三色提示：
  | 延迟 | 颜色 | 文案 |
  | --- | --- | --- |
  | ≤ 60 ms | 绿 `#10B469` | 流畅 |
  | 61–150 ms | 蓝 `#2F6BFF` | 一般 |
  | > 150 ms 或超时 | 红 `#F04438` | 较差 / 超时 |
- 首页同时显示最低 / 平均 / 最高，以及最近 30 次采样的迷你趋势柱。
- 独立页面：连续探测、逐条记录、丢包率、最小 / 平均 / 最大，可自定义目标并自动记忆。

> 说明：普通 App 无法使用 ICMP（raw socket 需要 root），因此延迟采用**TCP 握手耗时**测量，依次尝试 443 / 80 / 53 端口，误差通常在 1–3 ms，且能反映真实链路 RTT。

### 5. 深色模式
- 跟随系统（默认）/ 浅色 / 深色三档，右上角月亮图标切换，选择持久化。
- 通过 `values-night` / `values` 两套资源实现，非滤镜式反色。
- 强制档位的实现方式：所有页面继承 `ThemedActivity`，在 `attachBaseContext()` 里用
  `createConfigurationContext()` 生成一个 `uiMode` 被改写的 Context，
  这样 `getResources()` / `getTheme()` 从一开始就命中正确的 `-night` 限定符。

> 踩坑记录：最初用的是 `Activity.applyOverrideConfiguration()`（在 `onCreate` 里、`super.onCreate()` 之前调用）。
> 该 API 要求「此前从未访问过 resources」，而取基准配置必须先 `getResources().getConfiguration()`，
> 于是必然抛 `IllegalStateException: getResources() or getAssets() has already been called`，
> 异常被 catch 吞掉后表现为**深色系统下手动切浅色无效**。改用 `attachBaseContext` + `createConfigurationContext` 后修复。

---

## 界面预览

`design/preview/ui-preview.png`（首页，浅色 / 深色并排，按真实 dp 参数绘制的设计稿）
`design/preview/ui-links-preview.png`（链接页，分组标题 + 搜索框）
`design/ic_launcher.svg` / `design/ic_launcher_round.svg`（矢量图标源文件）

---

## 构建

### 方式一：脚本（无需 Gradle）

```bash
./build-apk.sh
# 产物：build-output/llxhq-traffic-consumer.apk
```

脚本不做依赖下载，需要本机已有 `jdk-17` 与 `Android SDK`（`platforms;android-34` + `build-tools;34.0.0`）：

```bash
# 目录约定：<BUILD_DIR>/jdk-*/ 与 <BUILD_DIR>/sdk/
BUILD_DIR=/path/to/dir ./build-apk.sh
# 或分别指定
JDK_DIR=/path/to/jdk17 SDK_DIR=/path/to/sdk ./build-apk.sh
```

没有现成 SDK 时，**直接用方式二（Android Studio）最省事**。

脚本流程：`aapt2 compile → aapt2 link → javac → d8 → 合并 classes.dex → zipalign → apksigner`。
签名密钥首次运行自动生成于 `keystore/llxhq.jks`（口令见 `build-apk.sh`，仅供自用分发，上架请自行更换）。
`keystore/` 已被 `.gitignore` 排除——**换机器重新构建会生成新密钥，覆盖安装前需先卸载旧版**。

### 方式二：Android Studio
直接打开本目录（已含 `settings.gradle.kts` / `app/build.gradle.kts`，AGP 8.5.2），
`local.properties` 中的 `sdk.dir` 改成你自己的 SDK 路径即可。

---

## 项目结构

```
app/src/main/
├── AndroidManifest.xml
├── java/cn/llxhq/consumer/
│   ├── MainActivity.java        首页：延迟仪表 + 流量统计 + 控制区
│   ├── LinksActivity.java       下载链接列表 / 增删 / 选择
│   ├── PingActivity.java        Ping 模块
│   ├── ThemedActivity.java      页面基类：attachBaseContext 套用深浅色
│   ├── TrafficEngine.java       流量消耗引擎（字节精确计数）
│   ├── LatencyMonitor.java      每秒延迟采样
│   ├── Net.java                 TCP 探测 / DNS 解析
│   ├── LinkStore.java           节点数据 + 自定义链接持久化
│   ├── Prefs.java               SharedPreferences 封装
│   ├── ThemeHelper.java         深浅色强制应用
│   ├── LatencyGauge.java        自绘仪表盘
│   ├── LatencyTrendView.java    自绘趋势柱
│   └── Utils.java               单位格式化 / 色彩分级
└── res/                         布局、颜色（含 values-night）、图标
```

---

## 注意事项

- 消耗流量时请保持本页面在前台；按返回键退出会立即停止（这是刻意的，避免后台偷跑流量）。
- 内置直链来自公开镜像站与各厂商 CDN，部分运营商节点采集自 2023–2024 年，可能随时间失效；
  遇到 404 / 超时换一个节点即可，也可在「选择 / 管理」里自行替换成自己的链接。
- 定向流量（免流卡）是否被计入取决于运营商对该域名的判定，与本工具统计口径无关。
- 仅在 Wi-Fi / 移动网络下都会消耗，请确认套餐后再启动大流量任务。
