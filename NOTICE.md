# NOTICE

## TrustWeave

Copyright (c) 2025 Geoknoesis LLC. All rights reserved.

TrustWeave is licensed under the GNU Affero General Public License v3.0 (AGPL v3.0).
See [LICENSE](LICENSE) for the full license text.

For commercial licensing inquiries, contact: licensing@geoknoesis.com

Website: https://www.geoknoesis.com

---

## Third-Party Dependencies

This product includes software developed by third parties. The following third-party
components are used in TrustWeave and are subject to their respective licenses.

### Core Runtime Dependencies

#### Kotlin and Kotlinx Libraries
- **Kotlin** - Copyright 2010-2025 JetBrains s.r.o.
  - License: Apache License 2.0
  - Website: https://kotlinlang.org/

- **Kotlinx Serialization** - Copyright 2010-2025 JetBrains s.r.o.
  - License: Apache License 2.0
  - Website: https://github.com/Kotlin/kotlinx.serialization

- **Kotlinx Coroutines** - Copyright 2010-2025 JetBrains s.r.o.
  - License: Apache License 2.0
  - Website: https://github.com/Kotlin/kotlinx.coroutines

#### HTTP and Networking
- **Ktor** - Copyright 2010-2025 JetBrains s.r.o.
  - License: Apache License 2.0
  - Website: https://ktor.io/

- **OkHttp** - Copyright 2019 Square, Inc.
  - License: Apache License 2.0
  - Website: https://square.github.io/okhttp/

#### JSON Processing
- **Jackson** - Copyright 2008-2025 FasterXML, LLC
  - License: Apache License 2.0
  - Website: https://github.com/FasterXML/jackson

- **Gson** - Copyright 2008 Google Inc.
  - License: Apache License 2.0
  - Website: https://github.com/google/gson

#### Cryptographic Libraries
- **BouncyCastle** - Copyright 2000-2025 The Legion of the Bouncy Castle Inc.
  - License: MIT License / BouncyCastle License
  - Website: https://www.bouncycastle.org/

- **Nimbus JOSE+JWT** - Copyright 2012-2025 Connect2id Ltd.
  - License: Apache License 2.0
  - Website: https://connect2id.com/products/nimbus-jose-jwt

#### JSON-LD Processing
- **jsonld-java** - Copyright 2013-2025 Digital Bazaar, Inc.
  - License: BSD 3-Clause License
  - Website: https://github.com/jsonld-java/jsonld-java

### Blockchain Integration Libraries

#### Ethereum and EVM-Compatible Chains
- **Web3j** - Copyright 2016 Web3 Labs Ltd.
  - License: Apache License 2.0
  - Website: https://web3j.io/

#### Bitcoin
- **Bitcoinj** - Copyright 2011-2025 The Bitcoinj developers
  - License: Apache License 2.0
  - Website: https://bitcoinj.github.io/

#### Algorand
- **Algorand SDK** - Copyright 2019-2025 Algorand Foundation
  - License: MIT License
  - Website: https://github.com/algorand/java-algorand-sdk

### Database Drivers

- **PostgreSQL JDBC Driver** - Copyright 1997-2025 PostgreSQL Global Development Group
  - License: BSD 2-Clause License
  - Website: https://jdbc.postgresql.org/

- **MySQL Connector/J** - Copyright 2015-2025 Oracle Corporation
  - License: GPL v2.0 with FOSS Exception / Commercial License
  - Website: https://dev.mysql.com/downloads/connector/j/

- **H2 Database** - Copyright 2004-2025 H2 Group
  - License: MPL 2.0 / EPL 1.0
  - Website: https://www.h2database.com/

- **HikariCP** - Copyright 2013-2025 Brett Wooldridge
  - License: Apache License 2.0
  - Website: https://github.com/brettwooldridge/HikariCP

### Cloud SDKs

#### AWS SDK
- **AWS SDK for Java** - Copyright 2010-2025 Amazon.com, Inc. or its affiliates
  - License: Apache License 2.0
  - Website: https://aws.amazon.com/sdk-for-java/

#### Azure SDK
- **Azure SDK for Java** - Copyright 2020-2025 Microsoft Corporation
  - License: MIT License
  - Website: https://github.com/Azure/azure-sdk-for-java

- **Microsoft Graph SDK** - Copyright 2015-2025 Microsoft Corporation
  - License: MIT License
  - Website: https://github.com/microsoftgraph/msgraph-sdk-java

- **Azure Identity** - Copyright 2020-2025 Microsoft Corporation
  - License: MIT License
  - Website: https://github.com/Azure/azure-sdk-for-java

#### Google Cloud SDK
- **Google Cloud Client Libraries** - Copyright 2016-2025 Google LLC
  - License: Apache License 2.0
  - Website: https://cloud.google.com/java

### HashiCorp Vault
- **Vault Java Driver** - Copyright 2015-2025 HashiCorp, Inc.
  - License: MIT License
  - Website: https://github.com/BetterCloud/vault-java-driver

### Spring Framework (Optional)
- **Spring Boot** - Copyright 2012-2025 VMware, Inc. or its affiliates
  - License: Apache License 2.0
  - Website: https://spring.io/projects/spring-boot

### Testing Frameworks

- **JUnit 5** - Copyright 2015-2025 JUnit Team
  - License: Eclipse Public License 2.0
  - Website: https://junit.org/junit5/

- **Kotest** - Copyright 2016-2025 Kotest
  - License: Apache License 2.0
  - Website: https://kotest.io/

- **Testcontainers** - Copyright 2015-2025 Testcontainers
  - License: MIT License
  - Website: https://www.testcontainers.org/

### Build Tools

- **Gradle** - Copyright 2007-2025 Gradle Inc.
  - License: Apache License 2.0
  - Website: https://gradle.org/

---

## License Summary

Most third-party dependencies use permissive open-source licenses compatible with AGPL v3.0:

- **Apache License 2.0** - Used by most dependencies (Kotlin, Ktor, Jackson, etc.)
- **MIT License** - Used by some dependencies (BouncyCastle, Algorand SDK, etc.)
- **BSD Licenses** - Used by some dependencies (PostgreSQL JDBC, jsonld-java)
- **EPL 1.0 / MPL 2.0** - Used by H2 Database
- **Eclipse Public License 2.0** - Used by JUnit 5
- **GPL v2.0 with FOSS Exception** - MySQL Connector/J (can be used under FOSS exception)

### Important Notes

1. **MySQL Connector/J**: When used with AGPL v3.0 licensed software, MySQL Connector/J is available under the GPL v2.0 license with FOSS Exception. For commercial use, please review Oracle's licensing terms.

2. **BouncyCastle**: Dual licensed under MIT License and BouncyCastle License (permissive).

3. **Dependency Verification**: For a complete and up-to-date list of all dependencies and their licenses, run:
   ```bash
   ./gradlew dependencies
   ```
   Or use license reporting tools to generate a detailed dependency license report.

---

## Acknowledgments

TrustWeave builds upon the excellent work of the open-source community. We thank all the developers and organizations who have contributed to the libraries and tools that make TrustWeave possible.

### Standards and Specifications

TrustWeave implements and follows:

- **W3C Decentralized Identifiers (DIDs) v1.0** - https://www.w3.org/TR/did-core/
- **W3C Verifiable Credentials Data Model v1.1** - https://www.w3.org/TR/vc-data-model/
- **W3C DID Method Registry** - https://www.w3.org/TR/did-spec-registries/
- **JSON-LD** - https://json-ld.org/
- **CAIP-2** - Chain Agnostic Improvement Proposal for blockchain identifiers

### Community

We are grateful to the broader decentralized identity and verifiable credentials community for their ongoing work on standards, specifications, and implementations.

---

## Questions About Licensing?

If you have questions about licensing, third-party dependencies, or need clarification on how these licenses interact:

- **Commercial Licensing**: licensing@geoknoesis.com
- **General Questions**: https://www.geoknoesis.com
- **Legal Inquiries**: Contact Geoknoesis LLC legal team

---

**Last Updated**: 2025

For the most current version of this notice, please refer to the latest release of TrustWeave.




