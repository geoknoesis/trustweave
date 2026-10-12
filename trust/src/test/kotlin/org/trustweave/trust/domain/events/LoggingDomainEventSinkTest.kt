package org.trustweave.trust.domain.events

import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.helpers.MessageFormatter
import org.trustweave.anchor.payment.AssetRef
import org.trustweave.anchor.payment.TokenAmount
import org.trustweave.trust.domain.DomainId
import java.lang.reflect.Proxy
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoggingDomainEventSinkTest {
    private data class Line(
        val level: String,
        val text: String,
    )

    /** A java.lang.reflect proxy capturing formatted SLF4J messages, avoiding a mocking framework. */
    private fun capturingLogger(lines: MutableList<Line>): Logger =
        Proxy.newProxyInstance(Logger::class.java.classLoader, arrayOf(Logger::class.java)) { _, method, args ->
            if (method.name == "info" || method.name == "warn") {
                val format = args[0] as String
                val rest = (args.drop(1).singleOrNull() as? Array<*>)?.toList() ?: args.drop(1)
                lines += Line(method.name, MessageFormatter.arrayFormat(format, rest.toTypedArray()).message!!)
            }
            null
        } as Logger

    private val domain = DomainId("edu")
    private val amount = TokenAmount("eip155:1", AssetRef.Native, BigInteger.valueOf(42))

    @Test
    fun `each event kind logs its identifying fields at the right level`() =
        runBlocking {
            val lines = mutableListOf<Line>()
            val sink = LoggingDomainEventSink(capturingLogger(lines))

            sink.emit(DomainEvent.OnChainSpendReserved(domain, "eip155:1", "c1", amount))
            sink.emit(DomainEvent.OnChainSpendSettled(domain, "eip155:1", "c1", amount, "0xhash", "0xpayer"))
            sink.emit(DomainEvent.OnChainSpendCancelled(domain, "eip155:1", "c2"))
            sink.emit(DomainEvent.OnChainSpendFailed(domain, "eip155:1", "c3", "rpc down"))

            assertEquals(listOf("info", "info", "info", "warn"), lines.map { it.level })
            assertEquals("on-chain spend reserved domain=edu chain=eip155:1 correlationId=c1 estimate=42", lines[0].text)
            assertTrue(lines[1].text.contains("actualFee=42 txHash=0xhash payer=0xpayer"), lines[1].text)
            assertEquals("on-chain spend cancelled domain=edu chain=eip155:1 correlationId=c2", lines[2].text)
            assertEquals("on-chain spend failed domain=edu chain=eip155:1 correlationId=c3 reason=rpc down", lines[3].text)
        }

    @Test
    fun `settled events with unknown transaction details log them as null`() =
        runBlocking {
            val lines = mutableListOf<Line>()
            LoggingDomainEventSink(capturingLogger(lines))
                .emit(DomainEvent.OnChainSpendSettled(domain, "c", "id", amount, null, null))

            assertTrue(lines.single().text.endsWith("txHash=null payer=null"), lines.single().text)
        }

    @Test
    fun `the default logger can be used without configuration`() =
        runBlocking {
            LoggingDomainEventSink().emit(DomainEvent.OnChainSpendCancelled(domain, "c", "id"))
            NoopDomainEventSink.emit(DomainEvent.OnChainSpendCancelled(domain, "c", "id"))
        }
}
