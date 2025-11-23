package com.trustweave.testkit.containers

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration

/**
 * TestContainer for PostgreSQL database.
 * 
 * Provides a local PostgreSQL instance for database wallet and other database-backed tests.
 * 
 * **Example Usage**:
 * ```kotlin
 * @Testcontainers
 * class DatabaseWalletTest {
 *     companion object {
 *         @JvmStatic
 *         val postgres = PostgresContainer.create()
 *     }
 *     
 *     @Test
 *     fun testWithDatabase() {
 *         val jdbcUrl = postgres.getJdbcUrl()
 *         // Use for database wallet testing
 *     }
 * }
 * ```
 */
class PostgresContainer private constructor(
    dockerImageName: DockerImageName
) : GenericContainer<PostgresContainer>(dockerImageName) {
    
    private var dbName: String = "vericore_test"
    private var dbUser: String = "test"
    private var dbPassword: String = "test"
    
    companion object {
        /**
         * Default PostgreSQL Docker image.
         */
        private val DEFAULT_IMAGE = DockerImageName.parse("postgres:15-alpine")
        
        /**
         * Creates a PostgreSQL container with default configuration.
         */
        @JvmStatic
        fun create(): PostgresContainer {
            return PostgresContainer(DEFAULT_IMAGE)
                .apply {
                    dbName = "vericore_test"
                    dbUser = "test"
                    dbPassword = "test"
                }
                .withEnv("POSTGRES_DB", "vericore_test")
                .withEnv("POSTGRES_USER", "test")
                .withEnv("POSTGRES_PASSWORD", "test")
                .withExposedPorts(5432)
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofMinutes(2))
        }
        
        /**
         * Creates a PostgreSQL container with custom configuration.
         */
        @JvmStatic
        fun create(
            databaseName: String = "vericore_test",
            username: String = "test",
            password: String = "test"
        ): PostgresContainer {
            return PostgresContainer(DEFAULT_IMAGE)
                .apply {
                    dbName = databaseName
                    dbUser = username
                    dbPassword = password
                }
                .withEnv("POSTGRES_DB", databaseName)
                .withEnv("POSTGRES_USER", username)
                .withEnv("POSTGRES_PASSWORD", password)
                .withExposedPorts(5432)
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofMinutes(2))
        }
    }
    
    /**
     * Gets the JDBC URL for connecting to the database.
     */
    fun getJdbcUrl(): String {
        return "jdbc:postgresql://${host}:${getMappedPort(5432)}/$dbName"
    }
    
    /**
     * Gets the database name.
     */
    fun getDatabaseName(): String {
        return dbName
    }
    
    /**
     * Gets the username.
     */
    fun getUsername(): String {
        return dbUser
    }
    
    /**
     * Gets the password.
     */
    fun getPassword(): String {
        return dbPassword
    }
}

