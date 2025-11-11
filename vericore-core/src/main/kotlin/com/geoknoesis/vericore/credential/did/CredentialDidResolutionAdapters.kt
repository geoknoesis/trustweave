package com.geoknoesis.vericore.credential.did

fun Any?.asCredentialDidResolution(): CredentialDidResolution? = when (this) {
    null -> null
    is CredentialDidResolution -> this
    is Boolean -> if (this) {
        CredentialDidResolution(isResolvable = true)
    } else {
        CredentialDidResolution(isResolvable = false)
    }
    is Map<*, *> -> CredentialDidResolution(document = this, raw = this, isResolvable = true)
    else -> {
        val document = extractDocument()
        val rawValue = extractRaw()
        val resolvable = extractResolvable() ?: (document != null || rawValue != null)
        CredentialDidResolution(
            document = document,
            raw = rawValue,
            isResolvable = resolvable
        )
    }
}

private fun Any.extractDocument(): Any? {
    return runCatching { javaClass.getMethod("getDocument").invoke(this) }.getOrNull()
        ?: runCatching { javaClass.getDeclaredField("document").apply { isAccessible = true }.get(this) }.getOrNull()
        ?: runCatching { javaClass.getMethod("document").invoke(this) }.getOrNull()
}

private fun Any.extractRaw(): Any? {
    return runCatching { javaClass.getMethod("getRaw").invoke(this) }.getOrNull()
        ?: runCatching { javaClass.getDeclaredField("raw").apply { isAccessible = true }.get(this) }.getOrNull()
}

private fun Any.extractResolvable(): Boolean? {
    return runCatching { javaClass.getMethod("isResolvable").invoke(this) as? Boolean }.getOrNull()
        ?: runCatching { javaClass.getMethod("isResolved").invoke(this) as? Boolean }.getOrNull()
}
