package org.trustweave.contract

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.anchor.DefaultBlockchainAnchorRegistry
import org.trustweave.contract.ContractVerificationAndExecutionTest.FakeCredentialService
import org.trustweave.contract.models.ContractParties
import org.trustweave.contract.models.ContractStatus
import org.trustweave.contract.models.ContractTerms
import org.trustweave.contract.models.ContractType
import org.trustweave.contract.models.ExecutionContext
import org.trustweave.contract.models.ExecutionModel
import org.trustweave.contract.models.Obligation
import org.trustweave.contract.models.ObligationType
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContractLifecycleHardeningTest {
    private val primary = "did:key:z6MkPrimary"
    private val counter = "did:key:z6MkCounter"
    private val outsider = "did:key:z6MkOutsider"
    private val chain = "test:chain"

    private fun request() =
        ContractDraftRequest(
            contractType = ContractType.Insurance,
            executionModel = ExecutionModel.Manual,
            parties = ContractParties(primary, counter),
            terms =
                ContractTerms(
                    obligations = listOf(Obligation("ob-1", primary, "Pay", ObligationType.PAYMENT)),
                    conditions = emptyList(),
                ),
            effectiveDate = "2025-01-01T00:00:00Z",
            expirationDate = "2099-01-01T00:00:00Z",
            contractData = buildJsonObject { put("domain", "test") },
        )

    private fun service(
        credentials: FakeCredentialService? = FakeCredentialService(),
        policy: TrustedIssuerPolicy? = null,
        verifyAnchors: Boolean = false,
        client: InMemoryBlockchainAnchorClient = InMemoryBlockchainAnchorClient(chain),
    ) = DefaultSmartContractService(
        credentialService = credentials,
        blockchainRegistry = DefaultBlockchainAnchorRegistry().apply { register(chain, client) },
        engines =
            org.trustweave.contract.evaluation
                .EvaluationEngines(),
        credentialResolver = null,
        trustedIssuerPolicy = policy,
        verifyAnchorOnVerify = verifyAnchors,
    )

    private suspend fun DefaultSmartContractService.bind(
        id: String,
        issuer: String = primary,
    ) = bindContract(id, issuer, "$issuer#key-1", chain)

    @Test
    fun `bind is rejected from every status except DRAFT and PENDING`() =
        runTest {
            val illegal = ContractStatus.values().filter { it != ContractStatus.DRAFT && it != ContractStatus.PENDING }
            for (target in illegal) {
                val svc = service()
                val id = svc.createDraft(request()).getOrThrow().id
                svc.bind(id).getOrThrow()
                svc.activateContract(id).getOrThrow()
                if (target == ContractStatus.EXECUTED) {
                    svc.executeContract(svc.getContract(id).getOrThrow(), ExecutionContext()).getOrThrow()
                } else if (target != ContractStatus.ACTIVE) {
                    svc.updateStatus(id, target).getOrThrow()
                }
                val before = svc.getContract(id).getOrThrow()
                assertTrue(svc.bind(id).isFailure, "bind from $target must fail")
                assertEquals(before, svc.getContract(id).getOrThrow(), "bind from $target must not change the contract")
            }
        }

    @Test
    fun `executed contract cannot be re-bound and re-executed`() =
        runTest {
            val svc = service()
            val id = svc.createDraft(request()).getOrThrow().id
            svc.bind(id).getOrThrow()
            val active = svc.activateContract(id).getOrThrow()
            svc.executeContract(active, ExecutionContext()).getOrThrow()

            assertTrue(svc.bind(id).isFailure)
            assertEquals(ContractStatus.EXECUTED, svc.getContract(id).getOrThrow().status)
            assertTrue(svc.executeContract(active, ExecutionContext()).isFailure)
        }

    @Test
    fun `every illegal transition through updateStatus is rejected`() =
        runTest {
            for (from in ContractStatus.values()) {
                for (to in ContractStatus.values()) {
                    // EXECUTED is only reachable through executeContract, never through updateStatus.
                    val legal = ContractValidator.validateStateTransition(from, to).isValid() && to != ContractStatus.EXECUTED
                    val svc = service()
                    val id = svc.createDraft(request()).getOrThrow().id
                    reach(svc, id, from)
                    val result = svc.updateStatus(id, to)
                    assertEquals(legal, result.isSuccess, "$from -> $to")
                }
            }
        }

    private suspend fun reach(
        svc: DefaultSmartContractService,
        id: String,
        target: ContractStatus,
    ) {
        if (target == ContractStatus.DRAFT) return
        svc.bind(id).getOrThrow() // PENDING, with a credential and an anchor
        if (target == ContractStatus.PENDING) return
        svc.activateContract(id).getOrThrow()
        when (target) {
            ContractStatus.ACTIVE -> Unit
            ContractStatus.EXECUTED -> svc.executeContract(svc.getContract(id).getOrThrow(), ExecutionContext()).getOrThrow()
            else -> svc.updateStatus(id, target).getOrThrow()
        }
    }

    @Test
    fun `activate is rejected from DRAFT and EXECUTED`() =
        runTest {
            val svc = service()
            val id = svc.createDraft(request()).getOrThrow().id
            assertTrue(svc.activateContract(id).isFailure)
            svc.bind(id).getOrThrow()
            val active = svc.activateContract(id).getOrThrow()
            svc.executeContract(active, ExecutionContext()).getOrThrow()
            assertTrue(svc.activateContract(id).isFailure)
        }

    @Test
    fun `updateStatus persists reason and metadata`() =
        runTest {
            val svc = service()
            val id = svc.createDraft(request()).getOrThrow().id
            val meta = buildJsonObject { put("ticket", "T-1") }
            svc.updateStatus(id, ContractStatus.CANCELLED, "customer request", meta).getOrThrow()

            val change = svc.statusHistory(id).single()
            assertEquals(ContractStatus.DRAFT, change.from)
            assertEquals(ContractStatus.CANCELLED, change.to)
            assertEquals("customer request", change.reason)
            assertEquals(meta, change.metadata)
        }

    @Test
    fun `contract numbers never collide`() =
        runTest {
            val svc = service()
            val numbers = (1..500).map { svc.createDraft(request()).getOrThrow().contractNumber }
            assertEquals(numbers.size, numbers.toSet().size)
        }

    @Test
    fun `verifyContract finds contract through credential index and drops stale credential after rebind`() =
        runTest {
            val svc = service()
            val id = svc.createDraft(request()).getOrThrow().id
            val first = svc.bind(id).getOrThrow()
            assertTrue(svc.verifyContract(first.credentialId).getOrThrow())
            val second = svc.bind(id).getOrThrow()
            assertNotEquals(first.credentialId, second.credentialId)
            assertTrue(svc.verifyContract(second.credentialId).getOrThrow())
            assertTrue(svc.verifyContract(first.credentialId).isFailure)
        }

    private fun outsiderIssuer() =
        { _: org.trustweave.credential.model.vc.Issuer ->
            org.trustweave.credential.model.vc.Issuer
                .from(
                    org.trustweave.core.identifiers
                        .Iri(outsider),
                )
        }

    @Test
    fun `verifyContract accepts the service's own issuer DID even when it is not a party`() =
        runTest {
            val svc = service()
            val id = svc.createDraft(request()).getOrThrow().id
            val bound = svc.bind(id, outsider).getOrThrow()
            assertTrue(svc.verifyContract(bound.credentialId).getOrThrow())
        }

    @Test
    fun `verifyContract rejects a credential whose issuer is neither a party nor the issuing DID`() =
        runTest {
            val svc = service(FakeCredentialService(issuerOverride = outsiderIssuer()))
            val id = svc.createDraft(request()).getOrThrow().id
            val bound = svc.bind(id, primary).getOrThrow()
            assertFalse(svc.verifyContract(bound.credentialId).getOrThrow())
        }

    @Test
    fun `verifyContract accepts a foreign issuer only when the trusted issuer policy allows it`() =
        runTest {
            val creds = { FakeCredentialService(issuerOverride = outsiderIssuer()) }
            val trusting = service(creds(), policy = TrustedIssuerPolicy { issuer, _ -> issuer == outsider })
            val id = trusting.createDraft(request()).getOrThrow().id
            assertTrue(trusting.verifyContract(trusting.bind(id, primary).getOrThrow().credentialId).getOrThrow())

            val refusing = service(creds(), policy = TrustedIssuerPolicy { _, _ -> false })
            val id2 = refusing.createDraft(request()).getOrThrow().id
            assertFalse(refusing.verifyContract(refusing.bind(id2, primary).getOrThrow().credentialId).getOrThrow())
        }

    @Test
    fun `anchor verification passes for an intact anchor`() =
        runTest {
            val svc = service(verifyAnchors = true)
            val id = svc.createDraft(request()).getOrThrow().id
            val bound = svc.bind(id).getOrThrow()
            assertTrue(svc.verifyContract(bound.credentialId).getOrThrow())
        }

    @Test
    fun `anchor verification fails when the chain no longer attests to the contract`() =
        runTest {
            val inner = InMemoryBlockchainAnchorClient(chain)
            var tamper = false
            val client =
                object : org.trustweave.anchor.BlockchainAnchorClient {
                    override suspend fun writePayload(
                        payload: kotlinx.serialization.json.JsonElement,
                        mediaType: String,
                    ) = inner.writePayload(payload, mediaType)

                    override suspend fun readPayload(ref: org.trustweave.anchor.AnchorRef) =
                        inner.readPayload(ref).let {
                            if (tamper) it.copy(payload = buildJsonObject { put("contractId", "other") }) else it
                        }
                }
            val svc =
                DefaultSmartContractService(
                    credentialService = FakeCredentialService(),
                    blockchainRegistry = DefaultBlockchainAnchorRegistry().apply { register(chain, client) },
                    verifyAnchorOnVerify = true,
                )
            val id = svc.createDraft(request()).getOrThrow().id
            val bound = svc.bind(id).getOrThrow()
            assertTrue(svc.verifyContract(bound.credentialId).getOrThrow())
            tamper = true
            assertFalse(svc.verifyContract(bound.credentialId).getOrThrow())
        }

    @Test
    fun `anchor verification fails closed when no client is registered for the anchor's chain`() =
        runTest {
            val registry = DefaultBlockchainAnchorRegistry().apply { register(chain, InMemoryBlockchainAnchorClient(chain)) }
            val svc =
                DefaultSmartContractService(
                    credentialService = FakeCredentialService(),
                    blockchainRegistry = registry,
                    verifyAnchorOnVerify = true,
                )
            val id = svc.createDraft(request()).getOrThrow().id
            val bound = svc.bind(id).getOrThrow()
            assertTrue(svc.verifyContract(bound.credentialId).getOrThrow())

            registry.unregister(chain)

            assertFalse(svc.verifyContract(bound.credentialId).getOrThrow(), "no client for the chain must not verify")
        }

    @Test
    fun `a slow bind on one contract never stalls an unrelated contract`() =
        runBlocking {
            val gate = CompletableDeferred<Unit>()
            val entered = AtomicInteger()
            val slow =
                object : FakeCredentialService() {
                    override suspend fun issue(
                        request: org.trustweave.credential.requests.IssuanceRequest,
                    ): org.trustweave.credential.results.IssuanceResult {
                        entered.incrementAndGet()
                        gate.await()
                        return super.issue(request)
                    }
                }
            val svc = service(slow)
            // More blocked binds than the 64 lock stripes of the old implementation guarantees collisions there.
            val blockedIds = (1..80).map { svc.createDraft(request()).getOrThrow().id }
            val binds = blockedIds.map { id -> async(Dispatchers.Default) { svc.bind(id) } }
            withTimeout(10_000) { while (entered.get() < blockedIds.size) delay(10) }

            val bystander = svc.createDraft(request()).getOrThrow().id
            val done = withTimeout(2_000) { svc.updateStatus(bystander, ContractStatus.CANCELLED) }
            assertTrue(done.isSuccess)
            assertEquals(blockedIds.size, svc.activeLockCount(), "one lock per contract with an operation in flight")

            gate.complete(Unit)
            binds.awaitAll().forEach { assertTrue(it.isSuccess) }
            assertEquals(0, svc.activeLockCount(), "lock entries must be released when idle")
        }

    @Test
    fun `lock entries are removed after success, failure and cancellation`() =
        runBlocking {
            val svc = service()
            val id = svc.createDraft(request()).getOrThrow().id
            svc.bind(id).getOrThrow()
            assertTrue(svc.bind("unknown").isFailure)
            assertEquals(0, svc.activeLockCount())

            val gate = CompletableDeferred<Unit>()
            val entered = CompletableDeferred<Unit>()
            val blocking =
                object : FakeCredentialService() {
                    override suspend fun issue(
                        request: org.trustweave.credential.requests.IssuanceRequest,
                    ): org.trustweave.credential.results.IssuanceResult {
                        entered.complete(Unit)
                        gate.await()
                        return super.issue(request)
                    }
                }
            val svc2 = service(blocking)
            val id2 = svc2.createDraft(request()).getOrThrow().id
            val job = async(Dispatchers.Default) { svc2.bind(id2) }
            entered.await()
            assertEquals(1, svc2.activeLockCount())
            job.cancelAndJoin()
            assertEquals(0, svc2.activeLockCount())
        }

    @Test
    fun `concurrent binds of one contract are serialised and leave exactly one credential behind`() =
        runBlocking {
            val svc = service()
            val id = svc.createDraft(request()).getOrThrow().id
            val results = (1..12).map { async(Dispatchers.Default) { svc.bind(id) } }.awaitAll()
            assertTrue(results.all { it.isSuccess })
            val current = svc.getContract(id).getOrThrow().credentialId
            assertEquals(
                1,
                svc
                    .cacheSizes()
                    .values
                    .toSet()
                    .single(),
                "stale credentials must be dropped on re-bind: ${svc.cacheSizes()}",
            )
            assertTrue(svc.verifyContract(current!!).getOrThrow())
        }

    @Test
    fun `a failed anchor write leaves no cached credential behind`() =
        runTest {
            val failing =
                object : org.trustweave.anchor.BlockchainAnchorClient {
                    override suspend fun writePayload(
                        payload: kotlinx.serialization.json.JsonElement,
                        mediaType: String,
                    ): org.trustweave.anchor.AnchorResult = error("chain down")

                    override suspend fun readPayload(ref: org.trustweave.anchor.AnchorRef): org.trustweave.anchor.AnchorResult =
                        error("chain down")
                }
            val svc =
                DefaultSmartContractService(
                    credentialService = FakeCredentialService(),
                    blockchainRegistry = DefaultBlockchainAnchorRegistry().apply { register(chain, failing) },
                )
            val id = svc.createDraft(request()).getOrThrow().id
            assertTrue(svc.bind(id).isFailure)
            assertEquals(0, svc.cacheSizes().values.sum())
            assertEquals(ContractStatus.DRAFT, svc.getContract(id).getOrThrow().status)
            assertNull(svc.getContract(id).getOrThrow().credentialId)
        }

    @Test
    fun `status history keeps only the most recent entries`() =
        runTest {
            val svc =
                DefaultSmartContractService(
                    credentialService = FakeCredentialService(),
                    blockchainRegistry = DefaultBlockchainAnchorRegistry().apply { register(chain, InMemoryBlockchainAnchorClient(chain)) },
                    limits = ContractStoreLimits(maxStatusHistoryPerContract = 3),
                )
            val id = svc.createDraft(request()).getOrThrow().id
            repeat(6) { svc.bind(id).getOrThrow() }
            svc.activateContract(id).getOrThrow()
            svc.updateStatus(id, ContractStatus.SUSPENDED).getOrThrow()
            svc.updateStatus(id, ContractStatus.ACTIVE).getOrThrow()

            val history = svc.statusHistory(id)
            assertEquals(3, history.size)
            assertEquals(ContractStatus.ACTIVE, history.last().to)
            assertEquals(listOf(ContractStatus.ACTIVE, ContractStatus.SUSPENDED, ContractStatus.ACTIVE), history.map { it.to })
        }

    @Test
    fun `the contract store refuses new drafts at its capacity instead of evicting`() =
        runTest {
            val svc = DefaultSmartContractService(limits = ContractStoreLimits(maxContracts = 2))
            val first = svc.createDraft(request()).getOrThrow()
            svc.createDraft(request()).getOrThrow()
            assertTrue(svc.createDraft(request()).isFailure)
            assertEquals(first.id, svc.getContract(first.id).getOrThrow().id)
        }

    @Test
    fun `limits must be positive`() {
        assertFailsWith<IllegalArgumentException> { ContractStoreLimits(maxContracts = 0) }
        assertFailsWith<IllegalArgumentException> { ContractStoreLimits(maxStatusHistoryPerContract = 0) }
    }
}
