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

## 接手建议

1. 先读 `app/src/main/kotlin/app/permissionsentinel/ui/`，不要先改视觉层。
2. 修改导航时保持键盘处理、玻璃底栏和震动逻辑的边界。
3. 修改总览指标时继续区分：UsageStats 前台会话、应用声明数、权限授予数。
4. 重新构建后必须重新安装并更新 APK 大小和 SHA-256。
5. 涉及震动的结论只能以 `app.permissionsentinel` 的 `dumpsys vibrator_manager` 记录为证据。