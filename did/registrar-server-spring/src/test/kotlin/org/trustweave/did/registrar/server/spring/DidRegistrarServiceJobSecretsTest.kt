package org.trustweave.did.registrar.server.spring

import kotlinx.coroutines.runBlocking
import org.trustweave.did.model.DidDocument
import org.trustweave.did.registrar.DidRegistrar
import org.trustweave.did.registrar.model.CreateDidOptions
import org.trustweave.did.registrar.model.DeactivateDidOptions
import org.trustweave.did.registrar.model.DidRegistrationResponse
import org.trustweave.did.registrar.model.DidState
import org.trustweave.did.registrar.model.OperationState
import org.trustweave.did.registrar.model.Secret
import org.trustweave.did.registrar.model.UpdateDidOptions
import org.trustweave.did.registrar.storage.InMemoryJobStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DidRegistrarServiceJobSecretsTest {
    private val hostileJobId = "../../other-callers-job"

    private val pending =
        DidRegistrationResponse(
            jobId = hostileJobId,
            didState =
                DidState(
                    state = OperationState.WAIT,
                    did = "did:example:1",
                    secret = Secret(recoveryKey = "RECOVERY-KEY", updateKey = "UPDATE-KEY"),
                ),
        )

    private val registrar =
        object : DidRegistrar {
            override suspend fun createDid(
                method: String,
                options: CreateDidOptions,
            ) = pending

            override suspend fun updateDid(
                did: String,
                document: DidDocument,
                options: UpdateDidOptions,
            ) = pending

            override suspend fun deactivateDid(
                did: String,
                options: DeactivateDidOptions,
            ) = pending
        }

    @Test
    fun `create returns the secret once and stores a secret-free job under a server-chosen id`() =
        runBlocking<Unit> {
            val jobs = InMemoryJobStorage()
            val response = DidRegistrarService(registrar, jobs).createDid("example", CreateDidOptions(returnSecrets = true))

            assertEquals("RECOVERY-KEY", response.didState.secret?.recoveryKey)
            assertNull(jobs.get(hostileJobId))
            val stored = assertNotNull(jobs.get(response.jobId!!))
            assertTrue(response.jobId != hostileJobId)
            assertNull(stored.didState.secret)
        }

    @Test
    fun `deactivate and update also store secret-free jobs`() =
        runBlocking<Unit> {
            val jobs = InMemoryJobStorage()
            val service = DidRegistrarService(registrar, jobs)
            val deactivated = service.deactivateDid("did:example:1", DeactivateDidOptions())
            val updated =
                service.updateDid(
                    "did:example:1",
                    DidDocument(
                        id =
                            org.trustweave.did.identifiers
                                .Did("did:example:1"),
                    ),
                    UpdateDidOptions(),
                )
            assertNull(jobs.get(deactivated.jobId!!)?.didState?.secret)
            assertNull(jobs.get(updated.jobId!!)?.didState?.secret)
        }
}
