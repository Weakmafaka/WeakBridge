package dev.weakbridge

import dev.weakbridge.ams.*
import dev.weakbridge.ble.GattTransport
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AmsClientTest {
    private class Transport : GattTransport {
        val writes = mutableListOf<Pair<UUID, ByteArray>>()
        val subscriptions = mutableListOf<Boolean>()
        var readValue = "Full title".toByteArray()

        override suspend fun subscribe(
            service: UUID,
            characteristic: UUID,
            enabled: Boolean,
            indication: Boolean,
        ) {
            subscriptions += enabled
        }

        override suspend fun write(service: UUID, characteristic: UUID, value: ByteArray) {
            writes += characteristic to value
        }

        override suspend fun read(service: UUID, characteristic: UUID) = readValue

        override fun has(service: UUID, characteristic: UUID) = true
    }

    @Test
    fun commandsRequireCurrentSupportAndAreDiscardedOnClose() = runTest {
        val t = Transport()
        val c = AmsClient(backgroundScope, t, { error("fatal") }, { testScheduler.currentTime })
        c.start()
        c.command(Ams.PLAY)
        runCurrent()
        assertEquals(0, t.writes.count { it.first == Ams.COMMAND })
        c.supported(byteArrayOf(0, 3))
        c.command(Ams.PLAY)
        c.command(Ams.PAUSE)
        runCurrent()
        assertEquals(1, t.writes.count { it.first == Ams.COMMAND })
        c.command(Ams.NEXT)
        c.close()
        runCurrent()
        assertEquals(1, t.writes.count { it.first == Ams.COMMAND })
    }

    @Test
    fun truncatedAttributesUseSelectThenRead() = runTest {
        val t = Transport()
        val c = AmsClient(backgroundScope, t, { error("fatal") }, { testScheduler.currentTime })
        c.start()
        c.update(byteArrayOf(2, 2, 1) + "Full".toByteArray())
        runCurrent()
        assertArrayEquals(byteArrayOf(2, 2), t.writes.last().second)
        assertEquals(Ams.ATTRIBUTE, t.writes.last().first)
        assertEquals("Full title", c.state.value.title)
        c.close()
    }

    @Test
    fun newerMetadataWinsOverPendingTruncatedRead() = runTest {
        val t = Transport()
        val c = AmsClient(backgroundScope, t, { error("fatal") }, { testScheduler.currentTime })
        c.start()
        c.update(byteArrayOf(2, 2, 1) + "Old".toByteArray())
        c.update(byteArrayOf(2, 2, 0) + "New".toByteArray())
        runCurrent()
        assertEquals("New", c.state.value.title)
        assertTrue(t.writes.none { it.first == Ams.ATTRIBUTE })
        c.close()
    }

    @Test
    fun recoveryIsBoundedAndStopsWhenMetadataArrives() = runTest {
        val t = Transport()
        val c = AmsClient(backgroundScope, t, { error("fatal") }, { testScheduler.currentTime })
        c.start()
        c.update(byteArrayOf(0, 1, 0) + "1,1,0".toByteArray())
        runCurrent()
        advanceTimeBy(5001)
        runCurrent()
        assertEquals(1, t.subscriptions.count { !it })
        c.update(byteArrayOf(2, 2, 0) + "Title".toByteArray())
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, t.subscriptions.count { !it })
        c.close()
    }

    @Test
    fun missingTrackRecoveryNeverLoopsForever() = runTest {
        val t = Transport()
        val c = AmsClient(backgroundScope, t, { error("fatal") }, { testScheduler.currentTime })
        c.start()
        c.update(byteArrayOf(0, 1, 0) + "1,1,0".toByteArray())
        runCurrent()
        advanceTimeBy(11_000)
        runCurrent()
        assertEquals(2, t.subscriptions.count { !it })
        advanceTimeBy(100_000)
        runCurrent()
        assertEquals(2, t.subscriptions.count { !it })
        c.close()
    }
}
