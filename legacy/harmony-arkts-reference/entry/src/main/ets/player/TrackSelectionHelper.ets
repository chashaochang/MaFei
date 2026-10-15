import { PlayerPageViewModel } from "../features/player/PlayerPageViewModel";
import {
  MediaPlaybackSource,
  MediaPlaybackStream,
  MediaPlaybackStreamType,
  MediaPlaybackSubtitleDeliveryMethod
} from "../media/playback/MediaPlaybackModels";
import { PlaybackRequestResult } from "./queue/PlaybackRequestResult";

export class TrackSelectionHelper {
  viewModel: PlayerPageViewModel

  constructor(viewModel: PlayerPageViewModel) {
    this.viewModel = viewModel
  }

  get mediaSourceOrNull(): MediaPlaybackSource | undefined {
    return this.viewModel.mediaSourceOrNull
  }

  selectInitialTracks() {
    if (!this.mediaSourceOrNull) {
      return
    }
    let mediaSource = this.mediaSourceOrNull
    if (mediaSource.selectedAudioStream) {
      this.selectPlayerAudioTrack(mediaSource, mediaSource.selectedAudioStream, true)
    }
    this.selectSubtitleTrack3(mediaSource, mediaSource.selectedSubtitleStream, true)
  }

  /**
   * Select an audio track in the media source and apply changes to the current player, if necessary and possible.
   *
   * @param mediaStreamIndex the [MediaStream.index] that should be selected
   * @return true if the audio track was changed
   */
  async selectAudioTrack(selectedMediaStream: MediaPlaybackStream): Promise<PlaybackRequestResult> {
    let mediaSource = this.viewModel.mediaSourceOrNull
    if (!mediaSource) {
      return PlaybackRequestResult.Failure
    }
    if (selectedMediaStream.Type != MediaPlaybackStreamType.Audio) {
      throw new Error('Selected stream must be an audio stream');
    }

    // For transcoding and external streams, we need to restart playback
    if (mediaSource.usesServerTranscoding() || selectedMediaStream.IsExternal) {
      return await this.viewModel.queueManager.selectAudioStreamAndRestartPlayback(selectedMediaStream)
    }
    let success = this.selectPlayerAudioTrack(mediaSource, selectedMediaStream, false)
    if (success) {
      this.viewModel.logTracks()
    }
    return success ? PlaybackRequestResult.Success : PlaybackRequestResult.Failure
  }

  private selectPlayerAudioTrack(mediaSource: MediaPlaybackSource, audioStream: MediaPlaybackStream,
    initial: boolean): boolean {
    if (mediaSource.usesServerTranscoding()) {
      // Transcoding does not require explicit audio selection
      return true
    }
    if (mediaSource.audioStreams.length == 1 || !initial && audioStream === mediaSource.selectedAudioStream) {
      return true
    }
    if (!mediaSource.selectAudioStream(audioStream)) {
      return false
    }
    this.viewModel.ui.selectedAudioStream = audioStream
    return true
  }

  async selectSubtitleTrack(mediaStream: MediaPlaybackStream): Promise<PlaybackRequestResult> {
    let mediaSource = this.viewModel.mediaSourceOrNull
    if (!mediaSource) {
      return PlaybackRequestResult.Failure
    }
    if (mediaStream?.DeliveryMethod == MediaPlaybackSubtitleDeliveryMethod.Encode) {
      return await this.viewModel.queueManager.selectSubtitleStreamAndRestartPlayback(mediaStream)
    }
    return this.selectSubtitleTrack3(mediaSource, mediaStream, false) ?
      PlaybackRequestResult.Success : PlaybackRequestResult.Failure
  }

  selectSubtitleTrack3(mediaSource: MediaPlaybackSource, subtitleStream: MediaPlaybackStream | undefined,
    initial: boolean): boolean {
    // Fast-pass: Skip execution on subsequent calls with the same selection
    if (!initial && subtitleStream === mediaSource.selectedSubtitleStream) {
      return true
    } else if (!mediaSource.selectSubtitleStream(subtitleStream)) { // Apply selection in media source, abort on failure
      return false
    }
    this.viewModel.ui.selectedSubtitleStream = mediaSource.selectedSubtitleStream

    // Apply selection in player
    if (subtitleStream == null) {
      // If no subtitle is selected, simply clear the selection and disable the subtitle renderer
      // trackSelector.clearSelectionAndDisableRendererByType(C.TRACK_TYPE_TEXT)
      this.viewModel.ui.currentSubtitle = ''
      this.viewModel.ui.subtitles = []
      return true
    }
    // let player = this.viewModel.playerOrNull
    // if(!player) return false
    switch (subtitleStream.DeliveryMethod) {
        case MediaPlaybackSubtitleDeliveryMethod.Encode:
          // Normally handled in selectSubtitleTrack(int) by restarting playback,
          // initial selection is always considered successful
          return true
        case MediaPlaybackSubtitleDeliveryMethod.Embed:
          // For embedded subtitles, we can match by the index of this stream in all embedded streams.
          // let embeddedStreamIndex = mediaSource.getEmbeddedStreamIndex(subtitleStream)
          // let subtitle = this.viewModel.initSource?.subtitleStreams[embeddedStreamIndex]
          // if(!subtitle) return false
          this.viewModel.selectSubtitle(subtitleStream)
          return true
      case MediaPlaybackSubtitleDeliveryMethod.External:
        // For external subtitles, we can simply match the ID that we set when creating the player media source.
        // for (group in player.currentTracks.groups) {
        //   if (group.getTrackFormat(0).id == "${ExternalSubtitleStream.ID_PREFIX}${subtitleStream.index}") {
        //     return trackSelector.selectTrackByTypeAndGroup(C.TRACK_TYPE_TEXT, group.mediaTrackGroup)
        //   }
        // }
        return true
      default:
        return false
    }
  }
}
