# Documentation Navigation Guide

This document explains the structure and organization of TrustWeave's documentation, following the four-pillar documentation architecture.

## Four Pillars

TrustWeave documentation is organized into four main pillars:

### 1. Getting Started (nav_order: 10-20)
**Purpose:** Help new users get up and running quickly

**Contents:**
- Installation and setup
- Quick start guides
- First application tutorials
- Common patterns
- Troubleshooting

**When to use:** You're new to TrustWeave or setting up a new project.

**Key Pages:**
- [Installation](getting-started/installation.md) - Add TrustWeave to your project
- [Quick Start](getting-started/quick-start.md) - Your first credential in 5 minutes
- [Your First Application](getting-started/your-first-application.md) - Complete example
- [Common Patterns](getting-started/common-patterns.md) - Best practices

### 2. Core Concepts (nav_order: 20-30)
**Purpose:** Explain what TrustWeave concepts are and why they exist

**Contents:**
- Decentralized Identifiers (DIDs)
- Verifiable Credentials
- Wallets
- Trust Registry
- Blockchain Anchoring
- Key Management

**When to use:** You want to understand the fundamentals and design principles.

**Key Pages:**
- [Mental Model](introduction/mental-model.md) - How TrustWeave works conceptually
- [DIDs](core-concepts/dids.md) - Understanding decentralized identity
- [Verifiable Credentials](core-concepts/verifiable-credentials.md) - Understanding credentials
- [Trust Registry](core-concepts/trust-registry.md) - Understanding trust relationships

### 3. How-To Guides (nav_order: 30-40)
**Purpose:** Step-by-step instructions for completing specific tasks

**Contents:**
- Task-oriented guides
- Practical examples
- Step-by-step instructions
- Use case scenarios

**When to use:** You need to accomplish a specific task or solve a specific problem.

**Key Pages:**
- [Use TrustWeave Facade](how-to/use-trustweave-facade.md) - Quick setup
- [Create DIDs](how-to/create-dids.md) - DID management
- [Issue Credentials](how-to/issue-verifiable-credential.md) - Credential issuance
- [Verify Credentials](how-to/verify-credentials.md) - Credential verification
- [Use Case Scenarios](scenarios/README.md) - Real-world examples

### 4. API Reference (nav_order: 60+)
**Purpose:** Complete documentation of all methods, parameters, and types

**Contents:**
- Method signatures
- Parameter descriptions
- Return types
- Error handling
- Code examples

**When to use:** You need to look up specific API details or understand method parameters.

**Key Pages:**
- [Core API](api-reference/core-api.md) - TrustWeave facade API
- [Wallet API](api-reference/wallet-api.md) - Wallet operations
- [Credential Service API](api-reference/credential-service-api.md) - Credential service SPI

## Additional Sections

### Introduction (nav_order: 1-9)
**Purpose:** High-level overview and orientation

**Contents:**
- What is TrustWeave?
- Key features
- Use cases
- Architecture overview

### Use Case Scenarios (nav_order: 40)
**Purpose:** Complete end-to-end workflows for real-world use cases

**Contents:**
- Academic credentials
- Employee onboarding
- Healthcare records
- Financial services (KYC)
- Government identity
- And 20+ more scenarios

**When to use:** You want to see how TrustWeave is used in practice for specific domains.

### Advanced Topics (nav_order: 50+)
**Purpose:** Advanced patterns, customization, and optimization

**Contents:**
- Custom adapters
- Performance optimization
- Error handling patterns
- Plugin development
- Testing strategies

**When to use:** You're building advanced features or customizing TrustWeave.

## Navigation Flow

### For New Users
1. **Introduction** → Learn what TrustWeave is
2. **Getting Started** → Install and create your first credential
3. **Core Concepts** → Understand the fundamentals
4. **How-To Guides** → Complete specific tasks
5. **API Reference** → Look up method details

### For Experienced Users
1. **How-To Guides** → Find the task you need
2. **Use Case Scenarios** → See real-world examples
3. **API Reference** → Look up specific methods
4. **Advanced Topics** → Customize and optimize

### For Contributors
1. **Contributing Guide** → Development setup
2. **Code Style Guide** → Coding standards
3. **Testing Guidelines** → Testing requirements
4. **Plugin Development** → Creating plugins

## Finding What You Need

### By Task
- **Install TrustWeave** → [Installation](getting-started/installation.md)
- **Create a DID** → [Create DIDs](how-to/create-dids.md)
- **Issue a Credential** → [Issue Credentials](how-to/issue-verifiable-credential.md)
- **Verify a Credential** → [Verify Credentials](how-to/verify-credentials.md)
- **Store Credentials** → [Manage Wallets](how-to/manage-wallets.md)
- **Anchor to Blockchain** → [Blockchain Anchoring](how-to/blockchain-anchoring.md)

### By Concept
- **What is a DID?** → [DIDs](core-concepts/dids.md)
- **What is a Verifiable Credential?** → [Verifiable Credentials](core-concepts/verifiable-credentials.md)
- **What is a Wallet?** → [Wallets](core-concepts/wallets.md)
- **What is Trust Registry?** → [Trust Registry](core-concepts/trust-registry.md)

### By Use Case
- **Education** → [Academic Credentials Scenario](scenarios/academic-credentials-scenario.md)
- **Healthcare** → [Healthcare Records Scenario](scenarios/healthcare-medical-records-scenario.md)
- **Finance** → [KYC Scenario](scenarios/financial-services-kyc-scenario.md)
- **Government** → [Digital Identity Scenario](scenarios/government-digital-identity-scenario.md)

## Navigation Tips

1. **Use the search** - Most documentation systems have search functionality
2. **Check the README** - Each section has a README explaining its contents
3. **Follow links** - Related topics are linked throughout the documentation
4. **Check scenarios** - Use case scenarios show complete workflows
5. **Refer to API docs** - When you need method details, go to API Reference

## Documentation Principles

1. **Progressive Disclosure** - Start simple, add complexity gradually
2. **Task-Oriented** - Focus on what users need to accomplish
3. **Example-Driven** - Every guide includes working code examples
4. **Context-Aware** - Each page explains why and when to use features
5. **Cross-Referenced** - Related topics are linked throughout

## Feedback

If you find navigation confusing or can't find what you need, please:
- Check the [FAQ](faq.md) for common questions
- Use the search functionality
- Review the [Getting Started](getting-started/README.md) section
- Open an issue on GitHub with suggestions

