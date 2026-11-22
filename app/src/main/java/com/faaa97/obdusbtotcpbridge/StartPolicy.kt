package com.faaa97.obdusbtotcpbridge

/** How [ObdService] reacts to a start request (D-016). Kept free of Android types so it can be unit tested. */
object StartPolicy {

    /**
     * Whether the service should keep retrying until an adapter shows up.
     *
     * - [alreadyEnabled]: once on, it stays on for the life of the service.
     * - [requested]: the start asked for it (`EXTRA_AUTO_CONNECT`): the headless launcher, a USB attach,
     *   the UI's autostart.
     * - [isStickyRestart]: Android restarted the service after killing the process (null intent). Only a
     *   running service gets restarted, and the adapter is usually still enumerating then, so giving up
     *   on the first failed open would leave the bridge stopped for good.
     */
    fun autoConnect(alreadyEnabled: Boolean, requested: Boolean, isStickyRestart: Boolean): Boolean =
        alreadyEnabled || requested || isStickyRestart
}
