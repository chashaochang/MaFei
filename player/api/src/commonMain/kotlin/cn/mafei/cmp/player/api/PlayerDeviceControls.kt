package cn.mafei.cmp.player.api

/**
 * Screen brightness and media volume, as the player's vertical gesture needs
 * them (legacy `PlayerChrome`: left half = brightness, right half = volume).
 *
 * Supplied by the host platform. The player UI never guesses these values and
 * never shows a level bar it cannot actually apply: a platform that cannot
 * read one of them returns null / 0 and the gesture for that side refuses to
 * engage instead of rendering feedback that changes nothing.
 *
 * [brightness] and [volume] are read when a drag starts, so they must be cheap
 * and non-suspending. [setBrightness]/[setVolume] are best effort — the
 * platform may clamp or drop the write — and the caller keeps displaying the
 * value it requested, exactly like the legacy page did.
 */
interface PlayerDeviceControls {

    /** Screen brightness in 0f..1f, or null when this platform cannot report it. */
    fun brightness(): Float?

    /** Applies a brightness in 0f..1f. Ignored while [brightness] returns null. */
    fun setBrightness(value: Float)

    /** Media volume in 0..[maxVolume] steps, or null when unavailable. */
    fun volume(): Int?

    /** Total media volume steps; 0 means the volume side is unavailable. */
    fun maxVolume(): Int

    /** Applies a media volume in 0..[maxVolume] steps. */
    fun setVolume(steps: Int)

    /**
     * Asks the platform for a fresh reading (best effort, may complete
     * asynchronously on the next interaction). Backends whose values only
     * arrive over an event channel use this to catch up with changes made
     * outside the player.
     */
    fun refresh() = Unit

    companion object {
        /**
         * Legacy fallback for a window whose brightness "follows the system"
         * (HarmonyOS reports -1 for that), so the gesture starts from the middle
         * instead of snapping to black.
         */
        const val DEFAULT_BRIGHTNESS = 0.5f

        /** Platform with neither control: both vertical gestures stay inert. */
        val Unsupported: PlayerDeviceControls = object : PlayerDeviceControls {
            override fun brightness(): Float? = null
            override fun setBrightness(value: Float) = Unit
            override fun volume(): Int? = null
            override fun maxVolume(): Int = 0
            override fun setVolume(steps: Int) = Unit
        }
    }
}
