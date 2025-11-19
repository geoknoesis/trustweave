package com.geoknoesis.vericore.examples.comprehensive

import com.geoknoesis.vericore.credential.dsl.*
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.credential.wallet.CredentialOrganization
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant

/**
 * Comprehensive DSL Example.
 * 
 * This example demonstrates ALL DSL features in a complete workflow:
 * 1. Trust Layer Configuration (with all features)
 * 2. DID Creation & Management
 * 3. Schema Registration & Validation
 * 4. Credential Issuance with Revocation
 * 5. Key Rotation
 * 6. DID Document Updates
 * 7. Wallet Operations (Organization, Query, Presentation)
 * 8. Credential Lifecycle (Store, Verify, Organize)
 * 9. Complete Workflow Chaining
 */
fun main() = runBlocking {
    println("=== Comprehensive DSL Example ===\n")
    
    // ============================================
    // STEP 1: Configure Complete Trust Layer
    // ============================================
    println("Step 1: Configuring complete trust layer...")
    val trustLayer = trustLayer {
        keys {
            provider("inMemory")
            algorithm(KeyAlgorithms.ED25519)
        }
        
        did {
            method(DidMethods.KEY) {
                algorithm(KeyAlgorithms.ED25519)
            }
        }
        
        credentials {
            defaultProofType(ProofTypes.ED25519)
            autoAnchor(false)
        }
        
        revocation {
            provider("inMemory")
        }
        
        schemas {
            autoValidate(false)
            defaultFormat(com.geoknoesis.vericore.spi.SchemaFormat.JSON_SCHEMA)
        }
    }
    println("✓ Trust layer configured with all features\n")
    
    // ============================================
    // STEP 2: Create DIDs using DSL
    // ============================================
    println("Step 2: Creating DIDs...")
    val issuerDid = trustLayer.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }
    println("Issuer DID: $issuerDid")
    
    val holderDid = trustLayer.createDid {
        method(DidMethods.KEY)
        algorithm(KeyAlgorithms.ED25519)
    }
    println("Holder DID: $holderDid\n")
    
    // ============================================
    // STEP 3: Register Schemas
    // ============================================
    println("Step 3: Registering schemas...")
    
    // Register JSON Schema
    trustLayer.registerSchema {
        id("https://example.com/schemas/degree")
        type(SchemaValidatorTypes.JSON_SCHEMA)
        jsonSchema {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
            put("properties", buildJsonObject {
                put("degree", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("type", buildJsonObject { put("type", "string") })
                        put("field", buildJsonObject { put("type", "string") })
                        put("institution", buildJsonObject { put("type", "string") })
                    })
                })
            })
        }
    }
    println("✓ JSON Schema registered")
    
    // Register SHACL Schema
    trustLayer.registerSchema {
        id("https://example.com/schemas/degree-shacl")
        type(SchemaValidatorTypes.SHACL)
        shacl {
            put("@context", "https://www.w3.org/ns/shacl#")
            put("sh:targetClass", "DegreeCredential")
            put("sh:property", buildJsonObject {
                put("sh:path", "degree")
            })
        }
    }
    println("✓ SHACL Schema registered\n")
    
    // ============================================
    // STEP 4: Create Status List for Revocation
    // ============================================
    println("Step 4: Creating revocation status list...")
    val statusList = trustLayer.revocation {
        forIssuer(issuerDid)
        purpose(com.geoknoesis.vericore.credential.revocation.StatusPurpose.REVOCATION)
        size(131072)
    }.createStatusList()
    println("✓ Status list created: ${statusList.id}\n")
    
    // ============================================
    // STEP 5: Issue Credential with Revocation
    // ============================================
    println("Step 5: Issuing credential with auto-revocation...")
    val credential = trustLayer.issue {
        credential {
            id("https://example.edu/credentials/degree-123")
            type(CredentialTypes.EDUCATION, CredentialTypes.DEGREE)
            issuer(issuerDid)
            subject {
                id(holderDid)
                "degree" {
                    "type" to "Bachelor"
                    "field" to "Computer Science"
                    "institution" to "Tech University"
                    "year" to "2023"
                }
            }
            issued(Instant.now())
            schema("https://example.com/schemas/degree", SchemaValidatorTypes.JSON_SCHEMA)
        }
        by(issuerDid = issuerDid, keyId = "key-1")
        withRevocation() // Auto-creates status list
    }
    println("✓ Credential issued with ID: ${credential.id}")
    println("  Has revocation status: ${credential.credentialStatus != null}\n")
    
    // ============================================
    // STEP 6: Create Wallet and Store Credential
    // ============================================
    println("Step 6: Creating wallet and storing credential...")
    val wallet = trustLayer.wallet {
        id("comprehensive-wallet")
        holder(holderDid)
        enableOrganization()
        enablePresentation()
    }
    
    val stored = credential.storeIn(wallet)
    println("✓ Credential stored with ID: ${stored.credentialId}\n")
    
    // ============================================
    // STEP 7: Organize Credentials
    // ============================================
    println("Step 7: Organizing credentials...")
    if (wallet is CredentialOrganization) {
        val orgResult = stored.organize {
            collection("Education", "Academic credentials") {
                add(stored.credentialId)
                tag(stored.credentialId, "education", "degree", "bachelor", "verified")
            }
            metadata(stored.credentialId) {
                "source" to "university.edu"
                "verified" to true
            }
            notes(stored.credentialId, "Bachelor's degree in Computer Science")
        }
        println("✓ Organized: ${orgResult.collectionsCreated} collection(s) created")
        println("  Errors: ${orgResult.errors.size}\n")
    }
    
    // ============================================
    // STEP 8: Query Credentials
    // ============================================
    println("Step 8: Querying credentials...")
    val educationCreds = wallet.queryEnhanced {
        byType(CredentialTypes.EDUCATION)
        valid()
        byTag("education")
    }
    println("✓ Found ${educationCreds.size} education credential(s)\n")
    
    // ============================================
    // STEP 9: Create Presentation
    // ============================================
    println("Step 9: Creating presentation...")
    val presentation = wallet.presentation {
        fromWallet(stored.credentialId)
        holder(holderDid)
        challenge("presentation-challenge-123")
        domain("example.com")
        proofType(ProofTypes.ED25519)
        selectiveDisclosure {
            reveal("degree.field", "degree.institution")
        }
    }
    println("✓ Presentation created with ${presentation.verifiableCredential.size} credential(s)\n")
    
    // ============================================
    // STEP 10: Verify Credential
    // ============================================
    println("Step 10: Verifying credential...")
    val verificationResult = stored.verify(trustLayer) {
        checkRevocation()
        checkExpiration()
    }
    println("✓ Verification result: ${if (verificationResult.valid) "Valid" else "Invalid"}")
    if (!verificationResult.valid) {
        verificationResult.errors.forEach { println("  - $it") }
    }
    println()
    
    // ============================================
    // STEP 11: Check Revocation Status
    // ============================================
    println("Step 11: Checking revocation status...")
    val revocationStatus = stored.checkRevocation(trustLayer.dsl())
    println("✓ Revocation status: ${if (revocationStatus.revoked) "Revoked" else "Not revoked"}\n")
    
    // ============================================
    // STEP 12: Rotate Key
    // ============================================
    println("Step 12: Rotating key...")
    try {
        val updatedDoc = trustLayer.rotateKey {
            did(issuerDid)
            algorithm(KeyAlgorithms.ED25519)
            removeOldKey("key-1")
        }
        println("✓ Key rotated successfully\n")
    } catch (e: Exception) {
        println("⚠ Key rotation skipped: ${e.message}\n")
    }
    
    // ============================================
    // STEP 13: Update DID Document
    // ============================================
    println("Step 13: Updating DID document...")
    try {
        val updatedDoc = trustLayer.updateDid {
            did(holderDid)
            method(DidMethods.KEY)
            addService {
                id("$holderDid#linked-domains")
                type(ServiceTypes.LINKED_DOMAINS)
                endpoint("https://holder.example.com")
            }
            addService {
                id("$holderDid#didcomm")
                type(ServiceTypes.DID_COMM_MESSAGING)
                endpoint("https://messaging.example.com")
            }
        }
        println("✓ DID document updated with services\n")
    } catch (e: Exception) {
        println("⚠ DID document update skipped: ${e.message}\n")
    }
    
    // ============================================
    // STEP 14: Complete Workflow
    // ============================================
    println("Step 14: Demonstrating complete workflow...")
    val workflowResult = trustLayer.completeWorkflow(
        didBlock = {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        },
        credentialBlock = { did ->
            credential {
                id("https://example.com/credentials/workflow-test")
                type(CredentialTypes.CERTIFICATION)
                issuer(issuerDid)
                subject {
                    id(holderDid)
                    "certification" {
                        "name" to "Test Certification"
                        "issuer" to "Test Issuer"
                    }
                }
                issued(Instant.now())
            }
        },
        wallet = wallet,
        organizeBlock = { stored ->
            wallet.organize {
                tag(stored.credentialId, "workflow", "test")
            }
        }
    )
    println("✓ Complete workflow executed:")
    println("  DID: ${workflowResult.did}")
    println("  Credential ID: ${workflowResult.credential.id}")
    println("  Stored ID: ${workflowResult.storedCredential.credentialId}")
    println("  Verification: ${if (workflowResult.verificationResult.valid) "Valid" else "Invalid"}")
    println("  Organization: ${workflowResult.organizationResult?.collectionsCreated ?: 0} collection(s)\n")
    
    // ============================================
    // STEP 15: Wallet Statistics
    // ============================================
    println("Step 15: Wallet statistics...")
    val stats = wallet.getStatistics()
    println("""
        Total credentials: ${stats.totalCredentials}
        Valid credentials: ${stats.validCredentials}
        Expired credentials: ${stats.expiredCredentials}
        Revoked credentials: ${stats.revokedCredentials}
        Collections: ${stats.collectionsCount}
        Tags: ${stats.tagsCount}
    """.trimIndent())
    
    println("\n=== Comprehensive Example Complete ===")
    println("\nDSL Features Demonstrated:")
    println("  ✓ Trust Layer Configuration")
    println("  ✓ DID Creation & Management")
    println("  ✓ Schema Registration (JSON Schema & SHACL)")
    println("  ✓ Credential Issuance with Auto-Revocation")
    println("  ✓ Key Rotation")
    println("  ✓ DID Document Updates")
    println("  ✓ Wallet Organization (Collections, Tags, Metadata)")
    println("  ✓ Enhanced Query (by type, tag, collection)")
    println("  ✓ Wallet Presentations")
    println("  ✓ Credential Lifecycle (Store, Verify, Organize)")
    println("  ✓ Complete Workflow Chaining")
    println("  ✓ Type-Safe Constants")
}


