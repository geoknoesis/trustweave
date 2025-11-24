package com.example

import kotlinx.serialization.Serializable

@Serializable
data class ModuleAData(val value: String)

// Internal declaration to potentially trigger friend module behavior
internal class InternalModuleA {
    fun getInternalValue(): String = "Internal"
}

class ModuleA {
    private val moduleB = ModuleB()
    
    fun getValue(): String = "Module A using ${moduleB.getValue()}"
    
    // Use internal class
    fun useInternal(): String {
        return InternalModuleA().getInternalValue()
    }
}

