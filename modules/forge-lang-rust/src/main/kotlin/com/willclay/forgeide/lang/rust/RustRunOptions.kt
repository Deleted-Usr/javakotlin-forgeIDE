package com.willclay.forgeide.lang.rust

/**
 * Rust's own run configuration choices, saved with each run configuration.
 *
 * These are the two choices [RustSettings] deliberately leaves out. They are
 * about one particular run rather than the whole project: the same game is
 * often run as a quick debug build while working on it, and as a release build
 * to see how fast it really is.
 *
 * Like the settings, each one is a Cargo flag you will see in the console.
 *
 * @property release builds with Cargo's release profile (`--release`):
 *                   slower to build, much faster to run
 * @property toolchain the rustup toolchain to use, such as `nightly` or `1.80`,
 *                     which runs `cargo +nightly`. Empty means rustup's default.
 */
data class RustRunOptions(
    val release: Boolean = false,
    val toolchain: String = ""
) {
    /** Added after `cargo run`. */
    fun profileArguments(): List<String> = if (release) listOf("--release") else emptyList()

    /** `+nightly`, which must come straight after `cargo`; null for rustup's default. */
    fun toolchainArgument(): String? = toolchain.takeIf { it.isNotEmpty() }?.let { "+$it" }

    fun toJson(): Map<String, Any> = linkedMapOf(
        // A word rather than a boolean, so the file reads the way Cargo talks
        // about it, and so a custom profile could be named here one day.
        "profile" to if (release) RELEASE else DEBUG,
        "toolchain" to toolchain
    )

    companion object {
        private const val DEBUG = "debug"
        private const val RELEASE = "release"

        /** Reads what a [RustRunOptionsPage] saved; anything missing keeps its default. */
        @JvmStatic
        fun from(options: Map<String, Any?>): RustRunOptions = RustRunOptions(
            release = options["profile"] == RELEASE,
            // Forgiving about a `+` typed by hand, since that is how it is written on the command line.
            toolchain = (options["toolchain"] as? String).orEmpty().trim().removePrefix("+")
        )
    }
}
