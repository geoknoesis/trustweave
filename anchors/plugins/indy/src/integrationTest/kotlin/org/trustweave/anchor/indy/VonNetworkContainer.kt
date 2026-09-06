package org.trustweave.anchor.indy

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import java.time.Duration

/** Four local Indy validators and a native VDR HTTP test adapter; no hosted ledger writes. */
class VonNetworkContainer :
    GenericContainer<VonNetworkContainer>(
        ImageFromDockerfile()
            .withFileFromClasspath("Dockerfile", "indy/Dockerfile")
            .withFileFromClasspath("indy_config.py", "indy/indy_config.py")
            .withFileFromClasspath("gateway.py", "indy/gateway.py"),
    ) {
    init {
        withExposedPorts(8001)
        waitingFor(
            Wait
                .forHttp("/status")
                .forPort(8001)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(5)),
        )
        withStartupAttempts(1)
    }

    fun proxyUrl(): String = "http://$host:${getMappedPort(8001)}"
}
