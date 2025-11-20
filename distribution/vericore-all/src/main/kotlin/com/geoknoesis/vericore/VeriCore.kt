package com.geoknoesis.vericore

import com.geoknoesis.vericore.anchor.AnchorResult
import com.geoknoesis.vericore.anchor.AnchorRef
import com.geoknoesis.vericore.anchor.BlockchainAnchorClient
import com.geoknoesis.vericore.anchor.BlockchainAnchorRegistry
import com.geoknoesis.vericore.core.*
import com.geoknoesis.vericore.core.vericoreCatching
import com.geoknoesis.vericore.core.normalizeKeyId
import com.geoknoesis.vericore.spi.services.WalletFactory
import com.geoknoesis.vericore.spi.services.WalletCreationOptions
import com.geoknoesis.vericore.spi.services.WalletCreationOptionsBuilder
import com.geoknoesis.vericore.credential.CredentialService
import com.geoknoesis.vericore.credential.CredentialServiceRegistry
import com.geoknoesis.vericore.credential.CredentialVerificationOptions
import com.geoknoesis.vericore.credential.CredentialVerificationResult
import com.geoknoesis.vericore.credential.did.CredentialDidResolver
import com.geoknoesis.vericore.did.toCredentialDidResolution
import com.geoknoesis.vericore.credential.issuer.CredentialIssuer
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.credential.models.VerifiablePresentation
import com.geoknoesis.vericore.credential.PresentationOptions
import com.geoknoesis.vericore.credential.models.CredentialStatus
import com.geoknoesis.vericore.credential.PresentationVerificationOptions
import com.geoknoesis.vericore.credential.PresentationVerificationResult
import com.geoknoesis.vericore.credential.presentation.PresentationService
import com.geoknoesis.vericore.credential.verifier.CredentialVerifier
import com.geoknoesis.vericore.credential.schema.SchemaRegistry
import com.geoknoesis.vericore.credential.schema.SchemaRegistrationResult
import com.geoknoesis.vericore.credential.schema.SchemaValidationResult
import com.geoknoesis.vericore.credential.revocation.StatusListManager
import com.geoknoesis.vericore.credential.revocation.RevocationStatus
import com.geoknoesis.vericore.credential.revocation.StatusListCredential
import com.geoknoesis.vericore.credential.revocation.StatusPurpose
import com.geoknoesis.vericore.credential.wallet.Wallet
import com.geoknoesis.vericore.credential.wallet.WalletProvider
import com.geoknoesis.vericore.credential.proof.Ed25519ProofGenerator
import com.geoknoesis.vericore.credential.proof.ProofGenerator
import com.geoknoesis.vericore.credential.proof.ProofGeneratorRegistry
import com.geoknoesis.vericore.did.Did
import com.geoknoesis.vericore.did.DidCreationOptions
import com.geoknoesis.vericore.did.DidCreationOptionsBuilder
import com.geoknoesis.vericore.did.DidDocument
import com.geoknoesis.vericore.did.DidMethod
import com.geoknoesis.vericore.did.DidResolutionResult
import com.geoknoesis.vericore.did.DidMethodRegistry
import com.geoknoesis.vericore.did.didCreationOptions
import com.geoknoesis.vericore.kms.KeyManagementService
import com.geoknoesis.vericore.kms.Algorithm
import com.geoknoesis.vericore.kms.KeyHandle
import com.geoknoesis.vericore.kms.UnsupportedAlgorithmException
import com.geoknoesis.vericore.spi.PluginLifecycle
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/**
 * Main entry point for VeriCore library.
 * 
 * VeriCore provides a unified, elegant API for decentralized identity and trust operations.
 * This facade simplifies common tasks while still allowing access to advanced features.
 * 
 * **Quick Start:**
 * ```kotlin
 * val vericore = VeriCore.create()
     * val did = vericore.createDid().getOrThrow()
     * val credential = vericore.issueCredential(did, keyId) { ... }.getOrThrow()
     * val valid = vericore.verifyCredential(credential).getOrThrow()
 * ```
 * 
 * **Custom Configuration:**
 * ```kotlin
 * val vericore = VeriCore.create {
 *     kms = MyCustomKms()
 *     walletFactory = MyWalletFactory()
 *     
 *     didMethods {
 *         + DidKeyMethod()
 *         + MyDidMethod()
 *     }
 *     
 *     blockchain {
 *         "ethereum:mainnet" to ethereumClient
 *         "algorand:testnet" to algorandClient
 *     }
 * }
 * ```
 * 
 * @see VeriCoreContext for advanced configuration
 */
class VeriCore private constructor(
    private val context: VeriCoreContext
) {
    
    // ========================================
    // DID Operations
    // ========================================
    
    /**
     * Creates a new DID using the default or specified method.
     * 
     * **Example:**
     * ```kotlin
     * // Simple usage
     * val did = vericore.createDid().getOrThrow()  // Uses default "key" method
     * 
     * // With custom method
     * val webDid = vericore.createDid(method = "web").getOrThrow()
     * 
     * // With error handling
     * val result = vericore.createDid()
     * result.fold(
     *     onSuccess = { did -> println("Created: ${did.id}") },
     *     onFailure = { error -> println("Failed: ${error.message}") }
     * )
     * ```
     * 
     * @param method DID method name (default: "key")
     * @param options Method-specific creation options
     * @return Result containing the created DID document
     */
    suspend fun createDid(
        method: String = "key",
        options: DidCreationOptions = DidCreationOptions()
    ): Result<DidDocument> = vericoreCatching {
        // Validate method is registered
        val availableMethods = context.getAvailableDidMethods()
        if (method !in availableMethods) {
            throw VeriCoreError.DidMethodNotRegistered(
                method = method,
                availableMethods = availableMethods
            )
        }
        
        val didMethod = context.getDidMethod(method)
            ?: throw VeriCoreError.DidMethodNotRegistered(
                method = method,
                availableMethods = availableMethods
            )
        
        didMethod.createDid(options)
    }

    /**
     * Creates a DID using a fluent builder for [DidCreationOptions].
     *
     * **Example:**
     * ```kotlin
     * val did = vericore.createDid("key") {
     *     algorithm = DidCreationOptions.KeyAlgorithm.ED25519
     *     purpose(DidCreationOptions.KeyPurpose.AUTHENTICATION)
     * }.getOrThrow()
     * ```
     */
    suspend fun createDid(
        method: String = "key",
        configure: DidCreationOptionsBuilder.() -> Unit
    ): Result<DidDocument> = createDid(method, didCreationOptions(configure))

    /**
     * Resolves a DID to its document.
     * 
     * **Example:**
     * ```kotlin
     * // Simple usage
     * val result = vericore.resolveDid("did:key:z6Mkfriq...").getOrThrow()
     * if (result.document != null) {
     *     println("DID resolved: ${result.document.id}")
     * }
     * 
     * // With error handling
     * val result = vericore.resolveDid("did:key:z6Mkfriq...")
     * result.fold(
     *     onSuccess = { resolution ->
     *         if (resolution.document != null) {
     *             println("DID resolved: ${resolution.document.id}")
     *         } else {
     *             println("DID not found")
     *         }
     *     },
     *     onFailure = { error -> println("Resolution failed: ${error.message}") }
     * )
     * ```
     * 
     * @param did The DID string to resolve
     * @return Resolution result containing the document and metadata
     */
    suspend fun resolveDid(did: String): Result<DidResolutionResult> = vericoreCatching {
        // Validate DID format
        DidValidator.validateFormat(did).let {
            if (!it.isValid()) {
                throw VeriCoreError.InvalidDidFormat(
                    did = did,
                    reason = it.errorMessage() ?: "Invalid DID format"
                )
            }
        }
        
        // Validate method is registered
        val availableMethods = context.getAvailableDidMethods()
        DidValidator.validateMethod(did, availableMethods).let {
            if (!it.isValid()) {
                throw VeriCoreError.DidMethodNotRegistered(
                    method = DidValidator.extractMethod(did) ?: "unknown",
                    availableMethods = availableMethods
                )
            }
        }
        
        context.resolveDid(did)
    }
    
    /**
     * Updates a DID document.
     * 
     * **Example:**
     * ```kotlin
     * val updated = vericore.updateDid("did:key:example") { document ->
     *     document.copy(
     *         service = document.service + Service(
     *             id = "${document.id}#service-1",
     *             type = "LinkedDomains",
     *             serviceEndpoint = "https://example.com/service"
     *         )
     *     )
     * }.getOrThrow()
     * ```
     * 
     * @param did The DID to update
     * @param updater Function that transforms the current document to the new document
     * @return Result containing the updated DID document
     */
    suspend fun updateDid(
        did: String,
        updater: (DidDocument) -> DidDocument
    ): Result<DidDocument> = vericoreCatching {
        require(did.isNotBlank()) { "DID is required" }
        
        DidValidator.validateFormat(did).let {
            if (!it.isValid()) {
                throw VeriCoreError.InvalidDidFormat(
                    did = did,
                    reason = it.errorMessage() ?: "Invalid DID format"
                )
            }
        }
        
        val methodName = DidValidator.extractMethod(did)
            ?: throw VeriCoreError.InvalidDidFormat(
                did = did,
                reason = "Failed to extract method from DID"
            )
        
        val method = context.didRegistry.get(methodName)
            ?: throw VeriCoreError.DidMethodNotRegistered(
                method = methodName,
                availableMethods = context.didRegistry.getAllMethodNames()
            )
        
        method.updateDid(did, updater)
    }
    
    /**
     * Deactivates a DID.
     * 
     * **Example:**
     * ```kotlin
     * val deactivated = vericore.deactivateDid("did:key:example").getOrThrow()
     * if (deactivated) {
     *     println("DID deactivated successfully")
     * }
     * ```
     * 
     * @param did The DID to deactivate
     * @return Result containing true if deactivated, false otherwise
     */
    suspend fun deactivateDid(did: String): Result<Boolean> = vericoreCatching {
        require(did.isNotBlank()) { "DID is required" }
        
        DidValidator.validateFormat(did).let {
            if (!it.isValid()) {
                throw VeriCoreError.InvalidDidFormat(
                    did = did,
                    reason = it.errorMessage() ?: "Invalid DID format"
                )
            }
        }
        
        val methodName = DidValidator.extractMethod(did)
            ?: throw VeriCoreError.InvalidDidFormat(
                did = did,
                reason = "Failed to extract method from DID"
            )
        
        val method = context.didRegistry.get(methodName)
            ?: throw VeriCoreError.DidMethodNotRegistered(
                method = methodName,
                availableMethods = context.didRegistry.getAllMethodNames()
            )
        
        method.deactivateDid(did)
    }
    
    // ========================================
    // Credential Operations
    // ========================================
    
    /**
     * Issues a verifiable credential.
     * 
     * **Example:**
     * ```kotlin
     * // Simple usage
     * val credential = vericore.issueCredential(
     *     issuerDid = "did:key:issuer",
     *     issuerKeyId = "key-1",
     *     credentialSubject = buildJsonObject {
     *         put("id", "did:key:subject")
     *         put("name", "Alice")
     *     },
     *     types = listOf("PersonCredential")
     * ).getOrThrow()
     * 
     * // With error handling
     * val result = vericore.issueCredential(...)
     * result.fold(
     *     onSuccess = { credential -> println("Issued: ${credential.id}") },
     *     onFailure = { error -> println("Issuance failed: ${error.message}") }
     * )
     * ```
     * 
     * @param issuerDid DID of the credential issuer
     * @param issuerKeyId Key ID for signing
     * @param credentialSubject The credential subject as JSON
     * @param types Credential types (VerifiableCredential is added automatically)
     * @param expirationDate Optional expiration date (ISO 8601)
     * @return Result containing the issued credential with proof
     */
    suspend fun issueCredential(
        issuerDid: String,
        issuerKeyId: String,
        credentialSubject: kotlinx.serialization.json.JsonElement,
        types: List<String> = listOf("VerifiableCredential"),
        expirationDate: String? = null
    ): Result<VerifiableCredential> = vericoreCatching {
        // Validate issuer DID format
        DidValidator.validateFormat(issuerDid).let {
            if (!it.isValid()) {
                throw VeriCoreError.InvalidDidFormat(
                    did = issuerDid,
                    reason = it.errorMessage() ?: "Invalid issuer DID format"
                )
            }
        }
        
        // Validate issuer DID method is registered
        val availableMethods = context.getAvailableDidMethods()
        DidValidator.validateMethod(issuerDid, availableMethods).let {
            if (!it.isValid()) {
                throw VeriCoreError.DidMethodNotRegistered(
                    method = DidValidator.extractMethod(issuerDid) ?: "unknown",
                    availableMethods = availableMethods
                )
            }
        }
        
        val normalizedKeyId = normalizeKeyId(issuerKeyId)
        val credentialId = "urn:uuid:${UUID.randomUUID()}"
        val credential = VerifiableCredential(
            id = credentialId,
            type = if ("VerifiableCredential" in types) types else listOf("VerifiableCredential") + types,
            issuer = issuerDid,
            credentialSubject = credentialSubject,
            issuanceDate = java.time.Instant.now().toString(),
            expirationDate = expirationDate
        )
        
        // Validate credential structure
        CredentialValidator.validateStructure(credential).let {
            if (!it.isValid()) {
                throw VeriCoreError.CredentialInvalid(
                    reason = it.errorMessage() ?: "Invalid credential structure",
                    credentialId = credentialId,
                    field = (it as? ValidationResult.Invalid)?.field
                )
            }
        }

        val proofGenerator = Ed25519ProofGenerator(
            signer = { data, keyId -> context.kms.sign(normalizeKeyId(keyId), data) }
        )

        val issuer = CredentialIssuer(proofGenerator)
        issuer.issue(credential, issuerDid, normalizedKeyId)
    }
    
    /**
     * Verifies a verifiable credential.
     * 
     * **Example:**
     * ```kotlin
     * val result = vericore.verifyCredential(credential).getOrThrow()
     * if (result.valid) {
     *     println("Credential is valid")
     * } else {
     *     println("Errors: ${result.errors}")
     * }
     * ```
     * 
     * @param credential The credential to verify
     * @param options Verification options (e.g., check revocation, expiration)
     * @return Result containing verification details
     */
    suspend fun verifyCredential(
        credential: VerifiableCredential,
        options: CredentialVerificationOptions = CredentialVerificationOptions()
    ): Result<CredentialVerificationResult> = vericoreCatching {
        val effectiveResolver = CredentialDidResolver { did ->
            runCatching { context.resolveDid(did) }
                .getOrNull()
                ?.toCredentialDidResolution()
        }
        val verifier = CredentialVerifier(defaultDidResolver = effectiveResolver)
        var result = verifier.verify(credential, options)
        
        // Enhance blockchain anchor verification if enabled and chainId is provided
        val chainId = options.chainId
        if (options.verifyBlockchainAnchor && chainId != null && credential.evidence != null) {
            val enhancedResult = verifyBlockchainAnchorEvidence(credential, chainId, result)
            result = enhancedResult
        }
        
        result
    }
    
    /**
     * Verifies blockchain anchor evidence by reading from the blockchain.
     */
    private suspend fun verifyBlockchainAnchorEvidence(
        credential: VerifiableCredential,
        chainId: String,
        currentResult: CredentialVerificationResult
    ): CredentialVerificationResult {
        // Find blockchain anchor evidence
        val anchorEvidence = credential.evidence?.find { evidence ->
            evidence.type.contains("BlockchainAnchorEvidence")
        } ?: return currentResult
        
        val evidenceDoc = anchorEvidence.evidenceDocument?.jsonObject ?: return currentResult
        val evidenceChainId = evidenceDoc["chainId"]?.jsonPrimitive?.content
        val txHash = evidenceDoc["txHash"]?.jsonPrimitive?.content
        
        if (evidenceChainId != chainId || txHash == null) {
            return currentResult.copy(
                valid = false,
                blockchainAnchorValid = false,
                errors = currentResult.errors + "Blockchain anchor evidence chainId mismatch or missing txHash"
            )
        }
        
        // Get blockchain client and verify anchor exists
        val client = context.getBlockchainClient(chainId)
        if (client == null) {
            return currentResult.copy(
                warnings = currentResult.warnings + "Blockchain client not available for chain: $chainId",
                blockchainAnchorValid = false
            )
        }
        
        return try {
            val anchorRef = AnchorRef(
                chainId = chainId,
                txHash = txHash
            )
            val anchorResult = client.readPayload(anchorRef)
            
            // Verify anchor exists and contains expected data
            // Note: Full verification would compare credential digest with anchored data
            currentResult.copy(
                blockchainAnchorValid = true
            )
        } catch (e: Exception) {
            currentResult.copy(
                valid = false,
                blockchainAnchorValid = false,
                errors = currentResult.errors + "Blockchain anchor verification failed: ${e.message}"
            )
        }
    }
    
    // ========================================
    // Presentation Operations
    // ========================================
    
    /**
     * Creates a verifiable presentation from credentials.
     * 
     * **Example:**
     * ```kotlin
     * val presentation = vericore.createPresentation(
     *     credentials = listOf(credential1, credential2),
     *     holderDid = "did:key:holder",
     *     holderKeyId = "key-1",
     *     challenge = "auth-challenge-12345"
     * ).getOrThrow()
     * ```
     * 
     * @param credentials List of verifiable credentials to include
     * @param holderDid DID of the presentation holder
     * @param holderKeyId Key ID for signing the presentation
     * @param challenge Optional challenge string for authentication
     * @param domain Optional domain string for authentication
     * @param proofType Proof type to use (default: "Ed25519Signature2020")
     * @return Result containing the verifiable presentation
     */
    suspend fun createPresentation(
        credentials: List<VerifiableCredential>,
        holderDid: String,
        holderKeyId: String,
        challenge: String? = null,
        domain: String? = null,
        proofType: String = "Ed25519Signature2020"
    ): Result<VerifiablePresentation> = vericoreCatching {
        require(credentials.isNotEmpty()) { "At least one credential is required" }
        require(holderDid.isNotBlank()) { "Holder DID is required" }
        require(holderKeyId.isNotBlank()) { "Holder key ID is required" }
        
        // Validate holder DID format
        DidValidator.validateFormat(holderDid).let {
            if (!it.isValid()) {
                throw VeriCoreError.InvalidDidFormat(
                    did = holderDid,
                    reason = it.errorMessage() ?: "Invalid holder DID format"
                )
            }
        }
        
        val normalizedKeyId = normalizeKeyId(holderKeyId)
        val proofGenerator = Ed25519ProofGenerator(
            signer = { data, keyId -> context.kms.sign(normalizeKeyId(keyId), data) },
            getPublicKeyId = { keyId -> 
                val keyHandle = context.kms.getPublicKey(normalizeKeyId(keyId))
                "${holderDid}#$keyId"
            }
        )
        
        val presentationService = PresentationService(
            proofGenerator = proofGenerator
        )
        
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = proofType,
            keyId = normalizedKeyId,
            challenge = challenge,
            domain = domain
        )
        
        presentationService.createPresentation(credentials, holderDid, options)
    }
    
    /**
     * Verifies a verifiable presentation.
     * 
     * **Example:**
     * ```kotlin
     * val result = vericore.verifyPresentation(
     *     presentation = presentation,
     *     expectedChallenge = "auth-challenge-12345"
     * ).getOrThrow()
     * 
     * if (result.valid) {
     *     println("Presentation is valid")
     * } else {
     *     println("Errors: ${result.errors}")
     * }
     * ```
     * 
     * @param presentation The presentation to verify
     * @param expectedChallenge Optional expected challenge value
     * @param expectedDomain Optional expected domain value
     * @param checkRevocation Whether to check credential revocation status
     * @return Result containing verification details
     */
    suspend fun verifyPresentation(
        presentation: VerifiablePresentation,
        expectedChallenge: String? = null,
        expectedDomain: String? = null,
        checkRevocation: Boolean = true
    ): Result<PresentationVerificationResult> = vericoreCatching {
        val effectiveResolver = CredentialDidResolver { did ->
            runCatching { context.resolveDid(did) }
                .getOrNull()
                ?.toCredentialDidResolution()
        }
        
        val credentialVerifier = CredentialVerifier(defaultDidResolver = effectiveResolver)
        val presentationService = PresentationService(
            credentialVerifier = credentialVerifier
        )
        
        val options = PresentationVerificationOptions(
            verifyChallenge = expectedChallenge != null,
            expectedChallenge = expectedChallenge,
            verifyDomain = expectedDomain != null,
            expectedDomain = expectedDomain,
            checkRevocation = checkRevocation
        )
        
        presentationService.verifyPresentation(presentation, options)
    }
    
    /**
     * Creates a selective disclosure presentation that only reveals specified fields.
     * 
     * **Example:**
     * ```kotlin
     * val presentation = vericore.createSelectiveDisclosure(
     *     credentials = listOf(credential),
     *     disclosedFields = listOf("credentialSubject.name", "credentialSubject.email"),
     *     holderDid = "did:key:holder",
     *     holderKeyId = "key-1"
     * ).getOrThrow()
     * ```
     * 
     * **Note:** Full zero-knowledge selective disclosure requires BBS+ proofs.
     * This method currently creates a regular presentation with only disclosed fields visible.
     * 
     * @param credentials List of verifiable credentials
     * @param disclosedFields List of field paths to disclose (e.g., ["credentialSubject.name"])
     * @param holderDid DID of the presentation holder
     * @param holderKeyId Key ID for signing the presentation
     * @param challenge Optional challenge string for authentication
     * @param domain Optional domain string for authentication
     * @param proofType Proof type to use (default: "Ed25519Signature2020")
     * @return Result containing the verifiable presentation with selective disclosure
     */
    suspend fun createSelectiveDisclosure(
        credentials: List<VerifiableCredential>,
        disclosedFields: List<String>,
        holderDid: String,
        holderKeyId: String,
        challenge: String? = null,
        domain: String? = null,
        proofType: String = "Ed25519Signature2020"
    ): Result<VerifiablePresentation> = vericoreCatching {
        require(credentials.isNotEmpty()) { "At least one credential is required" }
        require(disclosedFields.isNotEmpty()) { "At least one disclosed field is required" }
        require(holderDid.isNotBlank()) { "Holder DID is required" }
        require(holderKeyId.isNotBlank()) { "Holder key ID is required" }
        
        // Validate holder DID format
        DidValidator.validateFormat(holderDid).let {
            if (!it.isValid()) {
                throw VeriCoreError.InvalidDidFormat(
                    did = holderDid,
                    reason = it.errorMessage() ?: "Invalid holder DID format"
                )
            }
        }
        
        val normalizedKeyId = normalizeKeyId(holderKeyId)
        val proofGenerator = Ed25519ProofGenerator(
            signer = { data, keyId -> context.kms.sign(normalizeKeyId(keyId), data) },
            getPublicKeyId = { keyId -> 
                val keyHandle = context.kms.getPublicKey(normalizeKeyId(keyId))
                "${holderDid}#$keyId"
            }
        )
        
        val presentationService = PresentationService(
            proofGenerator = proofGenerator
        )
        
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = proofType,
            keyId = normalizedKeyId,
            challenge = challenge,
            domain = domain,
            selectiveDisclosure = true,
            disclosedFields = disclosedFields
        )
        
        presentationService.createSelectiveDisclosure(credentials, disclosedFields, holderDid, options)
    }
    
    /**
     * Checks if a credential has a revocation status field.
     * 
     * **Example:**
     * ```kotlin
     * val hasStatus = vericore.hasRevocationStatus(credential)
     * if (hasStatus) {
     *     println("Credential has status field: ${credential.credentialStatus?.id}")
     * }
     * ```
     * 
     * **Note:** This only checks if the credential has a `credentialStatus` field.
     * To actually check revocation status, use a StatusListManager (available via trust layer).
     * 
     * @param credential The credential to check
     * @return true if the credential has a credentialStatus field, false otherwise
     */
    fun hasRevocationStatus(credential: VerifiableCredential): Boolean {
        return credential.credentialStatus != null
    }
    
    /**
     * Gets the revocation status information from a credential.
     * 
     * **Example:**
     * ```kotlin
     * val status = vericore.getRevocationStatusInfo(credential)
     * if (status != null) {
     *     println("Status list ID: ${status.statusListCredential}")
     *     println("Status list index: ${status.statusListIndex}")
     * }
     * ```
     * 
     * **Note:** This only returns the status information from the credential.
     * To actually check if the credential is revoked, use a StatusListManager (available via trust layer).
     * 
     * @param credential The credential to check
     * @return The credential status information, or null if not present
     */
    fun getRevocationStatusInfo(credential: VerifiableCredential): com.geoknoesis.vericore.credential.models.CredentialStatus? {
        return credential.credentialStatus
    }
    
    // ========================================
    // Wallet Operations
    // ========================================
    
    /**
     * Creates an in-memory wallet for storing credentials.
     * 
     * **Example:**
     * ```kotlin
     * // Simplest usage - just provide holder DID
     * val wallet = vericore.createWallet("did:key:holder").getOrThrow()
     * 
     * // With options builder
     * val wallet = vericore.createWallet("did:key:holder") {
     *     label = "Holder Wallet"
     *     enableOrganization = true
     * }.getOrThrow()
     * 
     * // Full control
     * val wallet = vericore.createWallet(
     *     holderDid = "did:key:holder",
     *     provider = WalletProvider.Database,
     *     options = walletOptions { ... }
     * ).getOrThrow()
     * 
     * wallet.store(credential)
     * val credentials = wallet.query {
     *     byType("PersonCredential")
     *     notExpired()
     * }
     * ```
     * 
     * @param holderDid DID of the wallet holder
     * @return Result containing the new wallet instance
     */
    suspend fun createWallet(holderDid: String): Result<Wallet> =
        createWallet(holderDid, WalletProvider.InMemory)

    /**
     * Creates a wallet with a specific provider.
     * 
     * @param holderDid DID of the wallet holder
     * @param provider Wallet provider (defaults to InMemory)
     * @return Result containing the new wallet instance
     */
    suspend fun createWallet(
        holderDid: String,
        provider: WalletProvider
    ): Result<Wallet> =
        createWallet(holderDid, UUID.randomUUID().toString(), provider)

    /**
     * Creates a wallet with a specific provider and wallet ID.
     * 
     * @param holderDid DID of the wallet holder
     * @param walletId Wallet identifier (generated if not provided)
     * @param provider Wallet provider (defaults to InMemory)
     * @return Result containing the new wallet instance
     */
    suspend fun createWallet(
        holderDid: String,
        walletId: String,
        provider: WalletProvider = WalletProvider.InMemory
    ): Result<Wallet> =
        createWallet(holderDid, walletId, provider, WalletCreationOptions())

    /**
     * Creates a wallet with full configuration options.
     * 
     * @param holderDid DID of the wallet holder
     * @param walletId Optional wallet identifier (generated if not provided)
     * @param provider Strongly typed wallet provider identifier (defaults to in-memory)
     * @param options Provider-specific configuration passed to the underlying [WalletFactory]
     * @return Result containing the new wallet instance
     */
    suspend fun createWallet(
        holderDid: String,
        walletId: String = UUID.randomUUID().toString(),
        provider: WalletProvider = WalletProvider.InMemory,
        options: WalletCreationOptions
    ): Result<Wallet> = runCatching {
        val wallet = context.walletFactory.create(
            providerName = provider.id,
            walletId = walletId,
            holderDid = holderDid,
            options = options
        )

        wallet as? Wallet ?: throw IllegalStateException(
            "WalletFactory returned unsupported instance: ${wallet?.let { it::class.qualifiedName }}"
        )
    }

    /**
     * Creates a wallet using a fluent options builder.
     *
     * **Example:**
     * ```kotlin
     * val wallet = vericore.createWallet("did:key:holder") {
     *     label = "Holder Wallet"
     *     storagePath = "/wallets/holder"
     *     property("autoUnlock", true)
     * }.getOrThrow()
     * ```
     * 
     * @param holderDid DID of the wallet holder
     * @param provider Wallet provider (defaults to InMemory)
     * @param configure Options builder block
     * @return Result containing the new wallet instance
     */
    suspend fun createWallet(
        holderDid: String,
        provider: WalletProvider = WalletProvider.InMemory,
        configure: WalletOptionsBuilder.() -> Unit
    ): Result<Wallet> =
        createWallet(
            holderDid = holderDid,
            provider = provider,
            options = walletOptions(configure)
        )
    
    // ========================================
    // Wallet Operations
    // ========================================
    
    /**
     * Stores a credential in a wallet.
     * 
     * **Example:**
     * ```kotlin
     * val wallet = vericore.createWallet("did:key:holder").getOrThrow()
     * val credentialId = vericore.storeCredential(credential, wallet).getOrThrow()
     * println("Stored credential: $credentialId")
     * ```
     * 
     * @param credential The credential to store
     * @param wallet The wallet to store it in
     * @return Result containing the credential ID
     */
    suspend fun storeCredential(
        credential: VerifiableCredential,
        wallet: Wallet
    ): Result<String> = vericoreCatching {
        wallet.store(credential)
    }
    
    /**
     * Retrieves a credential from a wallet by ID.
     * 
     * **Example:**
     * ```kotlin
     * val credential = vericore.getCredential("cred-123", wallet).getOrThrow()
     * ```
     * 
     * @param credentialId The ID of the credential to retrieve
     * @param wallet The wallet to retrieve from
     * @return Result containing the credential, or null if not found
     */
    suspend fun getCredential(
        credentialId: String,
        wallet: Wallet
    ): Result<VerifiableCredential?> = vericoreCatching {
        require(credentialId.isNotBlank()) { "Credential ID is required" }
        wallet.get(credentialId)
    }
    
    /**
     * Lists all credentials in a wallet, optionally filtered.
     * 
     * **Example:**
     * ```kotlin
     * val credentials = vericore.listCredentials(wallet).getOrThrow()
     * 
     * // With filter
     * val filter = CredentialFilter(
     *     types = listOf("UniversityDegreeCredential")
     * )
     * val filtered = vericore.listCredentials(wallet, filter).getOrThrow()
     * ```
     * 
     * @param wallet The wallet to list credentials from
     * @param filter Optional filter to apply
     * @return Result containing the list of credentials
     */
    suspend fun listCredentials(
        wallet: Wallet,
        filter: com.geoknoesis.vericore.credential.wallet.CredentialFilter? = null
    ): Result<List<VerifiableCredential>> = vericoreCatching {
        wallet.list(filter)
    }
    
    /**
     * Deletes a credential from a wallet.
     * 
     * **Example:**
     * ```kotlin
     * val deleted = vericore.deleteCredential("cred-123", wallet).getOrThrow()
     * if (deleted) {
     *     println("Credential deleted")
     * }
     * ```
     * 
     * @param credentialId The ID of the credential to delete
     * @param wallet The wallet to delete from
     * @return Result containing true if deleted, false if not found
     */
    suspend fun deleteCredential(
        credentialId: String,
        wallet: Wallet
    ): Result<Boolean> = vericoreCatching {
        require(credentialId.isNotBlank()) { "Credential ID is required" }
        wallet.delete(credentialId)
    }
    
    /**
     * Queries credentials in a wallet using a query builder.
     * 
     * **Example:**
     * ```kotlin
     * val results = vericore.queryCredentials(wallet) {
     *     types("UniversityDegreeCredential")
     *     issuer("did:key:issuer")
     *     issuedAfter(Instant.parse("2024-01-01T00:00:00Z"))
     * }.getOrThrow()
     * ```
     * 
     * @param wallet The wallet to query
     * @param query The query builder lambda
     * @return Result containing the list of matching credentials
     */
    suspend fun queryCredentials(
        wallet: Wallet,
        query: com.geoknoesis.vericore.credential.wallet.CredentialQueryBuilder.() -> Unit
    ): Result<List<VerifiableCredential>> = vericoreCatching {
        wallet.query(query)
    }
    
    /**
     * Gets statistics about a wallet.
     * 
     * **Example:**
     * ```kotlin
     * val stats = vericore.getWalletStatistics(wallet).getOrThrow()
     * println("Total credentials: ${stats.totalCredentials}")
     * println("Valid credentials: ${stats.validCredentials}")
     * ```
     * 
     * @param wallet The wallet to get statistics for
     * @return Result containing wallet statistics
     */
    suspend fun getWalletStatistics(
        wallet: Wallet
    ): Result<com.geoknoesis.vericore.credential.wallet.WalletStatistics> = vericoreCatching {
        wallet.getStatistics()
    }
    
    // ========================================
    // Wallet Organization Operations
    // ========================================
    
    /**
     * Creates a collection in a wallet (if supported).
     * 
     * **Example:**
     * ```kotlin
     * val collectionId = vericore.createCollection(
     *     wallet = wallet,
     *     name = "My Credentials",
     *     description = "Personal credentials"
     * ).getOrThrow()
     * ```
     * 
     * @param wallet The wallet (must support CredentialOrganization)
     * @param name Collection name
     * @param description Optional description
     * @return Result containing the collection ID
     */
    suspend fun createCollection(
        wallet: Wallet,
        name: String,
        description: String? = null
    ): Result<String> = vericoreCatching {
        require(name.isNotBlank()) { "Collection name is required" }
        val org = wallet as? com.geoknoesis.vericore.credential.wallet.CredentialOrganization
            ?: throw VeriCoreError.InvalidOperation(
                code = "WALLET_CAPABILITY_NOT_SUPPORTED",
                message = "Wallet does not support credential organization (collections, tags, metadata)",
                context = mapOf("walletId" to wallet.walletId, "capability" to "CredentialOrganization")
            )
        org.createCollection(name, description)
    }
    
    /**
     * Tags a credential in a wallet (if supported).
     * 
     * **Example:**
     * ```kotlin
     * val tagged = vericore.tagCredential(
     *     wallet = wallet,
     *     credentialId = "cred-123",
     *     tags = setOf("important", "verified")
     * ).getOrThrow()
     * ```
     * 
     * @param wallet The wallet (must support CredentialOrganization)
     * @param credentialId The credential ID
     * @param tags Set of tags to add
     * @return Result containing true if tagged
     */
    suspend fun tagCredential(
        wallet: Wallet,
        credentialId: String,
        tags: Set<String>
    ): Result<Boolean> = vericoreCatching {
        require(credentialId.isNotBlank()) { "Credential ID is required" }
        require(tags.isNotEmpty()) { "At least one tag is required" }
        val org = wallet as? com.geoknoesis.vericore.credential.wallet.CredentialOrganization
            ?: throw VeriCoreError.InvalidOperation(
                code = "WALLET_CAPABILITY_NOT_SUPPORTED",
                message = "Wallet does not support credential organization",
                context = mapOf("walletId" to wallet.walletId, "capability" to "CredentialOrganization")
            )
        org.tagCredential(credentialId, tags)
    }
    
    /**
     * Adds metadata to a credential in a wallet (if supported).
     * 
     * **Example:**
     * ```kotlin
     * val added = vericore.addCredentialMetadata(
     *     wallet = wallet,
     *     credentialId = "cred-123",
     *     metadata = mapOf("source" to "issuer.com", "verified" to true)
     * ).getOrThrow()
     * ```
     * 
     * @param wallet The wallet (must support CredentialOrganization)
     * @param credentialId The credential ID
     * @param metadata Metadata to add
     * @return Result containing true if added
     */
    suspend fun addCredentialMetadata(
        wallet: Wallet,
        credentialId: String,
        metadata: Map<String, Any>
    ): Result<Boolean> = vericoreCatching {
        require(credentialId.isNotBlank()) { "Credential ID is required" }
        require(metadata.isNotEmpty()) { "Metadata cannot be empty" }
        val org = wallet as? com.geoknoesis.vericore.credential.wallet.CredentialOrganization
            ?: throw VeriCoreError.InvalidOperation(
                code = "WALLET_CAPABILITY_NOT_SUPPORTED",
                message = "Wallet does not support credential organization",
                context = mapOf("walletId" to wallet.walletId, "capability" to "CredentialOrganization")
            )
        org.addMetadata(credentialId, metadata)
    }
    
    /**
     * Lists all collections in a wallet (if supported).
     * 
     * **Example:**
     * ```kotlin
     * val collections = vericore.listCollections(wallet).getOrThrow()
     * collections.forEach { collection ->
     *     println("${collection.name}: ${collection.credentialCount} credentials")
     * }
     * ```
     * 
     * @param wallet The wallet (must support CredentialOrganization)
     * @return Result containing list of collections
     */
    suspend fun listCollections(wallet: Wallet): Result<List<com.geoknoesis.vericore.credential.wallet.CredentialCollection>> = vericoreCatching {
        val org = wallet as? com.geoknoesis.vericore.credential.wallet.CredentialOrganization
            ?: throw VeriCoreError.InvalidOperation(
                code = "WALLET_CAPABILITY_NOT_SUPPORTED",
                message = "Wallet does not support credential organization",
                context = mapOf("walletId" to wallet.walletId, "capability" to "CredentialOrganization")
            )
        org.listCollections()
    }
    
    /**
     * Gets a collection by ID from a wallet (if supported).
     * 
     * **Example:**
     * ```kotlin
     * val collection = vericore.getCollection(wallet, "collection-123").getOrThrow()
     * if (collection != null) {
     *     println("Collection: ${collection.name}")
     * }
     * ```
     * 
     * @param wallet The wallet (must support CredentialOrganization)
     * @param collectionId The collection ID
     * @return Result containing the collection, or null if not found
     */
    suspend fun getCollection(
        wallet: Wallet,
        collectionId: String
    ): Result<com.geoknoesis.vericore.credential.wallet.CredentialCollection?> = vericoreCatching {
        require(collectionId.isNotBlank()) { "Collection ID is required" }
        val org = wallet as? com.geoknoesis.vericore.credential.wallet.CredentialOrganization
            ?: throw VeriCoreError.InvalidOperation(
                code = "WALLET_CAPABILITY_NOT_SUPPORTED",
                message = "Wallet does not support credential organization",
                context = mapOf("walletId" to wallet.walletId, "capability" to "CredentialOrganization")
            )
        org.getCollection(collectionId)
    }
    
    /**
     * Deletes a collection from a wallet (if supported).
     * 
     * **Example:**
     * ```kotlin
     * val deleted = vericore.deleteCollection(wallet, "collection-123").getOrThrow()
     * if (deleted) {
     *     println("Collection deleted")
     * }
     * ```
     * 
     * @param wallet The wallet (must support CredentialOrganization)
     * @param collectionId The collection ID
     * @return Result containing true if deleted, false if not found
     */
    suspend fun deleteCollection(
        wallet: Wallet,
        collectionId: String
    ): Result<Boolean> = vericoreCatching {
        require(collectionId.isNotBlank()) { "Collection ID is required" }
        val org = wallet as? com.geoknoesis.vericore.credential.wallet.CredentialOrganization
            ?: throw VeriCoreError.InvalidOperation(
                code = "WALLET_CAPABILITY_NOT_SUPPORTED",
                message = "Wallet does not support credential organization",
                context = mapOf("walletId" to wallet.walletId, "capability" to "CredentialOrganization")
            )
        org.deleteCollection(collectionId)
    }
    
    /**
     * Adds a credential to a collection in a wallet (if supported).
     * 
     * **Example:**
     * ```kotlin
     * val added = vericore.addToCollection(
     *     wallet = wallet,
     *     credentialId = "cred-123",
     *     collectionId = "collection-456"
     * ).getOrThrow()
     * ```
     * 
     * @param wallet The wallet (must support CredentialOrganization)
     * @param credentialId The credential ID
     * @param collectionId The collection ID
     * @return Result containing true if added, false if credential or collection not found
     */
    suspend fun addToCollection(
        wallet: Wallet,
        credentialId: String,
        collectionId: String
    ): Result<Boolean> = vericoreCatching {
        require(credentialId.isNotBlank()) { "Credential ID is required" }
        require(collectionId.isNotBlank()) { "Collection ID is required" }
        val org = wallet as? com.geoknoesis.vericore.credential.wallet.CredentialOrganization
            ?: throw VeriCoreError.InvalidOperation(
                code = "WALLET_CAPABILITY_NOT_SUPPORTED",
                message = "Wallet does not support credential organization",
                context = mapOf("walletId" to wallet.walletId, "capability" to "CredentialOrganization")
            )
        org.addToCollection(credentialId, collectionId)
    }
    
    /**
     * Removes a credential from a collection in a wallet (if supported).
     * 
     * **Example:**
     * ```kotlin
     * val removed = vericore.removeFromCollection(
     *     wallet = wallet,
     *     credentialId = "cred-123",
     *     collectionId = "collection-456"
     * ).getOrThrow()
     * ```
     * 
     * @param wallet The wallet (must support CredentialOrganization)
     * @param credentialId The credential ID
     * @param collectionId The collection ID
     * @return Result containing true if removed, false if not found
     */
    suspend fun removeFromCollection(
        wallet: Wallet,
        credentialId: String,
        collectionId: String
    ): Result<Boolean> = vericoreCatching {
        require(credentialId.isNotBlank()) { "Credential ID is required" }
        require(collectionId.isNotBlank()) { "Collection ID is required" }
        val org = wallet as? com.geoknoesis.vericore.credential.wallet.CredentialOrganization
            ?: throw VeriCoreError.InvalidOperation(
                code = "WALLET_CAPABILITY_NOT_SUPPORTED",
                message = "Wallet does not support credential organization",
                context = mapOf("walletId" to wallet.walletId, "capability" to "CredentialOrganization")
            )
        org.removeFromCollection(credentialId, collectionId)
    }
    
    /**
     * Gets all credentials in a collection from a wallet (if supported).
     * 
     * **Example:**
     * ```kotlin
     * val credentials = vericore.getCredentialsInCollection(
     *     wallet = wallet,
     *     collectionId = "collection-456"
     * ).getOrThrow()
     * println("Collection has ${credentials.size} credentials")
     * ```
     * 
     * @param wallet The wallet (must support CredentialOrganization)
     * @param collectionId The collection ID
     * @return Result containing list of credentials in the collection
     */
    suspend fun getCredentialsInCollection(
        wallet: Wallet,
        collectionId: String
    ): Result<List<VerifiableCredential>> = vericoreCatching {
        require(collectionId.isNotBlank()) { "Collection ID is required" }
        val org = wallet as? com.geoknoesis.vericore.credential.wallet.CredentialOrganization
            ?: throw VeriCoreError.InvalidOperation(
                code = "WALLET_CAPABILITY_NOT_SUPPORTED",
                message = "Wallet does not support credential organization",
                context = mapOf("walletId" to wallet.walletId, "capability" to "CredentialOrganization")
            )
        org.getCredentialsInCollection(collectionId)
    }
    
    /**
     * Gets all tags for a credential in a wallet (if supported).
     * 
     * **Example:**
     * ```kotlin
     * val tags = vericore.getTags(wallet, "cred-123").getOrThrow()
     * println("Tags: ${tags.joinToString(", ")}")
     * ```
     * 
     * @param wallet The wallet (must support CredentialOrganization)
     * @param credentialId The credential ID
     * @return Result containing set of tags
     */
    suspend fun getTags(
        wallet: Wallet,
        credentialId: String
    ): Result<Set<String>> = vericoreCatching {
        require(credentialId.isNotBlank()) { "Credential ID is required" }
        val org = wallet as? com.geoknoesis.vericore.credential.wallet.CredentialOrganization
            ?: throw VeriCoreError.InvalidOperation(
                code = "WALLET_CAPABILITY_NOT_SUPPORTED",
                message = "Wallet does not support credential organization",
                context = mapOf("walletId" to wallet.walletId, "capability" to "CredentialOrganization")
            )
        org.getTags(credentialId)
    }
    
    /**
     * Removes tags from a credential in a wallet (if supported).
     * 
     * **Example:**
     * ```kotlin
     * val removed = vericore.untagCredential(
     *     wallet = wallet,
     *     credentialId = "cred-123",
     *     tags = setOf("old", "deprecated")
     * ).getOrThrow()
     * ```
     * 
     * @param wallet The wallet (must support CredentialOrganization)
     * @param credentialId The credential ID
     * @param tags Set of tags to remove
     * @return Result containing true if tags were removed
     */
    suspend fun untagCredential(
        wallet: Wallet,
        credentialId: String,
        tags: Set<String>
    ): Result<Boolean> = vericoreCatching {
        require(credentialId.isNotBlank()) { "Credential ID is required" }
        require(tags.isNotEmpty()) { "At least one tag is required" }
        val org = wallet as? com.geoknoesis.vericore.credential.wallet.CredentialOrganization
            ?: throw VeriCoreError.InvalidOperation(
                code = "WALLET_CAPABILITY_NOT_SUPPORTED",
                message = "Wallet does not support credential organization",
                context = mapOf("walletId" to wallet.walletId, "capability" to "CredentialOrganization")
            )
        org.untagCredential(credentialId, tags)
    }
    
    /**
     * Gets all tags used in a wallet (if supported).
     * 
     * **Example:**
     * ```kotlin
     * val allTags = vericore.getAllTags(wallet).getOrThrow()
     * println("All tags: ${allTags.joinToString(", ")}")
     * ```
     * 
     * @param wallet The wallet (must support CredentialOrganization)
     * @return Result containing set of all tags
     */
    suspend fun getAllTags(wallet: Wallet): Result<Set<String>> = vericoreCatching {
        val org = wallet as? com.geoknoesis.vericore.credential.wallet.CredentialOrganization
            ?: throw VeriCoreError.InvalidOperation(
                code = "WALLET_CAPABILITY_NOT_SUPPORTED",
                message = "Wallet does not support credential organization",
                context = mapOf("walletId" to wallet.walletId, "capability" to "CredentialOrganization")
            )
        org.getAllTags()
    }
    
    /**
     * Finds credentials by tag in a wallet (if supported).
     * 
     * **Example:**
     * ```kotlin
     * val credentials = vericore.findByTag(wallet, "important").getOrThrow()
     * println("Found ${credentials.size} credentials with tag 'important'")
     * ```
     * 
     * @param wallet The wallet (must support CredentialOrganization)
     * @param tag The tag to search for
     * @return Result containing list of credentials with the tag
     */
    suspend fun findByTag(
        wallet: Wallet,
        tag: String
    ): Result<List<VerifiableCredential>> = vericoreCatching {
        require(tag.isNotBlank()) { "Tag is required" }
        val org = wallet as? com.geoknoesis.vericore.credential.wallet.CredentialOrganization
            ?: throw VeriCoreError.InvalidOperation(
                code = "WALLET_CAPABILITY_NOT_SUPPORTED",
                message = "Wallet does not support credential organization",
                context = mapOf("walletId" to wallet.walletId, "capability" to "CredentialOrganization")
            )
        org.findByTag(tag)
    }
    
    /**
     * Gets metadata for a credential in a wallet (if supported).
     * 
     * **Example:**
     * ```kotlin
     * val metadata = vericore.getCredentialMetadata(wallet, "cred-123").getOrThrow()
     * if (metadata != null) {
     *     println("Notes: ${metadata.notes}")
     *     println("Tags: ${metadata.tags}")
     * }
     * ```
     * 
     * @param wallet The wallet (must support CredentialOrganization)
     * @param credentialId The credential ID
     * @return Result containing credential metadata, or null if not found
     */
    suspend fun getCredentialMetadata(
        wallet: Wallet,
        credentialId: String
    ): Result<com.geoknoesis.vericore.credential.wallet.CredentialMetadata?> = vericoreCatching {
        require(credentialId.isNotBlank()) { "Credential ID is required" }
        val org = wallet as? com.geoknoesis.vericore.credential.wallet.CredentialOrganization
            ?: throw VeriCoreError.InvalidOperation(
                code = "WALLET_CAPABILITY_NOT_SUPPORTED",
                message = "Wallet does not support credential organization",
                context = mapOf("walletId" to wallet.walletId, "capability" to "CredentialOrganization")
            )
        org.getMetadata(credentialId)
    }
    
    /**
     * Updates notes for a credential in a wallet (if supported).
     * 
     * **Example:**
     * ```kotlin
     * val updated = vericore.updateCredentialNotes(
     *     wallet = wallet,
     *     credentialId = "cred-123",
     *     notes = "This is an important credential"
     * ).getOrThrow()
     * ```
     * 
     * @param wallet The wallet (must support CredentialOrganization)
     * @param credentialId The credential ID
     * @param notes Notes text, or null to clear notes
     * @return Result containing true if notes were updated
     */
    suspend fun updateCredentialNotes(
        wallet: Wallet,
        credentialId: String,
        notes: String?
    ): Result<Boolean> = vericoreCatching {
        require(credentialId.isNotBlank()) { "Credential ID is required" }
        val org = wallet as? com.geoknoesis.vericore.credential.wallet.CredentialOrganization
            ?: throw VeriCoreError.InvalidOperation(
                code = "WALLET_CAPABILITY_NOT_SUPPORTED",
                message = "Wallet does not support credential organization",
                context = mapOf("walletId" to wallet.walletId, "capability" to "CredentialOrganization")
            )
        org.updateNotes(credentialId, notes)
    }
    
    // ========================================
    // Wallet Lifecycle Operations
    // ========================================
    
    /**
     * Archives a credential in a wallet (if supported).
     * 
     * Archived credentials are hidden from normal queries but can be retrieved via `getArchived()`.
     * 
     * **Example:**
     * ```kotlin
     * val archived = vericore.archiveCredential(wallet, "cred-123").getOrThrow()
     * if (archived) {
     *     println("Credential archived")
     * }
     * ```
     * 
     * @param wallet The wallet (must support CredentialLifecycle)
     * @param credentialId The credential ID
     * @return Result containing true if archived, false if credential not found
     */
    suspend fun archiveCredential(
        wallet: Wallet,
        credentialId: String
    ): Result<Boolean> = vericoreCatching {
        require(credentialId.isNotBlank()) { "Credential ID is required" }
        val lifecycle = wallet as? com.geoknoesis.vericore.credential.wallet.CredentialLifecycle
            ?: throw VeriCoreError.InvalidOperation(
                code = "WALLET_CAPABILITY_NOT_SUPPORTED",
                message = "Wallet does not support credential lifecycle (archive, refresh)",
                context = mapOf("walletId" to wallet.walletId, "capability" to "CredentialLifecycle")
            )
        lifecycle.archive(credentialId)
    }
    
    /**
     * Unarchives a credential in a wallet (if supported).
     * 
     * **Example:**
     * ```kotlin
     * val unarchived = vericore.unarchiveCredential(wallet, "cred-123").getOrThrow()
     * if (unarchived) {
     *     println("Credential unarchived")
     * }
     * ```
     * 
     * @param wallet The wallet (must support CredentialLifecycle)
     * @param credentialId The credential ID
     * @return Result containing true if unarchived, false if credential not found
     */
    suspend fun unarchiveCredential(
        wallet: Wallet,
        credentialId: String
    ): Result<Boolean> = vericoreCatching {
        require(credentialId.isNotBlank()) { "Credential ID is required" }
        val lifecycle = wallet as? com.geoknoesis.vericore.credential.wallet.CredentialLifecycle
            ?: throw VeriCoreError.InvalidOperation(
                code = "WALLET_CAPABILITY_NOT_SUPPORTED",
                message = "Wallet does not support credential lifecycle",
                context = mapOf("walletId" to wallet.walletId, "capability" to "CredentialLifecycle")
            )
        lifecycle.unarchive(credentialId)
    }
    
    /**
     * Gets all archived credentials from a wallet (if supported).
     * 
     * **Example:**
     * ```kotlin
     * val archived = vericore.getArchivedCredentials(wallet).getOrThrow()
     * println("Archived credentials: ${archived.size}")
     * ```
     * 
     * @param wallet The wallet (must support CredentialLifecycle)
     * @return Result containing list of archived credentials
     */
    suspend fun getArchivedCredentials(wallet: Wallet): Result<List<VerifiableCredential>> = vericoreCatching {
        val lifecycle = wallet as? com.geoknoesis.vericore.credential.wallet.CredentialLifecycle
            ?: throw VeriCoreError.InvalidOperation(
                code = "WALLET_CAPABILITY_NOT_SUPPORTED",
                message = "Wallet does not support credential lifecycle",
                context = mapOf("walletId" to wallet.walletId, "capability" to "CredentialLifecycle")
            )
        lifecycle.getArchived()
    }
    
    /**
     * Refreshes a credential in a wallet (if supported).
     * 
     * Attempts to refresh the credential from its refresh service if available.
     * 
     * **Example:**
     * ```kotlin
     * val refreshed = vericore.refreshCredential(wallet, "cred-123").getOrThrow()
     * if (refreshed != null) {
     *     println("Credential refreshed successfully")
     * } else {
     *     println("Credential refresh failed or not available")
     * }
     * ```
     * 
     * @param wallet The wallet (must support CredentialLifecycle)
     * @param credentialId The credential ID
     * @return Result containing the refreshed credential, or null if refresh failed or credential not found
     */
    suspend fun refreshCredential(
        wallet: Wallet,
        credentialId: String
    ): Result<VerifiableCredential?> = vericoreCatching {
        require(credentialId.isNotBlank()) { "Credential ID is required" }
        val lifecycle = wallet as? com.geoknoesis.vericore.credential.wallet.CredentialLifecycle
            ?: throw VeriCoreError.InvalidOperation(
                code = "WALLET_CAPABILITY_NOT_SUPPORTED",
                message = "Wallet does not support credential lifecycle",
                context = mapOf("walletId" to wallet.walletId, "capability" to "CredentialLifecycle")
            )
        lifecycle.refreshCredential(credentialId)
    }
    
    // ========================================
    // Revocation Operations
    // ========================================
    
    /**
     * Creates a new status list for credential revocation.
     * 
     * **Example:**
     * ```kotlin
     * val statusListManager = InMemoryStatusListManager()
     * val statusList = vericore.createStatusList(
     *     issuerDid = "did:key:issuer",
     *     purpose = StatusPurpose.REVOCATION,
     *     statusListManager = statusListManager
     * ).getOrThrow()
     * 
     * println("Created status list: ${statusList.id}")
     * ```
     * 
     * @param issuerDid The DID of the issuer
     * @param purpose The purpose of the status list (REVOCATION or SUSPENSION)
     * @param statusListManager The StatusListManager to use
     * @param size Optional initial size (default: 131072 entries)
     * @return Result containing the created status list credential
     */
    suspend fun createStatusList(
        issuerDid: String,
        purpose: com.geoknoesis.vericore.credential.revocation.StatusPurpose,
        statusListManager: com.geoknoesis.vericore.credential.revocation.StatusListManager,
        size: Int = 131072
    ): Result<com.geoknoesis.vericore.credential.revocation.StatusListCredential> = vericoreCatching {
        require(issuerDid.isNotBlank()) { "Issuer DID is required" }
        
        // Validate issuer DID format
        DidValidator.validateFormat(issuerDid).let {
            if (!it.isValid()) {
                throw VeriCoreError.InvalidDidFormat(
                    did = issuerDid,
                    reason = it.errorMessage() ?: "Invalid issuer DID format"
                )
            }
        }
        
        statusListManager.createStatusList(issuerDid, purpose, size)
    }
    
    /**
     * Revokes a credential using a StatusListManager.
     * 
     * **Example:**
     * ```kotlin
     * val statusListManager = InMemoryStatusListManager()
     * val revoked = vericore.revokeCredential(
     *     credentialId = "cred-123",
     *     statusListId = "status-list-1",
     *     statusListManager = statusListManager
     * ).getOrThrow()
     * ```
     * 
     * @param credentialId The ID of the credential to revoke
     * @param statusListId The ID of the status list
     * @param statusListManager The StatusListManager to use
     * @return Result containing true if revocation succeeded
     */
    suspend fun revokeCredential(
        credentialId: String,
        statusListId: String,
        statusListManager: com.geoknoesis.vericore.credential.revocation.StatusListManager
    ): Result<Boolean> = vericoreCatching {
        require(credentialId.isNotBlank()) { "Credential ID is required" }
        require(statusListId.isNotBlank()) { "Status list ID is required" }
        statusListManager.revokeCredential(credentialId, statusListId)
    }
    
    /**
     * Suspends a credential using a StatusListManager.
     * 
     * **Example:**
     * ```kotlin
     * val statusListManager = InMemoryStatusListManager()
     * val suspended = vericore.suspendCredential(
     *     credentialId = "cred-123",
     *     statusListId = "status-list-1",
     *     statusListManager = statusListManager
     * ).getOrThrow()
     * ```
     * 
     * @param credentialId The ID of the credential to suspend
     * @param statusListId The ID of the status list
     * @param statusListManager The StatusListManager to use
     * @return Result containing true if suspension succeeded
     */
    suspend fun suspendCredential(
        credentialId: String,
        statusListId: String,
        statusListManager: com.geoknoesis.vericore.credential.revocation.StatusListManager
    ): Result<Boolean> = vericoreCatching {
        require(credentialId.isNotBlank()) { "Credential ID is required" }
        require(statusListId.isNotBlank()) { "Status list ID is required" }
        statusListManager.suspendCredential(credentialId, statusListId)
    }
    
    /**
     * Checks the revocation status of a credential using a StatusListManager.
     * 
     * **Example:**
     * ```kotlin
     * val statusListManager = InMemoryStatusListManager()
     * val status = vericore.checkRevocationStatus(
     *     credential = credential,
     *     statusListManager = statusListManager
     * ).getOrThrow()
     * 
     * if (status.revoked) {
     *     println("Credential is revoked")
     * } else if (status.suspended) {
     *     println("Credential is suspended")
     * } else {
     *     println("Credential is active")
     * }
     * ```
     * 
     * @param credential The credential to check
     * @param statusListManager The StatusListManager to use
     * @return Result containing the revocation status
     */
    suspend fun checkRevocationStatus(
        credential: VerifiableCredential,
        statusListManager: com.geoknoesis.vericore.credential.revocation.StatusListManager
    ): Result<com.geoknoesis.vericore.credential.revocation.RevocationStatus> = vericoreCatching {
        statusListManager.checkRevocationStatus(credential)
    }
    
    /**
     * Gets a status list by ID from a StatusListManager.
     * 
     * **Example:**
     * ```kotlin
     * val statusListManager = InMemoryStatusListManager()
     * val statusList = vericore.getStatusList(
     *     statusListId = "status-list-1",
     *     statusListManager = statusListManager
     * ).getOrThrow()
     * 
     * if (statusList != null) {
     *     println("Status list: ${statusList.id}")
     * }
     * ```
     * 
     * @param statusListId The ID of the status list
     * @param statusListManager The StatusListManager to use
     * @return Result containing the status list credential, or null if not found
     */
    suspend fun getStatusList(
        statusListId: String,
        statusListManager: com.geoknoesis.vericore.credential.revocation.StatusListManager
    ): Result<com.geoknoesis.vericore.credential.revocation.StatusListCredential?> = vericoreCatching {
        require(statusListId.isNotBlank()) { "Status list ID is required" }
        statusListManager.getStatusList(statusListId)
    }
    
    /**
     * Updates a status list with revoked indices using a StatusListManager.
     * 
     * **Example:**
     * ```kotlin
     * val statusListManager = InMemoryStatusListManager()
     * val updated = vericore.updateStatusList(
     *     statusListId = "status-list-1",
     *     revokedIndices = listOf(0, 5, 10),
     *     statusListManager = statusListManager
     * ).getOrThrow()
     * ```
     * 
     * @param statusListId The ID of the status list
     * @param revokedIndices List of revoked credential indices
     * @param statusListManager The StatusListManager to use
     * @return Result containing the updated status list credential
     */
    suspend fun updateStatusList(
        statusListId: String,
        revokedIndices: List<Int>,
        statusListManager: com.geoknoesis.vericore.credential.revocation.StatusListManager
    ): Result<com.geoknoesis.vericore.credential.revocation.StatusListCredential> = vericoreCatching {
        require(statusListId.isNotBlank()) { "Status list ID is required" }
        statusListManager.updateStatusList(statusListId, revokedIndices)
    }
    
    // ========================================
    // Blockchain Anchoring
    // ========================================
    
    /**
     * Anchors data to a blockchain.
     * 
     * **Example:**
     * ```kotlin
     * // Simple usage
     * val result = vericore.anchor(
     *     data = myData,
     *     serializer = MyData.serializer(),
     *     chainId = "algorand:testnet"
     * ).getOrThrow()
     * println("Anchored at: ${result.ref.txHash}")
     * 
     * // With error handling
     * val result = vericore.anchor(...)
     * result.fold(
     *     onSuccess = { anchor -> println("Anchored at: ${anchor.ref.txHash}") },
     *     onFailure = { error -> println("Anchoring failed: ${error.message}") }
     * )
     * ```
     * 
     * @param data The data to anchor
     * @param serializer Kotlinx Serialization serializer for the data type
     * @param chainId Blockchain chain identifier (CAIP-2 format)
     * @return Result containing anchor result with transaction reference
     */
    suspend fun <T : Any> anchor(
        data: T,
        serializer: KSerializer<T>,
        chainId: String
    ): Result<AnchorResult> = vericoreCatching {
        // Validate chain ID format
        ChainIdValidator.validateFormat(chainId).let {
            if (!it.isValid()) {
                throw VeriCoreError.ChainNotRegistered(
                    chainId = chainId,
                    availableChains = context.getAvailableChains()
                )
            }
        }
        
        // Validate chain is registered
        val availableChains = context.getAvailableChains()
        ChainIdValidator.validateRegistered(chainId, availableChains).let {
            if (!it.isValid()) {
                throw VeriCoreError.ChainNotRegistered(
                    chainId = chainId,
                    availableChains = availableChains
                )
            }
        }
        
        val client = context.getBlockchainClient(chainId)
            ?: throw VeriCoreError.ChainNotRegistered(
                chainId = chainId,
                availableChains = availableChains
            )
        
        val json = Json.encodeToJsonElement(serializer, data)
        client.writePayload(json)
    }
    
    /**
     * Reads anchored data from a blockchain.
     * 
     * **Example:**
     * ```kotlin
     * // Simple usage
     * val data = vericore.readAnchor<MyData>(
     *     ref = anchorRef,
     *     serializer = MyData.serializer()
     * ).getOrThrow()
     * 
     * // With error handling
     * val result = vericore.readAnchor<MyData>(...)
     * result.fold(
     *     onSuccess = { data -> println("Read: $data") },
     *     onFailure = { error -> println("Read failed: ${error.message}") }
     * )
     * ```
     * 
     * @param ref The anchor reference
     * @param serializer Kotlinx Serialization serializer for the data type
     * @return Result containing the deserialized data
     */
    suspend fun <T : Any> readAnchor(
        ref: com.geoknoesis.vericore.anchor.AnchorRef,
        serializer: KSerializer<T>
    ): Result<T> = vericoreCatching {
        // Validate chain is registered
        val availableChains = context.getAvailableChains()
        ChainIdValidator.validateRegistered(ref.chainId, availableChains).let {
            if (!it.isValid()) {
                throw VeriCoreError.ChainNotRegistered(
                    chainId = ref.chainId,
                    availableChains = availableChains
                )
            }
        }
        
        val client = context.getBlockchainClient(ref.chainId)
            ?: throw VeriCoreError.ChainNotRegistered(
                chainId = ref.chainId,
                availableChains = availableChains
            )
        
        val result = client.readPayload(ref)
        Json.decodeFromJsonElement(serializer, result.payload)
    }
    
    // ========================================
    // Key Management Operations
    // ========================================
    
    /**
     * Generates a new cryptographic key.
     * 
     * **Example:**
     * ```kotlin
     * val key = vericore.generateKey(Algorithm.Ed25519).getOrThrow()
     * println("Generated key: ${key.id}")
     * ```
     * 
     * @param algorithm The algorithm to use
     * @param options Additional options for key generation
     * @return Result containing the key handle
     */
    suspend fun generateKey(
        algorithm: Algorithm,
        options: Map<String, Any?> = emptyMap()
    ): Result<KeyHandle> = vericoreCatching {
        // Check if algorithm is supported
        val supportedAlgorithms = context.kms.getSupportedAlgorithms()
        if (!supportedAlgorithms.contains(algorithm)) {
            throw VeriCoreError.UnsupportedAlgorithm(
                algorithm = algorithm.name,
                supportedAlgorithms = supportedAlgorithms.map { it.name }
            )
        }
        
        context.kms.generateKey(algorithm, options)
    }
    
    /**
     * Generates a new cryptographic key by algorithm name.
     * 
     * **Example:**
     * ```kotlin
     * val key = vericore.generateKey("Ed25519").getOrThrow()
     * ```
     * 
     * @param algorithmName The algorithm name (e.g., "Ed25519", "secp256k1")
     * @param options Additional options for key generation
     * @return Result containing the key handle
     */
    suspend fun generateKey(
        algorithmName: String,
        options: Map<String, Any?> = emptyMap()
    ): Result<KeyHandle> = vericoreCatching {
        val algorithm = Algorithm.parse(algorithmName)
            ?: throw VeriCoreError.UnsupportedAlgorithm(
                algorithm = algorithmName,
                supportedAlgorithms = context.kms.getSupportedAlgorithms().map { it.name }
            )
        
        // Check if algorithm is supported
        val supportedAlgorithms = context.kms.getSupportedAlgorithms()
        if (!supportedAlgorithms.contains(algorithm)) {
            throw VeriCoreError.UnsupportedAlgorithm(
                algorithm = algorithm.name,
                supportedAlgorithms = supportedAlgorithms.map { it.name }
            )
        }
        
        context.kms.generateKey(algorithm, options)
    }
    
    /**
     * Retrieves the public key information for a given key ID.
     * 
     * **Example:**
     * ```kotlin
     * val keyHandle = vericore.getPublicKey("key-1").getOrThrow()
     * println("Public key JWK: ${keyHandle.publicKeyJwk}")
     * ```
     * 
     * @param keyId The identifier of the key
     * @return Result containing the key handle with public key information
     */
    suspend fun getPublicKey(keyId: String): Result<KeyHandle> = vericoreCatching {
        require(keyId.isNotBlank()) { "Key ID is required" }
        val normalizedKeyId = normalizeKeyId(keyId)
        context.kms.getPublicKey(normalizedKeyId)
    }
    
    /**
     * Signs data using the specified key.
     * 
     * **Example:**
     * ```kotlin
     * val signature = vericore.sign("key-1", data.toByteArray()).getOrThrow()
     * ```
     * 
     * @param keyId The identifier of the key to use for signing
     * @param data The data to sign
     * @param algorithm Optional algorithm override
     * @return Result containing the signature bytes
     */
    suspend fun sign(
        keyId: String,
        data: ByteArray,
        algorithm: Algorithm? = null
    ): Result<ByteArray> = vericoreCatching {
        require(keyId.isNotBlank()) { "Key ID is required" }
        require(data.isNotEmpty()) { "Data to sign cannot be empty" }
        val normalizedKeyId = normalizeKeyId(keyId)
        context.kms.sign(normalizedKeyId, data, algorithm)
    }
    
    /**
     * Signs data using the specified key by algorithm name.
     * 
     * @param keyId The identifier of the key to use for signing
     * @param data The data to sign
     * @param algorithmName Optional algorithm name override
     * @return Result containing the signature bytes
     */
    suspend fun sign(
        keyId: String,
        data: ByteArray,
        algorithmName: String?
    ): Result<ByteArray> = vericoreCatching {
        require(keyId.isNotBlank()) { "Key ID is required" }
        require(data.isNotEmpty()) { "Data to sign cannot be empty" }
        val normalizedKeyId = normalizeKeyId(keyId)
        val algorithm = algorithmName?.let { Algorithm.parse(it) }
        context.kms.sign(normalizedKeyId, data, algorithm)
    }
    
    /**
     * Deletes a key from the key management service.
     * 
     * **Example:**
     * ```kotlin
     * vericore.deleteKey("key-1").getOrThrow()
     * ```
     * 
     * @param keyId The identifier of the key to delete
     * @return Result indicating success or failure
     */
    suspend fun deleteKey(keyId: String): Result<Unit> = vericoreCatching {
        require(keyId.isNotBlank()) { "Key ID is required" }
        val normalizedKeyId = normalizeKeyId(keyId)
        context.kms.deleteKey(normalizedKeyId)
    }
    
    /**
     * Gets the set of algorithms supported by the KMS.
     * 
     * **Example:**
     * ```kotlin
     * val algorithms = vericore.getSupportedAlgorithms()
     * println("Supported: ${algorithms.map { it.name }}")
     * ```
     * 
     * @return Set of supported algorithms
     */
    suspend fun getSupportedAlgorithms(): Set<Algorithm> {
        return context.kms.getSupportedAlgorithms()
    }
    
    /**
     * Checks if a specific algorithm is supported.
     * 
     * @param algorithm The algorithm to check
     * @return true if supported, false otherwise
     */
    suspend fun supportsAlgorithm(algorithm: Algorithm): Boolean {
        return context.kms.supportsAlgorithm(algorithm)
    }
    
    /**
     * Checks if an algorithm by name is supported.
     * 
     * @param algorithmName The algorithm name (case-insensitive)
     * @return true if supported, false otherwise
     */
    suspend fun supportsAlgorithm(algorithmName: String): Boolean {
        return context.kms.supportsAlgorithm(algorithmName)
    }
    
    // ========================================
    // Schema Management Operations
    // ========================================
    
    /**
     * Registers a credential schema with its definition.
     * 
     * **Example:**
     * ```kotlin
     * val schema = CredentialSchema(
     *     id = "https://example.com/schemas/person",
     *     type = "JsonSchemaValidator2018",
     *     schemaFormat = SchemaFormat.JSON_SCHEMA
     * )
     * 
     * val definition = buildJsonObject {
     *     put("\$schema", "http://json-schema.org/draft-07/schema#")
     *     put("type", "object")
     *     put("properties", buildJsonObject {
     *         put("name", buildJsonObject { put("type", "string") })
     *     })
     * }
     * 
     * val result = vericore.registerSchema(schema, definition).getOrThrow()
     * ```
     * 
     * @param schema Schema metadata
     * @param definition Schema definition (JSON Schema or SHACL shape)
     * @return Result containing registration result
     */
    suspend fun registerSchema(
        schema: com.geoknoesis.vericore.credential.models.CredentialSchema,
        definition: kotlinx.serialization.json.JsonObject
    ): Result<com.geoknoesis.vericore.credential.schema.SchemaRegistrationResult> = vericoreCatching {
        require(schema.id.isNotBlank()) { "Schema ID is required" }
        com.geoknoesis.vericore.credential.schema.SchemaRegistry.registerSchema(schema, definition)
    }
    
    /**
     * Gets schema metadata by ID.
     * 
     * **Example:**
     * ```kotlin
     * val schema = vericore.getSchema("https://example.com/schemas/person")
     * if (schema != null) {
     *     println("Schema type: ${schema.type}")
     * }
     * ```
     * 
     * @param schemaId Schema ID
     * @return Schema metadata, or null if not found
     */
    fun getSchema(schemaId: String): com.geoknoesis.vericore.credential.models.CredentialSchema? {
        return com.geoknoesis.vericore.credential.schema.SchemaRegistry.getSchema(schemaId)
    }
    
    /**
     * Gets schema definition by ID.
     * 
     * **Example:**
     * ```kotlin
     * val definition = vericore.getSchemaDefinition("https://example.com/schemas/person")
     * if (definition != null) {
     *     println("Schema definition: $definition")
     * }
     * ```
     * 
     * @param schemaId Schema ID
     * @return Schema definition, or null if not found
     */
    fun getSchemaDefinition(schemaId: String): kotlinx.serialization.json.JsonObject? {
        return com.geoknoesis.vericore.credential.schema.SchemaRegistry.getSchemaDefinition(schemaId)
    }
    
    /**
     * Validates a credential against a registered schema.
     * 
     * **Example:**
     * ```kotlin
     * val result = vericore.validateCredentialAgainstSchema(
     *     credential = credential,
     *     schemaId = "https://example.com/schemas/person"
     * ).getOrThrow()
     * 
     * if (result.valid) {
     *     println("Credential is valid")
     * } else {
     *     println("Validation errors: ${result.errors}")
     * }
     * ```
     * 
     * @param credential Credential to validate
     * @param schemaId Schema ID to validate against
     * @return Result containing validation result
     */
    suspend fun validateCredentialAgainstSchema(
        credential: VerifiableCredential,
        schemaId: String
    ): Result<com.geoknoesis.vericore.credential.schema.SchemaValidationResult> = vericoreCatching {
        require(schemaId.isNotBlank()) { "Schema ID is required" }
        com.geoknoesis.vericore.credential.schema.SchemaRegistry.validateCredential(credential, schemaId)
    }
    
    /**
     * Checks if a schema is registered.
     * 
     * @param schemaId Schema ID
     * @return true if schema is registered
     */
    fun isSchemaRegistered(schemaId: String): Boolean {
        return com.geoknoesis.vericore.credential.schema.SchemaRegistry.isRegistered(schemaId)
    }
    
    /**
     * Gets all registered schema IDs.
     * 
     * @return List of schema IDs
     */
    fun getAllSchemaIds(): List<String> {
        return com.geoknoesis.vericore.credential.schema.SchemaRegistry.getAllSchemaIds()
    }
    
    // ========================================
    // Trust Registry Operations
    // ========================================
    
    /**
     * Checks if an issuer DID is trusted for a specific credential type.
     * 
     * **Example:**
     * ```kotlin
     * val isTrusted = vericore.isTrustedIssuer(
     *     issuerDid = "did:key:issuer",
     *     credentialType = "EducationCredential"
     * ).getOrThrow()
     * ```
     * 
     * **Note:** This requires a TrustRegistry to be configured. If no registry is available,
     * this will return false (issuer is not trusted).
     * 
     * @param issuerDid The DID of the issuer to check
     * @param credentialType The credential type (null means check for any type)
     * @return Result containing true if trusted, false otherwise
     */
    suspend fun isTrustedIssuer(
        issuerDid: String,
        credentialType: String? = null
    ): Result<Boolean> = vericoreCatching {
        require(issuerDid.isNotBlank()) { "Issuer DID is required" }
        
        // For now, return false if no trust registry is configured
        // In the future, this could be added to VeriCoreContext
        // For now, we'll note that trust registry operations require explicit configuration
        throw VeriCoreError.InvalidOperation(
            code = "TRUST_REGISTRY_NOT_CONFIGURED",
            message = "Trust registry is not configured in VeriCore context. " +
                      "Use trust layer configuration or provide a TrustRegistry instance.",
            context = mapOf("issuerDid" to issuerDid, "credentialType" to credentialType)
        )
    }
    
    // ========================================
    // Context Access
    // ========================================
    
    /**
     * Gets the underlying context for advanced usage.
     * 
     * Use this when you need direct access to registries or services.
     * Most users should use the VeriCore facade methods instead.
     * 
     * @return The VeriCore context
     */
    fun getContext(): VeriCoreContext = context
    
    /**
     * Registers a DID method on this VeriCore instance.
     * 
     * Useful for adding methods after construction.
     * 
     * **Example:**
     * ```kotlin
     * vericore.registerDidMethod(DidWebMethod())
     * ```
     * 
     * @param method The DID method implementation to register
     */
    fun registerDidMethod(method: DidMethod) {
        context.didRegistry.register(method)
    }
    
    /**
     * Registers a blockchain client on this VeriCore instance.
     * 
     * Useful for adding blockchain clients after construction.
     * 
     * **Example:**
     * ```kotlin
     * vericore.registerBlockchainClient("ethereum:mainnet", ethereumClient)
     * ```
     * 
     * @param chainId Chain ID in CAIP-2 format (e.g., "ethereum:mainnet")
     * @param client Blockchain anchor client instance
     */
    fun registerBlockchainClient(chainId: String, client: BlockchainAnchorClient) {
        context.blockchainRegistry.register(chainId, client)
    }
    
    /**
     * Gets available DID method names.
     * 
     * @return List of registered DID method names
     */
    fun getAvailableDidMethods(): List<String> = context.didRegistry.getAllMethodNames()
    
    /**
     * Gets available blockchain chain IDs.
     * 
     * @return List of registered blockchain chain IDs
     */
    fun getAvailableChains(): List<String> = context.blockchainRegistry.getAllChainIds()
    
    // ========================================
    // Plugin Lifecycle Management
    // ========================================
    
    /**
     * Initializes all plugins that implement [PluginLifecycle].
     * 
     * This should be called after creating a VeriCore instance if you need
     * to initialize plugins before use.
     * 
     * **Example:**
     * ```kotlin
     * val vericore = VeriCore.create { ... }
     * vericore.initialize().getOrThrow()
     * ```
     * 
     * @param config Optional configuration map for plugins
     * @return Result indicating success or failure
     */
    suspend fun initialize(config: Map<String, Any?> = emptyMap()): Result<Unit> = vericoreCatching {
        val plugins = context.getAllPlugins()
        val failures = mutableListOf<String>()
        
        plugins.forEach { plugin ->
            if (plugin is PluginLifecycle) {
                val pluginConfig = config[plugin::class.simpleName] as? Map<String, Any?> ?: emptyMap()
                val initialized = plugin.initialize(pluginConfig)
                if (!initialized) {
                    failures.add("Plugin ${plugin::class.simpleName} failed to initialize")
                }
            }
        }
        
        if (failures.isNotEmpty()) {
            throw VeriCoreError.PluginInitializationFailed(
                pluginId = "multiple",
                reason = failures.joinToString("; ")
            )
        }
    }
    
    /**
     * Starts all plugins that implement [PluginLifecycle].
     * 
     * This should be called after [initialize] and before using VeriCore.
     * 
     * **Example:**
     * ```kotlin
     * val vericore = VeriCore.create { ... }
     * vericore.initialize().getOrThrow()
     * vericore.start().getOrThrow()
     * ```
     * 
     * @return Result indicating success or failure
     */
    suspend fun start(): Result<Unit> = vericoreCatching {
        val plugins = context.getAllPlugins()
        val failures = mutableListOf<String>()
        
        plugins.forEach { plugin ->
            if (plugin is PluginLifecycle) {
                val started = plugin.start()
                if (!started) {
                    failures.add("Plugin ${plugin::class.simpleName} failed to start")
                }
            }
        }
        
        if (failures.isNotEmpty()) {
            throw VeriCoreError.PluginInitializationFailed(
                pluginId = "multiple",
                reason = failures.joinToString("; ")
            )
        }
    }
    
    /**
     * Stops all plugins that implement [PluginLifecycle].
     * 
     * This should be called when shutting down VeriCore.
     * Plugins are stopped in reverse order of initialization.
     * 
     * **Example:**
     * ```kotlin
     * val vericore = VeriCore.create { ... }
     * // ... use vericore ...
     * vericore.stop().getOrThrow()
     * ```
     * 
     * @return Result indicating success or failure
     */
    suspend fun stop(): Result<Unit> = vericoreCatching {
        val plugins = context.getAllPlugins().reversed()
        val failures = mutableListOf<String>()
        
        plugins.forEach { plugin ->
            if (plugin is PluginLifecycle) {
                try {
                    val stopped = plugin.stop()
                    if (!stopped) {
                        failures.add("Plugin ${plugin::class.simpleName} failed to stop")
                    }
                } catch (e: Exception) {
                    failures.add("Plugin ${plugin::class.simpleName} error during stop: ${e.message}")
                }
            }
        }
        
        if (failures.isNotEmpty()) {
            throw VeriCoreError.InvalidOperation(
                code = "PLUGIN_STOP_FAILED",
                message = "Some plugins failed to stop: ${failures.joinToString("; ")}",
                context = emptyMap()
            )
        }
    }
    
    /**
     * Cleans up all plugins that implement [PluginLifecycle].
     * 
     * This should be called after [stop] for final cleanup.
     * 
     * **Example:**
     * ```kotlin
     * val vericore = VeriCore.create { ... }
     * // ... use vericore ...
     * vericore.stop().getOrThrow()
     * vericore.cleanup().getOrThrow()
     * ```
     * 
     * @return Result indicating success or failure
     */
    suspend fun cleanup(): Result<Unit> = vericoreCatching {
        val plugins = context.getAllPlugins().reversed()
        
        plugins.forEach { plugin ->
            if (plugin is PluginLifecycle) {
                try {
                    plugin.cleanup()
                } catch (e: Exception) {
                    // Log but don't fail on cleanup errors
                    // This is best effort cleanup
                }
            }
        }
    }
    
    // ========================================
    // Factory Methods
    // ========================================

    companion object {
        /**
         * Creates a VeriCore instance with sensible defaults.
         * 
         * Includes:
         * - In-memory key management
         * - did:key method support
         * - No blockchain anchoring (must be configured separately)
         * 
         * **Example:**
         * ```kotlin
         * val vericore = VeriCore.create()
         * ```
         * 
         * @return VeriCore instance with default configuration
         */
        fun create(): VeriCore = create(VeriCoreDefaults.inMemory())
        
        /**
         * Creates a VeriCore instance from a configuration object.
         *
         * @param config Fully constructed configuration
         */
        fun create(config: VeriCoreConfig): VeriCore {
            return VeriCore(VeriCoreContext.fromConfig(config))
        }
        
        /**
         * Creates a VeriCore instance with custom configuration.
         * Starts from defaults and applies overrides.
         *
         * **Example - DSL Style (Recommended):**
         * ```kotlin
         * val vericore = VeriCore.create {
         *     kms = InMemoryKeyManagementService()
         *     walletFactory = TestkitWalletFactory()
         *     
         *     didMethods {
         *         + DidKeyMethod()
         *         + DidWebMethod()
         *     }
         *     
         *     blockchain {
         *         "ethereum:mainnet" to ethereumClient
         *         "algorand:testnet" to algorandClient
         *     }
         *     
         *     credentialServices {
         *         + MyCredentialService()
         *     }
         * }
         * ```
         *
         * @param configure Configuration block
         * @return Configured VeriCore instance
         */
        fun create(configure: VeriCoreConfig.Builder.() -> Unit): VeriCore {
            val builder = VeriCoreDefaults.inMemory().toBuilder()
            builder.configure()
            return create(builder.build())
        }
    }
}

/**
 * VeriCore context holding all registries and services.
 * 
 * This class encapsulates all dependencies and configuration,
 * making it thread-safe and testable without global state.
 */
class VeriCoreContext private constructor(
    internal val kms: KeyManagementService,
    internal val walletFactory: WalletFactory,
    internal val didRegistry: DidMethodRegistry,
    internal val blockchainRegistry: BlockchainAnchorRegistry,
    internal val credentialRegistry: CredentialServiceRegistry,
    internal val proofRegistry: ProofGeneratorRegistry
) {
    
    /**
     * Gets a DID method by name.
     * 
     * @param methodName The DID method name (e.g., "key", "web", "ion")
     * @return The DID method if registered, null otherwise
     */
    fun getDidMethod(methodName: String): DidMethod? = didRegistry.get(methodName)
    
    /**
     * Gets available DID method names.
     * 
     * @return List of registered DID method names
     */
    fun getAvailableDidMethods(): List<String> = didRegistry.getAllMethodNames()
    
    /**
     * Gets a blockchain client by chain ID.
     * 
     * @param chainId Chain ID in CAIP-2 format (e.g., "ethereum:mainnet")
     * @return The blockchain client if registered, null otherwise
     */
    fun getBlockchainClient(chainId: String): BlockchainAnchorClient? =
        blockchainRegistry.get(chainId)
    
    /**
     * Gets available blockchain chain IDs.
     * 
     * @return List of registered blockchain chain IDs
     */
    fun getAvailableChains(): List<String> = blockchainRegistry.getAllChainIds()
    
    /**
     * Resolves a DID using registered methods.
     * 
     * **Note:** This is a lower-level method that returns `DidResolutionResult` directly.
     * For error handling with `Result<T>`, use [VeriCore.resolveDid] instead.
     * This method may throw exceptions if resolution fails.
     * 
     * @param did The DID string to resolve
     * @return Resolution result containing the document and metadata
     */
    suspend fun resolveDid(did: String): DidResolutionResult {
        return didRegistry.resolve(did)
    }

    /**
     * Gets the DID method registry.
     * 
     * @InternalApi This method exposes internal implementation details.
     * Use [getDidMethod] and [getAvailableDidMethods] for public operations.
     */
    @PublishedApi
    internal fun didMethodRegistry(): DidMethodRegistry = didRegistry

    /**
     * Gets the blockchain anchor registry.
     * 
     * @InternalApi This method exposes internal implementation details.
     * Use [getBlockchainClient] and [getAvailableChains] for public operations.
     */
    @PublishedApi
    internal fun blockchainRegistry(): BlockchainAnchorRegistry = blockchainRegistry

    /**
     * Gets the credential service registry.
     * 
     * @InternalApi This method exposes internal implementation details.
     * Access through VeriCore facade methods instead.
     */
    @PublishedApi
    internal fun credentialRegistry(): CredentialServiceRegistry = credentialRegistry

    /**
     * Gets the proof generator registry.
     * 
     * @InternalApi This method exposes internal implementation details.
     * Access through VeriCore facade methods instead.
     */
    @PublishedApi
    internal fun proofRegistry(): ProofGeneratorRegistry = proofRegistry
    
    /**
     * Gets all plugins that implement PluginLifecycle from all registries.
     * 
     * @return List of all plugins that implement PluginLifecycle
     */
    fun getAllPlugins(): List<Any> {
        val plugins = mutableListOf<Any>()
        
        // Add KMS if it implements PluginLifecycle
        if (kms is PluginLifecycle) {
            plugins.add(kms)
        }
        
        // Add wallet factory if it implements PluginLifecycle
        if (walletFactory is PluginLifecycle) {
            plugins.add(walletFactory)
        }
        
        // Add DID methods
        didRegistry.getAllMethodNames().forEach { methodName ->
            didRegistry.get(methodName)?.let { method ->
                if (method is PluginLifecycle) {
                    plugins.add(method)
                }
            }
        }
        
        // Add blockchain clients
        blockchainRegistry.getAllChainIds().forEach { chainId ->
            blockchainRegistry.get(chainId)?.let { client ->
                if (client is PluginLifecycle) {
                    plugins.add(client)
                }
            }
        }
        
        // Add credential services
        credentialRegistry.getAll().values.forEach { service ->
            if (service is PluginLifecycle) {
                plugins.add(service)
            }
        }
        
        // Add proof generators
        proofRegistry.getRegisteredTypes().forEach { proofType ->
            proofRegistry.get(proofType)?.let { generator ->
                if (generator is PluginLifecycle) {
                    plugins.add(generator)
                }
            }
        }
        
        return plugins
    }
    
    companion object {
        fun fromConfig(config: VeriCoreConfig): VeriCoreContext {
            return VeriCoreContext(
                kms = config.kms,
                walletFactory = config.walletFactory,
                didRegistry = config.didRegistry,
                blockchainRegistry = config.blockchainRegistry,
                credentialRegistry = config.credentialRegistry,
                proofRegistry = config.proofRegistry
            )
        }
    }
}

/**
 * Strongly typed configuration for [VeriCore].
 */
data class VeriCoreConfig(
    val kms: KeyManagementService,
    val walletFactory: WalletFactory,
    val didRegistry: DidMethodRegistry,
    val blockchainRegistry: BlockchainAnchorRegistry = BlockchainAnchorRegistry(),
    val credentialRegistry: CredentialServiceRegistry = CredentialServiceRegistry.create(),
    val proofRegistry: ProofGeneratorRegistry = ProofGeneratorRegistry()
) {
    fun toBuilder(): Builder = Builder(
        _kms = kms,
        _walletFactory = walletFactory,
        didRegistry = didRegistry.snapshot(),
        blockchainRegistry = blockchainRegistry.snapshot(),
        credentialRegistry = credentialRegistry.snapshot(),
        proofRegistry = proofRegistry.snapshot()
    )
    
    class Builder internal constructor(
        private var _kms: KeyManagementService?,
        private var _walletFactory: WalletFactory?,
        private val didRegistry: DidMethodRegistry,
        private val blockchainRegistry: BlockchainAnchorRegistry,
        private val credentialRegistry: CredentialServiceRegistry,
        private val proofRegistry: ProofGeneratorRegistry
    ) {
        /**
         * Sets the Key Management Service.
         * 
         * **Example:**
         * ```kotlin
         * kms = InMemoryKeyManagementService()
         * ```
         */
        var kms: KeyManagementService?
            get() = _kms
            set(value) {
                _kms = value
            }
        
        /**
         * Sets the Wallet Factory.
         * 
         * **Example:**
         * ```kotlin
         * walletFactory = TestkitWalletFactory()
         * ```
         */
        var walletFactory: WalletFactory?
            get() = _walletFactory
            set(value) {
                _walletFactory = value
            }
        
        /**
         * DSL block for registering DID methods.
         * 
         * **Example:**
         * ```kotlin
         * didMethods {
         *     + DidKeyMethod()
         *     + DidWebMethod()
         * }
         * ```
         */
        fun didMethods(block: DidMethodsBuilder.() -> Unit) {
            val builder = DidMethodsBuilder(didRegistry)
            builder.block()
        }
        
        /**
         * Internal builder for DID methods DSL.
         */
        inner class DidMethodsBuilder(
            private val registry: DidMethodRegistry
        ) {
            operator fun DidMethod.unaryPlus() {
                registry.register(this)
            }
        }
        
        /**
         * Registers a single DID method (backward compatibility).
         */
        fun registerDidMethod(method: DidMethod) {
            didRegistry.register(method)
        }
        
        /**
         * DSL block for registering blockchain clients.
         * 
         * **Example:**
         * ```kotlin
         * blockchain {
         *     "ethereum:mainnet" to ethereumClient
         *     "algorand:testnet" to algorandClient
         * }
         * ```
         */
        fun blockchain(block: MutableMap<String, BlockchainAnchorClient>.() -> Unit) {
            val clients = mutableMapOf<String, BlockchainAnchorClient>()
            clients.block()
            clients.forEach { (chainId, client) ->
                blockchainRegistry.register(chainId, client)
            }
        }
        
        /**
         * Registers a single blockchain client (backward compatibility).
         */
        fun registerBlockchainClient(chainId: String, client: BlockchainAnchorClient) {
            blockchainRegistry.register(chainId, client)
        }
        
        /**
         * DSL block for registering credential services.
         * 
         * **Example:**
         * ```kotlin
         * credentialServices {
         *     + MyCredentialService()
         * }
         * ```
         */
        fun credentialServices(block: CredentialServicesBuilder.() -> Unit) {
            val builder = CredentialServicesBuilder(credentialRegistry)
            builder.block()
        }
        
        /**
         * Internal builder for credential services DSL.
         */
        inner class CredentialServicesBuilder(
            private val registry: CredentialServiceRegistry
        ) {
            operator fun CredentialService.unaryPlus() {
                registry.register(this)
            }
        }
        
        /**
         * Registers a single credential service (backward compatibility).
         */
        fun registerCredentialService(service: CredentialService) {
            credentialRegistry.register(service)
        }
        
        /**
         * Unregisters a credential service.
         */
        fun unregisterCredentialService(providerName: String) {
            credentialRegistry.unregister(providerName)
        }

        /**
         * DSL block for registering proof generators.
         * 
         * **Example:**
         * ```kotlin
         * proofGenerators {
         *     + Ed25519ProofGenerator(signer)
         *     + BbsProofGenerator(signer)
         * }
         * ```
         */
        fun proofGenerators(block: ProofGeneratorsBuilder.() -> Unit) {
            val builder = ProofGeneratorsBuilder(proofRegistry)
            builder.block()
        }
        
        /**
         * Internal builder for proof generators DSL.
         */
        inner class ProofGeneratorsBuilder(
            private val registry: ProofGeneratorRegistry
        ) {
            operator fun ProofGenerator.unaryPlus() {
                registry.register(this)
            }
        }
        
        /**
         * Registers a single proof generator (backward compatibility).
         */
        fun registerProofGenerator(generator: ProofGenerator) {
            proofRegistry.register(generator)
        }

        /**
         * Unregisters a proof generator.
         */
        fun unregisterProofGenerator(proofType: String) {
            proofRegistry.unregister(proofType)
        }
        
        /**
         * Removes a blockchain client.
         */
        fun removeBlockchainClient(chainId: String) {
            blockchainRegistry.unregister(chainId)
        }
        
        /**
         * Removes a DID method.
         */
        fun removeDidMethod(methodName: String) {
            didRegistry.unregister(methodName)
        }
        
        /**
         * Builds the VeriCoreConfig from this builder.
         */
        fun build(): VeriCoreConfig {
            val kms = requireNotNull(_kms) { "KMS must be configured" }
            val walletFactory = requireNotNull(_walletFactory) { "WalletFactory must be configured" }
            require(didRegistry.size() > 0) { "At least one DID method must be registered" }
            
            return VeriCoreConfig(
                kms = kms,
                walletFactory = walletFactory,
                didRegistry = didRegistry.snapshot(),
                blockchainRegistry = blockchainRegistry.snapshot(),
                credentialRegistry = credentialRegistry.snapshot(),
                proofRegistry = proofRegistry.snapshot()
            )
        }
    }
    
    companion object
}


/**
 * Builder for constructing wallet creation options in a type-safe manner.
 */
class WalletOptionsBuilder {
    private val delegate = WalletCreationOptionsBuilder()

    var label: String?
        get() = delegate.label
        set(value) {
            delegate.label = value
        }

    var storagePath: String?
        get() = delegate.storagePath
        set(value) {
            delegate.storagePath = value
        }

    var encryptionKey: String?
        get() = delegate.encryptionKey
        set(value) {
            delegate.encryptionKey = value
        }

    var enableOrganization: Boolean
        get() = delegate.enableOrganization
        set(value) {
            delegate.enableOrganization = value
        }

    var enablePresentation: Boolean
        get() = delegate.enablePresentation
        set(value) {
            delegate.enablePresentation = value
        }

    fun property(key: String, value: Any?) {
        delegate.property(key, value)
    }

    internal fun build(): WalletCreationOptions = delegate.build()
}

/**
 * Convenience builder for wallet creation options.
 *
 * **Example:**
 * ```kotlin
 * val options = walletOptions {
 *     label = "Holder Wallet"
 *     storagePath = "/wallets/holders/alice"
 *     property("autoUnlock", true)
 * }
 * ```
 */
fun walletOptions(block: WalletOptionsBuilder.() -> Unit): WalletCreationOptions {
    val builder = WalletOptionsBuilder()
    builder.block()
    return builder.build()
}

