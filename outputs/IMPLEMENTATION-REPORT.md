# Permission Sentinel（权限哨兵）实现报告

日期：2026-10-26
项目：`E:\Sentinel`
真机：OnePlus 13（`2160d732`，Android 17 / API 37，1440×3168）

## 当前交付

| 文件 | 状态 |
|---|---|
| `outputs/permission-sentinel-final-debug.apk` | 最终验证版本，已安装到 OnePlus 13 |
| `app/build/outputs/apk/debug/app-debug.apk` | 同一最终 APK 的 Gradle 输出 |
| `outputs/permission-sentinel-release.apk` | 历史 Release，早于最终交互修复，不用于当前验收 |
| `artifacts/android-overview-dark-final.png` | 最终深色总览实拍 |
| `artifacts/android-timeline-filter-dark.png` | 时间线筛选菜单实拍 |

最终 APK：

- 大小：58,105,305 bytes
- SHA-256：`20DB22BD2B986300068133D297B3AA80339283C4BC300CD75A14E387889296E2`
- 包名：`app.permissionsentinel`
- 安装命令：`adb -s 2160d732 install -r outputs\permission-sentinel-final-debug.apk`
- 不得清除应用数据：`adb clear`

## 已实现的 UI

本版按用户反馈重构为统一的 Compose 移动端视觉语言：

- 底部四项导航：总览、时间线、应用、设置。
- 悬浮玻璃底栏：实时背景模糊、半透明 frost、发丝边框、统一图标/文字垂直居中。
- 页面切换：方向性平移、淡入淡出和按压缩放。
- 导航震动：仅在 Tab 实际切换时触发。
- 深色模式：系统状态栏、导航栏和应用表面颜色一致。
- 语言：英文 / 简体中文，可跟随系统。
- 主题：浅色 / 深色 / 跟随系统。

## 总览页

总览页已按最新反馈修正：

- 标题统一为「总览」。
- 刷新按钮显示准确更新时间。
- 「今天」指标明确来自 UsageStats 前台会话。
- 「敏感权限应用」明确统计已安装、可启动且声明敏感权限的应用。
- 热力图支持 7 / 14 / 30 天累计口径，格内显示真实分钟数，行标签带「时」，并显示高峰结论。
- 权限声明行显示应用数、占比、进度条和可进入应用列表的结构。
- 剪贴板和运动传感器说明明确：不可观测不代表没有访问。
- 不使用虚构趋势、伪造会话或无来源的实时数据。

## 时间线页

- 记录按时间降序排列，从今天开始。
- 日期筛选：今天、昨天、近 7 天。
- 搜索框与筛选菜单分离。
- 筛选菜单为锚定弹层，不会拉起输入法。
- 搜索时输入法出现会隐藏底部导航；输入法关闭后底栏恢复。
- Android Back 仅先关闭输入法，不会退出应用。

已验证路径：时间线 → 点击搜索 → 键盘出现 → `input keyevent 4` → 键盘关闭、页面仍在时间线、底部导航恢复。

## 真机验证结果

### 构建与测试

- `testDebugUnitTest`：26/26 通过
- `lintDebug`：通过
- `assembleDebug`：通过
- 最近完整构建：`BUILD SUCCESSFUL in 42s`

### 震动反馈

实现：

```kotlin
VibrationEffect.createWaveform(
    longArrayOf(0L, 45L),
    intArrayOf(0, 180),
    -1,
)
```

最终 APK 在 2026-10-26 17:25:58 产生真实应用级震动记录：

```text
10-26 17:25:58.198 | effect | finished
usage: TOUCH | app.permissionsentinel (uid=10524)
reason: From [S0, S45]
```

同一时间段没有 `ignored_unsupported`。系统自身的 TOUCH 记录没有作为应用震动证据。

### 时间线筛选

真机点击筛选按钮后：

- 弹出「全部应用」「仅敏感权限应用」菜单。
- 输入法未出现。
- 菜单位于内容区域上方，没有遮挡底部安全区。
- 关闭菜单后底部导航正常恢复。

## 数据与权限边界

当前功能只使用本地或系统可验证来源：

- UsageStats：前台使用会话。
- PackageManager：已安装应用和敏感权限声明。
- CameraManager.AvailabilityCallback：相机占用实时检测。
- 本地 DataStore：记录历史、主题、语言和保留设置。
- 应用未声明 `INTERNET` 权限。

当前限制：

- 标准模式下无法跨应用观测剪贴板访问历史。
- 标准模式下无法获得其他应用的运动传感器历史。
- 相机占用记录不包含触发应用包名，系统回调不提供该信息。
- 相机检测只在本应用进程存活期间累积。
- 尚未在更多 OEM、Android 版本或物理屏幕尺寸上完成独立验收。

## Git 状态

本项目当前存在大量未提交修改，包含 UI、主题、数据逻辑、测试、图标和本次报告。继续工作时不要回退这些文件。

## 五项修复（2026-10-26 第二批）

### 统计口径核查结论（先读代码定位，再修）

1. **单格/总量超"墙钟上限"的真正来源**：
   - **Activity 抖动碎片**：应用内 Activity 切换产生密集 RESUMED/PAUSED 对，pairSessions 把同包几分钟的使用拆成几十段秒级会话，每段在格子里独立向上取整成 ≥1 分钟 → 热力图总量放大约 10%（47,600 vs 43,200 吻合）、"今天 2,369 次"即碎片会话数。**修复**：`pairSessions` 合并同包间隔 ≤1 秒的相邻碎片（`mergeSamePackageFragments`），单测覆盖。
   - **分屏/悬浮窗并行**：多个应用同时前台、系统事件各自计时，总时长超墙钟是**系统口径的真实行为**（与系统"屏幕使用时间"一致），不是 bug。不做假性截断，改为在「今天」卡片的口径弹窗里明确解释。
   - 排除了取整算法问题：分桶即按小时切片，每格一次取整是格子语义的必然（旧实现与新实现数学等价，毫秒级重写保留以求清晰）。
2. **「今天」改口径**：从会话次数（2,369 次）改为**累计前台分钟**，support 文案改「前台会话」+ info 图标，点开弹窗解释口径（事件来源、分屏并行、每格取整一次）。
3. **热力图去格内数字**：数字只在点击详情条展示；选中格的勾图标在深色主题用深色（浅蓝底白勾对比度不足的问题一并修正）。

### 其余四项

- **底栏透出**：内容底部留白提到 100dp（64dp 栏高 + 12dp 下边距 + 余量），最后一项可完整滚出；frost 不透明度提到 88%（深）/90%（浅）；`RenderEffect` 仅 API 31+ 启用，否则回退实色栏；浅色模式边框改为深色发丝线（与深色一致可辨识）+ 保留阴影。
- **首屏重排**：敏感权限声明区块提前到统计卡之后；热力图移入折叠卡片（默认收起、`rememberSaveable`、高峰提示条常显）；「标准模式」状态行仅在缺授权时显示；更新时间挪到刷新按钮旁小字。
- **下拉菜单**：`tonalElevation=0` + 固定 shape/背景，两套主题样式统一。

## 授权引导拆分 + 本地会话归档（2026-10-26 新增）

### 授权引导：两个独立系统开关（已实现，真机待验）

设置 → 访问权限分区重构为 HAT 式的两张独立引导卡，不再有"总开关"概念：

- **卡 1「使用情况访问」**：状态、用途说明（时间线/热力图数据源）、三条厂商路径、直达按钮（`ACTION_USAGE_ACCESS_SETTINGS`）。
- **卡 2「权限观察（无障碍）」**：同结构，直达按钮（`ACTION_ACCESSIBILITY_SETTINGS`），标注"完全可选，默认关闭"。
- 厂商路径按 Pixel / 三星 / 一加分列，`Build.MANUFACTURER` 匹配当前厂商的行加粗高亮并带"本机"标记：
  - 使用情况访问：Pixel「设置 → 应用 → 特殊应用权限 → 使用情况访问」；三星「设置 → 安全和隐私 → 更多隐私设置 → 使用情况访问」；一加「设置 → 隐私 → 特殊应用权限 → 使用情况访问」
  - 权限观察：Pixel「设置 → 无障碍 → 已下载的应用 → 权限哨兵」；三星「设置 → 辅助功能 → 已安装的应用 → 权限哨兵」；一加「设置 → 更多设置 → 无障碍 → 已下载的应用 → 权限哨兵」
- 总览页横幅同样拆分：使用情况横幅与无障碍横幅各自独立出现，各自直达对应系统页；两个状态在从系统设置返回时（`LifecycleResumeEffect`）自动刷新。
- **待设备接回**：实测一加（PJZ110）两条直达 Intent 的落点，按实测修正一加路径文案（尤其无障碍在 ColorOS 的实际菜单位置）。

### 本地会话归档（已实现，单测通过；真机跨天探针待跑）

- `data/SessionArchive.kt`：按天文件归档（`filesDir/session_archive/sessions-YYYY-MM-DD.log`，行格式 `pkg|start|end`），纯文件 IO，零新依赖，不出设备。
- `merge`：按 (包名， 开始时间) 去重、进行中会话的结束时间持续更新；`read(from,to)` 读取窗口重叠的天文件（含前一天跨天会话）。
- `SentinelRepository.refresh` 现在的流程：系统事件查询 → 归档合并 → 归档读回窗口 → 合并去重（live 优先，保证进行中会话的 end 最新）后供热力图/时间线使用。**效果：系统事件日志只保留几天到几周，归档让 7/14/30 天热力图和时间线在系统窗口之外仍然有真实数据。**
- 单测 6 个新增（分天写入、去重与 end 更新、窗口过滤、跨天溢出、坏行跳过、oldestDay），全套 32/32 通过。
- 真机探针 `ArchiveProbeTest`：注入一条 20 天前的会话（远超系统保留期），断言 30 天窗口快照能从归档取回，随后清理探针数据。**待设备接回后执行。**

## 扩展隐私信号调研（2026-10-26 新增）

目标：在不 root、不 privileged 签名的前提下，评估哨兵还能观测哪些隐私信号。结论按证据强度分级。

### 已实现（真机探针待接回设备后执行）

| 信号 | API | 实现位置 | 归属应用 |
|---|---|---|---|
| 麦克风“使用中”（实时） | `AudioManager.registerAudioRecordingCallback` / `getActiveRecordingConfigurations()` | `data/MicWatch.kt` | ❌ 不可得：`getClientUid`/`getClientPackageName` 在 SDK 35 公开 API 中不存在（javap 实证），归属仅 shell 层可见 |
| 系统权限弹窗捕获 | 无障碍服务 `TYPE_WINDOW_STATE_CHANGED`，包名过滤 `*permissioncontroller*` | `watch/SentinelWatchService.kt` | ✅ 记录弹窗文本哈希（`RecordType.PERMISSION_DIALOG`），申请方可由前台归属推断 |
| 窗口切换（前台变更） | 同上服务（无事件过滤的窗口事件） | 同上 | ✅ 实时，早于 UsageStats 轮询 |
| 每应用网络流量 | `TrafficStats.getUidRx/TxBytes(uid)` 逐 UID | 探针 `MoreSignalsProbeTest` | ✅ 计数器公开可读（已入库为探针，UI 未展示） |

- 麦克风横幅（“麦克风使用中”）已加入总览页，与相机横幅同一样式；记录类型 `mic_on`/`mic_off`。
- 无障碍服务作用域严格限定：只处理 `permissioncontroller` 弹窗文本与窗口切换事件，声明 `canRetrieveWindowContent` 仅作为弹窗文本兜底，不读取、不存储其他应用屏幕内容；设置页新增“权限观察（无障碍）”状态行，跳转系统无障碍设置。
- 真机探针（`MoreSignalsProbeTest`）：`probe_micAttribution`（用 `screenrecord --audio-source mic` 制造跨进程录音，断言应用内可见录音配置）、`probe_accessibilityWatch`（连接 → 窗口事件 → 经测试 APK 的 `ProbeRequestActivity` 触发真实定位权限弹窗并断言捕获文本）、`probe_trafficPerUid`。**待设备接回后执行并回填证据。**

### 真机已验证（本机早期探针，仍然有效）

- 相机“使用中”（`CameraManager` 回调）：✅ 可检测，❌ 无归属。
- Shizuku / `GET_APP_OPS_STATS` 路线：❌ 本机全被拒（无 Shizuku、ADB 授权三处失败）——跨应用相机/麦克风**历史**仍然不可行。

### 确认不可行（普通应用，无需再试）

- 跨应用剪贴板、运动/身体传感器访问历史：无任何公开 API。
- 跨应用定位“使用中”：无公开 API（仅系统 UI 隐私指示器）。
- 权限弹窗以外的任意应用屏幕内容读取：拒绝实现——超出隐私透明工具的边界，属于监视。

### 备选（未实现，明确记录）

- `NotificationListenerService`：可读通知，对权限哨兵价值低，未实现。
- `dumpsys audio` 的录音归属：仅 shell/ADB 可得，可作 ADB 模式扩展（本版本未启用，与“标准模式”边界一致）。
- GrapheneOS 式系统级隐私面板：需要 privileged/系统签名，不可达。

## 接手建议

1. 先读 `app/src/main/kotlin/app/permissionsentinel/ui/`，不要先改视觉层。
2. 修改导航时保持键盘处理、玻璃底栏和震动逻辑的边界。
3. 修改总览指标时继续区分：UsageStats 前台会话、应用声明数、权限授予数。
4. 重新构建后必须重新安装并更新 APK 大小和 SHA-256。
5. 涉及震动的结论只能以 `app.permissionsentinel` 的 `dumpsys vibrator_manager` 记录为证据。
6. 新增探针执行方式：设备接回后先 `adb shell settings put secure enabled_accessibility_services app.permissionsentinel/app.permissionsentinel.watch.SentinelWatchService` 与 `settings put secure accessibility_enabled 1`，再跑 `connectedDebugAndroidTest`，结束后恢复原无障碍设置。