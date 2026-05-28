package org.trustweave.trust.dsl

import kotlin.DslMarker

/**
 * [DslMarker] for the TrustWeave configuration DSL.
 *
 * Applying this marker to the nested receiver-lambda builders (e.g.
 * [TrustWeaveConfig.Builder], [org.trustweave.trust.dsl.builders.KeysBuilder],
 * [org.trustweave.trust.dsl.builders.DidConfigBuilder],
 * [org.trustweave.trust.dsl.builders.AnchorConfigBuilder]) prevents accidental
 * access to an outer builder's receiver from within an inner block, which would
 * otherwise silently resolve to the wrong scope.
 */
@DslMarker
annotation class TrustWeaveDsl
