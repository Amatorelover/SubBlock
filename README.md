# 遮幕 · SubBlock

> 看双语视频时，盖住另一种语言的字幕条，只留你想看的那一行。

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-3DDC84.svg)](#)
[![minSdk](https://img.shields.io/badge/minSdk-26-orange.svg)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF.svg)](#)

**简体中文** | [English](#english)

---

## 这是什么

一款 Android 悬浮遮挡工具。它在屏幕上盖一层可拖动、可缩放、可调透明度的遮挡层，
用来在看双语视频时盖住不想看的那一种语言的字幕，只留下想看的那一行。

它**没有**网络权限——整个应用不联网、不采集数据、没有广告。

## 界面预览

> 截图待补充。若你愿意帮忙，可把截图放进 `docs/screenshots/` 并提交 PR。

## 功能

| 能力 | 说明 |
|---|---|
| **多块遮挡** | 同时存在多块遮挡区域，例如底部字幕与顶部弹幕一起盖 |
| **3 种遮挡样式** | 实心色块、毛玻璃模糊、渐变羽化。样式只影响观感，位置与数据完全通用 |
| **毛玻璃模糊** | Android 12+ 可用模糊模式，比一块死黑体面得多；低版本自动回退为半透明色块 |
| **自选颜色** | 内置色板之外，可用 HSV 取色器任意挑色，或直接粘贴十六进制色号 |
| **中英双语** | 默认英文 + 简体中文，应用内一键切换，无需重启（会立即重建界面） |
| **实时预览** | 编辑面板内模拟一块屏幕与假字幕，调颜色透明度不必来回切屏幕；预览与真实悬浮窗**共用同一份绘制代码** |
| **6 套预设模板** | 抖音底部、B站弹幕、追剧双挡等，点一下自动摆好位置 |
| **下拉磁贴** | 控制中心一键开关，不必打开应用 |
| **配置导入 / 导出** | 全部遮挡区域导出成一个 JSON 文件，换手机、重装或误删后一键恢复。用系统文件选择器读写，**不申请任何存储权限**，文件存到哪里由你决定 |
| **比例记忆** | 位置尺寸按屏幕比例保存，换设备、转横竖屏都能还原 |
| **边缘吸附与锁定** | 拖动时有对齐辅助，双击锁定防止手滑误触 |
| **动态取色** | Material 3，界面主色跟随你的壁纸 |

## 下载安装

从 [Releases](../../releases) 下载最新 APK，在手机上点击安装。

安装后**必须做两件事**：

1. **授予悬浮窗权限** —— 应用内会有红色卡片引导。这是系统级特殊权限，只能手动开启，代码无法代劳。
2. **允许通知** —— 那是前台服务的"身份证"，拒绝的话遮挡会在后台被系统回收。

建议再做一件：把「遮幕」加入手机管家的**自启动白名单**，否则重启后需要手动开一次。

> 因为使用了自己生成的签名证书、且未上架任何应用商店，安装时系统可能提示
> "未知来源应用"。这是正常现象，与"不联网、不采集数据"的设计并不冲突。

## 使用手势

| 操作 | 效果 |
|---|---|
| 按住拖动 | 移动遮挡块 |
| 拖动右下角圆点 | 缩放遮挡块 |
| 双击 | 锁定 / 解锁（锁定后不可拖动，防误触） |
| 长按 | 跳回应用内编辑这一块 |

## 从源码构建

**环境要求**：JDK 17、Android SDK（platform 35 与 build-tools 35.0.0）。

```bash
git clone https://github.com/Amatorelover/SubBlock.git
cd SubBlock

# 1. 指定 Android SDK 位置
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# 2. 构建（会自动下载 Gradle 8.9 与依赖）
./gradlew assembleRelease        # Windows: gradlew.bat assembleRelease
```

产物位于 `app/build/outputs/apk/release/app-release.apk`。

仓库根目录另有一个 `build.sh` 便捷脚本，会自动探测 JDK 与 SDK 位置（也可用
`JAVA_HOME` / `ANDROID_SDK_ROOT` 环境变量覆盖）：

```bash
bash build.sh          # Release 包
bash build.sh debug    # Debug 包
```

### 关于签名

Release 包需要签名。请把 `keystore.properties.sample` 复制为 `keystore.properties`
并填入你自己的密钥信息（该文件已被 `.gitignore` 排除，不会进入版本库）：

```properties
storeFile=keystore/subblock.jks
storePassword=你的密钥库口令
keyAlias=subblock
keyPassword=你的密钥口令
```

生成密钥库：

```bash
keytool -genkeypair -v -keystore app/keystore/subblock.jks \
  -alias subblock -keyalg RSA -keysize 2048 -validity 10000
```

> 未配置 `keystore.properties` 时项目仍可正常构建，只是 Release 包不带签名。
> **请务必自行备份密钥库**：一旦丢失，你将无法为已发布版本提供可覆盖安装的更新。

<details>
<summary>关于 Gradle 下载源（中国大陆 / 海外）</summary>

`gradle/wrapper/gradle-wrapper.properties` 中的 `distributionUrl` **默认指向腾讯云镜像**，
国内网络可开箱即用。

如果你在海外，或希望使用官方源，把该行替换为：

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
```

> 注意：官方源会 307 重定向到 GitHub Releases。若你的网络访问 GitHub 不稳定，
> 使用镜像会更可靠。

依赖仓库（`settings.gradle.kts`）默认使用 `google()` 与 `mavenCentral()`。
若下载依赖缓慢，可加入阿里云镜像：

```kotlin
maven { url = uri("https://maven.aliyun.com/repository/public") }
maven { url = uri("https://maven.aliyun.com/repository/google") }
```

</details>

## 项目结构

```
app/src/main/java/com/yanhu/subblock/
├── MainActivity.kt              应用入口，负责权限引导、页面切换与语言注入
├── AppLocale.kt                 语言选择：在 attachBaseContext 里注入语言
├── BootReceiver.kt              开机自启后恢复遮挡状态
├── data/
│   ├── BlockConfig.kt           单个遮挡块的数据模型（位置尺寸存 0~1 比例）
│   ├── SettingsStore.kt         DataStore 仓库 —— 全应用唯一的数据源头
│   ├── ConfigIO.kt              配置导入 / 导出：序列化、格式识别与边界收敛
│   └── Presets.kt               预设模板（只存资源 id，按当前语言生成）
├── overlay/
│   ├── OverlayService.kt        前台服务：把数据渲染成屏幕上的悬浮窗
│   ├── BlockRenderer.kt         三种遮挡样式的绘制（悬浮窗与预览共用同一份）
│   ├── BlockView.kt             一块遮挡区域的自绘 View 与手势逻辑
│   └── OverlayStatus.kt         服务运行状态与错误原因的上报通道
├── tile/BlockTileService.kt     控制中心磁贴
└── ui/
    ├── HomeScreen.kt            首页：总开关 + 遮挡块列表 + 语言 + 备份
    ├── BlockEditorSheet.kt      遮挡块编辑面板（含实时预览）
    ├── ColorPickerDialog.kt     自绘 HSV 取色器（零依赖）
    ├── PresetSheet.kt           模板选择面板
    ├── AboutScreen.kt           关于页：版本、作者声明、隐私承诺、源码链接
    ├── UiStrings.kt             数据层"类型"到界面文字的翻译层
    └── theme/Theme.kt           Material 3 主题与动态取色

app/src/main/res/
├── values/strings.xml           默认语言：英文
└── values-zh/strings.xml        简体中文
```

## 架构：只有一个数据源头

这个项目最重要的一个设计决定是：

> **界面不指挥悬浮窗。界面只修改数据，悬浮窗自己订阅数据变化并重绘。**

数据全部放在 DataStore 中，`OverlayService` 用 `combine(enabled, blocks)` 订阅它。
好处是显而易见的：

- 不需要写任何 Activity ↔ Service 互相通知的胶水代码
- 不可能出现"界面显示的状态"与"屏幕上真实的样子"不一致
- 拖动悬浮窗后写回数据，界面上的数值也会自动跟着更新

## 隐私

- **不申请联网权限。** 可自行核对 `app/src/main/AndroidManifest.xml`：其中没有 `INTERNET`
- **不收集任何数据。** 没有统计、没有埋点、没有广告、没有账号
- **所有配置只保存在你自己的手机上**（应用私有目录内的 DataStore）

## 常见问题

**为什么必须手动授予悬浮窗权限？**
遮挡层的本质是一个系统级悬浮窗。这个权限属于 Android 的"特殊权限"，系统设计上不允许
应用自行申请，只能由用户在设置中确认——任何同类应用都无法绕过。

**为什么我的毛玻璃没有效果？**
毛玻璃（blur-behind）需要 Android 12 及以上，且部分厂商 ROM 会关闭该能力。此时应用会
自动回退为半透明色块，遮挡功能不受影响。

**重启手机后遮挡块消失了？**
把「遮幕」加入手机管家的自启动白名单。部分 ROM 需要额外允许"后台弹出界面"权限。

**为什么不上架应用商店？**
个人项目，且签名证书自持。你可以直接使用 Releases 中的 APK，或自行编译。

## 参与贡献

欢迎提交 Issue 与 Pull Request。报告问题时，如果能附上「关于」页中的版本信息
（应用版本号 + Android 版本），定位会快很多。

## 赞助支持

如果这个小工具帮到了你，欢迎请我喝杯咖啡 ☕ —— 你的支持是它继续维护下去的动力。

- 国内：[爱发电](https://afdian.com/a/lihoo1998)

> 本项目完全免费开源，赞助纯属自愿，不构成任何功能承诺或服务义务。

## 路线图

- [x] v1.0 — 多块遮挡、毛玻璃、预设模板、磁贴
- [x] v1.1 — 关于页与作者声明、开源配套、Gradle Wrapper
- [x] v1.2 — 配置导入 / 导出（换机不丢配置）
- [x] v1.3 — 5 种遮挡样式、自选颜色、中英双语
- [x] v1.4 — 样式收敛到 3 种；修复预设与通知栏不跟随语言；几何数据统一收束
      （遮挡块不再能被拖出屏幕）；修复错误提示不可见；复用绘制对象
- [ ] v1.5 — 单元测试与 GitHub Actions 持续集成
- [ ] v1.5 — 按应用自动生效（只在指定 App 内显示遮挡）
- [ ] 长期 — 争取收录进 F-Droid

## 开源协议

[MIT License](LICENSE) © 2026 Lihoo

作者声明详见 [AUTHOR.md](AUTHOR.md)。

---

<a id="english"></a>

# SubBlock

> When watching bilingual video, cover the other language's subtitle line and keep only the one you want.

An Android overlay utility that draws a draggable, resizable, semi-transparent curtain
over the subtitle language you don't want when watching bilingual video, leaving only the line you want to read. Built with Kotlin and Jetpack Compose.

**It requests no network permission. No tracking, no ads, no accounts.**

## Features

- **Multiple overlay blocks** — cover bottom subtitles and top danmaku at the same time
- **Three mask styles** — solid color, blur behind and soft gradient
- **Blur mode** — frosted-glass effect on Android 12+; automatically falls back to a
  translucent solid color on older devices
- **Custom colors** — pick any color with the built-in HSV picker, or paste a hex code
- **Bilingual UI** — English and Simplified Chinese, switchable in-app and applied instantly
- **Live preview** — edit colors and opacity against a mock screen, no app switching.
  The preview and the real overlay **share the exact same drawing code**
- **Presets** — one tap to place overlays for common apps such as TikTok or Bilibili
- **Quick Settings tile** — toggle without opening the app
- **Config import / export** — export all overlays to a single JSON file and restore them
  on a new device. Uses the system file picker (SAF), so it needs **no storage permission**,
  and you decide where the file lives
- **Proportional geometry** — positions are stored as 0–1 ratios, so they survive
  resolution changes and rotation
- **Edge snapping, lock and double-tap-to-lock** — prevents accidental drags
- **Material 3 with dynamic color**

## Install

Download the latest APK from [Releases](../../releases). After installing you **must**:

1. Grant the **"Display over other apps"** permission (the app guides you to it).
   It is a special permission on Android and cannot be granted programmatically.
2. Allow **notifications**, which keep the foreground service alive.

Adding the app to your ROM's auto-start whitelist is strongly recommended.

## Build from source

Requires JDK 17 and Android SDK (platform 35, build-tools 35.0.0).

```bash
git clone https://github.com/Amatorelover/SubBlock.git
cd SubBlock
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew assembleRelease
```

For a signed release build, copy `keystore.properties.sample` to `keystore.properties`
and fill in your key details. The file is git-ignored; builds work without it (unsigned).

## Architecture

The core design decision:

> **The UI never commands the overlay. The UI only writes data, and the overlay
> subscribes to that data and redraws itself.**

All state lives in a single DataStore. `OverlayService` observes it via
`combine(enabled, blocks)`. This removes any Activity ↔ Service glue code and makes it
impossible for the on-screen result to drift out of sync with what the UI shows.

## Privacy

No `INTERNET` permission in the manifest, no analytics, no crash reporting, no ads.
All settings stay in the app's private storage on your device.

## Contributing

Issues and pull requests are welcome. When reporting a bug, please include the version
information shown in the in-app *About* screen.

## Sponsor

If this little tool helped you, consider buying me a coffee ☕. Your support keeps it maintained.

- China: [Aifadian (爱发电)](https://afdian.com/a/lihoo1998)

> SubBlock is free and open-source. Sponsorship is entirely voluntary and comes with no
> feature commitment or obligation.

## License

[MIT](LICENSE) © 2026 Lihoo — see [AUTHOR.md](AUTHOR.md) for the author's statement.
