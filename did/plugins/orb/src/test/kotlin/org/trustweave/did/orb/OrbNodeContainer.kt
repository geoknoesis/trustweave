package org.trustweave.did.orb

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration

/**
 * Testcontainers wrapper around a single TrustBloc Orb node sufficient to
 * round-trip Sidetree create / update / deactivate operations end-to-end.
 *
 * Configuration is deliberately minimal:
 *
 *  - In-memory storage for both the operation queue and the KMS-secrets store
 *    so each test run starts clean.
 *  - Local CAS (no IPFS).
 *  - VCT disabled and dev mode on — Orb's published-operation pipeline normally
 *    requires a configured witness or VCT log to verify each batch's anchor
 *    credential; without one the full anchoring round-trip never completes.
 *    That's an operator concern, not a wire-protocol concern, so the integration
 *    test asserts wire-format acceptance only.
 *  - The external endpoint is computed at start time and made available to the
 *    test via [baseUrl]; the same URL is added to `--allowed-origins` because
 *    Orb refuses operations whose `anchorOrigin` is unrecognised.
 *
 * Image: defaults to `ghcr.io/trustbloc/orb:latest`. Override with the
 * `orb.image` system property if a pin is needed in CI.
 */
class OrbNodeContainer(
    image: DockerImageName = DockerImageName.parse(System.getProperty("orb.image", IMAGE)),
) : GenericContainer<OrbNodeContainer>(image) {

    init {
        withExposedPorts(INTERNAL_PORT)
        // The external endpoint must match what we tell Orb about its own URL
        // (so anchorOrigin checks line up). The actual host port is allocated by
        // Testcontainers at start time; the `--external-endpoint` arg is rewritten
        // in start() once we know it.
        withCommand(
            "start",
            "--host-url", "0.0.0.0:$INTERNAL_PORT",
            "--did-namespace", "did:orb",
            "--database-type", "mem",
            "--kms-secrets-database-type", "mem",
            "--cas-type", "local",
            "--kms-type", "local",
            "--enable-dev-mode", "true",
            "--enable-unpublished-operation-store", "true",
            "--unpublished-operation-store-operation-types", "create,update,deactivate,recover",
            "--include-unpublished-operations-in-metadata", "true",
            // --external-endpoint, --anchor-credential-domain, --allowed-origins
            // are appended in start() once the host port is mapped.
        )
        waitingFor(
            Wait.forHttp("/healthcheck")
                .forPort(INTERNAL_PORT)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(2)),
        )
    }

    /** Base URL the test JVM uses to reach this Orb node. Available after [start]. */
    val baseUrl: String
        get() = "http://${host}:${getMappedPort(INTERNAL_PORT)}"

    /**
     * `anchorOrigin` value to pass in [OrbDidConfig.anchorOrigin] when running
     * against this container. Matches the URL Orb is configured to recognise
     * (`--external-endpoint` + `--allowed-origins`), which is the in-container
     * host:port pair rather than the host-mapped port [baseUrl] uses.
     */
    val anchorOrigin: String
        get() = "http://host.docker.internal:$INTERNAL_PORT"

    override fun start() {
        // Re-write the command now that we can't know the mapped port up front but
        // need three of Orb's args to carry the real external URL.
        val mappedHost = "host.docker.internal"
        val portPlaceholder = INTERNAL_PORT.toString()
        val externalUrlAtStart = "http://$mappedHost:$portPlaceholder"
        withCommand(
            *(commandParts() + arrayOf(
                "--external-endpoint", externalUrlAtStart,
                "--anchor-credential-domain", externalUrlAtStart,
                "--allowed-origins", externalUrlAtStart,
                // Also accept localhost-style origins for callers running the test
                // JVM directly on the host:
                "--allowed-origins", "http://localhost:$portPlaceholder",
            )),
        )
        super.start()
    }

    private fun commandParts(): Array<String> = commandParts

    private val commandParts: Array<String> = arrayOf(
        "start",
        "--host-url", "0.0.0.0:$INTERNAL_PORT",
        "--did-namespace", "did:orb",
        "--database-type", "mem",
        "--kms-secrets-database-type", "mem",
        "--cas-type", "local",
        "--kms-type", "local",
        "--enable-dev-mode", "true",
        "--enable-unpublished-operation-store", "true",
        "--unpublished-operation-store-operation-types", "create,update,deactivate,recover",
        "--include-unpublished-operations-in-metadata", "true",
    )

    companion object {
        /** Default image. Pin with `-Dorb.image=...` in CI if reproducibility matters. */
        const val IMAGE: String = "ghcr.io/trustbloc/orb:latest"

        /** Port Orb binds inside the container. */
        const val INTERNAL_PORT: Int = 48326
    }
}
