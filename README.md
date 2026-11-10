# 月度闹钟

把「每月 8 号交电费」这类周期性日子变得可靠。开源免费、无广告的 Android 月度提醒/闹钟应用，离线运行，不自带账号、不上传提醒内容。

> 适用 Android 8.0 及以上 · 不需要注册登录 · 不申请联网权限

## 下载

**[⬇ 下载 anqi-1.3.3.apk](https://github.com/VanBuren-project/monthly-alarm/releases/download/v1.3.3/anqi-1.3.3.apk)** · 7.1 MB

SHA-256：`5B35BA4F4CA20F2B0511B0833495E1DEDCA31B96B42229ADEE95108AEDFD5296`

也可以到 [Releases](https://github.com/VanBuren-project/monthly-alarm/releases) 页面查看更新说明与校验文件。

> **升级提示**：1.3.0 使用的是开发调试证书，1.3.1 起改用正式发布证书。两者签名不同，Android 不允许直接覆盖安装——请先卸载 1.3.0（已设的提醒会一并清除），再安装新版。从 1.3.1 起签名一致，之后可以一直直接覆盖升级，提醒数据保留。

## 它解决什么问题

手机自带的闹钟大多只能设「每天」或「每周几」，一次性提醒又要每次手动新建。生活里真正容易忘的，偏偏是那些**每月才来一次**的日子：

- 每月 8 号交电费，因为这天有 7 折
- 每月 20 号还信用卡、花呗
- 每月最后一天交房租、做月度结算
- 每月第二个周二开部门例会
- 每年 3 月 15 日报税、每年某日续保

月度闹钟把这些规则一次性设好，之后到点全屏响铃，不用再记。

## 使用场景

| 场景 | 推荐规则 | 说明 |
| --- | --- | --- |
| 每月固定日期缴费 | 每月某日 1–31 | 当月没有 31 号时按规则跳过，不会莫名提前 |
| 月底结算、交房租 | 每月最后一天 | 自动适配 30/31 天和闰年 2 月 |
| 每月第几个周几 | 每月第 N 个星期几 | 例如「第二个周二」「最后一个周五」 |
| 信用卡还款日 | 每月某日 + 提前结束条件 | 可设剩余次数，换卡后不用记着关 |
| 年年重复的纪念日/续费 | 每年某月某日 | 2 月 29 日只在闰年触发 |
| 单次重要事项 | 单次 | 精确到分钟，响过自动关闭 |
| 每天吃药、打卡 | 每天 / 工作日 | 工作日为周一至周五 |
| 每周固定几次 | 每周多选 | 可同时选周一、周三、周五 |

## 版本

- 应用名：月度闹钟
- 包名：`com.anqi.app`
- 版本：1.3.3（versionCode 8）
- 最低系统：Android 8.0（API 26）
- 目标系统：Android 16（API 36）
- 构建：Kotlin + Jetpack Compose + Material 3，release 构建（正式发布证书，RSA 4096 / v2 签名）

## 功能

### 重复规则

- **单次**：精确到年、月、日、时、分
- **每天**
- **工作日**：周一至周五（首版，不含中国法定节假日与调休）
- **每周**：可多选星期
- **每月某日**：1–31 日；当月没有这一天时跳过，不自动改到月末
- **每月最后一天**：自动适配大小月和闰年二月
- **每月第几个星期几**：第一个到第四个、最后一个
- **每年某月某日**：2 月 29 日在平年跳过，下一次落在闰年

### 提醒与结束

- 结束条件：永不 / 指定结束日期 / 剩余次数（1–365）
- 编辑页展示接下来三次提醒，保存前即可检查规则
- 首页只展示每个提醒的下一次，按时间排序；其余提醒在「接下来」中列出
- 开关用于暂停/恢复；单次提醒触发后自动关闭
- 全屏响铃：息屏时自动亮屏并弹出响铃页，贪睡（默认 10 分钟，可在设置中调为 1–120 分钟）、停止；全屏通知被厂商拦截时由响铃服务直接拉起页面兜底
- 音量加/减键可直接贪睡
- 震动开关：开启后按 `USAGE_ALARM` 通道循环震动（震动/静音模式同样生效），关闭则不震
- 系统默认铃声，响铃页配色、字体与主界面一致，深浅色跟随系统
- 开机、时间变更、时区变更后自动重排
- 精确闹钟权限缺失时降级为约 60 秒窗口，并在首页提示权限问题

### 后台可靠性与锁屏

- **每次进程启动都自愈**：打开应用、开机、改时间、改时区后，把所有已启用的提醒重新排一遍。被系统清掉一次的闹钟，下一次启动就会补回来
- **六小时心跳**：排一个加少量随机抖动的 `setAndAllowWhileIdle` 一次性唤醒，醒来后重排全部提醒并续排下一次；Doze 期间同样会被投递。专门对付「被强停后应用再没被打开过」
- **重排不会吃掉贪睡**：重排只清基础闹钟，不再取消进行中的贪睡
- **锁屏直接关闭**：响铃页只声明 `showWhenLocked`，不请求解除锁屏，因此锁屏上就能点「停止 / 贪睡」，不会被 PIN 键盘盖住
- **解锁即到前台**：响铃期间注册一次 `ACTION_USER_PRESENT` 广播（平时不监听），解锁瞬间主动拉起响铃页
- **打开应用落到响铃页**：正在响铃时点图标直接进关闭页，由 ring token 判断，贪睡后再次响铃同样生效
- **设置页引导**：省电白名单、自启动、最近任务锁定三件事的说明与跳转；另有「10 秒后响铃」用于真机自测，不改动任何提醒时间

### 日历视图

- 底部四个页签：**所有 / 年 / 月 / 周**，默认「所有」
- 日历是现有循环规则的未来投影，不另存一份日程；只显示已启用的提醒
- **年**：12 个月总览，手机 2 列、平板 3 列；今天描边、选中日珊瑚圆、有提醒的日期带小圆点
- **月**：周一开始的完整月历；点日期查看当天清单，可直接「在这一天新建」
- **周**：7 列 × 24 小时课程表式时间轴；同一时刻的多条提醒合并为「时间 · N」，点块查看当天清单
- 已过去的日期淡化且不画提醒点；周视图打开时定位到当前时间附近
- 日历中的提醒可直接打开编辑器修改

### 界面

- 深色/浅色跟随系统
- 首页、编辑器、设置页在大屏上限制内容宽度，避免文字被拉得过宽
- ≥840dp 使用左右双栏（列表 + 详情）；手机为单列
- 320dp 窄屏与 200% 字体下，主时间数字自适应宽度
- 时间输入使用 Material 3 `TimeInput`，避免小屏表盘溢出
- 设置页内提供「关于与声明」，包含作者、B 站、反馈方式和开源声明
- 空状态、权限提示、规则说明均为中文

### 电脑端界面预览

不用模拟器也能在电脑上查看真实 Compose 渲染结果：

- [所有 / 手机 360dp](preview/home-360.png)
- [编辑器 411dp](preview/editor-411.png)
- [平板双栏 840dp](preview/home-840.png)
- [年视图 360dp](preview/year-360.png)
- [年视图 840dp（平板三列）](preview/year-840.png)
- [月视图 360dp](preview/month-360.png)
- [周视图 360dp](preview/week-360.png)
- [周视图含提醒块](preview/week-event-360.png)
- [关于与声明 360dp](preview/about-360.png)
- [设置 360dp（权限与保活引导）](preview/settings-360.png)、[设置 360dp（试一次）](preview/settings-test-360.png)
- [响铃页 360dp（深色）](preview/ringing-dark-360.png)、[响铃页 360dp（浅色）](preview/ringing-light-360.png)

启动器图标按自适应图标前景层重新裁切居中，预览见 [`preview/icon-preview.png`](preview/icon-preview.png)。

生成命令：

```bash
./gradlew :app:testDebugUnitTest --tests com.anqi.app.UiPreviewTest
```

截图输出到 `app/build/screens/`。它通过 Robolectric 在 JVM 上渲染真实 Compose 树，不等于 Android 真机运行；锁屏、通知、精确闹钟仍需手机验收。作者本机这套测试还把 Robolectric 缓存重定向到了工作区内，如果你在自己的环境里跑，无需这些额外设置。

## 安装

1. 从 [Releases](https://github.com/VanBuren-project/monthly-alarm/releases) 下载 `anqi-1.3.3.apk`（或使用自己构建的 `app/build/outputs/apk/release/app-release.apk`）传到手机。
2. 如果手机上装过 1.3.0 或更早的调试证书版本，先卸载再安装（签名不同，无法覆盖）。
3. 在系统设置中允许文件管理器/浏览器「安装未知应用」。
3. 安装并打开「月度闹钟」。
4. 首次进入按首页提示依次开启：
   - 通知权限
   - 精确闹钟（Android 12+）
   - 全屏提醒（Android 14+）
5. **务必**再做一步：设置页 →「别让系统把它清掉」→「打开省电设置」，把本应用加入电池白名单并允许自启动；国产 ROM 还要在最近任务里长按应用卡片锁定它。

### 第一次测试

先在设置页点「10 秒后响铃」验证整条链路，再把应用从最近任务里划掉重试一次。然后新建一个「单次」提醒，设为 2–3 分钟后，锁屏等待。检查：

- 是否准时全屏亮起
- 锁屏状态下能否直接点「停止」或「贪睡」，无需先解锁
- 解锁后响铃页是否自己到前台
- 点「贪睡」后是否按设置分钟数再次响铃
- 点「停止」后是否安静
- 音量键是否能贪睡
- 从最近任务里划掉应用后，提醒是否仍然响
- 重启手机后提醒是否还在

## 构建

需要 JDK 17 与 Android SDK Platform 36（可直接用 Android Studio 打开本目录，或只用命令行）：

```powershell
# 从仓库根目录执行
./gradlew assembleRelease        # macOS / Linux
.\gradlew.bat assembleRelease    # Windows
```

产物：`app/build/outputs/apk/release/app-release.apk`

若用命令行而没有设置 `ANDROID_HOME`，在仓库根目录新建 `local.properties`：

```properties
sdk.dir=/path/to/Android/Sdk
```

`gradle/wrapper/gradle-wrapper.properties` 默认从腾讯云镜像下载 Gradle 8.13；下载慢或无法访问时可以改成官方地址：

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip
```

验证命令：

```bash
./gradlew testDebugUnitTest lintDebug
```

> 作者本机没有安装 Android Studio，构建时把 JDK、SDK、Gradle 缓存都放在工作区的 `.toolchain/` 与 `.gradle/` 下，并指向本机路径。上面的命令是等价的通用写法，仓库本身不包含这些工具链文件。

## 发布到 GitHub

仓库：<https://github.com/VanBuren-project/monthly-alarm>
发布页：<https://github.com/VanBuren-project/monthly-alarm/releases>

`v1.3.3` 的源码、APK、`RELEASE_NOTES.md` 与 `SHA256SUMS.txt` 将在真机验证通过后发布；此前已发布的 `v1.3.2` 保持不变。所有 APK 使用正式发布证书签名。

### 签名与密钥

- 正式密钥为 RSA 4096、有效期 100 年，使用 PKCS#12 格式的 `.jks`，别名 `monthly-alarm`。
- `app/build.gradle.kts` 从仓库根目录的 `keystore.properties` 读取签名信息；该文件和 `*.jks` 都在 `.gitignore` 中，源码仓库里只有构建逻辑，没有任何密钥材料。
- 自己构建时若没有 `keystore.properties`，release 构建会自动回退到 debug 证书，`assembleRelease` 仍可正常跑通。
- **密钥一旦丢失就无法再发布可覆盖安装的更新**，请把 `.jks` 与密码离线备份至少两份（例如加密压缩包 + U 盘）。

`keystore.properties` 格式：

```properties
storeFile=/absolute/path/to/monthly-alarm-release.jks
storePassword=你的密码
keyAlias=monthly-alarm
keyPassword=你的密码
```

后续发版流程：

1. 递增 `app/build.gradle.kts` 的 `versionCode` 与 `versionName`。
2. 用正式 keystore 构建：`./gradlew assembleRelease`（可选 `bundleRelease` 生成 AAB）。
3. 推送 tag 并上传 APK、`RELEASE_NOTES.md`、`SHA256SUMS.txt`。

若将来上架 Google Play，还需隐私政策 URL、Data safety 表单、512×512 图标、1024×500 特色图和内容分级。

## 已验证

- `assembleRelease`：成功
- `testDebugUnitTest`：7 个调度/心跳测试 + 7 个规则测试 + 11 个日历投影测试 + 13 个 Robolectric/Compose 界面测试，共 38 个，0 失败
- 电脑端真实 Compose 渲染：360dp 所有/年/月/周/设置/响铃页、411dp 编辑器、840dp 双栏，截图见 `preview/`
- `lintDebug`：0 errors，6 warnings（4 条依赖版本、2 条 adaptive icon monochrome）
- APK：`zipalign` 通过，APK Signature Scheme v2 验签通过（正式证书 `4B:0D:89:66:F9:7B:C2:1C:A1:94:F7:95:27:7C:DF:6B:AC:82:34:27:F4:F2:82:48:C2:69:93:20:3F:7C:40:A0`）
- 包名 `com.anqi.app`、应用名「月度闹钟」、版本 1.3.3（versionCode 8）、minSdk 26 / targetSdk 36 已用 `aapt2 dump badging` 核对；`ACTION_HEARTBEAT`、`scheduleHeartbeat`、`scheduleTestRing`、`openRingingAlarmIfAny` 已在 APK 的 dex 中确认存在

**未验证**：本机没有可用的硬件加速模拟器（AEHD 安装需要管理员/UAC 授权），也没有连接 Android 手机，因此自动化环节未覆盖真机锁屏响铃和国产 ROM 后台限制的实机验收（作者已在自用手机上手工验证 1.3.0 的日常响铃）。

## 已知限制 / 下一版

- 「工作日」目前只是周一至周五，尚未接入中国法定节假日和调休数据
- 日历只投影未来提醒，不保存独立的日历事件；已过去的日期不显示提醒点
- 贪睡中的临时提醒不会画在日历里
- 不支持自定义铃声选择（当前使用系统默认闹钟铃声）
- 不支持「跳过下一次」、提醒模板、历史记录、桌面小组件、云同步/备份
- 没有提前提醒、多个提前提醒、Wear OS
- 上架应用商店需要额外准备隐私政策、商店素材，并用 AAB 提交
- 未做 TalkBack 全量走查；已处理主要触控高度、字体缩放和窄屏溢出
- **强制停止无法自动恢复**：被系统 force-stop 的应用（含「清理后台」等价于强停的 ROM）不能执行任何代码，闹钟与心跳都会一并被清除，这是 Android 平台限制，第三方应用无法自行恢复。加电池白名单、允许自启动、锁定最近任务是唯一有效手段

## 目录

```
.
├── app/src/main/java/com/anqi/app/
│   ├── alarm/        AlarmManager 调度、响铃服务、开机/时区重排
│   ├── data/         规则计算（NextOccurrence）、日历投影、AlarmStore
│   ├── model/        Alarm / RepeatType 与 JSON 持久化
│   ├── ui/           Compose 页面、组件、主题
│   └── viewmodel/    状态与权限自检
├── app/src/test/     规则单元测试 + Robolectric/Compose 界面测试
├── preview/          电脑端真实 Compose 渲染截图
└── gradle/           版本目录与 Gradle Wrapper
```

## 作者与反馈

月度闹钟由 **范布伦计划** 开发和维护。

- B 站：[@范布伦计划](https://space.bilibili.com/4692966/)
- 主页：<https://space.bilibili.com/4692966/>
- 开源地址：<https://github.com/VanBuren-project/monthly-alarm>
- 问题反馈：<https://github.com/VanBuren-project/monthly-alarm/issues>

本软件开源免费，无广告、不收取任何费用。如遇 Bug 或有建议，请在 GitHub Issues 留言，或 B 站私信【范布伦计划】，并尽量附上 App 版本、手机型号、系统版本和复现步骤。B 站私信为个人反馈渠道，不保证即时回复。

## 声明

- **开源免费**：本项目以 MIT License 开源，任何人可自由使用、修改、分发；不设付费版、内购、广告或会员。
- **无账号、无联网**：应用不申请 `INTERNET` 权限，不自带账号体系，不上传提醒内容、通讯录、位置等任何个人数据。所有提醒只保存在本机。
- **提醒可靠性**：闹钟依赖 Android 系统 `AlarmManager` 与厂商后台策略。在小米、华为、OPPO、vivo 等系统上，需手动允许自启动、后台运行、电池优化白名单，并在最近任务中锁定应用；否则可能被系统杀死而漏响。
- **非关键用途**：本软件为个人开发的免费工具，不提供任何形式的可用性担保。请勿将本软件作为唯一手段用于医疗、服药、航班、考试等不可错过或涉及人身安全的事项，建议同时设置系统闹钟或日历提醒。
- **权限说明**：通知（显示提醒）、精确闹钟（准时触发）、全屏提醒（锁屏亮屏）、开机广播（重启后恢复提醒）、前台服务（响铃期间播放声音）。除此之外不申请其他敏感权限。
- **数据丢失**：提醒数据保存在应用私有目录，卸载应用或清除数据会一并删除，当前版本不提供云同步与自动备份。
- **第三方**：调度与响铃骨架参考 Malarm（MIT License，Copyright (c) 2026 adriablo），已按其许可保留版权声明；本项目不向上游提交，也不代表上游官方版本。

## 第三方

调度与响铃骨架参考 **Malarm**（MIT License，Copyright (c) 2026 adriablo），已按「月度闹钟」的规则模型和 Compose UI 重写。原始许可见 [LICENSE](LICENSE)。

本项目不向上游提交，也不代表上游官方版本。
