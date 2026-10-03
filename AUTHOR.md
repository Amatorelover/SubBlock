# 作者声明 · Author's Statement

> 本文件是「遮幕 (SubBlock)」的作者声明，也是应用内「关于」页所展示内容的完整版。

## 一、这个项目为什么存在

「遮幕」诞生于一个很朴素的需求：看双语视频时，你只想保留其中一种语言的字幕，于是便可以用遮挡块把另一种语言的字幕条盖住，只留下想看的那一行。

## 二、它做什么，不做什么

它只做一件事：在你指定的位置上盖一层遮挡。它不读取屏幕内容、不做文字识别、不修改也不下载任何视频。

具体来说，它**不做**这些事：

- 不申请联网权限（可核对 `AndroidManifest.xml`：其中没有 `INTERNET`）
- 不收集任何数据：没有统计、没有埋点、没有广告、没有账号体系
- 不做任何后台静默行为：所有功能都必须由你手动开启

## 三、免责声明

本项目以 MIT 协议开源，仅供个人学习与自用。使用者应自行确保使用方式符合当地法律法规及相关服务的使用条款，因使用本软件产生的后果由使用者自行承担。

## 四、关于代码与开发方式

作者本人没有 Android 开发经验，本项目是在 AI 辅助下完成的：作者负责定义需求、判断体验细节、逐项验收功能，AI 负责编写与重构代码。项目中的架构决策——例如"以 DataStore 作为唯一数据源，界面只写数据、悬浮窗自己订阅"——都记录在源码注释与 README 中。

这里如实说明这一点，是因为：
**作品的价值在于它是否真的好用，而开发手段理应公开透明。**

## 五、贡献与反馈

欢迎通过 GitHub 提交 Issue 或 Pull Request。提出问题时，如果能附上「关于」页里的"版本信息"（即应用版本号与 Android 版本），定位速度会快很多。

## 六、致谢

本项目站在这些开源项目的肩上：Kotlin 语言、Jetpack Compose 界面框架、AndroidX 与 Material Design 组件库。也感谢每一位提出建议、报告问题的人。

---

## English summary

SubBlock is a small Android utility: when watching a bilingual video, you only want to keep one language's subtitles, so it covers the other language's subtitle line with a draggable overlay. It was built by **Lihoo** with AI assistance.

**What it does**: draws a mask wherever you specify. It does not read your screen, perform text recognition, or modify or download any video.

**Disclaimer**: Provided under the MIT License for personal learning and personal use. Users are responsible for ensuring their usage complies with local laws and the terms of service of any third-party platform.

**Contributions** are welcome via GitHub Issues and Pull Requests. When reporting a bug, please include the version information shown in the in-app *About* page.
