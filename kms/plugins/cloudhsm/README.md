# AWS CloudHSM KMS Plugin

`kms:plugins:cloudhsm` — backs `KeyManagementService` with an AWS CloudHSM cluster.
CloudHSM exposes a standards-compliant PKCS#11 interface, so this plugin is a thin
adapter over [`kms:plugins:pkcs11`](../pkcs11/) that:

1. Composes the CloudHSM-specific login PIN (`hsmUser:password`).
2. Points SunPKCS11 at the CloudHSM PKCS#11 shared library
   (`libcloudhsm_pkcs11.so` on Linux, `cloudhsm_pkcs11.dll` on Windows).
3. Adds cluster-management helpers (`describeCluster`, `getClusterState`,
   `requireClusterActive`) backed by the AWS CloudHsmV2 SDK.

## Supported algorithms

Ed25519, P-256, P-384, P-521, RSA-2048 / 3072 / 4096. secp256k1 is intentionally
unsupported (CloudHSM does not expose it through PKCS#11).

## Configuration

| Option | Env var | Default | Description |
|---|---|---|---|
| `clusterId` | `AWS_CLOUDHSM_CLUSTER_ID` | — | `cluster-<alphanumeric>` (required) |
| `hsmUser` | `AWS_CLOUDHSM_HSM_USER` | — | Crypto User name (required) |
| `hsmPasswordEnvVar` | `AWS_CLOUDHSM_HSM_PASSWORD_ENV` | `AWS_CLOUDHSM_HSM_PASSWORD` | Env var holding the CU password |
| `region` | `AWS_REGION` / `AWS_DEFAULT_REGION` | `us-east-1` | AWS region |
| `partition` | `AWS_CLOUDHSM_PARTITION` | `PARTITION_1` | PKCS#11 partition label |
| `pkcs11LibraryPath` | `AWS_CLOUDHSM_PKCS11_LIBRARY` | platform default | Absolute path to the PKCS#11 library |
| `slot` | `AWS_CLOUDHSM_SLOT` | `0` | PKCS#11 slot index |
| `classicLoadBalancerEndpoint` | `AWS_CLOUDHSM_CLB_ENDPOINT` | — | Optional CLB endpoint (informational) |

The HSM user password itself is **never** passed as a config field. It is always
read from the environment variable named by `hsmPasswordEnvVar` at PIN-composition
time, so it never appears in serialized configuration or stack traces.

## Usage

```kotlin
val kms = KeyManagementServices.create("cloudhsm", mapOf(
    "clusterId" to "cluster-abcd1234efg",
    "hsmUser" to "trustweave-cu",
    "region" to "us-east-1",
))
// AWS_CLOUDHSM_HSM_PASSWORD must be exported in the process env.

val keyHandle = kms.generateKey(Algorithm.P256).getOrThrow()
val signature = kms.sign(keyHandle.id, payload, Algorithm.P256).getOrThrow()
```

## Local setup

There is no local sandbox for CloudHSM. To exercise this plugin you need a real
cluster in your AWS account.

1. **Provision a CloudHSM cluster** following the
   [AWS docs](https://docs.aws.amazon.com/cloudhsm/latest/userguide/getting-started.html).
2. **Install the Client SDK 5** on the machine that will run the JVM:
   - Linux: `sudo yum install ./cloudhsm-pkcs11-latest.el8.x86_64.rpm`
   - Windows: run the MSI from
     [Amazon's distribution](https://docs.aws.amazon.com/cloudhsm/latest/userguide/install-and-configure-client-mac.html).
3. **Bootstrap the client config** so it discovers the cluster:
   ```bash
   sudo /opt/cloudhsm/bin/configure-pkcs11 --cluster-id cluster-abcd1234efg
   ```
4. **Activate the cluster** and **create a Crypto User** with `cloudhsm-cli`:
   ```bash
   cloudhsm-cli cluster activate
   cloudhsm-cli user create --username trustweave-cu --role crypto-user --password ...
   ```
5. **Export environment variables** before launching the JVM:
   ```bash
   export AWS_CLOUDHSM_CLUSTER_ID=cluster-abcd1234efg
   export AWS_CLOUDHSM_HSM_USER=trustweave-cu
   export AWS_CLOUDHSM_HSM_PASSWORD=<the CU password>
   export AWS_CLOUDHSM_PKCS11_LIBRARY=/opt/cloudhsm/lib/libcloudhsm_pkcs11.so
   export AWS_REGION=us-east-1
   ```

## Running the integration tests

```
./gradlew :kms:plugins:cloudhsm:test
```

The integration test class
[`CloudHsmKeyManagementServiceIntegrationTest`](src/test/kotlin/org/trustweave/kms/cloudhsm/CloudHsmKeyManagementServiceIntegrationTest.kt)
is annotated with `@EnabledIfEnvironmentVariable` for the four required vars, so
it auto-skips when they are unset.

When the env is set, the test:

1. Calls `DescribeClusters` and asserts the cluster is `ACTIVE`.
2. Generates a P-256 key on the HSM.
3. Signs a payload and verifies the signature.
4. Deletes the key and confirms it is gone.

## References

- [AWS CloudHSM User Guide](https://docs.aws.amazon.com/cloudhsm/latest/userguide/)
- [CloudHSM Client SDK 5](https://docs.aws.amazon.com/cloudhsm/latest/userguide/client-sdk-5-overview.html)
- [PKCS#11 mechanism support matrix](https://docs.aws.amazon.com/cloudhsm/latest/userguide/pkcs11-library.html)
- [CloudHsmV2 API reference](https://docs.aws.amazon.com/cloudhsm/latest/APIReference/Welcome.html)
