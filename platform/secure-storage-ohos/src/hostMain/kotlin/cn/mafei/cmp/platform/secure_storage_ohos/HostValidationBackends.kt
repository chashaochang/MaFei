package cn.mafei.cmp.platform.secure_storage_ohos

import cn.mafei.cmp.core.secure_credential_api.CredentialAccessException
import cn.mafei.cmp.core.secure_credential_api.CredentialFailureKind

// Production factory access must fail: host tests inject internal fake ports.
internal actual fun createAssetBackend(namespace: String): AssetBackend = unavailable()
internal actual fun createLongCredentialVault(privateDirectory: String, namespace: String): LongCredentialVault = unavailable()

private fun unavailable(): Nothing = throw CredentialAccessException(
    CredentialFailureKind.Unsupported, "OHOS storage is unavailable in the host test runner",
)
