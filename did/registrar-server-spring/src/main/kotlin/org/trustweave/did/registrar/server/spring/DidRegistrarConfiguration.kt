package org.trustweave.did.registrar.server.spring

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.trustweave.did.registrar.DidRegistrar
import org.trustweave.did.registrar.storage.JobStorage

/**
 * Spring Boot configuration for DID Registrar Server.
 *
 * This configuration class sets up the necessary beans for the DID Registrar server.
 * You can override these beans in your application configuration if you need custom behavior.
 *
 * **Example Usage:**
 * ```kotlin
 * @SpringBootApplication
 * class MyApplication {
 *     @Bean
 *     fun registrar(): DidRegistrar {
 *         // Your DidRegistrar implementation
 *         return KmsBasedRegistrar(kms) { method, kms -> didMethodFor(method, kms) }
 *     }
 *
 *     @Bean
 *     fun jobStorage(): JobStorage {
 *         // Use InMemoryJobStorage for development or DatabaseJobStorage for production
 *         return InMemoryJobStorage()
 *     }
 * }
 * ```
 */
@Configuration
class DidRegistrarConfiguration {
    /**
     * Creates the DID Registrar Service bean.
     *
     * Note: You must provide DidRegistrar and JobStorage beans in your application configuration.
     */
    @Bean
    fun didRegistrarService(
        registrar: DidRegistrar,
        jobStorage: JobStorage,
    ): DidRegistrarService = DidRegistrarService(registrar, jobStorage)

    /**
     * Authentication for the mutating registrar endpoints, from
     * `trustweave.registrar.auth.bearer-token` (32-256 printable ASCII characters) or
     * `trustweave.registrar.auth.fronted-by-proxy` (a statement of what authenticates callers in
     * front of this server). With neither set, every create/update/deactivate is refused.
     */
    @Bean
    fun registrarAuthentication(
        @Value("\${trustweave.registrar.auth.bearer-token:}") bearerToken: String,
        @Value("\${trustweave.registrar.auth.fronted-by-proxy:}") frontedByProxy: String,
    ): RegistrarAuthentication = RegistrarAuthentication.fromProperties(bearerToken, frontedByProxy)
}
