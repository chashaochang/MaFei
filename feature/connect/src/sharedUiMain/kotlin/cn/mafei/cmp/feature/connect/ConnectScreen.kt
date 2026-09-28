package cn.mafei.cmp.feature.connect

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.GlassLevel
import cn.mafei.cmp.common.glass.GlassRole
import cn.mafei.cmp.common.glass.GlassSurface
import cn.mafei.cmp.common.glass.GlassSurfaceSpec
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.rememberCombinedGlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.glass.components.LiquidBottomTab
import cn.mafei.cmp.common.glass.components.LiquidBottomTabs
import cn.mafei.cmp.common.glass.components.LiquidBottomTabsStyle
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.design_system.MaFeiLayoutFamily
import cn.mafei.cmp.common.design_system.MaFeiSurface
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.common.design_system.currentMaFeiSafeArea
import cn.mafei.cmp.common.ui.InlineProblem
import cn.mafei.cmp.common.ui.MaFeiActionBar
import cn.mafei.cmp.common.ui.MaFeiLegacyDrawables
import cn.mafei.cmp.common.ui.MaFeiLoadingIndicator
import cn.mafei.cmp.common.ui.MaFeiLoadingStyle
import cn.mafei.cmp.common.ui.mafeiInteractive
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.feature.connect.generated.resources.Res
import cn.mafei.cmp.feature.connect.generated.resources.mafei_app_icon
import cn.mafei.cmp.feature.connect.generated.resources.provider_feiniu
import cn.mafei.cmp.feature.connect.generated.resources.provider_jellyfin
import org.jetbrains.compose.resources.painterResource

/**
 * CMP translation of the original MaFei connection page.
 *
 * Device width changes the available whitespace only. It must not introduce a
 * second information architecture or additional marketing copy.
 */
/**
 * 登录表单的液态玻璃面板。tintAlpha 刻意压低（Dialog 角色默认 .88，几乎不透明），
 * 这样背后的影视墙能透过这层玻璃被模糊出来 —— 模糊交给玻璃，墙本身保持清晰。
 */
private val LoginGlassSpec = GlassSurfaceSpec(
    role = GlassRole.Dialog,
    level = GlassLevel.Full,
    tintAlpha = .42f,
    intensity = 1f,
    cornerRadiusDp = 28f,
)

/**
 * 表单区外那层液态玻璃。手机（小屏）上不套：[showGlass] 为 false 时直接返回内容 ——
 * 面板在小屏里只是多余的容器，而且每帧多一次模糊；表单直接贴在墙上反而更干净。
 */
@Composable
private fun LoginGlassPanel(
    showGlass: Boolean,
    backdrop: GlassBackdrop,
    exportedBackdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (showGlass) {
        Box(modifier) {
            // 材质层：**只有材质、没有内容**。它把自己画出的材质导出，好让表单里的控件
            // 折射"影视墙 + 这层面板材质"。
            //
            // 表单必须留在这一层**外面**：导出层记录的是这一层的全部绘制，若采样它的
            // 控件也在这一层里，"记录"就会包含"读取自己"——skiko 的 drawable 图会因此
            // 递归到 native 栈耗尽（Stack Guard）直接 SIGILL。
            MaFeiSurface(
                spec = LoginGlassSpec,
                modifier = Modifier.fillMaxSize(),
                backdrop = backdrop,
                exportedBackdrop = exportedBackdrop,
            ) {}
            Box(
                Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(LoginGlassSpec.cornerRadiusDp.dp)),
                contentAlignment = Alignment.Center,
                content = { content() },
            )
        }
    } else {
        Box(modifier, contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
fun ConnectScreen(
    busy: Boolean,
    error: String?,
    onSubmit: (ProviderKind, String, String, String, Boolean) -> Unit,
    onOpenHistory: () -> Unit = {},
    modifier: Modifier = Modifier,
    /**
     * 窗口背景层的源（含影视墙），由调用方铺好并捕获。两栏布局必须传：本栏宽度会随
     * 布局动画变化，各自捕获本栏内容既会让两栏的墙错位、也会把模糊拉花。为 null 时
     * 本面板自己铺一层墙并捕获（单面板路由，例如「我的 → 重新连接账号」）。
     */
    pageBackdrop: GlassBackdrop? = null,
    formState: ConnectFormState = rememberConnectFormState(),
    routeTitle: String? = null,
    onBack: (() -> Unit)? = null,
    showProviderSwitcher: Boolean = true,
    showHistory: Boolean = true,
    onOpenServerDiscovery: (() -> Unit)? = null,
) {    val safeArea = currentMaFeiSafeArea()
    val provider = formState.provider
    val baseUrl = formState.baseUrl
    val username = formState.username
    val password = formState.password
    val rememberPassword = formState.rememberPassword
    // Some Jellyfin accounts allow an empty password. The server owns authentication policy.
    val canSubmit = !busy && baseUrl.isNotBlank() && username.isNotBlank()
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    // IME insets already overlap/replace the gesture/nav-bar area while the
    // keyboard is visible. Use a maximum, otherwise this route pads the same
    // physical edge once through safeArea and once through the keyboard.
    val contentBottomGuard = maxOf(safeArea.bottom, imeBottom)
    val submit: () -> Unit = {
        if (canSubmit) {
            onSubmit(provider, baseUrl.trim(), username.trim(), password, rememberPassword)
        }
    }

    // 表单面板自己的材质层，以及给面板内玻璃子元素用的组合源：窗口 + 面板。
    // 只喂窗口源的话，按钮只会折射影视墙、把面板漏掉（这正是操作栏给按钮传
    // buttonBackdrop 的原因）。
    // 小屏（手机）不套面板。按「窗口」尺寸判断，而不是本面板宽度 —— 桌面两栏布局下
    // 登录栏只有 420-560dp，用面板宽度会被误判成手机、面板整个消失。
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val phoneWindow = remember(windowInfo.containerSize, density) {
        val window = windowInfo.containerSize
        window.width <= 0 || mafeiLayout(
            widthDp = with(density) { window.width.toDp().value },
            heightDp = with(density) { window.height.toDp().value },
            fontScale = density.fontScale,
        ).family == MaFeiLayoutFamily.Phone
    }
    val ownBackdrop = if (pageBackdrop == null) rememberGlassBackdrop() else null
    val windowBackdrop = pageBackdrop ?: requireNotNull(ownBackdrop)
    val panelMaterial = rememberGlassBackdrop()
    val panelBackdrop = rememberCombinedGlassBackdrop(windowBackdrop, panelMaterial)
    Box(modifier.fillMaxSize()) {
        if (ownBackdrop != null) {
            Box(Modifier.fillMaxSize().captureGlassBackdrop(ownBackdrop)) {
                ConnectAtmosphere()
            }
        }
        Box(
            Modifier.fillMaxSize().padding(
                start = safeArea.start,
                end = safeArea.end,
            ),
        ) {
            // 内容按可用高度垂直居中：固定顶部留白在任何窗口高度下都不对（窗口一高就
            // 显得贴顶）。内容比可视区高时居中自然失效，退回可滚动。
            Box(
                Modifier.fillMaxSize().padding(
                    // 只有带标题栏时才需要顶部内边距；居中时不必再算安全区（内容在
                    // 中间，本来就远离边缘），否则桌面下这点 inset 会把内容压偏。
                    top = if (routeTitle == null) 0.dp else safeArea.top + 56.dp,
                    bottom = contentBottomGuard,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 表单区套一层液态玻璃：影视墙保持清晰，模糊由这层玻璃负责 —— 玻璃
                    // 折射的正是它背后的海报墙，这也是这块材质存在的意义。
                    // 手机（小屏）上不套：直接贴在墙上，见 LoginGlassPanel。
                    LoginGlassPanel(
                        showGlass = !phoneWindow,
                        backdrop = windowBackdrop,
                        exportedBackdrop = panelMaterial,
                        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                    ) {
                        Column(
                            // 有面板时 20dp 是「面板内边距」；手机上没面板，这 20dp 就
                            // 成了多余间距（外层已有 16dp），收成 8dp 让输入框与顶部切换
                            // 按钮的可见胶囊左右对齐（两者都落在 24dp 上）。
                            Modifier.padding(
                                horizontal = if (phoneWindow) 8.dp else 20.dp,
                                vertical = 22.dp,
                            ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CompactWelcome(topPadding = 6.dp)
                            CompactLoginForm(
                                backdrop = panelBackdrop,
                                provider = provider,
                                baseUrl = baseUrl,
                                onBaseUrlChange = { formState.baseUrl = it },
                                username = username,
                                onUsernameChange = { formState.username = it },
                                password = password,
                                onPasswordChange = { formState.password = it },
                                rememberPassword = rememberPassword,
                                onRememberPasswordChange = { formState.rememberPassword = it },
                                busy = busy,
                                canSubmit = canSubmit,
                                error = error,
                                onOpenHistory = onOpenHistory.takeIf { showHistory },
                                onOpenServerDiscovery = onOpenServerDiscovery.takeIf {
                                    provider == ProviderKind.Jellyfin
                                },
                                serverAddressFocusRequest = formState.serverAddressFocusRequest,
                                onSubmit = submit,
                                modifier = Modifier.widthIn(max = 375.dp).fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            if (showProviderSwitcher) {
                ProviderSwitcher(
                    provider = provider,
                    enabled = !busy,
                    backdrop = windowBackdrop,
                    onSelected = { selectedProvider ->
                        if (provider != selectedProvider) {
                            formState.provider = selectedProvider
                            formState.password = ""
                        }
                    },
                    modifier = Modifier.align(Alignment.TopEnd)
                        .padding(top = safeArea.top + if (routeTitle == null) 0.dp else 56.dp),
                )
            }
        }
        if (routeTitle != null && onBack != null) {
            MaFeiActionBar(
                title = routeTitle,
                onBack = onBack,
                backdrop = windowBackdrop,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun CompactWelcome(modifier: Modifier = Modifier, topPadding: Dp = 80.dp) {
    Column(
        modifier.padding(top = topPadding, bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(
            painter = painterResource(Res.drawable.mafei_app_icon),
            contentDescription = "马飞",
            modifier = Modifier.size(70.dp),
        )
        Text(
            "马飞",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = .92f),
        )
    }
}

@Composable
private fun ProviderSwitcher(
    provider: ProviderKind,
    enabled: Boolean,
    backdrop: GlassBackdrop,
    onSelected: (ProviderKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val providers = listOf(ProviderKind.Jellyfin, ProviderKind.Feiniu)
    val selectedIndex = providers.indexOf(provider).coerceAtLeast(0)
    LiquidBottomTabs(
        selectedTabIndex = { selectedIndex },
        onTabSelected = { index ->
            if (enabled) providers.getOrNull(index)?.let(onSelected)
        },
        backdrop = backdrop,
        tabsCount = providers.size,
        style = LiquidBottomTabsStyle.Compact,
        // The visible capsule remains 112 x 52 dp. The transparent host leaves
        // 24 dp horizontally so the selected droplet and its refraction fringe
        // can overflow without being clipped at the first or last item.
        modifier = modifier.width(160.dp).height(84.dp).alpha(if (enabled) 1f else .55f),
    ) {
        providers.forEachIndexed { index, item ->
            val name = if (item == ProviderKind.Jellyfin) "Jellyfin" else "飞牛影视"
            LiquidBottomTab(
                onClick = { if (enabled) onSelected(item) },
                index = index,
                isSelected = selectedIndex == index,
            ) {
                Image(
                    painter = painterResource(
                        if (item == ProviderKind.Jellyfin) Res.drawable.provider_jellyfin
                        else Res.drawable.provider_feiniu,
                    ),
                    contentDescription = name,
                    modifier = Modifier.size(21.dp).alpha(if (selectedIndex == index) 1f else .62f),
                )
            }
        }
    }
}

@Composable
private fun CompactLoginForm(
    backdrop: GlassBackdrop,
    provider: ProviderKind,
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    rememberPassword: Boolean,
    onRememberPasswordChange: (Boolean) -> Unit,
    busy: Boolean,
    canSubmit: Boolean,
    error: String?,
    onOpenHistory: (() -> Unit)?,
    onOpenServerDiscovery: (() -> Unit)?,
    serverAddressFocusRequest: Int,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rememberPasswordInteraction = remember { MutableInteractionSource() }
    val rememberPasswordHovered by rememberPasswordInteraction.collectIsHoveredAsState()
    Column(
        modifier.padding(top = 30.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ConnectField(
            backdrop = backdrop,
            value = baseUrl,
            onValueChange = onBaseUrlChange,
            placeholder = if (provider == ProviderKind.Feiniu) {
                "请输入 FNID 或服务器地址"
            } else {
                "请输入IP地址/域名"
            },
            enabled = !busy,
            focusRequestKey = serverAddressFocusRequest,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            trailing = onOpenHistory?.let { openHistory -> {
                Box(
                    Modifier.size(44.dp).mafeiInteractive(
                        onClick = openHistory,
                        enabled = !busy,
                        radiusDp = 22f,
                        label = "账号历史",
                    )
                        .semantics { contentDescription = "账号历史" },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(MaFeiLegacyDrawables.history),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                            Color.White.copy(alpha = if (busy) .28f else .72f),
                        ),
                    )
                }
            } },
        )
        ConnectField(
            backdrop = backdrop,
            value = username,
            onValueChange = onUsernameChange,
            placeholder = "请输入用户名",
            enabled = !busy,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        ConnectField(
            backdrop = backdrop,
            value = password,
            onValueChange = onPasswordChange,
            placeholder = "请输入密码",
            enabled = !busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        )

        Row(
            Modifier.toggleable(
                value = rememberPassword,
                enabled = !busy,
                role = Role.Checkbox,
                onValueChange = onRememberPasswordChange,
            ).hoverable(rememberPasswordInteraction).drawWithContent {
                drawContent()
                if (rememberPasswordHovered && !busy) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = .045f),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                    )
                }
            }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RememberPasswordCheckbox(
                checked = rememberPassword,
                enabled = !busy,
            )
            Text(
                "记住密码",
                color = Color.White.copy(alpha = if (busy) .42f else .76f),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        error?.let { InlineProblem(it) }

        Spacer(Modifier.height(4.dp))
        LiquidButton(
            onClick = { if (canSubmit) onSubmit() },
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth()
                .alpha(if (canSubmit) 1f else .58f)
                .then(if (canSubmit) Modifier else Modifier.semantics { disabled() }),
            isInteractive = canSubmit,
            surfaceColor = if (canSubmit) MaterialTheme.colorScheme.primary.copy(alpha = .22f)
            else Color.White.copy(alpha = .055f),
        ) {
            if (busy) {
                MaFeiLoadingIndicator(
                    modifier = Modifier.size(22.dp),
                    style = MaFeiLoadingStyle.Button,
                    tint = Color.White.copy(alpha = .78f),
                )
                Spacer(Modifier.width(9.dp))
            }
            Text(
                if (busy) "正在登录..." else "登录",
                color = Color.White.copy(alpha = if (canSubmit) .96f else .62f),
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (provider == ProviderKind.Jellyfin && onOpenServerDiscovery != null) {
            Box(
                Modifier.fillMaxWidth().height(44.dp)
                    .mafeiInteractive(
                        onClick = onOpenServerDiscovery,
                        enabled = !busy,
                        radiusDp = 10f,
                        label = "发现局域网内的设备",
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    "发现局域网内的设备",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = if (busy) .34f else .58f),
                )
            }
        }
    }
}

@Composable
private fun RememberPasswordCheckbox(
    checked: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(18.dp).alpha(if (enabled) 1f else .42f)) {
        val outlineWidth = 1.25.dp.toPx()
        val checkWidth = 1.8.dp.toPx()
        if (checked) {
            drawCircle(Color.White)
            val check = Path().apply {
                moveTo(size.width * .27f, size.height * .51f)
                lineTo(size.width * .43f, size.height * .67f)
                lineTo(size.width * .74f, size.height * .34f)
            }
            drawPath(
                path = check,
                color = Color.Black.copy(alpha = .88f),
                style = Stroke(
                    width = checkWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        } else {
            drawCircle(
                color = Color.White.copy(alpha = .66f),
                radius = size.minDimension / 2f - outlineWidth / 2f,
                style = Stroke(width = outlineWidth),
            )
        }
    }
}

@Composable
private fun ConnectField(
    backdrop: GlassBackdrop,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequestKey: Int = 0,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focused by interactionSource.collectIsFocusedAsState()
    val edgeColor by animateColorAsState(
        if (focused) MaterialTheme.colorScheme.primary.copy(alpha = .68f)
        else Color.Transparent,
        label = "connection field edge",
    )
    val shape = RoundedCornerShape(14.dp)
    val fieldHeight = 52.dp

    LaunchedEffect(focusRequestKey) {
        if (focusRequestKey > 0) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    GlassSurface(
        backdrop = backdrop,
        spec = GlassSurfaceSpec(
            role = GlassRole.Content,
            level = GlassLevel.Full,
            tintAlpha = .20f,
            intensity = .78f,
            cornerRadiusDp = 14f,
        ),
        tint = Color.Black,
        modifier = modifier.fillMaxWidth().heightIn(min = fieldHeight),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = fieldHeight).focusRequester(focusRequester)
                .border(if (focused) 1.25.dp else 0.dp, edgeColor, shape)
                .padding(start = 17.dp, end = if (trailing == null) 17.dp else 6.dp),
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = Color.White.copy(alpha = if (enabled) .94f else .45f),
            ),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interactionSource,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = .42f),
                            )
                        }
                        innerTextField()
                    }
                    trailing?.invoke()
                }
            },
        )
    }
}
