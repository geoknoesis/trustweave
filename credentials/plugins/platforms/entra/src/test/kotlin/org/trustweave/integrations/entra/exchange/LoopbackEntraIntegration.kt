package org.trustweave.integrations.entra.exchange

import okhttp3.OkHttpClient
import org.trustweave.integrations.entra.EntraConfig
import org.trustweave.integrations.entra.EntraIntegration

/** Builds an [EntraIntegration] with a plain client: the default one is SSRF-guarded and refuses loopback servers. */
internal fun loopbackEntraIntegration(config: EntraConfig): EntraIntegration = EntraIntegration(config, OkHttpClient())
