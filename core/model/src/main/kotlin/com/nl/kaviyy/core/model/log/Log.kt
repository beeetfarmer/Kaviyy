package com.nl.kaviyy.core.model.log

/**
 * The only logging entry point. Messages are redacted, then handed to [sink].
 * [sink] stays null in release builds (no-op) and R8 strips the calls (app keep rules).
 */
object Log {
    enum class Level { Debug, Info, Warn, Error }

    @Volatile
    var sink: ((level: Level, tag: String, message: String, error: Throwable?) -> Unit)? = null

    fun d(
        tag: String,
        message: () -> String,
    ) = emit(Level.Debug, tag, null, message)

    fun i(
        tag: String,
        message: () -> String,
    ) = emit(Level.Info, tag, null, message)

    fun w(
        tag: String,
        error: Throwable? = null,
        message: () -> String,
    ) = emit(Level.Warn, tag, error, message)

    fun e(
        tag: String,
        error: Throwable? = null,
        message: () -> String,
    ) = emit(Level.Error, tag, error, message)

    private fun emit(
        level: Level,
        tag: String,
        error: Throwable?,
        message: () -> String,
    ) {
        val out = sink ?: return
        // Exception messages can echo URLs/headers too, so only their redacted text is passed on.
        val errorText = error?.let { "\n${it::class.java.name}: ${it.message.orEmpty()}" }.orEmpty()
        out(level, tag, Redactor.redact(message() + errorText), null)
    }
}
