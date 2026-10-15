package cn.mafei.cmp.core.secure_credential_api

/**
 * Non-sensitive handle under which a secret is stored. Plain storage may keep
 * aliases freely — the alias itself carries no credential material. Aliases
 * are namespaced by convention, e.g. `"account/<id>/token"`. Persisting an
 * alias is done through its plain [value].
 */
data class CredentialAlias(val value: String) {
    init {
        require(value.isNotBlank()) { "CredentialAlias must not be blank" }
        require(value.trim() == value) { "CredentialAlias must not be padded with whitespace" }
    }

    override fun toString(): String = value
}
