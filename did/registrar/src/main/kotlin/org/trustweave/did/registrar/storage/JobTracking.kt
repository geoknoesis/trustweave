package org.trustweave.did.registrar.storage

import org.trustweave.did.registrar.model.DidRegistrationResponse
import java.util.UUID

/**
 * Returns a copy of this response with `didState.secret` removed.
 *
 * A [JobStorage] holds a response for later polling by whoever can read the job. Secrets (private
 * keys, recovery and update keys) are for the caller that asked for them in the response to its
 * own request; they must never be written to the store.
 */
fun DidRegistrationResponse.withoutSecrets(): DidRegistrationResponse =
    if (didState.secret == null) this else copy(didState = didState.copy(secret = null))

/**
 * Records [response] for polling and returns the response to hand back to the original caller.
 *
 * - The job is stored under a fresh, unguessable id chosen here. A `jobId` supplied by the
 *   registrar (which may be an upstream service) is never used as the storage key: it could collide
 *   with, or overwrite, another caller's job, and is not validated.
 * - The stored copy has its secrets removed ([withoutSecrets]); the returned response keeps them,
 *   so a caller that asked for `returnSecrets` receives them exactly once.
 */
fun JobStorage.track(response: DidRegistrationResponse): DidRegistrationResponse {
    val jobId = UUID.randomUUID().toString()
    store(jobId, response.copy(jobId = jobId).withoutSecrets())
    return response.copy(jobId = jobId)
}
