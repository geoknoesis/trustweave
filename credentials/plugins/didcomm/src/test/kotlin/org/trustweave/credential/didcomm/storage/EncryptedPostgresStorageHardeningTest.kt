package org.trustweave.credential.didcomm.storage

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.credential.didcomm.crypto.secret.encryption.KeyEncryption
import org.trustweave.credential.didcomm.models.DidCommMessage
import org.trustweave.credential.didcomm.storage.database.PostgresDidCommMessageStorage
import org.trustweave.credential.didcomm.storage.encryption.AesMessageEncryption
import org.trustweave.credential.didcomm.storage.encryption.EncryptedMessage
import org.trustweave.credential.didcomm.storage.search.BooleanOperator
import org.trustweave.credential.didcomm.storage.search.ComparisonOperator
import org.trustweave.credential.didcomm.storage.search.PostgresFullTextSearch
import org.trustweave.credential.didcomm.storage.search.QueryCondition
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Statement
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A DataSource that records every SQL string and bound parameter, with no database behind it. */
private class RecordingDataSource(
    private val updateCount: (String) -> Int = { 1 },
) {
    val sql = mutableListOf<String>()
    val params = mutableListOf<Any?>()

    private fun defaultFor(type: Class<*>): Any? =
        when (type) {
            java.lang.Boolean.TYPE -> false
            Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            else -> null
        }

    private fun <T> proxy(
        iface: Class<T>,
        handler: InvocationHandler,
    ): T = iface.cast(Proxy.newProxyInstance(iface.classLoader, arrayOf(iface), handler))

    val dataSource: DataSource =
        proxy(DataSource::class.java) { _, m, _ ->
            if (m.name == "getConnection") connection() else defaultFor(m.returnType)
        }

    private fun connection(): Connection =
        proxy(Connection::class.java) { _, m, args ->
            when (m.name) {
                "prepareStatement" -> prepared(args!![0] as String)
                "createStatement" ->
                    proxy(Statement::class.java) { _, sm, sargs ->
                        if (sm.name == "execute") sql.add(sargs!![0] as String)
                        defaultFor(sm.returnType)
                    }
                "createArrayOf" -> {
                    val values = (args!![1] as Array<*>).toList()
                    params.add(values)
                    proxy(java.sql.Array::class.java) { _, _, _ -> null }
                }
                else -> defaultFor(m.returnType)
            }
        }

    private fun prepared(text: String): PreparedStatement {
        sql.add(text)
        return proxy(PreparedStatement::class.java) { _, m, args ->
            when {
                m.name.startsWith("set") && args != null && args.size >= 2 && args[1] !is java.sql.Array ->
                    params.add(if (args[1] is ByteArray) (args[1] as ByteArray).toList() else args[1])
                m.name == "executeUpdate" -> return@proxy updateCount(text)
            }
            defaultFor(m.returnType)
        }
    }
}

class EncryptedPostgresStorageHardeningTest {
    private val key = ByteArray(32) { it.toByte() }
    private val message =
        DidCommMessage(
            id = "m-1",
            type = "https://didcomm.org/basicmessage/2.0/message",
            from = "did:example:alice-sender",
            to = listOf("did:example:bob-recipient"),
            body = buildJsonObject { put("content", "super-secret-body-text") },
            created = "2026-01-01T00:00:00Z",
            expiresTime = "2027-01-01T00:00:00Z",
            thid = "thread-secret-1",
            pthid = "parent-thread-secret",
        )

    @Test
    fun `encrypted storage never writes identifiers or body in plaintext`() =
        runBlocking<Unit> {
            val rec = RecordingDataSource()
            val storage = PostgresDidCommMessageStorage(rec.dataSource, AesMessageEncryption(key))
            storage.store(message)

            val written = rec.params.joinToString("|") { it.toString() }
            listOf("alice-sender", "bob-recipient", "super-secret-body-text", "thread-secret-1", "parent-thread-secret", "2027-01-01")
                .forEach { assertFalse(written.contains(it), "plaintext '$it' reached the database: $written") }
            val insert = rec.sql.first { it.contains("INSERT INTO didcomm_messages") }
            assertTrue(insert.contains("NULL"), insert) // body / expires_time are literally NULL
        }

    @Test
    fun `another sender cannot overwrite an existing message id`() =
        runBlocking<Unit> {
            val rec = RecordingDataSource { if (it.contains("ON CONFLICT (id)")) 0 else 1 }
            val storage = PostgresDidCommMessageStorage(rec.dataSource, null)
            assertFailsWith<IllegalStateException> { storage.store(message.copy(from = "did:example:mallory")) }
            // and no index rows were attached to the victim message
            assertFalse(rec.sql.any { it.contains("INSERT INTO didcomm_message_dids") })
            assertTrue(rec.sql.first { it.contains("ON CONFLICT (id)") }.contains("IS NOT DISTINCT FROM EXCLUDED.from_did"))
        }

    @Test
    fun `AES ciphertext is bound to its row id and legacy ciphertext still decrypts`() =
        runBlocking<Unit> {
            val aes = AesMessageEncryption(key, keyVersion = 3)
            val enc = aes.encrypt(message)
            assertEquals(message, aes.decrypt(enc, "m-1"))
            // moved onto another row id: authentication fails
            assertFailsWith<Exception> { aes.decrypt(enc, "m-2") }
            assertFailsWith<IllegalStateException> { aes.decrypt(enc) }

            // a row written before AAD binding: raw 12-byte nonce, no associated data
            val nonce = ByteArray(12) { 7 }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            val legacyJson =
                kotlinx.serialization.json.Json
                    .encodeToString(DidCommMessage.serializer(), message)
            val legacy = EncryptedMessage(1, cipher.doFinal(legacyJson.toByteArray()), nonce)
            assertEquals(message, aes.decrypt(legacy, "m-1"))
            assertEquals(message, aes.decrypt(legacy))
        }

    @Test
    fun `blind index is deterministic keyed and purpose separated`() =
        runBlocking<Unit> {
            val a = AesMessageEncryption(key)
            val b = AesMessageEncryption(ByteArray(32) { 9 })
            assertEquals(a.blindIndex("did", "x"), a.blindIndex("did", "x"))
            assertTrue(a.blindIndex("did", "x") != a.blindIndex("thread", "x"))
            assertTrue(a.blindIndex("did", "x") != b.blindIndex("did", "x"))
        }

    @Test
    fun `KeyEncryption binds associated data`() {
        val ke = KeyEncryption(key)
        val data = ke.encrypt("hello".toByteArray(), "purpose-a".toByteArray())
        assertContentEquals("hello".toByteArray(), ke.decrypt(data, "purpose-a".toByteArray()))
        assertFailsWith<Exception> { ke.decrypt(data, "purpose-b".toByteArray()) }
        assertFailsWith<Exception> { ke.decrypt(data) }
        assertContentEquals("hello".toByteArray(), ke.decrypt(ke.encrypt("hello".toByteArray())))
    }

    private fun search() = PostgresFullTextSearch(RecordingDataSource().dataSource, enableFullTextIndex = false)

    @Test
    fun `complex query only accepts allowlisted columns`() {
        assertFailsWith<IllegalArgumentException> {
            search().buildWhere(
                listOf(QueryCondition("1=1) OR (body", ComparisonOperator.EQ, "x")),
                BooleanOperator.AND,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            search().buildWhere(listOf(QueryCondition("message_json", ComparisonOperator.EQ, "x")), BooleanOperator.AND)
        }
    }

    @Test
    fun `BETWEEN and IN keep parameter positions aligned`() {
        val built =
            search().buildWhere(
                listOf(
                    QueryCondition("created_time", ComparisonOperator.BETWEEN, listOf("a", "b")),
                    QueryCondition("type", ComparisonOperator.IN, listOf("t1", "t2")),
                    QueryCondition("thid", ComparisonOperator.EQ, "th"),
                ),
                BooleanOperator.AND,
            )
        assertEquals(listOf<Any?>("a", "b", null, "th"), built.params)
        assertEquals(listOf<Any?>("t1", "t2"), built.arrays[3])
        assertEquals(4, built.sql.count { it == '?' })
        assertTrue(built.sql.contains("type = ANY(?)"))
        assertTrue(built.sql.contains("is_encrypted IS NOT TRUE"))
    }
}
