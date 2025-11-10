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
        api(project(":vericore-core"))
        api(project(":vericore-json"))
        api(project(":vericore-kms"))
        api(project(":vericore-did"))
        api(project(":vericore-anchor"))
        api(project(":vericore-testkit"))
        api(project(":vericore-waltid"))
        api(project(":vericore-godiddy"))
        api(project(":vericore-algorand"))
        api(project(":vericore-polygon"))
        api(project(":vericore-ganache"))
        api(project(":vericore-indy"))
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

