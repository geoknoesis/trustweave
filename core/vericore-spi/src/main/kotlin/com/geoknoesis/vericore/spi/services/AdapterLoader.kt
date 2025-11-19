package com.geoknoesis.vericore.spi.services

object AdapterLoader {

    fun didMethodService(): DidMethodService? =
        instantiate("com.geoknoesis.vericore.did.services.DidMethodServiceAdapter") as? DidMethodService

    fun didDocumentAccess(): DidDocumentAccess? =
        instantiate("com.geoknoesis.vericore.did.services.DidDocumentAccessAdapter") as? DidDocumentAccess

    fun verificationMethodAccess(): VerificationMethodAccess? =
        instantiate("com.geoknoesis.vericore.did.services.VerificationMethodAccessAdapter") as? VerificationMethodAccess

    fun serviceAccess(): ServiceAccess? =
        instantiate("com.geoknoesis.vericore.did.services.ServiceAccessAdapter") as? ServiceAccess

    fun kmsService(): KmsService? =
        instantiate("com.geoknoesis.vericore.testkit.services.TestkitKmsService") as? KmsService

    fun walletFactory(): WalletFactory? =
        instantiate("com.geoknoesis.vericore.testkit.services.TestkitWalletFactory") as? WalletFactory

    private fun instantiate(className: String): Any? = try {
        val clazz = Class.forName(className)
        clazz.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
    } catch (_: ClassNotFoundException) {
        null
    } catch (_: NoSuchMethodException) {
        null
    }
}


