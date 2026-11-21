package cn.mafei.cmp.common.glass

import android.os.Build

internal actual fun supportsFrostedSampling(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
