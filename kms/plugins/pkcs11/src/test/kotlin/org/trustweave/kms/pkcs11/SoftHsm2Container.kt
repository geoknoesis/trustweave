package org.trustweave.kms.pkcs11

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.nio.file.Files
import java.nio.file.Path

/**
 * Testcontainers wrapper around a SoftHSM2 instance used to provide a deterministic
 * PKCS#11 token for the [Pkcs11KeyManagementServiceIntegrationTest].
 *
 * # Why this is not the primary entry point for tests
 *
 * The JDK's `SunPKCS11` provider runs in-process and must `dlopen` (or `LoadLibrary`)
 * the vendor PKCS#11 shared library **on the host JVM**. A library loaded inside a
 * container is not addressable from the test JVM running on the host. To bridge that
 * gap there are two common strategies:
 *
 *  1. **Mount the token state on the host.** The host installs `libsofthsm2.so`/`.dll`
 *     itself, and we use the container only to bootstrap or share token data through
 *     a bind-mounted `/var/lib/softhsm/tokens` volume. The host JVM then loads the
 *     host-installed library against the shared tokens.
 *  2. **Copy the library out.** After `start()`, copy `/usr/lib/softhsm/libsofthsm2.so`
 *     from the container to a host temp path with [GenericContainer.copyFileFromContainer]
 *     and load that with `SunPKCS11`. This is fragile — the library is built against the
 *     container's glibc and ABI assumptions, which often do not match the host. We do
 *     not use this strategy by default; it is here as a fallback.
 *
 * For the integration test we therefore default to (1) **plus** the simpler form of
 * (1) which is "the host already has softhsm2 installed and `SOFTHSM2_LIB` is set in
 * the environment" — the test reads `SOFTHSM2_LIB` and uses the host library directly.
 * See [Pkcs11KeyManagementServiceIntegrationTest] for the runtime contract.
 *
 * # When to use this wrapper
 *
 * Use this wrapper when:
 *  - You want to run the integration tests against a clean, reproducible token state
 *    on every test run (the container is ephemeral by default).
 *  - You are on Linux *and* the container's `libsofthsm2.so` is ABI-compatible with
 *    the host JVM (typically: Linux host, glibc-based image).
 *
 * # Prerequisites
 *
 *  - A running Docker daemon reachable from the test JVM (Testcontainers default rules
 *    apply — see `~/.testcontainers.properties` or `DOCKER_HOST`).
 *  - On the host, a `libsofthsm2.so` (Linux) or `softhsm2-x64.dll` (Windows) available
 *    on disk for `SunPKCS11` to load. If the host has no PKCS#11 library at all, the
 *    test is skipped by [Pkcs11KeyManagementServiceIntegrationTest].
 *
 * # Image choice
 *
 * The default image is `dockershelf/softhsm2:latest` because it is small, community-
 * maintained, and ships `softhsm2-util` plus the native library at the conventional
 * Debian path `/usr/lib/softhsm/libsofthsm2.so`. If the image is unavailable in your
 * registry mirror, override [IMAGE] via a system property (`-Dsofthsm2.image=...`) or
 * publish your own image with `softhsm2-util` on `$PATH` and the library at
 * [DEFAULT_LIBRARY_PATH].
 */
class SoftHsm2Container(
    image: DockerImageName = DockerImageName.parse(System.getProperty("softhsm2.image", IMAGE)),
) : GenericContainer<SoftHsm2Container>(image) {

    /** PIN used by [Pkcs11KeyManagementServiceIntegrationTest]; must match. */
    val pin: CharArray = "5678".toCharArray()

    /** SO-PIN used to initialize the token. */
    val soPin: CharArray = "1234".toCharArray()

    /** PKCS#11 slot the token is initialized into. */
    val slot: Int = 0

    /** PKCS#11 token label. */
    val tokenLabel: String = "trustweave-test"

    /**
     * Host path to the SoftHSM2 native library that the test JVM should load via
     * `SunPKCS11`. Populated by [start] — either from the host installation if
     * [SOFTHSM2_LIB_PROPERTY] / `SOFTHSM2_LIB` is set, or from a file copied out of
     * the container.
     */
    lateinit var libraryPath: String
        private set

    init {
        // The image is a one-shot bootstrap tool — keep it alive long enough to copy
        // tokens out and inspect logs. `tail -f /dev/null` is the conventional idle.
        withCommand("sh", "-c", "tail -f /dev/null")
        waitingFor(Wait.forLogMessage(".*", 1).withStartupTimeout(java.time.Duration.ofMinutes(2)))
    }

    /**
     * Starts the container and initializes a token in [slot] with label [tokenLabel],
     * SO-PIN [soPin] and user PIN [pin]. The library path is resolved from the host
     * (preferring [SOFTHSM2_LIB_PROPERTY] / `SOFTHSM2_LIB`); if neither is set, the
     * library is copied out of the container to a temp file as a best-effort fallback.
     */
    override fun start() {
        super.start()

        val initResult = execInContainer(
            "softhsm2-util",
            "--init-token",
            "--slot", slot.toString(),
            "--label", tokenLabel,
            "--so-pin", String(soPin),
            "--pin", String(pin),
        )
        check(initResult.exitCode == 0) {
            "softhsm2-util --init-token failed (exit=${initResult.exitCode}): " +
                "stdout=${initResult.stdout} stderr=${initResult.stderr}"
        }

        libraryPath = System.getProperty(SOFTHSM2_LIB_PROPERTY)
            ?: System.getenv("SOFTHSM2_LIB")
                    ?: copyLibraryFromContainer()
    }

    /**
     * Copies `/usr/lib/softhsm/libsofthsm2.so` out of the container to a host temp file.
     *
     * **Caveat:** the resulting `.so` is built against the container's libc; loading it
     * on a host JVM with a different libc/ABI will fail at provider-init time. Prefer
     * setting `SOFTHSM2_LIB` to the host-installed library instead.
     */
    private fun copyLibraryFromContainer(): String {
        val temp: Path = Files.createTempFile("trustweave-libsofthsm2-", ".so")
        copyFileFromContainer(DEFAULT_LIBRARY_PATH, temp.toAbsolutePath().toString())
        return temp.toAbsolutePath().toString()
    }

    companion object {
        /**
         * Default image. Pinned to a tag rather than `:latest` would be preferable in
         * production CI; tests override via `-Dsofthsm2.image=...` if needed.
         */
        const val IMAGE: String = "dockershelf/softhsm2:latest"

        /** Conventional Debian path to the SoftHSM2 PKCS#11 library inside the image. */
        const val DEFAULT_LIBRARY_PATH: String = "/usr/lib/softhsm/libsofthsm2.so"

        /** System property used to point the container at a host-installed library. */
        const val SOFTHSM2_LIB_PROPERTY: String = "softhsm2.lib"
    }
}
