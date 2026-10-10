package com.willclay.forgeide.application.bootstrap

/**
 * Hears which phase [ForgeBootstrap] has reached, such as "Discovering
 * language plugins". The splash screen shows these, so the user sees what
 * Forge is actually doing while it starts.
 *
 * This is a `fun interface` rather than a Kotlin function type like
 * `(String) -> Unit` because of who calls it: from Java, a function type is
 * `Function1<String, Unit>` and every lambda would have to end with
 * `return Unit.INSTANCE`. A `fun interface` is a plain single-method
 * interface, so Java can simply pass `splash::setStatus`.
 *
 * Implementations are called on the bootstrap thread, not the EDT.
 */
fun interface BootstrapProgress {
    fun phase(description: String)

    companion object {
        /** Reports nowhere; for callers with no splash screen, such as tests. */
        @JvmField
        val NONE = BootstrapProgress { }
    }
}
