package com.willclay.forgeide.application.bootstrap

import com.willclay.forgeide.lang.api.Language

/** The outcome of discovery: usable languages plus the loader that must stay open. */
data class LanguageDiscovery(
    val languages: List<Language>,
    val classLoader: AutoCloseable?
)
