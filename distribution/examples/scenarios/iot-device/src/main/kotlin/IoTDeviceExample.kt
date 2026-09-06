package org.trustweave.examples.iot

import kotlinx.coroutines.runBlocking
import org.trustweave.examples.scenarios.runSignedScenario

/** An operator signs a device registration and firmware revision. See this scenario's README for scope and commands. */
fun main(): Unit =
    runBlocking {
        runSignedScenario(
            name = "iot-device",
            credentialType = "DeviceRegistrationCredential",
            claims =
                linkedMapOf(
                    "deviceId" to "sensor-demo-11",
                    "firmwareVersion" to "1.2.3",
                    "deviceModel" to "Example temperature sensor",
                ),
            tamperedClaim = "firmwareVersion",
            tamperedValue = "0.0.0",
        )
    }
