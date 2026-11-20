package com.nl.kaviyy.core.model.dispatch

import javax.inject.Qualifier

/** Inject `@Dispatcher(KaviyyDispatchers.IO) CoroutineDispatcher` instead of using `Dispatchers.*` directly. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(
    val value: KaviyyDispatchers,
)

enum class KaviyyDispatchers { Default, IO }
