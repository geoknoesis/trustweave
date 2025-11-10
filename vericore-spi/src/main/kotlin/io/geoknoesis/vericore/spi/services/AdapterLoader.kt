package io.geoknoesis.vericore.spi.services

object AdapterLoader {

    fun didRegistryService(): DidRegistryService? =
        instantiate("io.geoknoesis.vericore.did.services.DidRegistryServiceAdapter") as? DidRegistryService

    fun didMethodService(): DidMethodService? =
        instantiate("io.geoknoesis.vericore.did.services.DidMethodServiceAdapter") as? DidMethodService

    fun didDocumentAccess(): DidDocumentAccess? =
        instantiate("io.geoknoesis.vericore.did.services.DidDocumentAccessAdapter") as? DidDocumentAccess

    fun verificationMethodAccess(): VerificationMethodAccess? =
        instantiate("io.geoknoesis.vericore.did.services.VerificationMethodAccessAdapter") as? VerificationMethodAccess

    fun serviceAccess(): ServiceAccess? =
        instantiate("io.geoknoesis.vericore.did.services.ServiceAccessAdapter") as? ServiceAccess

    fun kmsService(): KmsService? =
        instantiate("io.geoknoesis.vericore.testkit.services.TestkitKmsService") as? KmsService

    fun walletFactory(): WalletFactory? =
        instantiate("io.geoknoesis.vericore.testkit.services.TestkitWalletFactory") as? WalletFactory

    fun blockchainRegistryService(): Any? =
        instantiate("io.geoknoesis.vericore.anchor.services.BlockchainRegistryServiceAdapter")

    private fun instantiate(className: String): Any? = try {
        val clazz = Class.forName(className)
        clazz.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
    } catch (_: ClassNotFoundException) {
        null
    } catch (_: NoSuchMethodException) {
        null
    }
}


