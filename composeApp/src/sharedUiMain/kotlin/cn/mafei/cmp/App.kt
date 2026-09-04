package cn.mafei.cmp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiSurface
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.glass.GlassLevel
import cn.mafei.cmp.common.glass.GlassRole
import cn.mafei.cmp.common.glass.GlassSurfaceSpec

@Composable
fun App() {
    MaFeiTheme {
        MaFeiSurface(
            spec = GlassSurfaceSpec(
                role = GlassRole.Content,
                level = GlassLevel.Opaque,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("MaFei 2.0 CMP")
                Text("Foundation ready · UI migration has not started")
            }
        }
    }
}
