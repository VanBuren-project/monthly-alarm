package com.anqi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anqi.app.BuildConfig
import com.anqi.app.ui.theme.Anqi

private const val AUTHOR = "范布伦计划"
private const val BILIBILI_URL = "https://space.bilibili.com/4692966/"
private const val GITHUB_URL = "https://github.com/VanBuren-project/monthly-alarm"

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    Column(
        Modifier
            .fillMaxSize()
            .background(Anqi.palette.canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnqiTextButton("返回", onBack, color = Anqi.palette.muted)
            Text(
                "关于",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = Anqi.palette.ink,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.width(52.dp))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(Modifier.height(28.dp))
                Text(
                    "月度闹钟",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Anqi.palette.ink
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Android",
                    style = MaterialTheme.typography.labelMedium,
                    color = Anqi.palette.muted
                )

                Spacer(Modifier.height(20.dp))
                Surface(
                    color = Anqi.palette.surface,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "月度闹钟是一款开源免费的月度提醒/闹钟应用，用于设置每月固定日期的提醒，例如每月 8 号交电费。",
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Anqi.palette.ink
                    )
                }

                Spacer(Modifier.height(22.dp))
                SectionLabel("作者与联系")
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = Anqi.palette.surface,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        InfoRow("作者", AUTHOR)
                        Hairline(Modifier.padding(horizontal = 16.dp))
                        LinkRow("B 站", AUTHOR, BILIBILI_URL, uriHandler)
                        Hairline(Modifier.padding(horizontal = 16.dp))
                        LinkRow("主页", "space.bilibili.com/4692966", BILIBILI_URL, uriHandler)
                        Hairline(Modifier.padding(horizontal = 16.dp))
                        LinkRow(
                            label = "开源地址",
                            value = if (GITHUB_URL.isBlank()) "GitHub 仓库（待补充）" else "github.com/VanBuren-project/monthly-alarm",
                            url = GITHUB_URL.takeIf { it.isNotBlank() },
                            uriHandler = uriHandler
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))
                SectionLabel("主要功能")
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = Anqi.palette.surface,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        FeatureLine("重复规则", "单次、每天、工作日、每周多选、每月某日、每月最后一天、每月第几个星期几、每年某月某日")
                        FeatureLine("结束条件", "永不结束、指定结束日期、剩余 1–365 次")
                        FeatureLine("全屏响铃", "贪睡（1–120 分钟，默认 10 分钟）、停止、音量键贪睡")
                        FeatureLine("日历四视图", "所有 / 年 / 月 / 周，按已有规则投影未来提醒")
                        FeatureLine("自动恢复", "开机、改时间、换时区后自动重排；单次提醒响过自动关闭")
                    }
                }

                Spacer(Modifier.height(22.dp))
                SectionLabel("适用场景")
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = Anqi.palette.surface,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            "每月 8 号交电费（这天有折扣）、每月 20 号还信用卡、每月最后一天交房租或做月度结算、每月第二个周二开例会、每年固定日期续保或报税。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Anqi.palette.muted
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "也可以用于每天吃药、每周固定锻炼、单次重要事项等通用提醒。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Anqi.palette.muted
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))
                SectionLabel("声明")
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = Anqi.palette.surface,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            "本软件开源免费，无广告、不收取任何费用。",
                            style = MaterialTheme.typography.titleMedium,
                            color = Anqi.palette.ink
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "应用不申请联网权限，不自带账号，不上传提醒内容、通讯录、位置等任何个人数据。所有提醒只保存在本机。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Anqi.palette.muted
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "提醒依赖系统闹钟与厂商后台策略。小米、华为、OPPO、vivo 等系统需手动允许自启动、后台运行、电池优化白名单，并在最近任务中锁定应用，否则可能被系统清理而漏响。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Anqi.palette.muted
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "本软件为个人开发的免费工具，不提供可用性担保。请勿作为唯一手段用于服药、医疗、航班、考试等不可错过或涉及人身安全的事项。卸载应用或清除数据会删除全部提醒，当前版本不提供云同步与备份。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Anqi.palette.muted
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "如遇 Bug 或有建议，请 B 站私信【范布伦计划】或在 GitHub Issues 留言，并尽量附上 App 版本、手机型号、系统版本和复现步骤。B 站私信为个人反馈渠道，不保证即时回复。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Anqi.palette.muted
                        )
                        Spacer(Modifier.height(8.dp))
                        AnqiTextButton(
                            "打开 B 站主页",
                            { openUrl(uriHandler, BILIBILI_URL) },
                            color = Anqi.palette.signal
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))
                SectionLabel("权限")
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = Anqi.palette.surface,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            "通知：显示提醒；精确闹钟：准时触发；全屏提醒：锁屏亮屏；开机广播：重启后恢复提醒；前台服务：响铃期间播放声音。除此之外不申请其他敏感权限。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Anqi.palette.muted
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text(
                    "应用代码以 MIT License 开源。调度与响铃骨架参考 Malarm（MIT License，© adriablo）。完整许可见仓库 LICENSE。",
                    style = MaterialTheme.typography.labelMedium,
                    color = Anqi.palette.muted
                )
                Spacer(Modifier.height(18.dp))
                AnqiButton("返回", onBack, filled = false)
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun FeatureLine(title: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = Anqi.palette.ink
        )
        Spacer(Modifier.height(3.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = Anqi.palette.muted
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.width(72.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Anqi.palette.muted
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = Anqi.palette.ink
        )
    }
}

@Composable
private fun LinkRow(
    label: String,
    value: String,
    url: String?,
    uriHandler: UriHandler
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = url != null) { url?.let { openUrl(uriHandler, it) } }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.width(72.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Anqi.palette.muted
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (url != null) Anqi.palette.ink else Anqi.palette.muted
        )
        if (url != null) {
            Text(
                "打开",
                style = MaterialTheme.typography.labelLarge,
                color = Anqi.palette.signal
            )
        }
    }
}

private fun openUrl(uriHandler: UriHandler, url: String) {
    runCatching { uriHandler.openUri(url) }
}
