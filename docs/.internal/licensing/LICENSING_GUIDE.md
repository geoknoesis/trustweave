---
title: TrustWeave Licensing Guide (Internal)
nav_exclude: true
---

# TrustWeave Licensing Guide (Internal)

> **Internal Document** - Comprehensive guide for sales, support, and business development teams

**Last Updated:** 2025-01-XX
**Status:** Active
**Audience:** Sales, Support, Legal, Business Development

---

## Executive Summary

TrustWeave uses a **dual-license model** that balances open-source community growth with commercial revenue:

1. **AGPL v3.0 (Community License)** - Free, open-source license for non-commercial use
2. **Commercial License** - Paid license for proprietary/commercial use

**Key Insight:** The source code is publicly available on GitHub under AGPL v3.0. The commercial license doesn't provide "access" to source code (it's already public), but rather the **right to use TrustWeave in proprietary software without copyleft obligations** and includes commercial support, indemnification, and other enterprise benefits.

---

## Table of Contents

1. [Dual Licensing Model Overview](#dual-licensing-model-overview)
2. [AGPL v3.0 (Community License)](#agpl-v30-community-license)
3. [Commercial License](#commercial-license)
4. [Pricing Tiers](#pricing-tiers)
5. [License Decision Matrix](#license-decision-matrix)
6. [Sales Guidance](#sales-guidance)
7. [Common Scenarios & Questions](#common-scenarios--questions)
8. [Legal Considerations](#legal-considerations)
9. [Support & Resources](#support--resources)

---

## Dual Licensing Model Overview

### Why Dual Licensing?

**Business Strategy:**
- **Open Source (AGPL)**: Maximizes adoption, community growth, and developer mindshare
- **Commercial License**: Generates revenue from enterprises that need proprietary use rights

**Market Positioning:**
- AGPL v3.0 is a strong copyleft license that requires derivative works to be open-sourced
- This creates a natural conversion path: companies using TrustWeave commercially must purchase a commercial license
- The open-source availability builds trust and reduces sales friction

### How It Works

```
┌─────────────────────────────────────────────────────────┐
│                    TrustWeave SDK                       │
│              (Source code on GitHub)                    │
└─────────────────────────────────────────────────────────┘
                        │
        ┌───────────────┴───────────────┐
        │                               │
        ▼                               ▼
┌───────────────┐              ┌──────────────────┐
│  AGPL v3.0    │              │  Commercial      │
│  (Free)       │              │  License         │
│               │              │  (Paid)          │
├───────────────┤              ├──────────────────┤
│ Non-commercial│              │ Proprietary use  │
│ Educational   │              │ Enterprise       │
│ Open source   │              │ SaaS             │
│ Research      │              │ Production       │
└───────────────┘              └──────────────────┘
```

### Key Distinction: Source Code Access

**Important Clarification for Sales Teams:**

The source code is **already publicly available** on GitHub. When we say "source code access" in the Enterprise tier, we mean:

1. **Right to modify and keep modifications proprietary** (not required to open-source)
2. **Support for custom modifications** (technical assistance)
3. **Custom development services** (GeoKnoesis can build custom features)
4. **Early access to development versions** (if applicable)

**NOT** just the ability to view the code (which is already public).

---

## AGPL v3.0 (Community License)

### What is AGPL v3.0?

The GNU Affero General Public License v3.0 is a strong copyleft license that:
- Requires derivative works to be open-sourced
- Requires network service providers to make source code available
- Ensures modifications remain free and open

### Who Can Use AGPL v3.0?

✅ **Permitted Uses:**
- Open-source projects (OSI-approved licenses)
- Educational institutions and research
- Personal/non-commercial projects
- Proof-of-concept and prototyping (non-production)
- Internal development and testing (non-revenue generating)

❌ **NOT Permitted:**
- Proprietary/commercial software
- SaaS applications (unless you open-source your entire application)
- Production systems supporting revenue-generating activities
- Enterprise deployments with proprietary code

### AGPL v3.0 Requirements

If using AGPL v3.0, you must:
1. **Open-source your modifications** - Any changes to TrustWeave must be released under AGPL v3.0
2. **Open-source derivative works** - Applications that incorporate TrustWeave must be open-sourced
3. **Provide source code** - If you run a network service using TrustWeave, you must provide source code to users
4. **Maintain copyright notices** - Cannot remove attribution or license notices

### Why AGPL v3.0?

- **Strong copyleft** creates natural conversion to commercial license
- **Network service provision** clause catches SaaS applications
- **Community-friendly** for legitimate open-source use cases
- **Industry standard** - well-understood by legal teams

---

## Commercial License

### What is the Commercial License?

A proprietary license that grants rights to:
- Use TrustWeave in proprietary/commercial software
- Modify TrustWeave and keep modifications proprietary
- Distribute TrustWeave as part of commercial products
- Avoid AGPL copyleft obligations

### Who Needs a Commercial License?

**Required for:**
- ✅ Proprietary/commercial software
- ✅ SaaS applications (where software isn't distributed to end users)
- ✅ Enterprise production deployments
- ✅ Internal systems supporting revenue-generating activities
- ✅ Products/services sold for a fee
- ✅ Consultancies deploying TrustWeave for clients

**Not Required for:**
- ❌ Open-source projects
- ❌ Educational/research use
- ❌ Personal/non-commercial projects
- ❌ Proof-of-concept (non-production)

### Commercial License Benefits

#### All Tiers Include:
- ✅ **No Copyleft Obligations** - Use in proprietary software without open-sourcing
- ✅ **Priority Technical Support** - Guaranteed response times
- ✅ **Regular Updates** - All updates, features, and security patches
- ✅ **Training & Documentation** - Commercial training materials
- ✅ **Custom Integration Assistance** - Help with integration

#### Professional & Enterprise Tiers Add:
- ✅ **Indemnification** - Protection against IP claims
- ✅ **Enhanced Support** - Faster response times, dedicated support

#### Enterprise Tier Adds:
- ✅ **Right to Modify Proprietarily** - Keep custom modifications private
- ✅ **Support for Custom Modifications** - Technical assistance for custom code
- ✅ **Custom Development Services** - GeoKnoesis can build custom features
- ✅ **Source Code Modifications** - Full rights to modify and maintain custom versions
- ✅ **24/7 Dedicated Support** - 1-hour SLA
- ✅ **Professional Services** - Custom development and consulting

### Commercial License Restrictions

- **No Removal of Notices** - Must maintain copyright and attribution
- **No Competing Products** - Cannot build products that directly compete with TrustWeave core
- **No Transfer** - License is non-transferable without written consent
- **No Sublicensing** - Cannot sublicense, rent, lease, or lend to third parties
- **No Reverse Engineering** - Cannot reverse engineer or decompile (except as permitted by law)

---

## Pricing Tiers

### Self-Hosted Commercial License

#### Basic License: $5,000/year

**Target Audience:**
- Small teams and startups
- Low-to-medium volume deployments
- Companies just starting with TrustWeave

**Includes:**
- Up to 8 CPU cores
- 2 environments (Production + Staging)
- Email support (2 business days response)
- All updates and security patches
- No source code modification rights (must use as-is)

**Best For:**
- Startups with limited budget
- Small-scale production deployments
- Companies evaluating TrustWeave for broader deployment

---

#### Professional License: $15,000/year

**Target Audience:**
- Growing companies
- Medium-scale deployments
- Production-critical applications

**Includes:**
- Up to 32 CPU cores
- Unlimited environments
- Priority support (4-hour response time)
- All updates and security patches
- Indemnification protection
- No source code modification rights (must use as-is)

**Best For:**
- Companies with established production use
- Multiple environments (dev, staging, production)
- Need for faster support response
- Regulatory compliance requirements

---

#### Enterprise License: $50,000+/year (negotiable)

**Target Audience:**
- Large enterprises
- High-volume deployments
- Mission-critical systems
- Custom requirements

**Includes:**
- Unlimited CPU cores
- Unlimited environments
- 24/7 dedicated support (1-hour SLA)
- All updates and security patches
- **Right to modify and keep modifications proprietary**
- **Support for custom modifications**
- **Custom development services**
- Full source code access (modification rights)
- Indemnification protection
- Professional services included
- Technical account manager

**Best For:**
- Fortune 500 enterprises
- High-volume transaction processing
- Custom feature requirements
- Air-gapped or highly regulated environments
- Need for custom modifications

**Pricing Notes:**
- Base price: $50,000/year
- Can scale to $100K-$150K+ for very large deployments
- Custom pricing for multi-year agreements
- Volume discounts available

---

### SaaS (TrustWeave Cloud) Pricing

**Note:** This is separate from self-hosted licensing. See `docs/.internal/saas/PRICING_STRATEGY.md` for details.

**Quick Reference:**
- **Free**: $0/month (1,000 API calls/month)
- **Starter**: $49/month (10,000 API calls/month)
- **Pro**: $149/month (100,000 API calls/month)
- **Enterprise**: Custom pricing

**Key Point:** SaaS customers don't need a separate commercial license - the SaaS subscription includes commercial use rights.

---

## License Decision Matrix

### Quick Decision Tree

```
Is your use case commercial/proprietary?
│
├─ NO → Use AGPL v3.0 (Free)
│   │
│   └─ Is it open-source, educational, or research?
│       └─ YES → AGPL v3.0 is correct
│
└─ YES → Commercial License Required
    │
    ├─ Do you need to modify TrustWeave source code?
    │   │
    │   ├─ YES → Enterprise License ($50K+)
    │   │
    │   └─ NO → Basic or Professional License
    │       │
    │       ├─ Small team, <8 cores → Basic ($5K)
    │       │
    │       └─ Growing company, <32 cores → Professional ($15K)
    │
    └─ Do you prefer managed service?
        └─ YES → TrustWeave Cloud (SaaS)
```

### Detailed Scenarios

| Scenario | License Needed | Tier | Reasoning |
|----------|---------------|------|-----------|
| University research project | AGPL v3.0 | Free | Non-commercial, educational |
| Open-source DID wallet | AGPL v3.0 | Free | Open-source project |
| Startup building SaaS with TrustWeave | Commercial | Basic/Pro | Commercial use, proprietary code |
| Enterprise production system | Commercial | Professional/Enterprise | Commercial use, production |
| Need custom TrustWeave features | Commercial | Enterprise | Requires modification rights |
| Air-gapped government deployment | Commercial | Enterprise | Custom security requirements |
| Consultancy deploying for clients | Commercial | Professional/Enterprise | Commercial services |
| Internal testing (non-production) | AGPL v3.0 | Free | Non-revenue generating |
| Proof-of-concept demo | AGPL v3.0 | Free | Non-production |
| Production system supporting revenue | Commercial | Basic+ | Revenue-generating activity |

---

## Sales Guidance

### Qualification Questions

**1. Use Case Assessment:**
- "What are you building with TrustWeave?"
- "Is this for production use or proof-of-concept?"
- "Will this support revenue-generating activities?"

**2. Deployment Assessment:**
- "Where will you deploy TrustWeave? (Cloud, on-premise, hybrid)"
- "How many environments do you need? (Dev, staging, production)"
- "What's your expected transaction volume?"

**3. Customization Needs:**
- "Do you need to modify TrustWeave source code?"
- "Do you have custom feature requirements?"
- "Do you need custom integrations?"

**4. Support Requirements:**
- "What support response time do you need?"
- "Do you need 24/7 support?"
- "Do you need dedicated technical account management?"

### Common Objections & Responses

**Objection: "Why do we need a license? The code is free on GitHub."**

**Response:**
"The code is free for non-commercial use under AGPL v3.0. However, AGPL requires that if you use TrustWeave in a commercial application, you must open-source your entire application. The commercial license gives you the right to use TrustWeave in proprietary software without that requirement, plus you get enterprise support, indemnification, and other benefits."

**Objection: "Can't we just use the open-source version?"**

**Response:**
"You can use the open-source version if you're willing to open-source your entire application under AGPL v3.0. If you need to keep your code proprietary, you'll need a commercial license. Also, the commercial license includes priority support, security patches, and indemnification - important for production systems."

**Objection: "The price seems high for just a library."**

**Response:**
"TrustWeave is infrastructure software that handles critical identity and trust operations. The commercial license includes not just the right to use it, but also enterprise support, security updates, indemnification, and the ability to keep your modifications proprietary. For production systems, this is essential. We also offer flexible payment terms and volume discounts."

**Objection: "We're just evaluating - can we start with the free version?"**

**Response:**
"Absolutely! You can use AGPL v3.0 for evaluation and proof-of-concept. Once you move to production with proprietary code, you'll need a commercial license. We can help you transition when you're ready."

### Upselling Strategies

**Basic → Professional:**
- "As you scale, you'll need faster support response times"
- "Multiple environments require Professional tier"
- "Indemnification is important for production systems"

**Professional → Enterprise:**
- "Do you need to customize TrustWeave for your specific requirements?"
- "Enterprise tier includes custom modification rights and support"
- "24/7 support with 1-hour SLA for mission-critical systems"

**Self-Hosted → SaaS:**
- "TrustWeave Cloud eliminates infrastructure management"
- "We handle updates, scaling, and security patches"
- "Starting at $49/month with no infrastructure costs"

---

## Common Scenarios & Questions

### Scenario 1: SaaS Application

**Question:** "We're building a SaaS application that uses TrustWeave. Do we need a license?"

**Answer:** Yes, you need a commercial license. SaaS applications are commercial use, and AGPL v3.0 would require you to open-source your entire application.

**Recommended Tier:** Professional or Enterprise, depending on scale and customization needs.

---

### Scenario 2: Internal Enterprise System

**Question:** "We're using TrustWeave for an internal system that doesn't generate revenue. Do we need a license?"

**Answer:** If the system supports revenue-generating activities (even indirectly), you need a commercial license. If it's truly non-revenue generating (e.g., internal research), AGPL v3.0 may be sufficient. When in doubt, consult with legal/licensing team.

**Recommended Tier:** Basic or Professional, depending on scale.

---

### Scenario 3: Open-Source Project

**Question:** "We're building an open-source project that uses TrustWeave. Do we need a license?"

**Answer:** No, you can use AGPL v3.0. As long as your project is open-source (OSI-approved license), you're covered.

---

### Scenario 4: Custom Modifications

**Question:** "We need to modify TrustWeave to add custom features. What license do we need?"

**Answer:** You need the Enterprise license, which includes the right to modify TrustWeave and keep modifications proprietary. Basic and Professional tiers don't include modification rights.

---

### Scenario 5: Consultancy/System Integrator

**Question:** "We deploy TrustWeave for our clients. Do we need a license?"

**Answer:** Yes, you need a commercial license. Each client deployment requires a license. Consider Professional or Enterprise tier depending on number of clients and support needs.

**Note:** We may offer partner/reseller programs for consultancies. Contact sales for details.

---

### Scenario 6: Educational Institution

**Question:** "We're a university using TrustWeave for research. Do we need a license?"

**Answer:** No, educational and research use is covered by AGPL v3.0. However, if you're commercializing research or using in production systems, you'll need a commercial license.

---

### Scenario 7: Government/Air-Gapped

**Question:** "We're a government agency with air-gapped requirements. What license do we need?"

**Answer:** You need a commercial license (Enterprise tier recommended). Enterprise tier includes custom modification rights and support for air-gapped deployments.

---

## Legal Considerations

### AGPL v3.0 Compliance

**Key Points:**
- AGPL v3.0 is a strong copyleft license
- Any derivative work must be open-sourced
- Network service providers must provide source code
- Modifications must be released under AGPL v3.0

**Risk Assessment:**
- Using TrustWeave in proprietary software without a commercial license violates AGPL v3.0
- This could result in legal action and requirement to open-source proprietary code
- Commercial license eliminates this risk

### Commercial License Terms

**Important Terms:**
- **Non-transferable**: License cannot be transferred without written consent
- **Non-sublicensable**: Cannot sublicense to third parties
- **Term**: Typically annual subscription (perpetual available)
- **Termination**: Automatic termination on breach of terms

**Indemnification:**
- Professional and Enterprise tiers include indemnification
- Protects against IP claims related to TrustWeave
- Important for enterprise customers

### Export Compliance

- TrustWeave may be subject to export control laws
- Customers responsible for compliance
- May restrict use in certain jurisdictions

### Governing Law

- Commercial license agreement specifies governing law
- Typically based on GeoKnoesis LLC jurisdiction
- Custom terms negotiable for large enterprise deals

---

## Support & Resources

### Internal Resources

- **Sales Team**: sales@geoknoesis.com
- **Legal Team**: legal@geoknoesis.com
- **Licensing Inquiries**: licensing@geoknoesis.com
- **Support**: support@geoknoesis.com

### Customer-Facing Resources

- **Public Documentation**: `docs/licensing/README.md`
- **Commercial License Terms**: `LICENSE-COMMERCIAL.md`
- **Pricing Page**: https://geoknoesis.com/licensing
- **Contact**: licensing@geoknoesis.com

### Escalation Path

1. **Sales Questions** → Sales team
2. **Legal Questions** → Legal team
3. **Technical Licensing Questions** → Engineering + Legal
4. **Custom Terms** → Legal + Sales

### License Agreement Templates

- Standard commercial license agreement (Legal team)
- Enterprise custom terms template (Legal team)
- Partner/reseller agreements (Legal team)

---

## Appendix: Quick Reference

### License Comparison

| Feature | AGPL v3.0 | Commercial License |
|---------|-----------|-------------------|
| **Cost** | Free | $5K-$50K+/year |
| **Proprietary Use** | ❌ No | ✅ Yes |
| **Modify & Keep Private** | ❌ No | ✅ Yes (Enterprise) |
| **Support** | Community | Priority/24/7 |
| **Indemnification** | ❌ No | ✅ Yes (Pro/Enterprise) |
| **Updates** | Public releases | Guaranteed access |
| **Custom Development** | ❌ No | ✅ Yes (Enterprise) |

### Pricing Quick Reference

| Tier | Price | Cores | Support | Modifications |
|------|-------|-------|---------|---------------|
| Basic | $5K/year | 8 | 2 days | ❌ |
| Professional | $15K/year | 32 | 4 hours | ❌ |
| Enterprise | $50K+/year | Unlimited | 1 hour | ✅ |

### Decision Checklist

- [ ] Is use case commercial/proprietary?
- [ ] Is it production or proof-of-concept?
- [ ] Do you need to modify source code?
- [ ] What support level is required?
- [ ] How many environments/cores needed?
- [ ] Do you need indemnification?

---

**Document Owner:** Business Development / Legal
**Review Frequency:** Quarterly
**Last Review:** 2025-10-XX
**Next Review:** 2025-12-XX

