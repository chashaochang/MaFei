import AVFoundation
import UIKit
import MaFeiShared
import MediaPlayer

private final class VideoView: UIView {
    override class var layerClass: AnyClass { AVPlayerLayer.self }
}


/** Swift owns AVFoundation values; Kotlin owns commands, queue, lifecycle and reporting. */
final class ApplePlayerBridge: NSObject, IosAvPlayerBridge {
    private final class Playback {
        let player = AVPlayer()
        var proxy: AppleAuthorizedMediaProxy?
        weak var layer: AVPlayerLayer?
        var epoch = 0
        var seeking = false
        var wantsPlay = false
        var speed: Float = 1
        var failure: Int32 = 0
        var audioOnly = false
    }
    private var next: Int64 = 0
    private var players: [Int64: Playback] = [:]
    private var systemPlayer: Int64 = 0
    private var controlBinding: Int64 = 0
    private var controlHandler: ((String, KotlinDouble) -> Void)?
    private var commandTargets: [(MPRemoteCommand, Any)] = []
    private var mediaTitle = ""
    private var lastSystemUpdate: TimeInterval = 0

    func create() -> Int64 { next += 1; players[next] = Playback(); return next }
    func createVideoView() -> Any { let view = VideoView(); view.backgroundColor = .black; return view }
    func loadUrl(handle: Int64, url: String, headers: [String: String]) {
        guard let playback = players[handle] else { return }
        playback.failure = 0
        guard let url = URL(string: url), ["http", "https", "file"].contains(url.scheme ?? "") else {
            playback.failure = 2; return
        }
        playback.epoch += 1
        playback.proxy?.close(); playback.proxy = nil
        if !headers.isEmpty && ["http", "https"].contains(url.scheme ?? "") {
            playback.player.replaceCurrentItem(with: nil)
            let epoch = playback.epoch
            let proxy = AppleAuthorizedMediaProxy(source: url, headers: headers)
            playback.proxy = proxy
            proxy.start { [weak playback] local in DispatchQueue.main.async {
                guard let playback, playback.epoch == epoch else { proxy.close(); return }
                guard let local else { playback.failure = 1; return }
                playback.player.replaceCurrentItem(with: AVPlayerItem(url: local))
                if playback.wantsPlay && !playback.seeking { playback.player.rate = playback.speed }
            } }
            return
        }
        playback.player.replaceCurrentItem(with: AVPlayerItem(url: url))
    }
    func play(handle: Int64) { guard let p = players[handle] else { return }; p.wantsPlay = true; if !p.seeking { p.player.rate = p.speed } }
    func pause(handle: Int64) { players[handle]?.wantsPlay = false; players[handle]?.player.pause() }
    func stop(handle: Int64) {
        guard let p = players[handle] else { return }; p.epoch += 1; p.wantsPlay = false; p.seeking = false
        p.player.pause(); p.player.replaceCurrentItem(with: nil)
        p.proxy?.close(); p.proxy = nil
        p.audioOnly = false
        if systemPlayer == handle {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            systemPlayer = 0
        }
        if !players.values.contains(where: { $0.audioOnly }) {
            try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        }
    }
    func configureAudioOnly(handle: Int64, audioOnly: Bool) -> Bool {
        guard let p = players[handle] else { return false }
        p.audioOnly = audioOnly
        systemPlayer = handle
        do {
            // Video also plays with the silent switch on, but only music gets a background grant.
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: audioOnly ? .default : .moviePlayback)
            try AVAudioSession.sharedInstance().setActive(true)
            return audioOnly
        } catch { p.audioOnly = false; return false }
    }
    func bindSystemControls(title: String, canNext: Bool, canPrevious: Bool,
                            handler: @escaping (String, KotlinDouble) -> Void) -> Int64 {
        clearCommandTargets()
        controlBinding += 1
        let binding = controlBinding
        controlHandler = handler
        mediaTitle = title
        let center = MPRemoteCommandCenter.shared()
        func bind(_ command: MPRemoteCommand, _ name: String, enabled: Bool = true) {
            command.isEnabled = enabled
            guard enabled else { return }
            let target = command.addTarget { [weak self] event in
                guard let self, self.controlBinding == binding, self.controlHandler != nil else { return .noSuchContent }
                let value = (event as? MPChangePlaybackPositionCommandEvent)?.positionTime ?? 0
                DispatchQueue.main.async { [weak self] in
                    guard let self, self.controlBinding == binding else { return }
                    self.controlHandler?(name, KotlinDouble(value: name == "seek" ? value * 1000 : value))
                }
                return .success
            }
            commandTargets.append((command, target))
        }
        bind(center.playCommand, "play"); bind(center.pauseCommand, "pause")
        bind(center.togglePlayPauseCommand, "toggle"); bind(center.stopCommand, "stop")
        bind(center.changePlaybackPositionCommand, "seek")
        bind(center.nextTrackCommand, "next", enabled: canNext)
        bind(center.previousTrackCommand, "previous", enabled: canPrevious)
        updateSystemMetadata(force: true)
        return binding
    }
    func unbindSystemControls(binding: Int64) {
        guard binding == controlBinding else { return }
        controlBinding += 1
        controlHandler = nil
        clearCommandTargets()
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }
    private func clearCommandTargets() {
        commandTargets.forEach { $0.0.removeTarget($0.1); $0.0.isEnabled = false }
        commandTargets.removeAll()
    }
    private func updateSystemMetadata(force: Bool = false) {
        let now = Date.timeIntervalSinceReferenceDate
        guard force || now - lastSystemUpdate >= 1 else { return }
        guard controlHandler != nil, let player = players[systemPlayer] else { return }
        lastSystemUpdate = now
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [
            MPMediaItemPropertyTitle: mediaTitle,
            MPMediaItemPropertyPlaybackDuration: Double(milliseconds(player.player.currentItem?.duration)) / 1000,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: Double(milliseconds(player.player.currentTime())) / 1000,
            MPNowPlayingInfoPropertyPlaybackRate: player.player.rate,
        ]
    }
    func seek(handle: Int64, positionMs: Int64) {
        guard let p = players[handle] else { return }
        let epoch = p.epoch; p.seeking = true
        p.player.seek(to: CMTime(value: positionMs, timescale: 1000), toleranceBefore: .zero, toleranceAfter: .zero) { [weak p] _ in
            DispatchQueue.main.async { guard let p, p.epoch == epoch else { return }; p.seeking = false; if p.wantsPlay { p.player.rate = p.speed } }
        }
    }
    func setSpeed(handle: Int64, speed: Float) { guard let p = players[handle] else { return }; p.speed = speed; if p.wantsPlay && !p.seeking { p.player.rate = speed } }
    func attachLayer(handle: Int64, layer: Any?) { guard let p = players[handle], let layer = layer as? AVPlayerLayer else { return }; p.layer = layer; layer.player = p.player }
    func detachLayer(handle: Int64) { players[handle]?.layer?.player = nil; players[handle]?.layer = nil }
    func positionMs(handle: Int64) -> Int64 { updateSystemMetadata(); return milliseconds(players[handle]?.player.currentTime()) }
    func durationMs(handle: Int64) -> Int64 { milliseconds(players[handle]?.player.currentItem?.duration) }
    func bufferedMs(handle: Int64) -> Int64 {
        guard let range = players[handle]?.player.currentItem?.loadedTimeRanges.last?.timeRangeValue else { return 0 }
        return milliseconds(CMTimeRangeGetEnd(range))
    }
    func timeControl(handle: Int64) -> Int32 { Int32(players[handle]?.player.timeControlStatus.rawValue ?? 0) }
    func itemStatus(handle: Int64) -> Int32 {
        if (players[handle]?.failure ?? 0) != 0 { return 2 }
        return Int32(players[handle]?.player.currentItem?.status.rawValue ?? 0)
    }
    func errorKind(handle: Int64) -> Int32 {
        if let failure = players[handle]?.failure, failure != 0 { return failure }
        guard let error = players[handle]?.player.currentItem?.error as NSError? else { return 0 }
        return error.domain == NSURLErrorDomain ? 1 : 2
    }
    func releasePlayer(handle: Int64) { stop(handle: handle); detachLayer(handle: handle); players.removeValue(forKey: handle) }
    func tracks(handle: Int64) -> [PlayerTrack] {
        guard let item = players[handle]?.player.currentItem else { return [] }
        return [AVMediaCharacteristic.audible, .legible].flatMap { characteristic -> [PlayerTrack] in
            guard let group = item.asset.mediaSelectionGroup(forMediaCharacteristic: characteristic) else { return [] }
            return group.options.enumerated().map { index, option in
                PlayerTrack(id: String(index), kind: characteristic == .audible ? .audio : .subtitle,
                            label: option.displayName, language: option.extendedLanguageTag,
                            selected: item.currentMediaSelection.selectedMediaOption(in: group) === option)
            }
        }
    }
    func selectTrack(handle: Int64, kind: TrackKind, id: String?) {
        guard let item = players[handle]?.player.currentItem,
              let group = item.asset.mediaSelectionGroup(forMediaCharacteristic: kind == .audio ? .audible : .legible) else { return }
        let option = id.flatMap(Int.init).flatMap { group.options.indices.contains($0) ? group.options[$0] : nil }
        item.select(option, in: group)
    }
    func contentMode(handle: Int64, mode: PlayerContentMode) { players[handle]?.layer?.videoGravity = mode == .fill ? .resizeAspectFill : .resizeAspect }
    private func milliseconds(_ time: CMTime?) -> Int64 {
        guard let time else { return 0 }; let seconds = CMTimeGetSeconds(time)
        return seconds.isFinite && seconds > 0 ? Int64(seconds * 1000) : 0
    }
}
