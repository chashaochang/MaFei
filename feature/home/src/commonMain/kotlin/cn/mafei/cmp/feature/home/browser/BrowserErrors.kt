package cn.mafei.cmp.feature.home

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.repositories.*

/** Never display exception messages, URLs, response bodies or credential-bearing causes. */
fun authenticationRecoveryMessage(kind: AuthFailureKind): String = when (kind) {
    AuthFailureKind.TokenExpired, AuthFailureKind.TokenRevoked -> "当前账号登录已过期，请重新登录"
    AuthFailureKind.AccountLocked -> "当前账号已被锁定，请联系服务器管理员"
    AuthFailureKind.MfaRequired -> "当前账号需要重新验证，请重新登录"
    AuthFailureKind.InvalidCredentials -> "当前账号认证未通过，请重新登录"
}

internal fun browseErrorMessage(error: Exception): String = when (error) {
    is AuthenticationError -> authenticationRecoveryMessage(error.kind)
    is AuthorizationError -> "当前账号没有访问权限"
    is NetworkUnreachableError, is TimeoutError -> "无法连接服务器，请检查网络后重试"
    is ResourceNotFoundError -> "内容已不存在或不可见"
    is ProtocolDecodingError -> "服务器返回了无法识别的数据"
    else -> "加载失败，请重试"
}
