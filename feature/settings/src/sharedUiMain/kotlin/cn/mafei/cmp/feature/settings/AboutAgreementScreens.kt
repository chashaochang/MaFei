package cn.mafei.cmp.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.ui.MaFeiActionBarScaffold
import cn.mafei.cmp.common.ui.MaFeiLegacyDrawables
import cn.mafei.cmp.common.ui.mafeiInteractive
import org.jetbrains.compose.resources.painterResource

private const val MAFEI_GITHUB_URL = "https://github.com/chashaochang/MaFei"
private const val MAFEI_APP_GALLERY_URL =
    "https://appgallery.huawei.com/app/detail?id=cn.xiaobai.mafei"

/** Mechanical Compose translation of ArkUI `HomeAboutPrivacyPane` + `AboutPageSurface`. */
@Composable
fun AboutPrivacyScreen(
    onBack: () -> Unit,
    onPrivacy: () -> Unit,
    onUserAgreement: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    versionName: String = "",
) {
    val safeArea = LocalMaFeiSafeArea.current
    MaFeiActionBarScaffold(
        title = "关于马飞",
        onBack = onBack,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.widthIn(max = 560.dp).fillMaxWidth()
                    .padding(start = 16.dp, top = 12.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AboutPrivacyAction(
                    title = "隐私协议",
                    icon = MaFeiLegacyDrawables.privacy,
                    onClick = onPrivacy,
                    modifier = Modifier.weight(1f),
                )
                AboutPrivacyAction(
                    title = "用户协议",
                    icon = MaFeiLegacyDrawables.settings,
                    onClick = onUserAgreement,
                    modifier = Modifier.weight(1f),
                )
            }

            Column(
                Modifier.weight(1f).widthIn(max = 560.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = safeArea.bottom + 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
            Column(
                Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painter = painterResource(MaFeiLegacyDrawables.appIcon),
                    contentDescription = null,
                    modifier = Modifier.size(88.dp).clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Fit,
                )
                Text(
                    "马飞",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (versionName.isNotBlank()) {
                    Text(
                        versionName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                }
                Text(
                    "HarmonyOS 平台的 Jellyfin 第三方客户端",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }

            AboutSection("相关链接") {
                AboutGroup {
                    AboutLinkRow("华为应用市场", MAFEI_APP_GALLERY_URL) {
                        onOpenUrl(MAFEI_APP_GALLERY_URL)
                    }
                    AboutDivider()
                    AboutLinkRow("GitHub", MAFEI_GITHUB_URL) {
                        onOpenUrl(MAFEI_GITHUB_URL)
                    }
                }
            }
            AboutSection("开源许可") {
                AboutGroup {
                    AboutInfoRow("许可证", "GPL-3.0-or-later")
                }
            }
            AboutSection("鸣谢") {
                AboutGroup {
                    AboutInfoRow("Jellyfin", "感谢 Jellyfin 开源项目")
                    AboutDivider()
                    AboutInfoRow("mpv", "感谢 mpv 开源项目")
                    AboutDivider()
                    AboutInfoRow("OpenHarmony", "感谢 OpenHarmony 开源项目")
                }
            }
            }
        }
    }
}

@Composable
private fun AboutPrivacyAction(
    title: String,
    icon: org.jetbrains.compose.resources.DrawableResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.height(48.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .82f))
            .mafeiInteractive(onClick, radiusDp = 12f, label = title)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            contentScale = ContentScale.Fit,
        )
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
    }
}

@Composable
private fun AboutSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 12.dp).semantics { heading() },
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
        )
        content()
    }
}

@Composable
private fun AboutGroup(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .82f)),
    ) {
        content()
    }
}

@Composable
private fun AboutLinkRow(title: String, content: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().mafeiInteractive(onClick, radiusDp = 0f, label = title)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            title,
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
        )
        Text(
            content,
            Modifier.fillMaxWidth(),
            color = Color(0xFF4DA3FF),
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AboutInfoRow(title: String, content: String) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            Modifier.width(96.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
        )
        Text(
            content,
            Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AboutDivider() {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = .42f)),
    )
}

private data class AgreementCopy(val title: String, val content: String)

private val agreementCopies = listOf(
    AgreementCopy(
        "1. 协议接受",
        "欢迎使用JellyFin客户端！通过下载、安装或使用本应用，您同意遵守本用户协议的所有条款和条件。如果您不同意本协议，请不要使用本应用。",
    ),
    AgreementCopy(
        "2. 应用描述",
        "JellyFin客户端是一个开源的媒体服务器客户端应用，用于连接和访问JellyFin媒体服务器。本应用允许您：\n• 浏览和播放媒体内容\n• 管理媒体库\n• 同步播放进度\n• 获取弹幕等增强功能",
    ),
    AgreementCopy(
        "3. 使用条件",
        "您同意：\n• 仅将本应用用于合法目的\n• 不会逆向工程、反编译或试图提取源代码（除非法律明确允许）\n• 不会使用本应用进行任何违法活动\n• 尊重版权和知识产权\n• 不会干扰应用的正常运行",
    ),
    AgreementCopy(
        "4. 内容责任",
        "您理解并同意：\n• 您对通过本应用访问的所有内容负责\n• 您必须拥有或获得适当的许可来访问和播放媒体内容\n• 我们不对您服务器上的内容承担任何责任\n• 您应遵守当地法律法规关于媒体内容的规定",
    ),
    AgreementCopy(
        "5. 开源许可",
        "本应用基于GPL-3.0-or-later许可证开源发布。您可以：\n• 自由使用、修改和分发本应用\n• 查看和研究源代码\n• 贡献代码改进\n\n但必须遵守GPL-3.0-or-later许可证的条款。",
    ),
    AgreementCopy(
        "6. 免责声明",
        "本应用按\"现状\"提供，不提供任何明示或暗示的保证。我们不保证：\n• 应用的持续可用性\n• 应用完全无错误\n• 应用满足您的特定需求\n• 数据不会丢失\n\n您使用本应用的风险由您自行承担。",
    ),
    AgreementCopy(
        "7. 责任限制",
        "在法律允许的最大范围内，我们不对以下情况承担责任：\n• 使用或无法使用本应用造成的任何损失\n• 数据丢失或损坏\n• 第三方服务的问题\n• 因网络问题导致的服务中断",
    ),
    AgreementCopy(
        "8. 第三方服务",
        "本应用可能集成第三方服务（如弹弹play API）。这些服务有各自的使用条款，您在使用时应遵守相关条款。我们不对第三方服务的可用性或质量负责。",
    ),
    AgreementCopy(
        "9. 协议修改",
        "我们保留随时修改本协议的权利。重大变更会在应用中通知您。继续使用应用即表示您接受修改后的协议。",
    ),
    AgreementCopy(
        "10. 终止",
        "您可以随时停止使用本应用并删除它。如果您违反本协议，我们有权终止您的使用权限。",
    ),
    AgreementCopy(
        "11. 适用法律",
        "本协议受中华人民共和国法律管辖。如有争议，应通过友好协商解决；协商不成的，可向有管辖权的人民法院提起诉讼。",
    ),
    AgreementCopy(
        "12. 联系方式",
        "如果您对本协议有任何疑问，请联系我们：\n• QQ群：991893385\n• GitHub：在项目仓库中提交问题",
    ),
)

/** Exact text and layout translation of ArkUI `UserAgreementPage`. */
@Composable
fun UserAgreementScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeArea = LocalMaFeiSafeArea.current
    MaFeiActionBarScaffold(
        title = "用户协议",
        onBack = onBack,
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = safeArea.bottom + 32.dp),
            ) {
                Text(
                    "用户使用协议",
                    Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp).semantics { heading() },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "最后更新时间：2024年12月1日",
                    Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
                agreementCopies.forEach { section ->
                    Text(
                        section.title,
                        Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        section.content,
                        Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                    )
                }
            }
        }
    }
}
