plugins {
    `java-platform`
    `maven-publish`
}

description = "VeriCore Bill of Materials"

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api(project(":core:vericore-core"))
        api(project(":core:vericore-json"))
        api(project(":kms:vericore-kms"))
        api(project(":did:vericore-did"))
        api(project(":chains:vericore-anchor"))
        api(project(":core:vericore-testkit"))
        api(project(":kms:plugins:waltid"))
        api(project(":did:plugins:godiddy"))
        api(project(":chains:plugins:algorand"))
        api(project(":chains:plugins:polygon"))
        api(project(":chains:plugins:ganache"))
        api(project(":chains:plugins:indy"))
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])
            
            pom {
                name.set("VeriCore BOM")
                description.set("Bill of Materials for VeriCore modules")
                url.set("https://github.com/geoknoesis/vericore")
                
                licenses {
                    license {
                        name.set("AGPL-3.0")
                        url.set("https://www.gnu.org/licenses/agpl-3.0.txt")
                    }
                }
                
                developers {
                    developer {
                        id.set("vericore-team")
                        name.set("VeriCore Team")
                        email.set("info@geoknoesis.com")
                    }
                }
            }
        }
    }
}

