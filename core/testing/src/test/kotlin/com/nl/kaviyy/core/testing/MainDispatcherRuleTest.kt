package com.nl.kaviyy.core.testing

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class MainDispatcherRuleTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun mainDispatcherIsReplaced() =
        runTest {
            flow { emit(1) }.flowOn(Dispatchers.Main).test {
                assertEquals(1, awaitItem())
                awaitComplete()
            }
        }
}
