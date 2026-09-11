package com.offlinep2p.feature.connection

import com.offlinep2p.core.common.ConnectionState
import com.offlinep2p.core.network.P2PTransport
import com.offlinep2p.core.network.TransportEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionCoordinatorTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeTransport : P2PTransport {
        val bus = MutableSharedFlow<TransportEvent>(
            replay = 0, extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        override val events = bus
        var advertised = false
        var discovered = false
        var lastConnectRequest: String? = null
        var lastDisconnectRequest: String? = null
        var shutdowns = 0

        override suspend fun startAdvertising(displayName: String) { advertised = true }
        override suspend fun startDiscovery() { discovered = true }
        override suspend fun connect(peerId: String) { lastConnectRequest = peerId }
        override suspend fun disconnect(peerId: String) { lastDisconnectRequest = peerId }
        override suspend fun sendControl(peerId: String, payload: ByteArray) {}
        override suspend fun sendAudio(peerId: String, frame: ByteArray) {}
        override suspend fun shutdown() { shutdowns++ }
    }

    @Test
    fun `startAdvertising updates state and calls transport`() = runTest(dispatcher) {
        val t = FakeTransport()
        val c = ConnectionCoordinator(t, "Alice")
        c.startAdvertising()
        advanceUntilIdle()
        assertTrue(t.advertised)
        assertEquals(ConnectionState.Advertising, c.state.value)
    }

    @Test
    fun `PeerFound event populates peers list`() = runTest(dispatcher) {
        val t = FakeTransport()
        val c = ConnectionCoordinator(t, "Alice")
        c.startDiscovery()
        advanceUntilIdle()
        t.bus.emit(TransportEvent.PeerFound("peer-1", "Bob"))
        advanceUntilIdle()
        val peers = c.peers.first()
        assertEquals(1, peers.size)
        assertEquals("Bob", peers[0].displayName)
    }

    @Test
    fun `Connected event transitions state and clears peers`() = runTest(dispatcher) {
        val t = FakeTransport()
        val c = ConnectionCoordinator(t, "Alice")
        c.startDiscovery()
        advanceUntilIdle()
        t.bus.emit(TransportEvent.PeerFound("peer-1", "Bob"))
        t.bus.emit(TransportEvent.Connected("peer-1", "Bob"))
        advanceUntilIdle()
        val state = c.state.value
        assertTrue(state is ConnectionState.Connected)
        assertEquals("Bob", (state as ConnectionState.Connected).peerName)
        assertTrue(c.peers.value.isEmpty())
    }

    @Test
    fun `Error event maps to Failed state`() = runTest(dispatcher) {
        val t = FakeTransport()
        val c = ConnectionCoordinator(t, "Alice")
        c.startDiscovery()
        advanceUntilIdle()
        t.bus.emit(TransportEvent.Error("radio off"))
        advanceUntilIdle()
        val s = c.state.value
        assertTrue(s is ConnectionState.Failed)
        assertEquals("radio off", (s as ConnectionState.Failed).reason)
    }

    @Test
    fun `disconnect from connected state calls transport disconnect and shutdown`() = runTest(dispatcher) {
        val t = FakeTransport()
        val c = ConnectionCoordinator(t, "Alice")
        c.startDiscovery(); advanceUntilIdle()
        t.bus.emit(TransportEvent.Connected("peer-1", "Bob")); advanceUntilIdle()
        c.disconnect(); advanceUntilIdle()
        assertEquals("peer-1", t.lastDisconnectRequest)
        assertEquals(1, t.shutdowns)
        assertEquals(ConnectionState.Disconnected, c.state.value)
    }
}
