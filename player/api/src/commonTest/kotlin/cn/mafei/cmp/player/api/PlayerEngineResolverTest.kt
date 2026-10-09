package cn.mafei.cmp.player.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerEngineResolverTest {
    @Test fun autoUsesSystemForLegacyCompatibleSourceWhenAvailable() {
        val decision = PlayerEngineResolver.resolve(
            PlayerEngineType.Auto,
            PlayerEngineSourceFacts(container = "mp4", videoCodec = "h264", audioCodecs = listOf("aac")),
            systemAvailable = true,
        )
        assertEquals(PlayerEngineType.System, decision.engine)
        assertTrue(decision.reason.contains("系统播放器"))
    }

    @Test fun hdr444FallsBackToSoftwareMpv() {
        val decision = PlayerEngineResolver.resolve(
            PlayerEngineType.Auto,
            PlayerEngineSourceFacts(container = "mkv", videoCodec = "hevc", pixelFormat = "yuv444p10le"),
            systemAvailable = true,
        )
        assertEquals(PlayerEngineType.Mpv, decision.engine)
        assertTrue(decision.forceMpvSoftwareDecode)
        assertTrue(decision.reason.contains("像素格式"))
    }

    @Test fun fixedSystemStillFallsBackInsteadOfPretendingSupport() {
        val decision = PlayerEngineResolver.resolve(
            PlayerEngineType.System,
            PlayerEngineSourceFacts(container = "iso", videoCodec = "hevc"),
            systemAvailable = true,
        )
        assertEquals(PlayerEngineType.Mpv, decision.engine)
        assertTrue(decision.reason.contains("回退"))
    }

    @Test fun unavailableSystemCapabilityIsExplicitInReason() {
        val decision = PlayerEngineResolver.resolve(
            PlayerEngineType.Auto,
            PlayerEngineSourceFacts(container = "mp4", videoCodec = "h264"),
            systemAvailable = false,
        )
        assertEquals(PlayerEngineType.Mpv, decision.engine)
        assertTrue(decision.reason.contains("暂不提供系统播放器"))
    }
}
