---
title: The Story Behind TrustWeave
nav_order: 3
parent: Introduction
keywords:
  - origin story
  - why trustweave
  - history
  - motivation
  - frustration
---

# The Story Behind TrustWeave

> "The best way to learn is by doing. But what happens when the complexity of the tools gets in the way of understanding?"

## The Problem: Too Many Technologies, Too Much Confusion

Imagine you're building something that requires trust and identity. Maybe you're creating a system for universities to issue digital diplomas, or a healthcare platform that needs to verify patient credentials, or an IoT network where devices need to prove who they are.

You start researching, and immediately you're hit with a wall of acronyms and concepts:

- **Blockchain** — but which one? Ethereum? Algorand? Polygon? Bitcoin? Each has different APIs, different ways of working, different trade-offs.
- **DIDs** (Decentralized Identifiers) — but there are dozens of DID methods: `did:key`, `did:web`, `did:ion`, `did:ethr`, `did:algo`, `did:peer`, and many more. Each has its own documentation, its own quirks, its own implementation.
- **Verifiable Credentials** — W3C standards that seem straightforward, until you realize every blockchain and DID method has different ways of anchoring and verifying them.
- **KMS** (Key Management Systems) — AWS KMS? Azure Key Vault? HashiCorp Vault? Your own HSM? Each requires learning a completely different API.

You think: "Okay, I'll just pick one and learn it deeply."

But then reality hits: **What if you picked wrong?** What if you build everything on Ethereum, but then discover that Algorand would have been faster and cheaper? What if you chose `did:key` for simplicity, but later need `did:ion` for production? What if AWS KMS gets expensive, and you want to switch to Azure?

You'd have to **rewrite everything**. Months of work, down the drain.

## The Frustration: Learning vs. Building

This is where many developers get stuck. They spend weeks or months:

1. Reading documentation for blockchain X
2. Learning how DID method Y works
3. Understanding how to integrate KMS Z
4. Trying to figure out how all these pieces fit together
5. Building custom glue code to make them work
6. Realizing they might have made the wrong choice
7. Starting over with different technologies

**The core problem:** You wanted to **build something trusted** — to establish a trusted domain for your use case. But instead, you're drowning in implementation details, vendor-specific APIs, and the fear of making the wrong technology choice.

You can't experiment. You can't easily try different combinations. You can't answer the fundamental question: "What's the best setup for my specific use case?" because trying different setups means rewriting massive amounts of code.

## The Realization: The Purpose Was Lost

Here's what hit us hardest: **We had lost sight of the bigger picture.**

All these technologies — blockchains, DIDs, verifiable credentials, KMS — they're all just **tools**. Their collective purpose is simple:

> **To establish trust and identity in digital interactions, without relying on a central authority.**

That's it. That's the high-level purpose. But when you're buried in blockchain transaction fees, DID document structures, and key rotation policies, that purpose becomes invisible. You're fighting with tools instead of solving problems.

What if there was a way to work at the **right level of abstraction**? What if you could say:

- "I want to issue a credential"
- "I want to verify someone's identity"
- "I want to anchor trust to a blockchain"

...and let the system figure out which specific technologies to use, based on your configuration?

## The Vision: Freedom to Experiment

We imagined a different approach. What if you could:

- **Switch blockchains** by changing a configuration line, not rewriting code?
- **Try different DID methods** without learning each one's API?
- **Test with in-memory keys**, then deploy with AWS KMS, then switch to Azure — all without changing your application code?
- **Experiment freely** to find the right combination for your use case?

What if the technologies became **pluggable** — like choosing a database or a web framework — instead of being hard-coded into your application architecture?

This wasn't just about convenience. It was about **freedom**:

- Freedom from vendor lock-in
- Freedom to make the right choice for your use case
- Freedom to change your mind later
- Freedom to focus on **what you're building**, not **how the tools work**

## The Birth of TrustWeave

So we built TrustWeave.

We built it with a simple idea: **Your application code should describe *what* you want to do, not *how* it gets done.**

Want to issue a credential? You write:

```kotlin
val credential = trustWeave.issue {
    credential {
        issuer(issuerDid)
        subject { 
            id(holderDid)
            "degree" to "Bachelor of Science"
        }
        type("VerifiableCredential", "UniversityDegree")
    }
}
```

That's it. No blockchain-specific code. No DID-method-specific code. No KMS-specific code. Just: "I want to issue this credential."

Under the hood, TrustWeave handles:
- Which blockchain to anchor to (if any)
- Which DID method to use
- Which keys to use for signing
- How to format the credential
- How to create the proof

**You configure the technologies. TrustWeave handles the complexity.**

## The Power: Play, Experiment, Apply

This changes everything.

### Play Without Fear

You can now **play** with different technologies:

```kotlin
// Try Ethereum
val trustWeave = TrustWeave.build {
    blockchains { "ethereum:mainnet" to ethereumClient }
    did { method(ETHR) }
}

// Switch to Algorand? Just change the config
val trustWeave = TrustWeave.build {
    blockchains { "algorand:mainnet" to algorandClient }
    did { method("algo") }
}

// Your application code? It stays exactly the same.
```

No rewrites. No fear. Just experimentation.

### Understand at the Right Level

You no longer need to understand:
- How Ethereum transactions work internally
- The exact structure of a `did:ion` DID document
- AWS KMS API calls
- How different DID methods handle key rotation

You just need to understand the **concepts**:
- What's a DID? (A decentralized identifier)
- What's a Verifiable Credential? (A tamper-evident claim)
- What's blockchain anchoring? (Putting trust data on a blockchain)
- What's a KMS? (A place to store keys securely)

TrustWeave handles the rest.

### Apply to Any Domain

This is where it gets powerful. Because TrustWeave is **domain-agnostic**, you can use it for anything:

- **Education**: Universities issuing verifiable diplomas
- **Healthcare**: Hospitals sharing patient records with patient consent
- **Supply Chain**: Tracking products from source to consumer
- **IoT**: Devices proving their identity and firmware integrity
- **Government**: Citizens holding verifiable digital IDs
- **Finance**: KYC/AML credentials that are portable across institutions
- **Your Domain**: Whatever trusted interactions you need

**The same code. Different domains. Same trust.**

## The Mission: Establish Trusted Domains

This is the real goal: **Establishing trusted domains.**

A trusted domain is where:
- Participants can verify each other's identities
- Claims can be verified without calling a central authority
- Trust relationships can be established and maintained
- Users own their credentials and can share them as needed

Whether you're building a trusted domain for:
- Academic credentials
- Professional certifications
- Medical records
- Supply chain tracking
- IoT device networks
- Or something entirely new

**TrustWeave gives you the foundation.** You focus on your domain logic. We handle the trust infrastructure.

## The Promise

TrustWeave promises you:

1. **No vendor lock-in**: Switch technologies with configuration, not code rewrites
2. **True experimentation**: Try different combinations without fear
3. **Right-level abstraction**: Work with concepts, not implementation details
4. **Domain freedom**: Apply trust to any use case
5. **Future-proof**: Adopt new technologies as they emerge

Most importantly: **You get to focus on building trusted domains, not fighting with tools.**

## Join Us

TrustWeave was born from frustration, but it's built on hope: **Hope that establishing trust in digital systems can be approachable, flexible, and empowering.**

We're building this for everyone who:
- Wants to understand trust and identity at a high level
- Needs to experiment with different technologies
- Wants to apply trust to their specific domain
- Refuses to be locked into a single vendor or technology

If that sounds like you, welcome. Let's build trusted domains together.

---

**Next Steps:**
- [What is TrustWeave?](what-is-trustweave.md) - Learn about the technical details
- [Key Features](key-features.md) - See what TrustWeave can do
- [Quick Start](../getting-started/quick-start.md) - Start building in 5 minutes
- [Use Cases](use-cases.md) - See how others are using TrustWeave

