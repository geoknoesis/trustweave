package org.trustweave.anchor.indy

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration

/**
 * TestContainers wrapper around `bcgovimages/von-network` — a self-contained Hyperledger
 * Indy test network with a built-in HTTP browser, a NYM self-registration endpoint and
 * pool ports.
 *
 * Container exposes:
 * - 9000: HTTP ledger browser + `/register` self-serve NYM endpoint
 * - 9701-9708: indy node pool ports (ZeroMQ)
 *
 * For the anchor plugin we only use the HTTP front-end which doubles as an
 * `indy-vdr-proxy`-compatible submit endpoint via the ledger browser's `/submit` route
 * (von-network ships its own thin proxy on the same port).
 *
 * Image is ~1.5 GB so this container is lazy-pulled on first use.
 */
class VonNetworkContainer : GenericContainer<VonNetworkContainer>(IMAGE) {

    init {
        withExposedPorts(BROWSER_PORT)
        for (p in POOL_PORTS) withExposedPorts(p)
        withCommand("./scripts/start_webserver.sh")
        waitingFor(
            Wait.forHttp("/genesis")
                .forPort(BROWSER_PORT)
                .forStatusCodeMatching { it in 200..299 }
                .withStartupTimeout(Duration.ofMinutes(5))
        )
        withStartupAttempts(1)
    }

    /** Base URL of the HTTP browser / proxy endpoint. */
    fun browserUrl(): String = "http://$host:${getMappedPort(BROWSER_PORT)}"

    /** Genesis transaction file as served by `/genesis` — used to bootstrap a pool. */
    fun genesisUrl(): String = "${browserUrl()}/genesis"

    companion object {
        val IMAGE: DockerImageName = DockerImageName.parse("bcgovimages/von-network-base:latest")

        const val BROWSER_PORT = 9000
        val POOL_PORTS = listOf(9701, 9702, 9703, 9704, 9705, 9706, 9707, 9708)
    }
}
