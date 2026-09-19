package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.secure_credential_api.CredentialFailureKind

/** Authentication succeeded but admission/persistence failed. Never retain raw backend causes. */
class ConnectionSaveException(val credentialFailure: CredentialFailureKind? = null) :
    Exception("Authenticated session could not be saved")

/** Stable user-safe messages; no response bodies, server URLs or raw exception text. */
fun connectFailureMessage(error: Exception): String = when (error) {
    is ConnectionSaveException -> when (error.credentialFailure) {
        CredentialFailureKind.AccessDenied -> "服务器认证已通过，但设备拒绝保存登录凭证。请检查应用签名和安全存储权限，无需修改服务器密码。"
        CredentialFailureKind.BackendUnavailable -> "服务器认证已通过，但设备安全存储暂不可用。请解锁设备或检查系统钥匙串后重试。"
        else -> "服务器认证已通过，但无法保存登录状态。请检查设备存储后重试。"
    }
    is AuthenticationError -> "登录未通过，请检查账号和密码"
    is AuthorizationError -> "此账号没有访问权限"
    is ClientConfigError -> "请检查服务器地址和用户名"
    is NetworkUnreachableError, is TimeoutError -> "无法连接服务器，请检查地址和网络"
    is ProtocolDecodingError -> "服务器登录响应不完整或不受支持"
    else -> "连接失败，请检查服务器和网络后重试"
}
