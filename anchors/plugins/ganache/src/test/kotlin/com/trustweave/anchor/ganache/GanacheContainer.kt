package com.trustweave.anchor.ganache

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

/**
 * Testcontainers wrapper for Ganache CLI Docker container.
 *
 * Automatically downloads and runs Ganache in a Docker container for testing.
 * Uses the official trufflesuite/ganache-cli image.
 *
 * @see https://hub.docker.com/r/trufflesuite/ganache-cli/
 */
class GanacheContainer : GenericContainer<GanacheContainer>(
    DockerImageName.parse("trufflesuite/ganache-cli:latest")
) {
    companion object {
        private const val GANACHE_PORT = 8545
        private const val GANACHE_MNEMONIC = "test test test test test test test test test test test junk"
    }

    init {
        withExposedPorts(GANACHE_PORT)
        withCommand(
            "--deterministic",
            "--mnemonic", GANACHE_MNEMONIC,
            "--port", GANACHE_PORT.toString(),
            "--networkId", "1337",
            "--gasLimit", "12000000" // Higher gas limit for large data transactions
        )
        waitingFor(Wait.forListeningPort())
        withStartupTimeout(java.time.Duration.ofSeconds(30))
    }

    /**
     * Get the RPC URL for connecting to Ganache.
     * Use this URL when creating GanacheBlockchainAnchorClient.
     */
    fun getRpcUrl(): String {
        return "http://${host}:${getMappedPort(GANACHE_PORT)}"
    }

    /**
     * Get the first account's private key (deterministic from mnemonic).
     * This is Hardhat's default account 0.
     */
    fun getFirstAccountPrivateKey(): String {
        return "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"
    }

    /**
     * Get the first account's address (deterministic from mnemonic).
     * This is Hardhat's default account 0.
     */
    fun getFirstAccountAddress(): String {
        return "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
    }
}

