package org.trustweave.credential.vi.model

/**
 * Verifiable Intent execution mode.
 *
 * Per the spec, mode is **inferred from the L2 mandate `vct`** (open vs final), never from which
 * arguments a caller supplies. A mandate set mixing open and final types is an error.
 *
 * - [IMMEDIATE]: 2-layer (L1 + finalized L2); the user has confirmed concrete values.
 * - [AUTONOMOUS]: 3-layer (L1 + constrained/open L2 + agent-signed L3a/L3b).
 */
public enum class MandateMode {
    IMMEDIATE,
    AUTONOMOUS,
}
