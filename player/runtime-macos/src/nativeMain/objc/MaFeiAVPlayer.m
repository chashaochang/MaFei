#import <AVFoundation/AVFoundation.h>
#import <CoreMedia/CMTime.h>
#import <jni.h>

// JNI bridge for one AVPlayer per handle (GLM-05 macOS runtime). Everything
// is synchronous query/command — the Kotlin runtime drives readiness and
// progress by polling live engine state, so no callback threading exists.
// Handles are ARC-managed AVPlayer references cast through intptr_t.

static AVPlayer *playerFor(jlong handle) {
    return (__bridge AVPlayer *)(void *)(intptr_t)handle;
}

JNIEXPORT jlong JNICALL
Java_cn_mafei_cmp_player_runtime_1macos_NativeAvPlayer_create(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    // CFBridgingRetain transfers ownership out of ARC: the handle keeps the
    // player alive until releasePlayer balances it with __bridge_transfer.
    AVPlayer *player = [[AVPlayer alloc] init];
    return (jlong)CFBridgingRetain(player);
}

JNIEXPORT void JNICALL
Java_cn_mafei_cmp_player_runtime_1macos_NativeAvPlayer_loadUrl(JNIEnv *env, jclass clazz, jlong handle, jstring url) {
    (void)env; (void)clazz;
    const char *urlChars = (*env)->GetStringUTFChars(env, url, NULL);
    NSString * urlString = [NSString stringWithUTF8String:urlChars ? urlChars : ""];
    (*env)->ReleaseStringUTFChars(env, url, urlChars);
    NSURL *parsed = [NSURL URLWithString:urlString];
    if (parsed == nil) {
        parsed = [NSURL fileURLWithPath:urlString];
    }
    if (parsed == nil) {
        return;
    }
    // P0: custom HTTP headers are NOT applied (AVURLAsset has no public
    // header API without a resource-loader delegate) — documented Pending.
    AVPlayerItem *item = [AVPlayerItem playerItemWithURL:parsed];
    [playerFor(handle) replaceCurrentItemWithPlayerItem:item];
}

JNIEXPORT void JNICALL
Java_cn_mafei_cmp_player_runtime_1macos_NativeAvPlayer_play(JNIEnv *env, jclass clazz, jlong handle) {
    (void)env; (void)clazz;
    [playerFor(handle) play];
}

JNIEXPORT void JNICALL
Java_cn_mafei_cmp_player_runtime_1macos_NativeAvPlayer_pause(JNIEnv *env, jclass clazz, jlong handle) {
    (void)env; (void)clazz;
    [playerFor(handle) pause];
}

JNIEXPORT void JNICALL
Java_cn_mafei_cmp_player_runtime_1macos_NativeAvPlayer_stop(JNIEnv *env, jclass clazz, jlong handle) {
    (void)env; (void)clazz;
    AVPlayer *player = playerFor(handle);
    [player pause];
    [player replaceCurrentItemWithPlayerItem:nil];
}

JNIEXPORT void JNICALL
Java_cn_mafei_cmp_player_runtime_1macos_NativeAvPlayer_seek(JNIEnv *env, jclass clazz, jlong handle, jlong positionMs) {
    (void)env; (void)clazz;
    CMTime time = CMTimeMake(positionMs, 1000);
    [[playerFor(handle) currentItem]
        seekToTime:time toleranceBefore:kCMTimeZero toleranceAfter:kCMTimeZero
        completionHandler:nil];
}

JNIEXPORT void JNICALL
Java_cn_mafei_cmp_player_runtime_1macos_NativeAvPlayer_setSpeed(JNIEnv *env, jclass clazz, jlong handle, jfloat speed) {
    (void)env; (void)clazz;
    playerFor(handle).rate = speed;
}

// The layer pointer is an AVPlayerLayer* handed over by the UI layer (video
// compositing is a UI concern); 0 detaches. Audio playback needs no layer.
JNIEXPORT void JNICALL
Java_cn_mafei_cmp_player_runtime_1macos_NativeAvPlayer_attachLayer(JNIEnv *env, jclass clazz, jlong handle, jlong layerPointer) {
    (void)env; (void)clazz;
    if (layerPointer == 0) return;
    CALayer *layer = (__bridge CALayer *)(void *)(intptr_t)layerPointer;
    if (![layer isKindOfClass:[AVPlayerLayer class]]) {
        return;
    }
    ((AVPlayerLayer *)layer).player = playerFor(handle);
}

JNIEXPORT void JNICALL
Java_cn_mafei_cmp_player_runtime_1macos_NativeAvPlayer_detachLayer(JNIEnv *env, jclass clazz, jlong handle) {
    (void)env; (void)clazz; (void)handle;
    // The layer keeps rendering its last frame; ownership stays with the UI.
}

JNIEXPORT jlong JNICALL
Java_cn_mafei_cmp_player_runtime_1macos_NativeAvPlayer_positionMs(JNIEnv *env, jclass clazz, jlong handle) {
    (void)env; (void)clazz;
    CMTime time = playerFor(handle).currentTime;
    if (!CMTIME_IS_NUMERIC(time)) return 0;
    return (jlong)(CMTimeGetSeconds(time) * 1000.0);
}

JNIEXPORT jlong JNICALL
Java_cn_mafei_cmp_player_runtime_1macos_NativeAvPlayer_durationMs(JNIEnv *env, jclass clazz, jlong handle) {
    (void)env; (void)clazz;
    AVPlayerItem *item = playerFor(handle).currentItem;
    if (item == nil || !CMTIME_IS_NUMERIC(item.duration)) return 0;
    double seconds = CMTimeGetSeconds(item.duration);
    return seconds > 0 ? (jlong)(seconds * 1000.0) : 0;
}

JNIEXPORT jlong JNICALL
Java_cn_mafei_cmp_player_runtime_1macos_NativeAvPlayer_bufferedMs(JNIEnv *env, jclass clazz, jlong handle) {
    (void)env; (void)clazz;
    AVPlayerItem *item = playerFor(handle).currentItem;
    if (item == nil || item.loadedTimeRanges.count == 0) return 0;
    CMTimeRange range = [item.loadedTimeRanges.lastObject CMTimeRangeValue];
    double end = CMTimeGetSeconds(CMTimeRangeGetEnd(range));
    return end > 0 ? (jlong)(end * 1000.0) : 0;
}

// Mirrors AVPlayerTimeControlStatus: 0 paused, 1 waiting (buffering), 2 playing.
JNIEXPORT jint JNICALL
Java_cn_mafei_cmp_player_runtime_1macos_NativeAvPlayer_timeControl(JNIEnv *env, jclass clazz, jlong handle) {
    (void)env; (void)clazz;
    return (jint)playerFor(handle).timeControlStatus;
}

// Mirrors AVPlayerItemStatus: 0 unknown, 1 readyToPlay, 2 failed.
JNIEXPORT jint JNICALL
Java_cn_mafei_cmp_player_runtime_1macos_NativeAvPlayer_itemStatus(JNIEnv *env, jclass clazz, jlong handle) {
    (void)env; (void)clazz;
    AVPlayerItem *item = playerFor(handle).currentItem;
    return item == nil ? 0 : (jint)item.status;
}

// Error classification for a failed item: 0 none, 1 network, 2 decode.
JNIEXPORT jint JNICALL
Java_cn_mafei_cmp_player_runtime_1macos_NativeAvPlayer_errorKind(JNIEnv *env, jclass clazz, jlong handle) {
    (void)env; (void)clazz;
    NSError *error = playerFor(handle).currentItem.error;
    if (error == nil) return 0;
    if ([error.domain isEqualToString:NSURLErrorDomain]) return 1;
    return 2;
}

JNIEXPORT void JNICALL
Java_cn_mafei_cmp_player_runtime_1macos_NativeAvPlayer_releasePlayer(JNIEnv *env, jclass clazz, jlong handle) {
    (void)env; (void)clazz;
    if (handle == 0) return;
    AVPlayer *player = (__bridge_transfer AVPlayer *)(void *)(intptr_t)handle;
    [player pause];
    [player replaceCurrentItemWithPlayerItem:nil];
    // ARC releases the player here, balancing CFBridgingRetain.
}
