# Security Policy

## Supported Versions

TrustWeave provides security updates for the following versions:

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

We recommend using the latest stable release to ensure you receive security updates and bug fixes.

## Reporting a Vulnerability

We take the security of TrustWeave seriously. If you believe you have found a security vulnerability, please report it to us as described below.

### Please Do NOT:

- ❌ Open a public GitHub issue for security vulnerabilities
- ❌ Share the vulnerability publicly until it has been resolved
- ❌ Use the vulnerability for malicious purposes

### Please DO:

- ✅ Report the vulnerability privately using one of the methods below
- ✅ Provide detailed information about the vulnerability
- ✅ Allow us reasonable time to address the vulnerability before disclosure

### How to Report

**Email:** security@geoknoesis.com

**Preferred Format:** Include the following information in your report:

1. **Type of vulnerability** (e.g., authentication bypass, injection, etc.)
2. **Affected component** (module, class, or feature)
3. **Description** of the vulnerability
4. **Steps to reproduce** (detailed steps to exploit the vulnerability)
5. **Potential impact** (what could an attacker do?)
6. **Suggested fix** (if you have ideas)
7. **Proof of concept** (code, screenshots, or links if applicable)

### What to Expect

After you submit a security report:

1. **Acknowledgment**: You will receive an acknowledgment within **48 hours**
2. **Initial Assessment**: We will perform an initial assessment within **7 days**
3. **Updates**: We will provide periodic updates on the status of the vulnerability
4. **Resolution**: We will work to resolve the vulnerability as quickly as possible
5. **Disclosure**: After a fix is available, we will coordinate disclosure with you

### Response Timeline

- **48 hours**: Initial acknowledgment
- **7 days**: Initial assessment and severity classification
- **30 days**: Status update (if not yet resolved)
- **90 days**: Target resolution for critical vulnerabilities
- **Disclosure**: Coordinated after fix is available (typically 7-30 days after release)

*Note: Complex vulnerabilities may take longer to resolve. We will keep you informed of progress.*

## Scope

### In Scope

We welcome reports about security vulnerabilities in:

- ✅ TrustWeave core libraries and modules
- ✅ Authentication and authorization mechanisms
- ✅ Cryptographic operations and key management
- ✅ DID creation, resolution, and verification
- ✅ Verifiable Credential issuance and verification
- ✅ Blockchain anchoring operations
- ✅ API endpoints and network communications
- ✅ Plugin system security
- ✅ Dependency vulnerabilities that affect TrustWeave

### Out of Scope

The following are generally considered out of scope:

- ❌ Denial of Service (DoS) attacks (rate limiting should be handled by your application)
- ❌ Social engineering attacks
- ❌ Physical security issues
- ❌ Issues in third-party dependencies that don't directly affect TrustWeave
- ❌ Issues requiring physical access to the device
- ❌ Issues in experimental or deprecated features
- ❌ Missing security headers (unless they lead to a direct vulnerability)
- ❌ Self-XSS or issues that require user interaction
- ❌ Issues in example code or documentation (unless exploitable in production)

*If you're unsure whether a vulnerability is in scope, please report it and we'll assess it.*

## Security Best Practices

### For Users

- **Keep TrustWeave Updated**: Always use the latest stable version
- **Secure Key Management**: Use production-grade KMS (AWS KMS, Azure Key Vault, Google Cloud KMS, HashiCorp Vault)
- **Encrypt Credential Storage**: Never store credentials in plain text
- **Validate Credentials**: Always verify credentials before trusting them
- **Use TLS/HTTPS**: Encrypt all network communications
- **Implement Rate Limiting**: Protect against abuse in your applications
- **Follow Principle of Least Privilege**: Grant minimum required permissions
- **Regular Security Audits**: Review your implementation regularly
- **Secure Configuration**: Use secure defaults and review configuration options

### For Contributors

- **Review Security Implications**: Consider security when designing features
- **Secure Coding Practices**: Follow secure coding guidelines
- **Dependency Updates**: Keep dependencies up to date
- **Security Testing**: Include security considerations in tests
- **Documentation**: Document security-relevant features and limitations

For detailed security guidance, see [Security Documentation](docs/security/README.md).

## Security Updates

Security updates are released through:

1. **GitHub Releases**: Tagged releases with security fixes
2. **Maven Central**: Updated artifacts in Maven Central
3. **Security Advisories**: GitHub Security Advisories for tracked vulnerabilities
4. **Release Notes**: Detailed information in [CHANGELOG.md](CHANGELOG.md)

## Vulnerability Disclosure

After a vulnerability is fixed:

1. **Fix Release**: A new version is released with the security fix
2. **Security Advisory**: A GitHub Security Advisory is published (if applicable)
3. **Release Notes**: The vulnerability and fix are documented in release notes
4. **CVE Assignment**: CVEs are assigned for significant vulnerabilities
5. **Coordinated Disclosure**: We coordinate with reporters before public disclosure

## Hall of Fame

We recognize security researchers who responsibly disclose vulnerabilities. Contributors who report valid security issues will be:

- Listed in our security acknowledgments (if desired)
- Credited in security advisories
- Thanked for helping keep TrustWeave secure

## Responsible Disclosure

We ask that security researchers:

- Act in good faith and avoid accessing or modifying data that does not belong to you
- Respect user privacy and data protection
- Not disrupt production systems
- Not violate any laws or breach any agreements
- Allow reasonable time for fixes before disclosure

## Additional Resources

- [Security Documentation](docs/security/README.md) - Detailed security guidance
- [Contributing Guide](CONTRIBUTING.md) - General contribution guidelines
- [Code of Conduct](CODE_OF_CONDUCT.md) - Community standards
- [W3C Security Considerations](https://www.w3.org/TR/vc-data-model/#security-considerations) - W3C VC security guidance

## Contact

**Security Issues:** security@geoknoesis.com

**General Support:** https://www.geoknoesis.com

**GitHub Issues:** For non-security issues, please use [GitHub Issues](https://github.com/geoknoesis/trustweave/issues)

---

**Thank you for helping keep TrustWeave and its users safe!**




