---
title: Quick Start
nav_order: 40
parent: Getting Started
keywords:
  - quick start
  - getting started
  - first credential
  - tutorial
  - beginner
  - example
  - did
  - verifiable credential
redirect_from:
  - /tutorials/getting-started/quick-start/
---

# Quick Start

Create an issuer and holder, issue a credential, and verify its signature locally.
This example uses in-memory keys and a locally registered JSON-LD vocabulary. It is
a development example, not a production custody configuration.

## Run the checked example

From the repository root, use JDK 21 and the checked-in Gradle wrapper:

```bash
./gradlew :distribution:examples:runDocumentationQuickStart
```

On Windows, use `.\gradlew.bat` with the same task. The checkout uses Kotlin 2.3.21
and Gradle 9.5.0. For a separate application, first follow [Installation](installation.md).

## Complete example

<!-- example-source: distribution/examples/src/main/kotlin/org/trustweave/examples/documentation/DocumentationQuickStart.kt -->
```kotlin
package org.trustweave.examples.documentation

import kotlinx.coroutines.runBlocking
import org.trustweave.credential.jsonld.JsonLdContexts
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.results.getOrThrow
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.quickStart
import org.trustweave.trust.types.getOrThrowDid

/** Local example: the vocabulary is registered in-process; no remote context fetch. */
fun main() =
    runBlocking {
        val contextUrl = "https://example.org/contexts/person/v1"
        JsonLdContexts.register(
            contextUrl,
            """{"@context":{"PersonCredential":"https://example.org/vocab#PersonCredential","name":"https://schema.org/name"}}""",
        )
        val trustWeave = TrustWeave.quickStart()
        try {
            val issuer = trustWeave.createDid().getOrThrowDid()
            val holder = trustWeave.createDid().getOrThrowDid()
            val credential =
                trustWeave
                    .issue {
                        credential {
                            type("PersonCredential")
                            issuer(issuer)
                            subject(holder.value) { "name" to "Alice" }
                        }
                        signedBy(issuer)
                        additionalOption(JsonLdContexts.CONTEXTS_PROOF_OPTION, listOf(contextUrl))
                    }.getOrThrow()
            check(trustWeave.verify(credential) is VerificationResult.Valid) {
                "The issued credential did not verify"
            }
            println("Credential verified")
        } finally {
            trustWeave.close()
        }
    }
```

Expected output includes `Credential verified`. The command fails if verification
fails. Generated DIDs vary on each run. The code closes TrustWeave in `finally`.

## Why the context is explicit

JSON-LD claim names must have vocabulary definitions so canonicalization includes
them in the signed data. `JsonLdContexts.register` registers a trusted local context;
it does not publish that URL. The example maps `PersonCredential` and `name` and
adds the context to issuance. In production, distribute and pin your approved
vocabulary for issuers and verifiers. Do not remove the context just to shorten
the example or call test-only `withTestClaimContexts` helpers from application code.

`quickStart` is an extension function, so its import is required. The issuance
`getOrThrow` extension comes from `org.trustweave.credential.results`; DID helpers
come from `org.trustweave.trust.types`. A valid signature does not by itself prove
that an issuer is trusted for your business policy.

## Next steps

- [Installation](installation.md): dependency and toolchain configuration.
- [Result and exception contracts](api-patterns.md): handle failures explicitly.
- [Provider deployment and custody](../api-reference/provider-deployment-profiles.md): assessed support and production boundaries.
- [Production integration checklist](production-integration-checklist.md): trust, timeouts and operations.
- [Compiled examples](../../distribution/examples/README.md): additional workflows and prerequisites.
