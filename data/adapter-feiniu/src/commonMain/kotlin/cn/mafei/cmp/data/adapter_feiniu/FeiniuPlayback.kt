package cn.mafei.cmp.data.adapter_feiniu

import cn.mafei.cmp.core.domain.AuthenticationError
import cn.mafei.cmp.core.domain.ClientConfigError
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.data.adapter_contract.AdapterCapability
import cn.mafei.cmp.data.adapter_contract.PlaybackEvent
import cn.mafei.cmp.data.adapter_contract.PlaybackOptions
import cn.mafei.cmp.data.adapter_contract.PlaybackPreparation
import cn.mafei.cmp.data.adapter_contract.PlaybackQuality
import cn.mafei.cmp.data.adapter_contract.PlaybackTrack
import cn.mafei.cmp.data.adapter_contract.PlaybackTrackKind
import cn.mafei.cmp.data.adapter_contract.PlaybackTransport
import cn.mafei.cmp.data.adapter_contract.RefusalReason
import cn.mafei.cmp.data.adapter_contract.requireCapability
import cn.mafei.cmp.providers.feiniu.core.FeiniuDigest
import cn.mafei.cmp.providers.feiniu.core.FeiniuIdentity
import cn.mafei.cmp.providers.feiniu.core.FeiniuOfficialStreamRequest
import cn.mafei.cmp.providers.feiniu.core.FeiniuOfficialStreamRequestHeader
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayInfoData
import cn.mafei.cmp.providers.feiniu.core.FeiniuPlayRecordRequest
import cn.mafei.cmp.providers.feiniu.core.FeiniuStreamListData
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

/** Owns negotiated playback handles and accepts reports only for its prepared streams. */
internal class FeiniuPlayback(private val adapterSession: FeiniuAdapterSession) {
    private val preparedStreams = mutableListOf<PlaybackPreparation.Ready>()

    private val resolution get() = adapterSession.resolution
    private val provider get() = adapterSession.provider
    private val capabilities get() = adapterSession.capabilities

    suspend fun preparePlayback(item: MediaId, options: PlaybackOptions): PlaybackPreparation {
        println("[MaFeiPlayback] feiniu prepare start item=${item.value}")
        val operation = adapterSession.currentOperation()
        val selectedMediaGuid = options.mediaSourceId?.trim()?.takeIf(String::isNotEmpty)
        val initialInfo = adapterSession.translate {
            adapterSession.facade.playback.playInfo(
                resolution,
                itemGuid = item.value,
                mediaGuid = selectedMediaGuid,
                pinnedOperation = operation,
            )
        }
        println("[MaFeiPlayback] feiniu play/info completed")
        val playableItemGuid = initialInfo.guid?.trim()?.takeIf(String::isNotEmpty) ?: item.value
        val info = if (playableItemGuid != item.value) {
            try {
                adapterSession.translate {
                    adapterSession.facade.playback.playInfo(
                        resolution,
                        itemGuid = playableItemGuid,
                        mediaGuid = selectedMediaGuid,
                        pinnedOperation = operation,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                initialInfo
            }
        } else {
            initialInfo
        }
        val streams = adapterSession.translate {
            adapterSession.facade.playback.streamList(resolution, playableItemGuid, beforePlay = 1)
        }
        println("[MaFeiPlayback] feiniu stream/list completed files=${streams.files.size}")
        val preferredMediaGuid = selectedMediaGuid
            ?: info.mediaGuid?.trim()?.takeIf(String::isNotEmpty)
        val file = streams.files.firstOrNull { it.guid?.trim() == preferredMediaGuid }
            ?: streams.files.firstOrNull()
        val video = streams.videoStreams.firstOrNull { stream ->
            val videoGuid = info.videoGuid?.trim()
            !videoGuid.isNullOrEmpty() && stream.guid?.trim() == videoGuid
        } ?: streams.videoStreams.firstOrNull { stream ->
            val fileGuid = file?.guid?.trim()
            !fileGuid.isNullOrEmpty() && stream.mediaGuid?.trim() == fileGuid
        } ?: streams.videoStreams.firstOrNull()
        val mediaGuid = file?.guid?.trim()?.takeIf(String::isNotEmpty)
            ?: video?.mediaGuid?.trim()?.takeIf(String::isNotEmpty)
            ?: info.mediaGuid?.trim()?.takeIf(String::isNotEmpty)
            ?: preferredMediaGuid
            ?: return PlaybackPreparation.Refused(
                RefusalReason.NoPlayableSource,
                detail = "play/info offered no media_guid for ${item.value}",
            )
        val fileName = file?.fileName?.trim().orEmpty().ifEmpty { file?.path?.trim().orEmpty() }
        if (fileName.lowercase().endsWith(".strm")) {
            println("[MaFeiPlayback] feiniu STRM detected")
            resolveLegacyStrmDirectLink(
                item = item,
                info = info,
                streams = streams,
                options = options,
                mediaGuid = mediaGuid,
                operation = operation,
            )?.let { ready ->
                println("[MaFeiPlayback] feiniu STRM official stream ready")
                preparedStreams.add(ready)
                return ready
            }
            println("[MaFeiPlayback] feiniu STRM official stream unavailable, using range fallback")
        }

        // 旧版 FeiniuVideoPlaybackProvider 的分支，两个方向都要有：
        //   * 没给码率上限 → 原画直连（range），也就是这条默认路径；
        //   * 给了码率上限 → 让 fnOS 转码（POST /play/play）。
        // 普通文件本身没有可选档位，档位只能由服务端重编产出。缺了转码分支，清晰度
        // 菜单要么根本不出现，要么点了只是重新加载同一个原文件。
        val requestedBitrate = options.maxStreamingBitrate?.takeIf { it > 0 }
        if (requestedBitrate != null) {
            println("[MaFeiPlayback] feiniu transcoded path requested bitrate=$requestedBitrate")
            val transcoded = resolveTranscodedStream(
                item = item,
                info = info,
                streams = streams,
                options = options,
                mediaGuid = mediaGuid,
                video = video,
                requestedBitrate = requestedBitrate,
                operation = operation,
            )
            if (transcoded is PlaybackPreparation.Ready) preparedStreams.add(transcoded)
            return transcoded
        }

        // 原画直连本身没有预制档位，但 fnOS 能按需重编，所以 `qualities = null`：
        // 界面据此生成旧版的码率阶梯；用户选中后重新进入上面的 /play/play 分支。
        val streamUrl = adapterSession.facade.playback.directRangeUrl(resolution.preferred.baseUrl, mediaGuid)
        val ready = PlaybackPreparation.Ready(
            streamUrl = streamUrl,
            subtitleUrl = externalSubtitleUrl(streams, info, options.subtitleStreamIndex),
            tracks = playbackTracks(streams, info, options),
            transport = PlaybackTransport.DirectPlay,
            qualities = null,
            headers = adapterSession.facade.assets.playbackHeaders(
                operation.credentials,
                resolution.preferred.requiresRelayCookie,
            ),
            multiConnectionAllowed = !fileName.lowercase().endsWith(".strm"),
            playSessionId = null,
            reportingContext = FeiniuReportingContext(
                itemGuid = info.guid?.takeIf { it.isNotBlank() } ?: item.value,
                mediaGuid = mediaGuid,
                videoGuid = video?.guid?.takeIf(String::isNotBlank)
                    ?: info.videoGuid?.takeIf(String::isNotBlank)
                    ?: mediaGuid,
                audioGuid = info.audioGuid ?: "",
                subtitleGuid = info.subtitleGuid ?: "",
                resolution = video?.resolutionType.orEmpty(),
                bitrate = video?.bitRate?.toLong() ?: 0L,
                durationSeconds = info.item?.durationSeconds,
                playLink = "/v/api/v1/media/range/$mediaGuid",
            ).encode(),
        )
        preparedStreams.add(ready)
        return ready
    }

    /**
     * fnOS 的轨道目录来自 `/stream` 的 stream list，选中项来自 `play/info` 的
     * audio_guid / subtitle_guid。index 用 fnOS 自己的 stream index，重新协商时按
     * index 反查 guid。
     *
     * 直连（range 原文件）路径下，音轨/内嵌字幕都在播放器手里，重新请求同一 URL
     * 不会换轨 —— `renegotiate = false`，由原生 runtime 切换；外挂字幕有自己的
     * `/subtitle/dl` 文件，走 provider 重新协商。
     */
    private fun playbackTracks(
        streams: FeiniuStreamListData,
        info: FeiniuPlayInfoData,
        options: PlaybackOptions,
        /**
         * 转码流（`/play/play`）：音轨/字幕由 fnOS 在服务端选择，换轨必须重新请求，
         * 所以每条轨道都 `renegotiate = true`；直连原文件则相反（内嵌轨只能由
         * 播放器内部切换）。
         */
        transcoded: Boolean = false,
        /** 转码响应：它才是「实际选了哪条轨」的权威来源。 */
        negotiated: cn.mafei.cmp.providers.feiniu.core.FeiniuPlayPlayData? = null,
    ): List<PlaybackTrack> {
        val selectedAudioGuid = info.audioGuid?.takeIf { it.isNotBlank() }
        val selectedSubtitleGuid = info.subtitleGuid?.takeIf { it.isNotBlank() }
        val selectedAudioIndex = negotiated?.audioIndex
            ?: options.audioStreamIndex
            ?: streams.audioStreams.firstOrNull { it.guid?.trim() == selectedAudioGuid }?.index
            ?: streams.audioStreams.firstOrNull { it.isDefault == 1 }?.index
            ?: streams.audioStreams.firstOrNull()?.index
        val selectedSubtitleIndex = negotiated?.subtitleIndex
            ?: when (val requested = options.subtitleStreamIndex) {
                null -> streams.subtitleStreams.firstOrNull { it.guid?.trim() == selectedSubtitleGuid }?.index
                    ?: streams.subtitleStreams.firstOrNull { it.isDefault == 1 }?.index
                else -> requested
            }
        val audioTracks = streams.audioStreams.mapNotNull { stream ->
            val index = stream.index ?: return@mapNotNull null
            PlaybackTrack(
                index = index,
                kind = PlaybackTrackKind.Audio,
                displayTitle = stream.feiniuAudioDisplayTitle(),
                title = stream.title?.trim()?.takeIf { it.isNotEmpty() },
                language = stream.language?.trim()?.takeIf { it.isNotEmpty() },
                codec = stream.codecName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: stream.codecType?.trim()?.takeIf { it.isNotEmpty() },
                channels = stream.channels,
                selected = index == selectedAudioIndex,
                isExternal = false,
                // 直连原文件：换音轨只能在播放器内部完成；转码流则要 provider 重新协商。
                renegotiate = transcoded,
            )
        }
        val subtitleTracks = streams.subtitleStreams.mapNotNull { stream ->
            val index = stream.index ?: return@mapNotNull null
            val external = (stream.isExternal ?: 0) > 0 || (stream.extraFile ?: 0) > 0
            PlaybackTrack(
                index = index,
                kind = PlaybackTrackKind.Subtitle,
                displayTitle = stream.feiniuSubtitleDisplayTitle(),
                title = stream.title?.trim()?.takeIf { it.isNotEmpty() },
                language = stream.language?.trim()?.takeIf { it.isNotEmpty() },
                codec = stream.codecName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: stream.codecType?.trim()?.takeIf { it.isNotEmpty() }
                    ?: stream.format?.trim()?.takeIf { it.isNotEmpty() },
                channels = null,
                selected = selectedSubtitleIndex != null && selectedSubtitleIndex >= 0 &&
                    index == selectedSubtitleIndex,
                isExternal = external,
                // 外挂字幕始终要 provider 给文件；转码流的字幕也在服务端选。
                renegotiate = transcoded || external,
            )
        }
        return audioTracks + subtitleTracks
    }

    /**
     * 外挂字幕 URL：按选中轨（或默认轨）的 guid 走 fnOS 的 `/subtitle/dl`。内嵌
     * 字幕没有独立文件，返回 null，由播放器从原文件里取。
     */
    private fun externalSubtitleUrl(
        streams: FeiniuStreamListData,
        info: FeiniuPlayInfoData,
        requestedSubtitleIndex: Int?,
    ): String? {
        val track = selectedSubtitleStream(streams, info, requestedSubtitleIndex) ?: return null
        if ((track.isExternal ?: 0) <= 0 && (track.extraFile ?: 0) <= 0) return null
        val guid = track.guid?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return adapterSession.facade.assets.subtitleDownloadUrl(resolution.preferred.baseUrl, guid)
    }

    /**
     * Mechanical equivalent of the old HarmonyOS STRM path. fnOS returns 8192
     * from play/play for direct-original STRM sources; the actual cloud URL is
     * obtained from /stream and must never receive Feiniu credentials.
     */
    private suspend fun resolveLegacyStrmDirectLink(
        item: MediaId,
        info: FeiniuPlayInfoData,
        streams: FeiniuStreamListData,
        options: PlaybackOptions,
        mediaGuid: String,
        operation: cn.mafei.cmp.providers.feiniu.core.FeiniuSessionSnapshot,
    ): PlaybackPreparation.Ready? {
        println("[MaFeiPlayback] feiniu STRM user/info start")
        val sourceName = try {
            adapterSession.translate { adapterSession.facade.auth.userInfo(resolution, pinnedOperation = operation) }
                .sources.firstOrNull()?.sourceName?.trim().orEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (authentication: AuthenticationError) {
            throw authentication
        } catch (_: Exception) {
            return null
        }
        println("[MaFeiPlayback] feiniu STRM user/info completed")
        println("[MaFeiPlayback] feiniu STRM official /stream start")
        val official = try {
            adapterSession.translate {
                adapterSession.facade.playback.officialStream(
                    resolution = resolution,
                    request = FeiniuOfficialStreamRequest(
                        mediaGuid = mediaGuid,
                        ip = FeiniuDigest.md5(sourceName),
                        header = FeiniuOfficialStreamRequestHeader(listOf(FeiniuIdentity.USER_AGENT)),
                        level = 1,
                    ),
                    pinnedOperation = operation,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (authentication: AuthenticationError) {
            throw authentication
        } catch (_: Exception) {
            return null
        }
        println("[MaFeiPlayback] feiniu STRM official /stream completed")
        // fnOS 的清晰度就是上游给的一组签名直链（每个自带 resolution / bitrate）。
        // 我们不造档位表，只把能直连的那几条摆出来，然后按用户给的上限挑一条：
        // 不超过上限里最大的一条；都超上限就退到最小的一条（并如实上报实际档位，
        // 界面据此显示真正在播的档，而不是用户点的那一档）。
        val ladder = (official.directLinkQualities + official.qualities).httpQualities()
        val ceiling = options.maxStreamingBitrate?.takeIf { it > 0 }
        val quality = ladder.pickQuality(ceiling) ?: return null
        val streamUrl = quality.url?.trim().orEmpty()
        val appliedBitrate = ceiling?.let { quality.bitrate?.takeIf { bitrate -> bitrate > 0 }?.toInt() }
        val playbackStreams = mergeFeiniuOfficialStreams(
            listed = streams,
            entries = listOf(mediaGuid to official),
        )
        return PlaybackPreparation.Ready(
            streamUrl = streamUrl,
            subtitleUrl = externalSubtitleUrl(playbackStreams, info, options.subtitleStreamIndex),
            tracks = playbackTracks(playbackStreams, info, options),
            headers = official.header.toPlaybackHeaders(),
            allowInsecureTls = streamUrl.startsWith("https://", ignoreCase = true),
            multiConnectionAllowed = false,
            playSessionId = null,
            // fnOS 不重编：它只是把上游那条现成的流交给播放器。
            transport = PlaybackTransport.DirectStream,
            appliedBitrate = appliedBitrate,
            qualities = ladder.mapNotNull { candidate ->
                candidate.bitrate?.takeIf { it > 0 }?.toInt()?.let { bitrate ->
                    PlaybackQuality(bitrate = bitrate, label = candidate.resolution?.takeIf(String::isNotBlank))
                }
            },
            reportingContext = FeiniuReportingContext(
                itemGuid = info.guid?.takeIf(String::isNotBlank) ?: item.value,
                mediaGuid = mediaGuid,
                videoGuid = info.videoGuid?.takeIf(String::isNotBlank) ?: mediaGuid,
                audioGuid = info.audioGuid.orEmpty(),
                subtitleGuid = info.subtitleGuid.orEmpty(),
                resolution = quality.resolution.orEmpty(),
                bitrate = quality.bitrate ?: 0L,
                durationSeconds = info.item?.durationSeconds,
                // Preserve the Feiniu resource identity for progress reports;
                // never send the signed third-party URL back to fnOS.
                playLink = "/v/api/v1/media/range/$mediaGuid",
            ).encode(),
        )
    }

    /**
     * Legacy `FeiniuVideoPlaybackProvider.playTranscoded` — POST /v/api/v1/play/play.
     *
     * fnOS re-encodes to the requested ceiling and answers with an expiring
     * `play_link` plus the tracks it actually selected. This is the only way an
     * ordinary file can offer qualities: the range route serves the original
     * bytes and ignores every ceiling, so without this branch the chrome either
     * hides the quality menu or shows one that changes nothing.
     *
     * The response is authoritative for the selected tracks — the server may
     * answer with different indexes or an external subtitle file than the ones
     * requested.
     */
    private suspend fun resolveTranscodedStream(
        item: MediaId,
        info: FeiniuPlayInfoData,
        streams: FeiniuStreamListData,
        options: PlaybackOptions,
        mediaGuid: String,
        video: cn.mafei.cmp.providers.feiniu.core.FeiniuVideoStream?,
        requestedBitrate: Int,
        operation: cn.mafei.cmp.providers.feiniu.core.FeiniuSessionSnapshot,
    ): PlaybackPreparation {
        val audio = selectedAudioStream(streams, info, options.audioStreamIndex)
        val subtitle = selectedSubtitleStream(streams, info, options.subtitleStreamIndex)
        val session = try {
            adapterSession.translate {
                adapterSession.facade.playback.play(
                    resolution = resolution,
                    request = cn.mafei.cmp.providers.feiniu.core.FeiniuPlayPlayRequest(
                        mediaGuid = mediaGuid,
                        videoGuid = video?.guid?.takeIf(String::isNotBlank)
                            ?: info.videoGuid?.takeIf(String::isNotBlank)
                            ?: mediaGuid,
                        videoEncoder = video?.codecName?.takeIf(String::isNotBlank) ?: "h264",
                        resolution = video?.resolutionType.orEmpty(),
                        bitrate = requestedBitrate.toLong(),
                        // 与旧版一致：服务端从当前秒数生成转码流，播放器加载后仍会
                        // 再 Seek 一次，兼顾服务端切片与原生播放器的最终落点。
                        startTimestamp = (options.startPositionMs ?: 0L).coerceAtLeast(0L) / 1_000L,
                        audioEncoder = audio?.codecName?.takeIf(String::isNotBlank) ?: "aac",
                        audioGuid = audio?.guid?.trim().orEmpty(),
                        subtitleGuid = subtitle?.guid?.trim().orEmpty(),
                        channels = (audio?.channels ?: 2).coerceAtLeast(1),
                        forcedSdr = 0,
                    ),
                    pinnedOperation = operation,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (authentication: AuthenticationError) {
            throw authentication
        } catch (failure: Exception) {
            return PlaybackPreparation.Refused(
                RefusalReason.NoPlayableSource,
                detail = "fnOS play/play failed: ${failure.message}",
            )
        }
        val baseUrl = resolution.preferred.baseUrl
        // play_link 是服务端签发的临时链接：过解析器（同源、可解析），失败即拒绝。
        val streamUrl = session.resolvePlayLink(baseUrl)
            ?: return PlaybackPreparation.Refused(
                RefusalReason.NoPlayableSource,
                detail = "fnOS play/play returned no playable link for ${item.value}",
            )
        val negotiatedSubtitleIndex = session.data.subtitleIndex ?: options.subtitleStreamIndex
        // 转码响应可能给出一个独立字幕文件；没有就走原来的外挂字幕 URL 构造。
        val negotiatedSubtitleUrl = session.data.subtitleLink
            ?.takeIf { session.data.isSubtitleExternal == 1 }
            ?.let { session.resolveSubtitleLink(baseUrl) }
        val subtitleUrl = negotiatedSubtitleUrl
            ?: externalSubtitleUrl(streams, info, negotiatedSubtitleIndex)
        val ready = PlaybackPreparation.Ready(
            streamUrl = streamUrl,
            subtitleUrl = subtitleUrl,
            tracks = playbackTracks(streams, info, options, transcoded = true, negotiated = session.data),
            headers = adapterSession.facade.assets.playbackHeaders(operation.credentials, session.requiresRelayCookie),
            multiConnectionAllowed = true,
            playSessionId = null,
            transport = PlaybackTransport.Transcode,
            // 档位由服务端重编产出 → 按需生成，界面可以自己造档位表。
            qualities = null,
            appliedBitrate = requestedBitrate,
            reportingContext = FeiniuReportingContext(
                itemGuid = info.guid?.takeIf { it.isNotBlank() } ?: item.value,
                mediaGuid = session.data.mediaGuid?.takeIf { it.isNotBlank() } ?: mediaGuid,
                videoGuid = session.data.videoGuid?.takeIf { it.isNotBlank() }
                    ?: video?.guid?.takeIf(String::isNotBlank)
                    ?: info.videoGuid?.takeIf(String::isNotBlank)
                    ?: mediaGuid,
                audioGuid = session.data.audioGuid?.takeIf { it.isNotBlank() } ?: audio?.guid.orEmpty(),
                subtitleGuid = session.data.subtitleGuid?.takeIf { it.isNotBlank() } ?: subtitle?.guid.orEmpty(),
                resolution = video?.resolutionType.orEmpty(),
                bitrate = requestedBitrate.toLong(),
                durationSeconds = info.item?.durationSeconds,
                // 旧版同样把 play/play 返回的链接回报给 fnOS（它不是第三方云链接）。
                playLink = session.data.playLink?.trim().orEmpty()
                    .ifEmpty { "/v/api/v1/media/range/$mediaGuid" },
            ).encode(),
        )
        return ready
    }

    private fun selectedAudioStream(
        streams: FeiniuStreamListData,
        info: FeiniuPlayInfoData,
        requestedIndex: Int?,
    ): cn.mafei.cmp.providers.feiniu.core.FeiniuAudioStream? = when (requestedIndex) {
        null -> streams.audioStreams.firstOrNull { it.guid?.trim() == info.audioGuid?.trim() }
            ?: streams.audioStreams.firstOrNull { it.isDefault == 1 }
            ?: streams.audioStreams.firstOrNull()
        else -> streams.audioStreams.firstOrNull { it.index == requestedIndex }
    }

    private fun selectedSubtitleStream(
        streams: FeiniuStreamListData,
        info: FeiniuPlayInfoData,
        requestedIndex: Int?,
    ): cn.mafei.cmp.providers.feiniu.core.FeiniuSubtitleStream? = when (requestedIndex) {
        // 明确的「关字幕」：不带字幕轨去协商。
        null -> streams.subtitleStreams.firstOrNull { it.guid?.trim() == info.subtitleGuid?.trim() }
            ?: streams.subtitleStreams.firstOrNull { it.isDefault == 1 }
        else -> if (requestedIndex < 0) null else streams.subtitleStreams.firstOrNull { it.index == requestedIndex }
    }

    private fun List<cn.mafei.cmp.providers.feiniu.core.FeiniuDirectLinkQuality>.httpQualities() =
        filter { quality ->
            val url = quality.url?.trim().orEmpty()
            url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
        }

    /**
     * Ceiling selection over the upstream ladder. No ceiling keeps the legacy
     * behaviour (first reachable link); with one, the largest rendition at or
     * below it wins, and if every rendition exceeds it the smallest one is
     * served anyway — the caller reports its real bitrate, so the chrome shows
     * what is actually playing. Never re-request the same link and claim a
     * different quality.
     */
    private fun List<cn.mafei.cmp.providers.feiniu.core.FeiniuDirectLinkQuality>.pickQuality(
        ceiling: Int?,
    ): cn.mafei.cmp.providers.feiniu.core.FeiniuDirectLinkQuality? {
        if (isEmpty()) return null
        if (ceiling == null) return first()
        return filter { (it.bitrate ?: 0L) in 1..ceiling.toLong() }
            .maxByOrNull { it.bitrate ?: 0L }
            ?: minByOrNull { it.bitrate ?: 0L }
            ?: first()
    }

    private fun JsonObject?.toPlaybackHeaders(): Map<String, String> = buildMap {
        this@toPlaybackHeaders?.forEach { (rawName, rawValue) ->
            val name = rawName.trim()
            if (name.isEmpty()) return@forEach
            val values = when (rawValue) {
                is JsonArray -> rawValue.mapNotNull {
                    (it as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty)
                }
                is JsonPrimitive -> listOfNotNull(rawValue.content.trim().takeIf(String::isNotEmpty))
                else -> listOf(rawValue.toString())
            }
            if (values.isNotEmpty()) {
                put(name, values.joinToString(if (name.equals("Cookie", ignoreCase = true)) "; " else ", "))
            }
        }
    }

    suspend fun reportPlayback(stream: PlaybackPreparation.Ready, event: PlaybackEvent) {
        adapterSession.currentOperation()
        requireCapability(capabilities, AdapterCapability.PlaybackReporting)
        if (preparedStreams.none { it === stream }) {
            throw ClientConfigError("reportPlayback rejected a stream not prepared by this adapter instance")
        }
        val context = FeiniuReportingContext.decode(stream.reportingContext)
            ?: throw ClientConfigError("Feiniu reporting context is missing or malformed")
        // Contract positions are ms; the wire reports seconds. Legacy protocol
        // skips reports entirely at ts <= 0 (no zero-position writes).
        val timestampSeconds = event.positionMs / 1000L
        if (timestampSeconds <= 0L) return
        val durationSeconds = event.durationMs?.let { it / 1000L } ?: context.durationSeconds ?: 0L
        adapterSession.translate {
            adapterSession.facade.playback.record(
                resolution,
                FeiniuPlayRecordRequest(
                    itemGuid = context.itemGuid,
                    mediaGuid = context.mediaGuid,
                    videoGuid = context.videoGuid,
                    audioGuid = context.audioGuid,
                    subtitleGuid = context.subtitleGuid.ifBlank { null },
                    resolution = context.resolution,
                    bitrate = context.bitrate,
                    timestampSeconds = timestampSeconds,
                    durationSeconds = durationSeconds,
                    playLink = context.playLink,
                ),
                pinnedOperation = adapterSession.currentOperation(),
            )
        }
    }

}

/**
 * Opaque reporting context carried inside [PlaybackPreparation.Ready] — the
 * record endpoint needs the full negotiation handle set (guids, resolution,
 * bitrate, duration and the expiring play link) beyond what the event carries.
 * Encoded as JSON; never persisted, never derived by re-parsing stream URLs.
 */
@Serializable
private data class FeiniuReportingContext(
    val itemGuid: String,
    val mediaGuid: String,
    val videoGuid: String,
    val audioGuid: String,
    val subtitleGuid: String,
    val resolution: String,
    val bitrate: Long,
    val durationSeconds: Long? = null,
    val playLink: String? = null,
) {
    fun encode(): String = Json.Default.encodeToString(serializer(), this)

    companion object {
        fun decode(raw: String?): FeiniuReportingContext? = raw?.let {
            runCatching { Json.Default.decodeFromString(serializer(), it) }.getOrNull()
        }
    }
}
