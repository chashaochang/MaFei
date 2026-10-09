package cn.mafei.cmp.player.api

/** Platform window actions do not own a second player or a playback queue. */
interface PlayerWindowControls {
    val pictureInPictureSupported: Boolean
    fun pictureInPicture()
    val castingSupported: Boolean get() = false
    fun casting() { throw UnsupportedPlayerCapability("casting") }
    object Unsupported : PlayerWindowControls {
        override val pictureInPictureSupported = false
        override fun pictureInPicture() = throw UnsupportedPlayerCapability("pictureInPicture")
    }
}
