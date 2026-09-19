package cn.mafei.cmp.feature.connect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cn.mafei.cmp.core.domain.ProviderKind

/** Keeps an unfinished login draft stable while adaptive navigation changes its presentation. */
@Stable
class ConnectFormState internal constructor() {
    var provider by mutableStateOf(ProviderKind.Jellyfin)
    var baseUrl by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var rememberPassword by mutableStateOf(true)
}

@Composable
fun rememberConnectFormState(): ConnectFormState = remember { ConnectFormState() }
