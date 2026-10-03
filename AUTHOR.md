# 作者声明 · Author's Statement

> 本文件是「遮幕 (SubBlock)」的作者声明，也是应用内「关于」页所展示内容的完整版。

## 一、这个项目为什么存在

「遮幕」诞生于一个很朴素的需求：看短视频和剧集时，画面上"烧死"的硬字幕会挡住内容本身，
而能找到的同类工具要么年久失修、在新系统上已经跑不起来，要么只允许一块固定位置的遮挡区域。

于是作者决定自己动手写一个。它不打算做成一个商业产品，只是想成为一个**自己愿意每天用**的小工具。

## 二、它做什么，不做什么

**它只做一件事**：在你指定的屏幕区域上，盖一层半透明色块或毛玻璃。

它**不做**这些事：

- 不读取、不分析、不识别屏幕内容（没有任何截屏或 OCR 逻辑）
- 不修改、不下载、不破解任何视频或受版权保护的内容
- 不申请联网权限（可核对 `AndroidManifest.xml`：其中没有 `INTERNET`）
- 不收集任何数据：没有统计、没有埋点、没有广告、没有账号体系
- 不做任何后台静默行为：所有功能都必须由你手动开启

## 三、免责声明

本项目仅供个人学习与自用。使用者应自行确保其使用方式符合当地法律法规以及相关服务的
使用条款。因使用本软件所产生的任何后果，由使用者自行承担。

本项目以 MIT 协议开源，作者不对其适用性、稳定性作任何明示或暗示的担保。

## 四、关于代码与开发方式

作者本人没有 Android 开发经验，本项目是在 AI 辅助下完成的：作者负责定义需求、判断体验
细节、逐项验收功能，AI 负责编写与重构代码。项目中的架构决策——例如"以 DataStore 作为
唯一数据源，界面只写数据、悬浮窗自己订阅"——都记录在源码注释与 README 中。

这里如实说明这一点，是因为：
**作品的价值在于它是否真的好用，而开发手段理应公开透明。**

## 五、贡献与反馈

欢迎通过 GitHub 提交 Issue 或 Pull Request。提出问题时，如果能附上「关于」页里的
"版本信息"（即应用版本号与 Android 版本），定位速度会快很多。

## 六、致谢

本项目站在这些开源项目的肩上：Kotlin 语言、Jetpack Compose 界面框架、AndroidX 与
Material Design 组件库。也感谢每一位提出建议、报告问题的人。

---

## English summary

SubBlock is a small Android utility that covers burned-in subtitles with a draggable
overlay. It was built by **Lihoo** with AI assistance, because the author has no prior
Android development experience but wanted a tool that actually works on modern Android.

**What it never does**: no screen reading or OCR, no network permission (there is no
`INTERNET` in the manifest), no analytics, no ads, no accounts. All settings stay on
your device.

**Disclaimer**: Provided for personal and educational use, under the MIT License and
without warranty of any kind. Users are responsible for ensuring their usage complies
with local laws and the terms of service of any third-party platform.

**Contributions** are welcome via GitHub Issues and Pull Requests. When reporting a bug,
please include the version information shown in the in-app *About* page.
