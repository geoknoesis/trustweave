package com.example

import kotlinx.serialization.Serializable

@Serializable
data class ModuleBData(val value: String)

// Internal declaration to potentially trigger friend module behavior
internal class InternalModuleB {
    fun getInternalValue(): String = "Internal B"
}

class ModuleB {
    private val moduleC = ModuleC()
    
    fun getValue(): String = "Module B using ${moduleC.getValue()}"
    
    // Use internal class
    fun useInternal(): String {
        return InternalModuleB().getInternalValue()
    }
}

