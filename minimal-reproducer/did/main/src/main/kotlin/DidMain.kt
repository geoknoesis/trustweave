package com.example

import kotlinx.serialization.Serializable

@Serializable
data class DidMainData(val value: String)

// Internal declaration to potentially trigger friend module behavior
internal class InternalDidMain {
    fun getInternalValue(): String = "Internal DidMain"
}

class DidMain {
    private val common = Common()
    
    fun getValue(): String = "DidMain using ${common.getValue()}"
    
    // Use internal class
    fun useInternal(): String {
        return InternalDidMain().getInternalValue()
    }
}

