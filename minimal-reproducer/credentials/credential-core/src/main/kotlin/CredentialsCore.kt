package com.example

import kotlinx.serialization.Serializable

@Serializable
data class CredentialsCoreData(val value: String)

// Internal declaration to potentially trigger friend module behavior
internal class InternalCredentialsCore {
    fun getInternalValue(): String = "Internal CredentialsCore"
}

class CredentialsCore {
    private val didCore = com.example.DidCore()
    
    fun getValue(): String = "CredentialsCore using ${didCore.getValue()}"
    
    // Use internal class
    fun useInternal(): String {
        return InternalCredentialsCore().getInternalValue()
    }
    
    // Use types from did:did-core to ensure compilation dependency
    fun useDidCoreType(): com.example.DidCoreData {
        return com.example.DidCoreData("test")
    }
}
