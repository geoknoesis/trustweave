package org.trustweave.credential.vi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.trustweave.credential.vi.crypto.sha256B64Url
import org.trustweave.credential.vi.verification.BudgetReservation
import org.trustweave.credential.vi.verification.PostgresIntentLedger
import org.trustweave.credential.vi.verification.SettlementOutcome
import kotlin.test.assertEquals

class CrossLanguageLedgerModelTest {
    @Test
    fun `PostgreSQL agrees with Python model through 214 reserve and settlement transitions`() {
        val fixture = Json.parseToJsonElement(checkNotNull(javaClass.getResource("/vi_stateful_model.json")).readText()).jsonObject
        val actions = fixture["actions"]!!.jsonArray
        assertEquals(214, actions.size)
        PostgreSQLContainer<Nothing>("postgres:16-alpine").use { postgres ->
            postgres.start()
            val source =
                PGSimpleDataSource().apply {
                    setURL(postgres.jdbcUrl)
                    user = postgres.username
                    password = postgres.password
                }
            var ledger = PostgresIntentLedger(source)
            ledger.initializeSchema()
            for ((index, item) in actions.withIndex()) {
                if (index % 17 == 0) ledger = PostgresIntentLedger(source)
                val action = item.jsonObject

                fun text(key: String) = action[key]!!.jsonPrimitive.content

                fun hash(key: String) = sha256B64Url(text(key).toByteArray())
                val accepted =
                    if (text("op") == "reserve") {
                        ledger.reserve(
                            hash("scope"),
                            hash("transaction"),
                            hash("challenge"),
                            BudgetReservation(
                                "USD",
                                100,
                                action["amount"]!!.jsonPrimitive.long,
                                action["maximumOccurrences"]!!.jsonPrimitive.long,
                            ),
                        )
                    } else {
                        ledger.reconcileHashes(hash("scope"), hash("transaction"), SettlementOutcome.valueOf(text("op")), text("evidence"))
                    }
                assertEquals(action["expected"]!!.jsonPrimitive.boolean, accepted, "transition $index")
                val health = ledger.healthSnapshot()
                val states = action["expectedStates"]!!.jsonObject
                assertEquals(states["RESERVED"]!!.jsonPrimitive.long, health.pending, "pending at $index")
                assertEquals(states["SETTLED"]!!.jsonPrimitive.long, health.settled, "settled at $index")
                assertEquals(states["RELEASED"]!!.jsonPrimitive.long, health.released, "released at $index")
                val expected =
                    action["expectedSpent"]!!
                        .jsonObject
                        .mapKeys {
                            sha256B64Url(
                                it.key.toByteArray(),
                            )
                        }.mapValues { it.value.jsonPrimitive.long }
                val actual = mutableMapOf<String, Long>()
                source.connection.use { connection ->
                    connection.createStatement().use { sql ->
                        sql.executeQuery("SELECT scope,spent FROM vi_budget_accounts").use { rows ->
                            while (rows.next()) actual[rows.getString(1)] = rows.getLong(2)
                        }
                    }
                }
                assertEquals(expected, actual, "durable spending at $index")
            }
        }
    }
}
