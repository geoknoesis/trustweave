# Advanced Topics

This section is aimed at teams who are moving past the quick starts and now need to harden production deployments. Each guide assumes you already understand the core DID / credential lifecycle and want to fine-tune behaviour through typed options, SPI hooks, or operational playbooks.

Start with the published deep dives:

- [Key Rotation](key-rotation.md) — how to plan, automate, and test cryptographic key rollovers using the same registries and KMS interfaces that power the facade. Ideal for security engineers and ops teams.
- [Verification Policies](verification-policies.md) — modelling advanced validation rules (anchors, revocation, domain checks) with `CredentialVerificationOptions` and interpreting the structured `CredentialVerificationResult`.

Upcoming additions will cover SPI extension points, adapter authoring, test strategies, and performance tuning. If you want to prioritise a topic—or contribute your own guide—open an issue or follow the workflow in the [Contributing Guide](../contributing/README.md).

