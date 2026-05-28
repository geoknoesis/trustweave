plugins {
    `java-platform`
    `maven-publish`
}

description = "TrustWeave Bill of Materials"

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api(project(":credentials:credential-api"))
        api(project(":kms:kms-core"))
        api(project(":did:did-core"))
        api(project(":anchors:anchor-core"))
        api(project(":testkit"))
        api(project(":kms:plugins:waltid"))
        api(project(":did:plugins:godiddy"))
        api(project(":did:plugins:ebsi"))
        api(project(":did:plugins:jwk"))
        api(project(":anchors:plugins:algorand"))
        api(project(":anchors:plugins:polygon"))
        api(project(":anchors:plugins:ganache"))
        api(project(":anchors:plugins:indy"))
        api(project(":credentials:plugins:status-list:bitstring"))
        api(project(":credentials:plugins:status-list:token"))
        api(project(":credentials:plugins:status-list:publishing"))
        api(project(":credentials:plugins:status-list:server"))
        api(project(":credentials:vc-api-server"))
        api(project(":credentials:oidc4vci-server"))
        api(project(":credentials:plugins:presentation-exchange"))
        api(project(":credentials:plugins:siop"))
        api(project(":credentials:plugins:mdl"))
        api(project(":credentials:plugins:bbs"))
        api(project(":credentials:plugins:openid-federation"))
        api(project(":credentials:plugins:eudiw"))
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

