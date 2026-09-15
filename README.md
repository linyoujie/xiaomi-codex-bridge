<p align="center"><img src="assets/logo/codex-firefly-app-icon-rounded.png" width="180" alt="Xiaomi Codex Bridge particle firefly mascot"></p>

![CODEX particle bridge](assets/logo/codex-particle-github-header.png)

# Xiaomi Codex 桥接器

把小米智能音箱 LX04 的 800×480 屏幕变成一个静音的 Codex 实时状态台。Mac 端桥接器读取 Codex Desktop 状态，通过 USB ADB 将经过清理的任务状态发送到音箱；Android 端以 GPU 粒子动画显示任务、时钟和通勤信息。

> 这是社区项目，并非 Xiaomi 或 OpenAI 官方产品。目前针对 Xiaomi LX04（Android 8.1、800×480）设计和测试。

## 支持功能

### Codex 状态显示

- 自动识别 `工作中`、`等待确认`、`已完成`、`失败` 和 `空闲` 五种状态。
- 优先读取 Codex Desktop 本地 IPC；不可用时自动回退到 session JSONL。
- 显示任务名称、执行阶段、耗时和活跃任务数量；右下角以“周一 · 5天后 reset”、“剩下 xx%”和进度条显示每周额度及下次刷新时间。
- 多任务按“等待确认 → 工作中 → 失败 → 最近完成 → 空闲”仲裁；汇总 IPC 与近期 session 的全部进行中任务，显示总数，并每 6 秒翻页。
- ImageGen 等产生超大 JSONL 行的任务也能通过内存映射识别，不需要完整载入文件。
- 标题最多两行；桥接器不会向音箱发送提示词全文、工具参数或本地文件路径。

### 粒子视觉与完成动画

- 黑曜背景与青紫粒子波流，针对 PowerVR GE8300 和 1 GB RAM 优化。
- OpenGL ES 2.0、固定刷新节奏、VBO 顶点缓存和自动粒子预算降级。
- 粒子离屏后由 GPU 直接裁剪，不在 Java 层持续追踪单个粒子。
- 状态配色：蓝青工作、琥珀等待、绿色完成、红色失败、灰青空闲。
- 完成动画：粒子线聚合到爆点，然后进行多轮烟花扩散；完成页不长期停留，约 8 秒后进入待机。
- 全程禁止频闪，适合办公室静音使用。

### 时钟、待机与通勤

- 右上角显示 24 小时时间及 `M月d日 星期X`。
- 时间由 Mac 同步，不依赖音箱可能不准确的系统时钟。
- USB 断开或状态过期时放大显示时间与日期，并保留低调离线指示。
- 左右滑动可在 Codex 状态页和“回家”页之间切换；开始工作、等待确认或失败时自动切到第 1 页，任务完成后自动切到回家页。第 1 页保留已完成任务，滑回时重新播放烟花。
- 回家页显示当前出发所需分钟数和预计到家时间，不是距离固定下班时间的倒计时。
- 在 Mac 桥接器中选择公共交通、自驾或步行；音箱只显示选中的一种方式。
- 通勤查询在后台运行，失败时使用短期缓存且不会阻塞 Codex 状态同步。

### Xiaomi桥接器 macOS App

- 原生 macOS 外壳，所有设置都在 App 内完成，不会另外打开设置网页。
- 可配置设备序列号、通勤起终点、出行方式、刷新间隔、ADB 和 Chrome 路径。
- 内置“测试连接”“安装显示 APK”和“保存并重启”。
- Chrome 无头渲染集成在桥接器内，不打开可见的地图窗口。
- 关闭窗口默认隐藏到后台；点击 Dock 图标可重新打开。只有菜单中的“退出 Xiaomi桥接器”才会停止服务。
- 支持登录后自动启动、USB 重连、全量状态重同步和音箱 Launcher 抢占后的自动恢复。

### 隐私与静音

- Android 清单不申请麦克风、音频、定位或网络权限；唯一权限是开机恢复。
- 不播放声音、不修改系统音量。
- 音箱仅通过 USB ADB 接收清理后的状态、时间和通勤结果。
- 地址只保存在 Mac 的本地配置中。仓库不包含设备序列号、住址、日志或用户配置。
- 通勤功能会由 Mac 请求 Google Maps 公共网页；如不填写起终点，则保持关闭。

## 系统要求

- macOS，以及已安装的 Python 3（桥接器仅使用标准库）。
- Android Platform Tools（`adb`）。
- 构建 APK 时需要 Android SDK Build Tools、Android Platform 35 和 JDK 8+。
- 通勤查询需要 Google Chrome；不使用通勤功能时可不配置地址。
- 已开启 ADB 调试并可通过 USB 连接的 Xiaomi LX04。

## 快速开始

### 1. 构建 Android APK

```sh
./build.sh
```

产物为 `build/codex-status.apk`。默认使用本地 debug keystore 签名，适合侧载测试。

### 2. 构建 Xiaomi桥接器.app

```sh
./mac-app/build-app.sh
```

产物：

- `build/Xiaomi桥接器.app`
- `build/Xiaomi桥接器.zip`

打开 App，填写设备序列号后点击“测试连接”，再点击“安装显示 APK”。按需填写通勤起终点，并选择公共交通、自驾或步行。

本地设置保存在：

```text
~/Library/Application Support/XiaomiBridge/config.json
```

该文件不会被打包或提交到 Git。

### 3. 只运行命令行桥接器（可选）

```sh
LX04_SERIAL="你的设备序列号" ./mac/build-bridge.sh
```

也可以安装独立 LaunchAgent：

```sh
./mac/install-launch-agent.sh
```

卸载：`./mac/uninstall-launch-agent.sh`。

## 手动发送状态

```sh
LX04_SERIAL="你的设备序列号" ./status.sh working "Implementing feature"
LX04_SERIAL="你的设备序列号" ./status.sh waiting "Needs approval"
LX04_SERIAL="你的设备序列号" ./status.sh completed "Task complete"
LX04_SERIAL="你的设备序列号" ./status.sh failed "Build failed"
LX04_SERIAL="你的设备序列号" ./status.sh idle "Ready"
```

广播接口为 `com.codex.status.UPDATE`，支持：`state`、`title`、`phase`、`started_at_ms`、`active_count`、`quota_5h_percent`、`quota_7d_percent`、`host_time_ms`、`connected` 及通勤字段。

## 测试

```sh
python3 -m unittest discover -s tests -v
```

APK 构建完成后可检查权限：

```sh
aapt dump permissions build/codex-status.apk
```

## 已知限制

- Codex Desktop IPC 属于非官方内部接口，未来版本可能变化；项目已保留 JSONL 回退解析层。
- 通勤数据来自 Google Maps 公共页面而非正式 API，页面结构变化可能导致暂时无法解析。
- 当前 UI 按 LX04 的 800×480 横屏调优；其他 Android 屏幕可能需要调整布局。
- macOS App 使用 ad-hoc 签名，没有 Apple Developer ID 公证；首次打开可能需要在系统隐私与安全设置中允许。

## 品牌资产

官方图标为 C1“萤火虫·左下”：紫色萤火虫、青色状态腹部和黑曜蓝背景。其他探索候选保存在 `assets/ip-candidates/`。
