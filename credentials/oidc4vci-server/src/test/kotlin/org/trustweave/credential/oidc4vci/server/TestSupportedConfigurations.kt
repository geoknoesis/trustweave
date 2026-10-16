package org.trustweave.credential.oidc4vci.server

import org.trustweave.credential.oidc4vci.models.CredentialConfiguration

/** Credential configuration ids the test issuers advertise; offers may only name these. */
internal const val MALICIOUS_TYPE = """Degree","evil":true,"issuer":"did:evil:attacker"""

internal val TEST_CONFIGURATIONS: Map<String, CredentialConfiguration> =
    listOf("A", "T", "UniversityDegree", "DegreeCredential", "UniversityDegreeCredential", MALICIOUS_TYPE)
        .associateWith { CredentialConfiguration(format = "jwt_vc_json") }
