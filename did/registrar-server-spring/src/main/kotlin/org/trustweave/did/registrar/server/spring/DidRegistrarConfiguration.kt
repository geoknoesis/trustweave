package org.trustweave.did.registrar.server.spring

import org.trustweave.did.registrar.DidRegistrar
import org.trustweave.did.registrar.storage.InMemoryJobStorage
import org.trustweave.did.registrar.storage.JobStorage
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

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
 *         return KmsBasedRegistrar(kms)
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
        jobStorage: JobStorage
    ): DidRegistrarService {
        return DidRegistrarService(registrar, jobStorage)
    }
}

