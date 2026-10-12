package org.trustweave.did.events

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DidEventBusBehaviourTest {
    private val did = Did("did:example:alice")
    private val other = Did("did:example:bob")

    @Test
    fun `publishing with no subscriber completes and is not replayed to late subscribers`() =
        runTest(UnconfinedTestDispatcher()) {
            val bus = DefaultDidEventBus()
            bus.publish(DidEvent.Created(did))

            val late = async(start = CoroutineStart.UNDISPATCHED) { bus.events.take(1).toList() }
            bus.publish(DidEvent.Deactivated(other))

            val received = late.await()
            assertEquals(1, received.size)
            assertIs<DidEvent.Deactivated>(received.single())
        }

    @Test
    fun `every subscriber receives every event in publication order`() =
        runTest(UnconfinedTestDispatcher()) {
            val bus = DefaultDidEventBus()
            val a = async(start = CoroutineStart.UNDISPATCHED) { bus.events.take(3).toList() }
            val b = async(start = CoroutineStart.UNDISPATCHED) { bus.events.take(3).toList() }

            val events =
                listOf(
                    DidEvent.Created(did),
                    DidEvent.VerificationMethodAdded(did, "key-1"),
                    DidEvent.ServiceRemoved(did, "svc-1"),
                )
            events.forEach { bus.publish(it) }

            assertEquals(events, a.await())
            assertEquals(events, b.await())
        }

    @Test
    fun `typed subscription filters out other event types`() =
        runTest(UnconfinedTestDispatcher()) {
            val bus = DefaultDidEventBus()
            val updates = async(start = CoroutineStart.UNDISPATCHED) { bus.subscribe<DidEvent.Updated>().take(2).toList() }

            bus.publish(DidEvent.Created(did))
            bus.publish(DidEvent.Updated(did))
            bus.publish(DidEvent.ServiceAdded(did, "svc"))
            bus.publish(DidEvent.Updated(other))

            assertEquals(listOf(did, other), updates.await().map { it.did })
        }

    @Test
    fun `class based subscription behaves like the reified one`() =
        runTest(UnconfinedTestDispatcher()) {
            val bus = DefaultDidEventBus()
            val removed =
                async(start = CoroutineStart.UNDISPATCHED) {
                    bus.subscribe(DidEvent.VerificationMethodRemoved::class.java).take(1).toList()
                }
            bus.publish(DidEvent.VerificationMethodAdded(did, "key-1"))
            bus.publish(DidEvent.VerificationMethodRemoved(did, "key-1"))

            assertEquals("key-1", removed.await().single().methodId)
        }

    @Test
    fun `subscribing to the sealed base type receives all subtypes`() =
        runTest(UnconfinedTestDispatcher()) {
            val bus = DefaultDidEventBus()
            val all = async(start = CoroutineStart.UNDISPATCHED) { bus.subscribe<DidEvent>().take(2).toList() }
            bus.publish(DidEvent.Created(did))
            bus.publish(DidEvent.ServiceAdded(did, "s"))
            assertEquals(2, all.await().size)
        }

    @Test
    fun `events carry their payloads and a default timestamp close to now`() {
        val before =
            kotlin.time.Clock.System
                .now()
        val doc = DidDocument(id = did)
        val created = DidEvent.Created(did, doc)
        val updated = DidEvent.Updated(did, previousDocument = doc, updatedDocument = doc.copy(controller = listOf(other)))
        val after =
            kotlin.time.Clock.System
                .now()

        assertEquals(doc, created.document)
        assertEquals(listOf(other), updated.updatedDocument!!.controller)
        assertTrue(created.timestamp in before..after)
        assertEquals(did, DidEvent.Deactivated(did).did)
        assertEquals(null, DidEvent.Deactivated(did).document)
    }

    @Test
    fun `explicit timestamps are preserved`() {
        val ts = Instant.fromEpochSeconds(1_700_000_000)
        assertEquals(ts, DidEvent.ServiceAdded(did, "s", ts).timestamp)
        assertEquals(ts, DidEvent.ServiceRemoved(did, "s", ts).timestamp)
        assertEquals(ts, DidEvent.VerificationMethodAdded(did, "k", ts).timestamp)
    }
}
