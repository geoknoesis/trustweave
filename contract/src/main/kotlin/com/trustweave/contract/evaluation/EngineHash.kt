package com.trustweave.contract.evaluation

import com.trustweave.contract.models.ConditionType
import com.trustweave.core.util.DigestUtils
import kotlinx.serialization.json.*
import java.io.InputStream
import java.security.MessageDigest

/**
 * Internal utility for computing evaluation engine implementation hashes.
 * 
 * Provides multiple methods for computing hashes to detect engine tampering:
 * - Class file bytecode hash (most reliable)
 * - Engine descriptor hash (metadata-based)
 * - Combined hash (bytecode + descriptor)
 * 
 * This is an internal implementation detail and should not be used directly.
 * Use [BaseEvaluationEngine] which provides automatic hash calculation.
 */
internal object EngineHash {
    
    /**
     * Computes hash from class file bytecode.
     * 
     * This is the most reliable method for detecting tampering,
     * as it hashes the actual compiled bytecode.
     * 
     * @param engineClass The engine class
     * @return Multibase-encoded hash (e.g., "uABC123...")
     * @throws IllegalStateException if class file cannot be accessed
     */
    fun fromClassFile(engineClass: Class<out ContractEvaluationEngine>): String {
        val className = engineClass.name.replace('.', '/') + ".class"
        val classLoader = engineClass.classLoader
            ?: throw IllegalStateException("Cannot get classloader for ${engineClass.name}")
        
        val inputStream: InputStream = classLoader.getResourceAsStream(className)
            ?: throw IllegalStateException("Cannot find class file for ${engineClass.name}")
        
        return inputStream.use { stream ->
            val bytecode = stream.readBytes()
            val digest = MessageDigest.getInstance("SHA-256").digest(bytecode)
            
            // Use multibase encoding (base58btc) consistent with TrustWeave
            encodeMultibase(digest)
        }
    }
    
    /**
     * Computes hash from engine descriptor (metadata).
     * 
     * This approach hashes engine metadata rather than bytecode.
     * Useful when bytecode access is restricted or for dynamic engines.
     * 
     * @param engineId Engine identifier
     * @param version Engine version
     * @param supportedConditionTypes Supported condition types
     * @param implementationSignature Optional signature of critical methods
     * @return Multibase-encoded hash
     */
    fun fromDescriptor(
        engineId: String,
        version: String,
        supportedConditionTypes: Set<ConditionType>,
        implementationSignature: String? = null
    ): String {
        val descriptor = buildJsonObject {
            put("engineId", engineId)
            put("version", version)
            put("supportedConditionTypes", buildJsonArray {
                supportedConditionTypes.forEach { add(it.name) }
            })
            if (implementationSignature != null) {
                put("implementationSignature", implementationSignature)
            }
        }
        
        return DigestUtils.sha256DigestMultibase(descriptor)
    }
    
    /**
     * Computes combined hash from both bytecode and descriptor.
     * 
     * Most comprehensive approach - detects both code and metadata changes.
     * 
     * @param engineClass The engine class
     * @param engineId Engine identifier
     * @param version Engine version
     * @param supportedConditionTypes Supported condition types
     * @return Multibase-encoded hash
     */
    fun combined(
        engineClass: Class<out ContractEvaluationEngine>,
        engineId: String,
        version: String,
        supportedConditionTypes: Set<ConditionType>
    ): String {
        // Hash bytecode
        val bytecodeHash = fromClassFile(engineClass)
        
        // Hash descriptor
        val descriptorHash = fromDescriptor(
            engineId, version, supportedConditionTypes
        )
        
        // Combine both hashes
        val combined = buildJsonObject {
            put("bytecodeHash", bytecodeHash)
            put("descriptorHash", descriptorHash)
        }
        
        return DigestUtils.sha256DigestMultibase(combined)
    }
    
    /**
     * Encodes bytes to multibase format (base58btc with 'u' prefix).
     * Consistent with TrustWeave's DigestUtils implementation.
     */
    private fun encodeMultibase(bytes: ByteArray): String {
        // Use the same base58 encoding as DigestUtils
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = java.math.BigInteger(1, bytes)
        val sb = StringBuilder()
        
        while (num > java.math.BigInteger.ZERO) {
            val remainder = num.mod(java.math.BigInteger.valueOf(58))
            sb.append(alphabet[remainder.toInt()])
            num = num.divide(java.math.BigInteger.valueOf(58))
        }
        
        // Add leading zeros
        for (byte in bytes) {
            if (byte.toInt() == 0) {
                sb.append('1')
            } else {
                break
            }
        }
        
        return "u${sb.reverse().toString()}"
    }
}

