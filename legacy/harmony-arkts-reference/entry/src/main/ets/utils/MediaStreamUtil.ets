import { MediaPlaybackStream } from "../media/playback/MediaPlaybackModels"

export class MediaStreamUtil {
  static isLandscape(mediaStream?: MediaPlaybackStream): boolean {
    if (mediaStream?.Width && mediaStream?.Height) {
      return mediaStream.Width > mediaStream.Height
    } else {
      return true
    }
  }

  // static aspectRational(mediaStream: MediaStream): Rational {
  //   if (mediaStream.Width && mediaStream.Height) {
  //     return mediaStream.Width > mediaStream.Height
  //   } else {
  //     return true
  //   }
  // }
}
