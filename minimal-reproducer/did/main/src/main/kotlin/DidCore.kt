package com.example

import kotlinx.serialization.Serializable

@Serializable
data class DidCoreData(val value: String)

// Internal declaration to potentially trigger friend module behavior
internal class InternalDidCore {
    fun getInternalValue(): String = "Internal DidCore"
}

class DidCore {
    private val common = Common()
    
    fun getValue(): String = "DidCore using ${common.getValue()}"
    
    // Use internal class
    fun useInternal(): String {
        return InternalDidCore().getInternalValue()
    }
}

