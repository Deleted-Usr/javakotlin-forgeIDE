package com.willclay.forgeide.application.bootstrap

/** A startup failure that leaves Forge with no reasonable way to continue */
class BootstrapException(message: String, cause: Throwable) : Exception(message, cause)