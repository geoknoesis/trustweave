package org.trustweave.anchor.bitcoin

import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The Bitcoin RPC client attaches `Authorization: Basic <rpcUser:rpcPassword>` to every call, and
 * the connection also carries the signed transaction. Over plaintext to a public host both are
 * readable and rewritable in transit, so a public `http://` endpoint must be refused at
 * configuration time rather than silently used.
 *
 * Loopback and private-range hosts stay on plaintext deliberately: `http://localhost:8332` is the
 * documented way to run a local node, TLS buys nothing there, and refusing it would break the normal
 * deployment. This is the same policy `AbstractEvmAnchorClient` already applies to JSON-RPC.
 */
class BitcoinRpcTransportSecurityTest {
    private fun client(rpcUrl: String) =
        BitcoinBlockchainAnchorClient(
            BitcoinBlockchainAnchorClient.TESTNET,
            mapOf(
                "rpcUrl" to rpcUrl,
                "rpcUser" to "user",
                "rpcPassword" to "password",
            ),
        )

    @Test
    fun `plaintext rpc to a public host is refused`() {
        val error = assertFailsWith<IllegalArgumentException> { client("http://node.example.com:8332") }

        assertTrue(
            error.message!!.contains("plaintext", ignoreCase = true),
            "expected the refusal to name the problem, got: ${error.message}",
        )
    }

    @Test
    fun `plaintext rpc to localhost is allowed - that is the documented local node`() {
        client("http://localhost:8332")
        client("http://127.0.0.1:8332")
    }

    @Test
    fun `plaintext rpc to a private-range host is allowed`() {
        client("http://10.0.0.5:8332")
        client("http://192.168.1.10:8332")
    }

    @Test
    fun `an unresolvable host is refused rather than assumed local`() {
        // PrivateNetworkGuard.rejectionReason() reports a reason both for a private host and for one
        // that cannot be resolved. Reading "has a reason" as "is local" would allow plaintext to an
        // unresolvable public host — failing open on exactly the DNS failure an attacker can induce.
        assertFailsWith<IllegalArgumentException> { client("http://no-such-host.invalid:8332") }
    }

    @Test
    fun `https to a public host is allowed`() {
        client("https://node.example.com:8332")
    }
}
