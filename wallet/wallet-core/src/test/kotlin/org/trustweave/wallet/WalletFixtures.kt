package org.trustweave.wallet

import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.model.DidDocument
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Shared test doubles for the wallet-core behaviour tests. */
internal fun sampleCredential(
    id: String,
    issuer: String = "did:key:z6MkIssuer",
    subject: String = "did:key:z6MkSubject",
    types: List<String> = listOf("VerifiableCredential"),
    expiresAt: Instant? = null,
    signed: Boolean = true,
): VerifiableCredential =
    VerifiableCredential(
        id = CredentialId(id),
        type = types.map { CredentialType.fromString(it) },
        issuer =
            Issuer.fromDid(
                org.trustweave.did.identifiers
                    .Did(issuer),
            ),
        issuanceDate = Clock.System.now() - 2.days,
        credentialSubject = CredentialSubject.fromIri(Iri(subject)),
        expirationDate = expiresAt,
        proof = if (signed) CredentialProof.JwtProof("eyJhbGciOiJFZERTQSJ9.stub.stub") else null,
    )

/** Plain storage-only wallet. */
internal open class MapWallet(
    override val walletId: String,
) : Wallet {
    protected val items = linkedMapOf<String, VerifiableCredential>()
    var closed = false
        private set

    override suspend fun store(credential: VerifiableCredential): String {
        val id = credential.id?.value ?: "generated-${items.size}"
        items[id] = credential
        return id
    }

    override suspend fun get(credentialId: String): VerifiableCredential? = items[credentialId]

    override suspend fun list(filter: CredentialFilter?): List<VerifiableCredential> = items.values.toList()

    override suspend fun delete(credentialId: String): Boolean = items.remove(credentialId) != null

    override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> {
        val builder = CredentialQueryBuilder().apply(query)
        return items.values.filter(builder.toPredicate())
    }

    override fun close() {
        closed = true
    }
}

/** Wallet that has a DID identity and tags/collections/archive support. */
internal class RichWallet(
    walletId: String,
    override val walletDid: String,
    override val holderDid: String,
) : MapWallet(walletId),
    DidManagement,
    CredentialOrganization,
    CredentialLifecycle {
    private val tagsById = mutableMapOf<String, Set<String>>()
    private val collections = linkedMapOf<String, CredentialCollection>()
    private val archived = linkedSetOf<String>()

    override suspend fun createDid(
        method: String,
        options: DidCreationOptions,
    ): String = "did:$method:created"

    override suspend fun getDids(): List<String> = listOf(walletDid)

    override suspend fun getPrimaryDid(): String = walletDid

    override suspend fun setPrimaryDid(did: String): Boolean = did == walletDid

    override suspend fun resolveDid(did: String): DidDocument? = null

    override suspend fun createCollection(
        name: String,
        description: String?,
    ): String {
        val id = "col-${collections.size + 1}"
        collections[id] = CredentialCollection(id, name, description)
        return id
    }

    override suspend fun getCollection(collectionId: String) = collections[collectionId]

    override suspend fun listCollections() = collections.values.toList()

    override suspend fun deleteCollection(collectionId: String) = collections.remove(collectionId) != null

    override suspend fun addToCollection(
        credentialId: String,
        collectionId: String,
    ) = collectionId in collections && credentialId in items

    override suspend fun removeFromCollection(
        credentialId: String,
        collectionId: String,
    ) = false

    override suspend fun getCredentialsInCollection(collectionId: String) = emptyList<VerifiableCredential>()

    override suspend fun tagCredential(
        credentialId: String,
        tags: Set<String>,
    ): Boolean {
        if (credentialId !in items) return false
        tagsById[credentialId] = (tagsById[credentialId] ?: emptySet()) + tags
        return true
    }

    override suspend fun untagCredential(
        credentialId: String,
        tags: Set<String>,
    ): Boolean {
        val existing = tagsById[credentialId] ?: return false
        tagsById[credentialId] = existing - tags
        return true
    }

    override suspend fun getTags(credentialId: String): Set<String> = tagsById[credentialId] ?: emptySet()

    override suspend fun getAllTags(): Set<String> = tagsById.values.flatten().toSet()

    override suspend fun findByTag(tag: String) = items.filterKeys { tag in (tagsById[it] ?: emptySet()) }.values.toList()

    override suspend fun addMetadata(
        credentialId: String,
        metadata: Map<String, Any>,
    ) = false

    override suspend fun getMetadata(credentialId: String): CredentialMetadata? = null

    override suspend fun updateNotes(
        credentialId: String,
        notes: String?,
    ) = false

    override suspend fun archive(credentialId: String): Boolean = credentialId in items && archived.add(credentialId)

    override suspend fun unarchive(credentialId: String): Boolean = archived.remove(credentialId)

    override suspend fun getArchived(): List<VerifiableCredential> = archived.mapNotNull { items[it] }

    override suspend fun refreshCredential(credentialId: String): VerifiableCredential? = items[credentialId]
}
