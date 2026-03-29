package org.trustweave.awskms

/**
 * AWS KMS-specific option key constants.
 *
 * These constants are used when configuring AWS KMS keys and operations.
 * For cross-provider generic keys (e.g., keyId, description) use
 * [org.trustweave.kms.KmsOptionKeys].
 */
object AwsKmsOptionKeys {
    /** AWS region identifier (e.g., "us-east-1"). */
    const val REGION = "region"

    /** AWS IAM access key ID for explicit credential configuration. */
    const val ACCESS_KEY_ID = "accessKeyId"

    /** AWS IAM secret access key for explicit credential configuration. */
    const val SECRET_ACCESS_KEY = "secretAccessKey"

    /** AWS STS session token for temporary credentials. */
    const val SESSION_TOKEN = "sessionToken"

    /** Override the AWS KMS endpoint URL (useful for local testing with LocalStack). */
    const val ENDPOINT_OVERRIDE = "endpointOverride"

    /** Number of days before a scheduled key deletion takes effect (7–30). */
    const val PENDING_WINDOW_IN_DAYS = "pendingWindowInDays"

    /** Optional alias to associate with the generated key (e.g., "alias/my-key"). */
    const val ALIAS = "alias"

    /** Tags to attach to the key (Map<String, String>). */
    const val TAGS = "tags"

    /** Enable automatic annual key rotation for the generated key. */
    const val ENABLE_AUTOMATIC_ROTATION = "enableAutomaticRotation"
}
