package org.trustweave.wallet.services

import org.trustweave.core.plugin.ModuleCapabilities

/** Provider factories call this before opening files, pools or network clients. */
fun WalletCreationOptions.validateDeployment(module: String) {
    if (deploymentPolicy == WalletDeploymentPolicy.LEGACY) return
    ModuleCapabilities.requireDeployment(
        module,
        operations = setOf("store", "get", "list", "delete"),
        formats = setOf("json-vc"),
        allowExperimental = deploymentPolicy == WalletDeploymentPolicy.EXPERIMENTAL,
    )
}
