plugins {
    `java-platform`
    `maven-publish`
}

description = "TrustWeave Bill of Materials"

javaPlatform {
    allowDependencies()
}

// Constraints cover every published TrustWeave module so consumers can align versions
// with a single platform import. Intentionally excluded:
//   - :testkit               (test-support module; must not be steered onto prod classpaths)
//   - :distribution:examples / :distribution:conformance (sample/verification projects)
//   - KMP scaffolds (:common-mp, :did:did-identifiers-mp, :wallet:wallet-core-mp,
//     :credentials:credential-models-mp) — not yet published (no maven-publish applied)
dependencies {
    constraints {
        // Core
        api(project(":common"))
        api(project(":trust"))
        api(project(":contract"))
        api(project(":distribution:all"))

        // DID domain
        api(project(":did:did-core"))
        api(project(":did:registrar"))
        api(project(":did:registrar-server-ktor"))
        api(project(":did:registrar-server-spring"))
        api(project(":did:plugins:base"))
        api(project(":did:plugins:sidetree-core"))
        api(project(":did:plugins:key"))
        api(project(":did:plugins:web"))
        api(project(":did:plugins:ethr"))
        api(project(":did:plugins:ion"))
        api(project(":did:plugins:polygon"))
        api(project(":did:plugins:sol"))
        api(project(":did:plugins:peer"))
        api(project(":did:plugins:ens"))
        api(project(":did:plugins:plc"))
        api(project(":did:plugins:cheqd"))
        api(project(":did:plugins:jwk"))
        api(project(":did:plugins:godiddy"))
        api(project(":did:plugins:threebox"))
        api(project(":did:plugins:btcr"))
        api(project(":did:plugins:tezos"))
        api(project(":did:plugins:orb"))
        api(project(":did:plugins:ebsi"))

        // Wallet domain
        api(project(":wallet:wallet-core"))
        api(project(":wallet:wallet-services"))
        api(project(":wallet:plugins:database"))
        api(project(":wallet:plugins:file"))
        api(project(":wallet:plugins:cloud"))

        // KMS domain
        api(project(":kms:kms-core"))
        api(project(":kms:plugins:inmemory"))
        api(project(":kms:plugins:aws"))
        api(project(":kms:plugins:azure"))
        api(project(":kms:plugins:google"))
        api(project(":kms:plugins:hashicorp"))
        api(project(":kms:plugins:ibm"))
        api(project(":kms:plugins:thales"))
        api(project(":kms:plugins:thales-luna"))
        api(project(":kms:plugins:cyberark"))
        api(project(":kms:plugins:fortanix"))
        api(project(":kms:plugins:utimaco"))
        api(project(":kms:plugins:cloudhsm"))
        api(project(":kms:plugins:entrust"))
        api(project(":kms:plugins:waltid"))
        api(project(":kms:plugins:pkcs11"))
        api(project(":kms:plugins:venafi"))

        // Anchors domain
        api(project(":anchors:anchor-core"))
        api(project(":anchors:plugins:evm-base"))
        api(project(":anchors:plugins:algorand"))
        api(project(":anchors:plugins:polygon"))
        api(project(":anchors:plugins:ethereum"))
        api(project(":anchors:plugins:base"))
        api(project(":anchors:plugins:arbitrum"))
        api(project(":anchors:plugins:optimism"))
        api(project(":anchors:plugins:zksync"))
        api(project(":anchors:plugins:bitcoin"))
        api(project(":anchors:plugins:starknet"))
        api(project(":anchors:plugins:cardano"))
        api(project(":anchors:plugins:ganache"))
        api(project(":anchors:plugins:indy"))

        // Credentials domain
        api(project(":credentials:credential-api"))
        api(project(":credentials:plugins:status-list:database"))
        api(project(":credentials:plugins:status-list:bitstring"))
        api(project(":credentials:plugins:status-list:token"))
        api(project(":credentials:plugins:status-list:publishing"))
        api(project(":credentials:plugins:status-list:server"))
        api(project(":credentials:plugins:anchor"))
        api(project(":credentials:plugins:platforms:servicenow"))
        api(project(":credentials:plugins:platforms:salesforce"))
        api(project(":credentials:plugins:platforms:entra"))
        api(project(":credentials:plugins:oidc4vci"))
        api(project(":credentials:plugins:oidc4vp"))
        api(project(":credentials:plugins:didcomm"))
        api(project(":credentials:plugins:chapi"))
        api(project(":credentials:plugins:presentation-exchange"))
        api(project(":credentials:plugins:siop"))
        api(project(":credentials:plugins:openid-federation"))
        api(project(":credentials:plugins:mdl"))
        api(project(":credentials:plugins:bbs"))
        api(project(":credentials:plugins:eudiw"))
        api(project(":credentials:plugins:jades"))
        // NOTE: credentials:plugins:verifiable-intent is in-progress (not yet in
        // settings.gradle.kts on main) — add it here when the module lands.
        api(project(":credentials:oidc4vci-server"))
        api(project(":credentials:vc-api-server"))

        // Signatures domain (eIDAS QES)
        api(project(":signatures:tsa-core"))
        api(project(":signatures:trust-lists"))
        api(project(":signatures:jades"))
        api(project(":signatures:cades"))
        api(project(":signatures:xades"))
        api(project(":signatures:pades"))
        api(project(":signatures:etsi-validation"))

        // Trust Registry domain
        api(project(":trust-registry:trust-registry-core"))
        api(project(":trust-registry:plugins:database"))
        api(project(":trust-registry:trust-registry-server"))
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])

            pom {
                name.set("TrustWeave BOM")
                description.set("Bill of Materials for TrustWeave modules")
                url.set("https://github.com/geoknoesis/trustweave")

                licenses {
                    license {
                        name.set("AGPL-3.0")
                        url.set("https://www.gnu.org/licenses/agpl-3.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("trustweave-team")
                        name.set("TrustWeave Team")
                        email.set("info@geoknoesis.com")
                    }
                }
            }
        }
    }
}

