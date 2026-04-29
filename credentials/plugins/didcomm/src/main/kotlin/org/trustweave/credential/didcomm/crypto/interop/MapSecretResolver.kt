package org.trustweave.credential.didcomm.crypto.interop

import org.didcommx.didcomm.secret.Secret
import org.didcommx.didcomm.secret.SecretResolver
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe [SecretResolver] backed by an in-memory map (kid → [Secret]).
 *
 * Populate via [put] before packing or unpacking. Suitable for tests and for wiring
 * KMS-exported secrets in application startup code.
 */
class MapSecretResolver(
    initial: Map<String, Secret> = emptyMap(),
) : SecretResolver {
    private val secrets = ConcurrentHashMap(initial)

    fun put(kid: String, secret: Secret) {
        secrets[kid] = secret
    }

    fun putAll(map: Map<String, Secret>) {
        secrets.putAll(map)
    }

    override fun findKey(kid: String): Optional<Secret> = Optional.ofNullable(secrets[kid])

    override fun findKeys(kids: List<String>): Set<String> = kids.filter { secrets.containsKey(it) }.toSet()
}
