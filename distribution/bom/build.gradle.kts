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
        api(project(":credentials:credential-core"))
        api(project(":kms:kms-core"))
        api(project(":did:did-core"))
        api(project(":anchors:anchor-core"))
        api(project(":testkit"))
        api(project(":kms:plugins:waltid"))
        api(project(":did:plugins:godiddy"))
        api(project(":anchors:plugins:algorand"))
        api(project(":anchors:plugins:polygon"))
        api(project(":anchors:plugins:ganache"))
        api(project(":anchors:plugins:indy"))
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

