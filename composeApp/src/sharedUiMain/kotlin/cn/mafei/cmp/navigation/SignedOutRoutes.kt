package cn.mafei.cmp

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Login-flow keys. Keep package and type names stable for serialized route identities. */
@Serializable
internal sealed interface SignedOutRoute : NavKey

@Serializable
internal data object ConnectRoute : SignedOutRoute

@Serializable
internal data object AccountHistoryRoute : SignedOutRoute

@Serializable
internal data object ServerDiscoveryRoute : SignedOutRoute
