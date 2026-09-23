package com.lakasir.acp.acp

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AcpClientFactoryTest {

    @Test
    fun `factory creates independent clients`() = runTest(UnconfinedTestDispatcher()) {
        val factory = DefaultAcpClientFactory()
        val first = factory.create(backgroundScope)
        val second = factory.create(backgroundScope)

        assertNotSame(first, second)
    }
}
