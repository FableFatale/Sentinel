# Permission Sentinel（权限哨兵）— 实现报告

日期：2026-10-25　设备：OnePlus PJZ110（Android 17 / SDK 37，1440×3168）

## 交付物

| 文件 | 说明 |
|---|---|
| `outputs/permission-sentinel-release.apk` | Release（R8 混淆 + 资源收缩，2.4 MB），已装真机 |
| `outputs/permission-sentinel-debug.apk` | Debug（58 MB，含调试符号） |
| `outputs/screenshot-*.png` | 真机实拍：总览（中文/深色、英文/浅色）、时间线、应用、设置、热力图交互 |
| 项目根 | Gradle 工程（`./gradlew` 未提交，用系统 Gradle 8.14 + JDK 17 + AGP 8.7.3 + Kotlin 2.0.21 + Compose BOM 2024.12） |

## 架构

单模块 `:app`，无 Room、无导航库、无 Hilt；依赖仅 AndroidX（Compose M3、Lifecycle、DataStore、Splashscreen）+ kotlinx-coroutines + material-icons-extended。**无 INTERNET 权限**（清单未声明，本地优先是结构性的，不是策略性的）。

- `data/`：纯 Kotlin 逻辑（会话配对、热力图分桶、记录编解码、权限分类）+ 设备源（UsageEvents、PackageManager、CameraManager availability callback）+ DataStore 存储。
- `ui/`：Compose M3；四页（总览/时间线/应用/设置）+ 底部导航（仅实际切换时 `CONTEXT_CLICK` 轻震动）。
- `SentinelApplication` 持有进程级 `SentinelRepository`（相机监听全生命周期）。
- 主题：浅/深/跟随系统；语言：系统/English/简体中文（`LocaleManager`，minSdk 33 免 appcompat）；edge-to-edge + Scaffold 安全区域。

## 真机可行性验证（先于 UI，PRD 要求）

以原 APK（`app.permissionsentinel.audit`，Shizuku 路线）为参照，在本机实测：

| 信号 | 结论 | 证据（`app/build/outputs/androidTest-results/` 探针日志） |
|---|---|---|
| 使用情况访问（PACKAGE_USAGE_STATS） | ✅ 可用 | 本机 op 默认 allow；探针读取 24h 内 **3657 个会话**；UI 全部时间线/热力图数据来源于此 |
| 跨应用相机占用（实时） | ✅ 可用 | `CameraManager.AvailabilityCallback` 免权限注册成功（5 个摄像头）；端到端：打开系统相机 → 应用"记录 0→2"（cam_on/cam_off 各一条） |
| 跨应用相机/麦克风**历史** | ❌ 不可行 | 原 APK 依赖 Shizuku（未安装）+ `GET_APP_OPS_STATS`（ADB `appops set` 报 Unknown operation、`pm grant` 被 GRANT_RUNTIME_PERMISSIONS 拒绝、shell 已无 MANAGE_APP_OPS_MODES）；探针确认他包 op 历史被屏蔽 |
| 麦克风占用 | ❌ 不可观测 | 无公开跨应用 API；UI 不展示、不宣称 |

因此 UI 遵守诚实边界：权限覆盖显示"声明应用数"（149 个真实应用；本机 93 个可见授予标志），剪贴板/传感器标注"不可观测"，相机记录不归属具体应用（回调不提供包名，记录 actor 为 `(unknown)`）。

## 测试结果

- **单元测试 22/22 通过**：会话配对（跨天/切换/悬挂事件/零长会话）、热力图分桶（跨小时切分/分钟上进/星期索引）、记录编解码（往返/坏行跳过/5000 条裁剪）、权限分类（授予/掩码/多权限归并）。
- **真机探针 5/5 通过**（`connectedDebugAndroidTest`）：usageAccess、cameraAvailability、packagePermissionFlags、appOpsForeignRead、uidSelfCheck。
- **局部功能实测（真机手势驱动）**：四页导航、热力图点击查看时段明细（Wed 15:00–16:00 · 16 min：Threads/同花顺/sing-box）、日期切换、浅色/深色切换（状态栏图标联动）、中英切换（activity 重建）、相机检测记录、下拉刷新、清除记录对话框。

## 交互与动效（真机验证）

- **切换震动**：底栏切换用 `VibratorManager` + `VibrationEffect.createPredefined(EFFECT_TICK)`（需 `VIBRATE` 权限，普通级）。实测 `dumpsys vibrator_manager` 记录：`app.permissionsentinel … played: Prebaked=TICK`（62ms，TOUCH 用途），仅在 tab 实际变化时触发。此前的 `View.performHapticFeedback(CONTEXT_CLICK)` 在 ColorOS 上几乎无感，已替换。
- **透明玻璃底栏**：悬浮圆角胶囊，纯 Compose 实现（无三方库）：内容 Box 经 `drawWithContent` 录制进 `rememberGraphicsLayer()`，底栏以 `graphicsLayer { renderEffect = BlurEffect(14dp, 14dp, TileMode.Decal) }` + 半透明 frost + 发丝边框/顶部高光合成；列表内容从玻璃下滑过并实时模糊。深浅主题各自调 tint。
- **动效**：页面切换 `AnimatedContent` 方向性滑动+淡入淡出；底栏图标 spring 弹跳放大 + 选中药丸淡入 + 按压整列缩小；应用列表行按压缩放反馈；总览统计数字变化上滑动画。

## 已验证功能 / 限制

**已验证**：真实前台会话（近 28 天，本机事件日志全量可读）、7×24 交互热力图（真实分钟数着色）、应用清单与权限覆盖统计、相机实时检测与本地记录、双语双主题、安全区域自适应、玻璃底栏、切换震动、页面/按压/数字动效、启动动画（SplashScreen API）。

**限制**：① 跨应用麦克风/相机历史监控在本机不可行（无 root/Shizuku），故不提供该功能；② 相机记录仅在哨兵进程存活时累积（前台/后台均可，进程被杀则中断）；③ 少数无 launch intent 的系统组件显示原始包名；④ 相机记录不含归属应用（平台不提供）。

## 复现

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home gradle assembleDebug testDebugUnitTest connectedDebugAndroidTest
adb install -r app/build/outputs/apk/release/app-release.apk
```
