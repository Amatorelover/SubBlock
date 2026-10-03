# 遮幕 SubBlock · 推广文案包

> 用途：把项目推广到不同社区时，直接复制对应模板、按需微调即可。
> 维护原则：所有文案基于真实功能，不夸大、不使用绝对化用语、不承诺隐私之外的效果。
> 项目主页：https://github.com/Amatorelover/SubBlock
> 最新 APK：https://github.com/Amatorelover/SubBlock/releases/latest

---

## 0. 核心卖点（所有渠道统一口径）

| 维度 | 表述 |
|---|---|
| 一句话（中） | 看双语视频时，盖住另一种语言的字幕，只留你想看的那一行。 |
| 一句话（英） | When watching bilingual video, cover one language's subtitle line and keep only the one you want. |
| 最大差异点 | 不申请任何网络权限——无联网、无埋点、无广告、无账号。隐私可自检。 |
| 技术栈 | Kotlin + Jetpack Compose，Material 3 动态取色，minSdk 26，targetSdk 36。 |
| 安装方式 | GitHub Releases 下载 APK 自装；未上架应用商店（个人签名）。 |

**目标人群与渠道映射**

| 人群 | 痛点 | 优先渠道 |
|---|---|---|
| 学外语、刷 Netflix/Disney+/日剧韩剧的人 | 想只留中字或只留英字 | 小红书、B站、字幕组/语言学习社群、Reddit r/androidapps |
| 厌恶追踪/广告的 Android 用户 | 隐私、离线、FOSS | r/fossdroid、F-Droid、Privacy Guides、Product Hunt |
| 效率/工具控 | 悬浮窗、磁贴、预设 | V2EX、少数派、酷安 |
| 开发者 | 单一数据源架构、Compose 实践 | Hacker News (Show HN)、GitHub Topics |

---

## 1. V2EX（节点：分享创造 / Android）

标题：【自研 Android 小工具】遮幕 SubBlock：看双语视频时只留你想看的那行字幕

正文：

> 做一个看双语视频时会用到的悬浮遮挡工具。
>
> 场景很具体：看 Netflix/日剧/韩剧/网课时，画面上常叠着两种语言的字幕。遮幕在屏幕上盖一层可拖动、可缩放、可调透明度的遮挡层，把不想看的那一种语言盖住，只留下想看的那一行。
>
> 几个特点：
> - 多块遮挡 + 3 种样式（实心 / 毛玻璃 / 渐变），Android 12+ 有毛玻璃模糊
> - 实时预览、HSV 取色、6 套预设、控制中心磁贴一键开关
> - **不申请任何网络权限**，无埋点、无广告、无账号，配置只存在本机
> - Kotlin + Jetpack Compose，MIT 开源
>
> GitHub：https://github.com/Amatorelover/SubBlock
> 直接装：Releases 里下 APK。装完需手动开悬浮窗权限和通知（前台服务需要）。
>
> 因为是个人签名、没上架商店，装的时候系统会提示"未知来源"，属正常。欢迎提 Issue / PR。

---

## 2. 少数派（投稿 / 效率工具话题）

标题：我用 Kotlin 写了个"遮字幕"的悬浮工具，顺便把"零网络权限"当成了底线

导语：看双语剧时总被两种字幕干扰？这个开源小工具只做一件事——盖住你不想要的那行字幕。它不联网、不采集数据，连读取存储的权限都不申请。

正文要点（投稿时展开）：
1. 缘起：作者声明里那段——看双语视频只想保留一种语言
2. 功能演示清单（对应截图：home / editor / overlay-bilingual / styles）
3. 隐私设计：AndroidManifest 无 INTERNET、DataStore 本地存储、SAF 导入导出不申请存储权限
4. 架构亮点：UI 只写数据，OverlayService 订阅 DataStore 重绘（可给开发者看）
5. 获取与安装注意事项（悬浮窗权限、通知、自启动白名单）
6. 路线图：按应用自动生效、F-Droid 收录

---

## 3. 酷安（动态 / 应用推荐）

文案（短文案风格）：

> 遮幕 SubBlock｜看双语视频只留一行字幕
> 悬浮遮挡层，可拖可缩可调透明度，盖住不想看的那国语言字幕。毛玻璃/渐变/实心三种样式，控制中心磁贴一键开关。
> 最关键：不联网、无广告、无埋点，连存储权限都不申请。MIT 开源，GitHub 自取 APK。
> 🔗 github.com/Amatorelover/SubBlock

---

## 4. 小红书 / B站（场景种草，配图/录屏效果最好）

标题参考：
- "看美剧只留英文字幕学英语，这个方法太爽了"
- "双语字幕太乱？一个悬浮遮罩帮你只留中字"
- "不想被字幕剧透？把那行字幕盖掉就行"

正文骨架：
1. 抛出痛点（双语字幕挤在一起、想练听力却被中字带跑）
2. 展示遮幕效果（录屏：盖住中字只留英字 / 盖住英字只留中字）
3. 强调"不打扰"：不联网、不偷数据
4. 引导：GitHub 搜 SubBlock，Releases 下 APK

> 注意：小红书/B站以真实录屏取胜，README 截图占位补好后建议优先在这里发。

---

## 5. Product Hunt

Tagline: Cover one subtitle language and keep only the line you want — fully offline.

First Comment:

> Hi PH 👋
>
> SubBlock is a tiny Android overlay tool born from a simple need: when watching bilingual video, you often only want one of the two subtitle lines.
>
> It draws a draggable, resizable, opacity-adjustable curtain over the language you don't want — solid, blur-behind, or soft gradient.
>
> What makes it different: **zero network permission.** No analytics, no ads, no account. All config lives in DataStore on your device. You can literally verify it in the manifest.
>
> Built with Kotlin + Jetpack Compose (Material 3 dynamic color). MIT licensed.
>
> Grab the APK from GitHub Releases. Feedback and PRs welcome!

Topics to pick: Android, Open Source, Privacy, Productivity

---

## 6. Hacker News（Show HN）

Title: Show HN: SubBlock – Android overlay to hide one subtitle language in bilingual video

Text:

> A small open-source Android app (Kotlin + Jetpack Compose) that draws a draggable, resizable overlay to cover one of the two subtitle lines when watching bilingual video.
>
> The interesting bits for HN:
> - Single source of truth: the UI never commands the overlay. It only writes to DataStore; the foreground service subscribes via combine() and redraws. No Activity↔Service glue, no state drift.
> - Privacy by construction: no INTERNET permission in the manifest, no analytics, no account. Config import/export uses the system file picker (SAF), so it needs no storage permission either.
> - Three mask styles (solid / blur-behind on Android 12+ / gradient), live preview sharing the exact same drawing code as the real overlay.
>
> MIT licensed. APK in GitHub Releases. Curious about the architecture trade-offs or the foreground-service specialUse subtype on targetSdk 36 — happy to discuss.
>
> https://github.com/Amatorelover/SubBlock

---

## 7. Reddit

r/fossdroid（最契合隐私卖点）:

> Title: [APP] SubBlock – overlay to hide one subtitle language in bilingual video (no network permission, FOSS)
>
> Body: Open-source (MIT) Android tool to cover one of the two subtitle lines when watching bilingual content. No INTERNET permission, no analytics, no ads, no account. Config stays in DataStore. APK on GitHub Releases; you can verify the manifest yourself. Looking for feedback on the architecture (single DataStore source of truth driving a foreground overlay service).

r/androidapps:

> Title: [APP] SubBlock: keep only the subtitle language you want while watching bilingual video
>
> Body: A floating overlay that hides the subtitle line you don't want. Drag/resize/opacity, blur + gradient styles, Quick Settings tile. Fully offline, no account. GitHub link in comments.

r/android（偏开发，谨慎发，易被判推广）: 建议只在有人问"怎么做悬浮窗遮挡"时作为案例引用，不主动发广告。

---

## 8. X / Twitter（短文案，带图转化最高）

> Watching bilingual video but only want one subtitle line?
> SubBlock draws an overlay to hide the other language — solid, blur, or gradient.
> Fully offline: no network permission, no ads, no tracking.
> MIT · Kotlin + Compose
> github.com/Amatorelover/SubBlock

---

## 9. Telegram 频道 / 群组

> 【开源推荐】遮幕 SubBlock — 看双语视频只留一行字幕的悬浮遮挡工具
> 不联网、无广告、无埋点，配置存本机。毛玻璃/渐变/实心三样式，控制中心磁贴开关。
> 🔗 https://github.com/Amatorelover/SubBlock

---

## 10. F-Droid 收录（长期信任背书，步骤）

F-Droid 是隐私/FOSS 用户最信任的分发渠道，和本项目卖点高度契合。收录需满足：

1. **元数据**：在仓库建 `fastlane/metadata/android/com.yanhu.subblock/` 结构，含多语言 `full_description.txt`、`short_description.txt`、`title.txt`、至少 1 张 `images/phoneScreenshots/` 截图、CHANGELOG。
2. **构建可复现**：F-Droid 必须能从源码构建（不能只给二进制 APK）。需补全 `build.sh` 之外的 Gradle 可复现构建说明，且签名密钥由 F-Droid 生成（与当前自签不同，升级需保持 key 一致——见下方注意）。
3. **合规**：确认所有依赖为 FOSS 许可；当前项目依赖均为 AndroidX / Compose（Apache-2.0 / MIT），应无阻碍。
4. 提交到 https://gitlab.com/fdroid/rfp 的 Request For Packaging 议题，或在 f-droid-data 提 Merge Request。

> ⚠️ 关键风险：F-Droid 构建的 APK 使用 F-Droid 的签名，与你当前自签 APK **包名相同但签名不同**，已装用户无法直接覆盖升级。需提前在 README/Release 说明迁移方式（卸载重装，配置可 SAF 导出再导入）。

---

## 11. 发布节奏建议（避免过度曝光导致反感）

| 阶段 | 动作 | 渠道 |
|---|---|---|
| 第 1 周 | 门面补齐：真实截图 4 张、首页 demo GIF、Topics 已设、badge 已加 | GitHub 自身 |
| 第 2 周 | 中文社区首波 | V2EX + 少数派 + 酷安 |
| 第 3 周 | 场景种草（需录屏） | 小红书 + B站 |
| 第 4 周 | 国际开发者/隐私圈 | Product Hunt + Show HN + r/fossdroid |
| 持续 | 回应每个 Issue，保持 Release 节奏 | 全渠道 |
| 中期 | 提交 F-Droid RFP | F-Droid |

> 原则：同一内容间隔 ≥1 周再换平台，避免被判定 spam；每个渠道只发一次主帖 + 必要时答疑问，不刷屏。
