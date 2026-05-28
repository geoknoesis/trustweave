# TrustWeave Documentation Style Guide

This style guide ensures consistent terminology, formatting, and patterns across all TrustWeave documentation.

## Terminology Standards

### API Names

**Class Names (Capitalized)**
- Use `TrustWeave` when referring to the class itself
- Use `TrustWeave` when showing class usage in documentation text
- Example: "The `TrustWeave` class provides a unified interface for all operations."

**Configuration entrypoint**
- Use `TrustWeave.build { }` in examples; it is a suspend function that returns a `TrustWeave` facade.
- The internal config builder `trustWeave(name) { }` (lowercase) builds a `TrustWeaveConfig` only—prefer `TrustWeave.build { }` in documentation unless you are discussing config objects explicitly.

**Variable Names (camelCase)**
- Use `trustWeave` (lowercase) for variable instances
- Example: `val trustWeave = TrustWeave.build { ... }`

### Do NOT Use

- `TrustLayer` or “trust layer” as a product/API name (deprecated; use **TrustWeave**)
- `trustlayer` as a pseudo class name
- Mixed capitalization (`Trustweave`, `TRUSTWEAVE`, etc.) in prose

### Correct Usage Examples

```kotlin
// ✅ Correct: Class name capitalized
import org.trustweave.trust.TrustWeave
// Note: DSL constants (IN_MEMORY, ED25519, KEY, etc.) live in org.trustweave.trust.dsl.credential
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.DidMethods.KEY

// ✅ Correct: Variable name camelCase
val trustWeave = TrustWeave.build {
    keys { provider(IN_MEMORY); algorithm(ED25519) }
    did { method(KEY) { algorithm(ED25519) } }
}

// ✅ Correct: same DSL block via TrustWeave.build (suspend)
val trustWeave = TrustWeave.build {
    keys { provider(IN_MEMORY); algorithm(ED25519) }
    did { method(KEY) { algorithm(ED25519) } }
}
```

## Code Example Standards

### Import Statements

Always include necessary imports at the top of code examples:

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.credential.results.VerificationResult
// DSL constants live in org.trustweave.trust.dsl.credential — import specific constants you need:
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import kotlinx.coroutines.runBlocking
```

### Error Handling

Always show error handling in production examples:

```kotlin
import org.trustweave.trust.dsl.credential.DidMethods.KEY
// ✅ Correct: Show error handling
try {
    val did = trustWeave.createDid { method(KEY) }.getOrThrowDid()
} catch (error: Exception) {
    println("Error: ${error.message}")
}

// ✅ Correct: Show exhaustive when expressions
when (result) {
    is VerificationResult.Valid -> println("Valid")
    is VerificationResult.Invalid.Expired -> println("Expired")
    is VerificationResult.Invalid -> println(result.allErrors.joinToString())
}
```

For **`VerificationResult.Invalid`**, **`IssuanceResult.Failure`**, and **`PresentationResult.Failure`**, prefer **`allErrors`** for user-facing or log messages unless you need a specific subtype’s fields.

### Variable Naming

- Use descriptive names: `issuerDid`, `holderDid`, `credential`
- Avoid abbreviations: `did` (acceptable), `iss` (avoid), `cred` (avoid)

## Documentation Structure

### Page Structure

1. **Front Matter** (required)
   - `title`: Clear, descriptive title
   - `nav_order`: Sequential number (10, 20, 30, etc.)
   - `parent`: Parent section name (if nested)
   - `keywords`: Relevant search terms

2. **Introduction** (required)
   - Brief overview (1-2 paragraphs)
   - What the reader will learn

3. **Content** (required)
   - Organized with clear headings
   - Code examples at appropriate points
   - Diagrams for complex concepts

4. **Next Steps** (recommended)
   - Links to related pages
   - Suggested reading order

### Code Example Placement

- Place code examples immediately after introducing a concept
- Include complete, runnable examples
- Show expected output after examples
- Don't dump all code at the end of the page

### Diagram Standards

- Use Mermaid diagrams for flowcharts and sequences
- Include color styling for clarity
- Add descriptive labels
- Place diagrams after text introduction, before code examples

## Markdown Standards

### Headings

- Use `#` for page title (handled by front matter)
- Use `##` for main sections
- Use `###` for subsections
- Use `####` for sub-subsections (avoid if possible)

### Lists

- Use `-` for unordered lists
- Use `1.` for ordered lists
- Indent nested lists with 2 spaces

### Code Blocks

- Use triple backticks (```) for code blocks
- Always specify language (`kotlin`, `yaml`, `bash`, etc.)
- Use inline code (`` ` ``) for class names, methods, variables

### Emphasis

- Use **bold** for important terms (first mention)
- Use *italic* for emphasis (sparingly)
- Use `code` for technical terms in prose

## Link Standards

### Internal Links

- Use relative paths: `[Getting Started](getting-started/README.md)`
- Use fragments for sections: `[Quick Start](getting-started/quick-start.md#hello-trustweave)`

### External Links

- Include full URL: `[W3C VC Spec](https://www.w3.org/TR/vc-data-model/)`
- Add `{:target="_blank"}` for external links (if needed)

## Version Information

### Version References

- Always include version in quick start guides
- Format: `**Version:** 0.6.0`
- Include Kotlin/Java version requirements

### Deprecation

- Mark deprecated APIs clearly
- Provide migration path
- Include removal timeline

## Visual Standards

### Diagrams

- Use Mermaid syntax for all diagrams
- Consistent color scheme:
  - Success/Valid: `#4caf50` (green)
  - Error/Invalid: `#f44336` (red)
  - Info/Process: `#2196f3` (blue)
  - Warning: `#ff9800` (orange)
- Include style definitions for consistency

### Callouts

- Use `> **Note:**` for important notes
- Use `> **Warning:**` for warnings
- Use `> **Tip:**` for tips
- Use `> ✅` for success indicators
- Use `> ❌` for error indicators

## Navigation Order

### Four Pillars

1. **Getting Started** (nav_order: 10-19)
2. **Core Concepts** (nav_order: 20-29)
3. **How-To Guides** (nav_order: 30-39)
4. **API Reference** (nav_order: 60+)

### Other Sections

- **Introduction** (nav_order: 0-9)
- **Scenarios** (nav_order: 40+)
- **Advanced** (nav_order: 50+)
- **Reference** (nav_order: 70+)

## Quality Checklist

Before publishing documentation, ensure:

- Terminology follows style guide
- Code examples are complete and runnable
- Error handling is shown for production code
- Diagrams are present for complex concepts
- Links are correct (internal and external)
- Version information is current
- Navigation order is correct
- Front matter is complete
- Next steps are provided
- Spelling and grammar are correct

---

**Last Updated:** January 2025  
**Maintainer:** TrustWeave Documentation Team
