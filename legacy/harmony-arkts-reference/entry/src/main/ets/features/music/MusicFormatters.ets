export class MusicFormatters {
  static duration(durationMs: number): string {
    const totalSeconds = Math.max(0, Math.floor(durationMs / 1000))
    const hours = Math.floor(totalSeconds / 3600)
    const minutes = Math.floor((totalSeconds % 3600) / 60)
    const seconds = totalSeconds % 60
    const secondText = seconds.toString().padStart(2, '0')
    if (hours > 0) {
      return `${hours}:${minutes.toString().padStart(2, '0')}:${secondText}`
    }
    return `${minutes}:${secondText}`
  }
}
