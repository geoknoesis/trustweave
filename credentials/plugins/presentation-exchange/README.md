# TrustWeave Presentation Exchange

DIF Presentation Exchange v2.0 models and matcher for TrustWeave verifiable credentials.

## Overview

This module implements the [DIF Presentation Exchange v2.0](https://identity.foundation/presentation-exchange/spec/v2.0.0/) data model, plus a JSONPath-based matcher that selects credentials from a holder's wallet that satisfy a verifier's requirements.

Presentation Exchange (PEX) is a declarative protocol with two halves:

- **Verifier side** publishes a `PresentationDefinition` — a JSON document that declares which credentials, formats, and claims are required.
- **Holder side** evaluates the definition against the credentials it holds, selects those that satisfy each `InputDescriptor`, and returns a `PresentationSubmission` that maps every descriptor to the credential(s) presented.

The module provides:

- Fully `@Serializable` Kotlin data classes for every PEX v2.0 envelope: [PresentationDefinition.kt](src/main/kotlin/org/trustweave/credential/pex/PresentationDefinition.kt)
- A pure-Kotlin matcher backed by Jayway JSONPath: [PresentationDefinitionMatcher.kt](src/main/kotlin/org/trustweave/credential/pex/PresentationDefinitionMatcher.kt)

It has no transport bindings — produce a definition with your verifier, hand it over OID4VP / DIDComm / whatever you use, and feed it back into the matcher on the holder side.

## Key Concepts

- **PresentationDefinition** — the top-level envelope a verifier sends. Carries an `id`, optional `name` / `purpose`, a list of `InputDescriptor`s, optional `Format` constraints, and optional `SubmissionRequirement`s.
- **InputDescriptor** — one logical credential requirement (e.g. "a driver's license"). Carries `id`, optional `name` / `purpose`, optional `Format` override, optional `group` tags, and `Constraints`.
- **Constraints** — field-level rules: `fields`, `limit_disclosure`, `subject_is_issuer`, `is_holder`, `same_subject`.
- **Field** — a single constraint expressed as one or more **JSONPath** expressions plus an optional **JSON Schema** `filter` applied to the resolved value. Fields can be `optional`.
- **submission_requirements** — pick-rules over named `group`s: take ALL descriptors in a group, or PICK `count` / `min` / `max` of them.
- **PresentationSubmission** — the holder's response: a list of `DescriptorMap` entries telling the verifier where in the VP each requested credential lives.

## Models

All models live in package `org.trustweave.credential.pex` and serialize directly to spec-compliant JSON (snake_case field names handled via `@SerialName`).

| Type | Purpose |
|---|---|
| `PresentationDefinition` | Top-level verifier request |
| `InputDescriptor` | One credential requirement |
| `Constraints` | Field rules + `limit_disclosure` + holder/subject assertions |
| `Field` | JSONPath + JSON Schema filter |
| `Format` | Per-format constraints (`jwt_vc`, `jwt_vp`, `ldp_vc`, `ldp_vp`, `vc+sd-jwt`, `mso_mdoc`) |
| `AlgorithmConstraint` | `alg` list for JWT-based formats |
| `ProofTypeConstraint` | `proof_type` list for Linked Data Proof formats |
| `SubmissionRequirement` | Group-level pick rule |
| `SubmissionRule` | `ALL` / `PICK` enum |
| `LimitDisclosure` | `REQUIRED` / `PREFERRED` enum |
| `Optionality` | `REQUIRED` / `PREFERRED` enum |
| `HolderSubject` | Asserts the holder controls listed field ids |
| `SameSubject` | Asserts listed field ids share a subject |
| `PresentationSubmission` | Holder's response |
| `DescriptorMap` | Single `id → format → path (+ optional path_nested)` mapping |

## Matching

[PresentationDefinitionMatcher](src/main/kotlin/org/trustweave/credential/pex/PresentationDefinitionMatcher.kt) is a stateless `object` with two entry points:

```kotlin
fun match(
    definition: PresentationDefinition,
    credentials: List<VerifiableCredential>,
): Map<String, List<VerifiableCredential>>

fun buildSubmission(
    definition: PresentationDefinition,
    matches: Map<String, List<VerifiableCredential>>,
): PresentationSubmission
```

How it evaluates each `InputDescriptor`:

1. If `constraints.limit_disclosure == REQUIRED`, drops credentials whose `proof` is not `SdJwtVcProof` or `MdocProof`.
2. If there are no `fields`, every remaining credential matches.
3. Otherwise, only **required** fields (`optional = false`, the default) are enforced. For each one:
   - The credential is converted to a `Map<String, Any?>` document (`@context`, `id`, `type`, `issuer`, dates, `credentialStatus`, `credentialSchema`, and a flattened `credentialSubject` with `id` + claims).
   - Each JSONPath in `Field.path` is tried in order via [Jayway JsonPath](https://github.com/json-path/JsonPath). The first non-null result wins.
   - If `Field.filter` is set, the JSON Schema sub-schema is applied to that value. Per spec, if the path resolves to an array the filter passes when any element satisfies it.
4. A credential matches the descriptor only if every required field evaluates to `true`.

Supported JSON Schema filter keywords:

`const`, `enum`, `type` (`string` / `number` / `integer` / `boolean` / `array` / `object` / `null`), `minimum`, `maximum`, `exclusiveMinimum`, `exclusiveMaximum`, `minLength`, `maxLength`, `pattern`.

`buildSubmission` produces a `PresentationSubmission` with a fresh UUID, omits descriptors with no matches, picks the first matching credential per descriptor, and infers each `DescriptorMap.format` from the descriptor's `Format` and the credential's `proof` (`mso_mdoc` for `MdocProof`, `vc+sd-jwt` for `SdJwtVcProof`, otherwise `ldp_vc`). Paths are emitted as `$.verifiableCredential[<i>]`.

## Usage

### Gradle

```kotlin
dependencies {
    implementation("org.trustweave.credentials:credentials-plugins-presentation-exchange:0.6.0")
}
```

Transitively pulls in [`com.jayway.jsonpath:json-path`](https://github.com/json-path/JsonPath) for path evaluation and `kotlinx-serialization-json` for the data model.

### Defining a PresentationDefinition

```kotlin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.credential.pex.*

val definition = PresentationDefinition(
    id = "pd-degree-2026-01",
    name = "Degree + age check",
    purpose = "Verify university degree and that holder is 18 or older",
    inputDescriptors = listOf(
        InputDescriptor(
            id = "degree",
            name = "University degree",
            constraints = Constraints(
                fields = listOf(
                    Field(
                        path = listOf("$.type"),
                        filter = buildJsonObject {
                            put("type", "string")
                            put("const", "EducationCredential")
                        },
                    ),
                    Field(
                        path = listOf("$.credentialSubject.degree"),
                        filter = buildJsonObject {
                            put("type", "string")
                            put("pattern", "^(Bachelor|Master|PhD)$")
                        },
                    ),
                ),
            ),
        ),
        InputDescriptor(
            id = "age",
            constraints = Constraints(
                fields = listOf(
                    Field(
                        path = listOf("$.credentialSubject.age"),
                        filter = buildJsonObject {
                            put("type", "integer")
                            put("minimum", 18)
                        },
                    ),
                ),
            ),
        ),
    ),
    format = Format(
        ldpVc = ProofTypeConstraint(proofType = listOf("Ed25519Signature2020")),
        sdJwtVc = AlgorithmConstraint(alg = listOf("ES256")),
    ),
)

val wire: String = Json.encodeToString(PresentationDefinition.serializer(), definition)
```

### Matching credentials and building a submission

`match` and `buildSubmission` are plain (non-suspend) functions, so they can be called directly from any context. Wrap with `runBlocking { ... }` only if you are calling suspend code (such as loading credentials from a `Wallet`) alongside.

```kotlin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.pex.*

fun selectCredentials(
    walletCredentials: List<VerifiableCredential>,
    definitionJson: String,
): Pair<Map<String, List<VerifiableCredential>>, PresentationSubmission> {
    val definition = Json.decodeFromString(
        PresentationDefinition.serializer(),
        definitionJson,
    )

    val matches = PresentationDefinitionMatcher.match(definition, walletCredentials)

    if (matches.values.any { it.isEmpty() }) {
        error("Wallet cannot satisfy descriptors: " +
            matches.filterValues { it.isEmpty() }.keys.joinToString())
    }

    val submission = PresentationDefinitionMatcher.buildSubmission(definition, matches)
    return matches to submission
}

// Calling from a coroutine context, e.g. when the wallet load is suspending:
fun main() = runBlocking {
    val credentials: List<VerifiableCredential> = /* wallet.listCredentials() */ emptyList()
    val (selected, submission) = selectCredentials(credentials, definitionJson = "...")
    println("Submission id: ${submission.id}")
    submission.descriptorMap.forEach { dm ->
        println("  ${dm.id} -> ${dm.path} (${dm.format})")
    }
}
```

### Serializing the submission to send alongside a VP

```kotlin
import kotlinx.serialization.json.Json
import org.trustweave.credential.pex.PresentationSubmission

val json = Json { encodeDefaults = false }
val submissionJson: String = json.encodeToString(PresentationSubmission.serializer(), submission)
// Attach `submissionJson` to your VP / OID4VP authorization response / DIDComm message.
```

## Limitations

The module implements the data model in full but the matcher is intentionally lean. The following PEX v2.0 features are **not** evaluated by the current matcher and must be enforced by the caller:

- **`submission_requirements`** — `SubmissionRequirement` / `SubmissionRule` are modelled and serialized but `match` always returns one entry per `InputDescriptor`. Pick / ALL / count / min / max rules over `group` are not applied.
- **`subject_is_issuer`, `is_holder`, `same_subject`** — modelled (`Constraints.subjectIsIssuer`, `isHolder`, `sameSubject`) but ignored during matching.
- **`predicate` fields** — `Field.predicate` is modelled but not enforced; predicates do not return derived claims.
- **`limit_disclosure`** — `REQUIRED` is honoured by filtering down to SD-JWT VC / mdoc credentials, but the matcher does not itself derive a selectively disclosed credential; that remains the job of the SD-JWT / mdoc engine in `credentials:credential-api`.
- **`Format` constraints on the definition** — `Format` blocks are carried through to `DescriptorMap.format` heuristically but `match` does not currently reject credentials whose proof format is incompatible with the definition's `Format` constraints. Pre-filter your input if you need strict format enforcement.
- **JSON Schema filter keywords** — only the keywords listed under [Matching](#matching) are supported. `required`, `properties`, `items`, `contains`, `not`, `oneOf`, `anyOf`, `allOf`, and similar composite keywords are silently ignored.
- **JSONPath dialect** — uses Jayway's dialect; filter expressions like `$.type[?(@ == 'X')]` work, but anything Jayway does not recognise will resolve to `null` rather than raise.

The matcher is deterministic and side-effect free; it never opens a wallet, fetches a credential, or performs cryptographic verification. Verify credentials separately (e.g. through `CredentialService`) before or after selection.

## References

- [DIF Presentation Exchange v2.0](https://identity.foundation/presentation-exchange/spec/v2.0.0/) — normative spec
- [DIF Presentation Exchange landing page](https://identity.foundation/presentation-exchange/) — overview and related work
- [JSON Schema (Draft 2020-12)](https://json-schema.org/specification.html) — `filter` semantics
- [Jayway JsonPath](https://github.com/json-path/JsonPath) — JSONPath dialect used by the matcher
