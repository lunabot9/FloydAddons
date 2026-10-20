package gg.floyd.features.impl.pvp.experiments

/** A click that restores window focus must not pause or alter the experiment. */
class ExperimentFocusGuard {
    private var focused = true
    private var ignoreUntil = Long.MIN_VALUE
    fun reset(focused: Boolean, now: Long) { this.focused = focused; ignoreUntil = Long.MIN_VALUE }
    fun observe(focused: Boolean, now: Long) {
        if (focused && !this.focused) ignoreUntil = now + 250
        this.focused = focused
    }
    fun ignoreClick(focused: Boolean, now: Long): Boolean {
        observe(focused, now)
        return !focused || now < ignoreUntil
    }
}
