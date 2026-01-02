# Credential API Performance Characteristics

## Overview

This document describes the performance characteristics of key operations in the `credential-api` module, including expected execution times, resource usage, and scalability considerations.

## Performance Metrics

### Credential Issuance

**Operation:** `CredentialService.issue()`

**Time Complexity:** O(n) where n is the number of claims
- Input validation: O(1)
- Proof generation: O(n) for canonicalization and signing
- Overall: O(n)

**Space Complexity:** O(n)
- Credential object: O(n) where n is the number of claims
- Proof generation: O(n) for canonicalized document

**Typical Execution Times:**
- Small credential (10 claims): ~50-100ms
- Medium credential (100 claims): ~200-500ms
- Large credential (1000 claims): ~1-3s

**Bottlenecks:**
- JSON-LD canonicalization (VC-LD): Most expensive operation
- Cryptographic signing: Moderate overhead
- DID resolution (if needed): Network latency

**Optimization Opportunities:**
- Cache canonicalized documents if same credential is reissued
- Batch issuance for multiple credentials
- Async DID resolution with caching

---

### Credential Verification

**Operation:** `CredentialService.verify()`

**Time Complexity:** O(n) where n is the number of claims
- Input validation: O(1)
- Proof verification: O(n) for canonicalization and signature verification
- Temporal validation: O(1)
- Overall: O(n)

**Space Complexity:** O(n)
- Credential object: O(n)
- Canonicalized document: O(n)

**Typical Execution Times:**
- Small credential (10 claims): ~50-100ms
- Medium credential (100 claims): ~200-500ms
- Large credential (1000 claims): ~1-3s

**Bottlenecks:**
- DID resolution for verification method: Network latency (if not cached)
- JSON-LD canonicalization (VC-LD): Most expensive operation
- Cryptographic signature verification: Moderate overhead

**Optimization Opportunities:**
- Cache DID documents
- Cache canonicalized documents
- Parallel verification for multiple credentials
- Skip revocation checks if not required

---

### Batch Verification

**Operation:** `CredentialService.verify(List<VerifiableCredential>)`

**Time Complexity:** O(m × n) where m is number of credentials, n is average claims per credential
- Sequential verification: O(m × n)
- Parallel verification (if implemented): O(n) with O(m) parallelism

**Typical Execution Times:**
- 10 credentials (10 claims each): ~500ms-1s
- 100 credentials (10 claims each): ~5-10s (sequential)
- 100 credentials (10 claims each): ~1-2s (parallel with 10 threads)

**Optimization Opportunities:**
- Parallel verification with coroutines
- Batch DID resolution
- Shared canonicalization cache

---

### Presentation Creation

**Operation:** `CredentialService.createPresentation()`

**Time Complexity:** O(m × n) where m is number of credentials, n is average claims per credential
- Similar to batch issuance

**Typical Execution Times:**
- 5 credentials (10 claims each): ~250-500ms
- 50 credentials (10 claims each): ~2.5-5s

---

## Resource Limits

### Memory Usage

**Per Credential:**
- Small credential (10 claims): ~10-20 KB
- Medium credential (100 claims): ~50-100 KB
- Large credential (1000 claims): ~500 KB - 1 MB

**Security Limits (from `SecurityConstants`):**
- `MAX_CREDENTIAL_SIZE_BYTES`: 1 MB
- `MAX_PRESENTATION_SIZE_BYTES`: 5 MB
- `MAX_CLAIMS_PER_CREDENTIAL`: 1000
- `MAX_CREDENTIALS_PER_PRESENTATION`: 100

**Memory Considerations:**
- Canonicalization creates temporary copies of credential data
- Signature verification may load public keys into memory
- DID resolution may cache DID documents

### CPU Usage

**Operations (relative CPU cost):**
1. JSON-LD canonicalization: Highest (O(n log n) for sorting)
2. Cryptographic operations (signing/verification): High
3. JSON parsing/serialization: Moderate
4. Input validation: Low

**Optimization:**
- Canonicalization is the primary CPU bottleneck
- Consider caching or optimization of canonicalization algorithm

### Network Usage

**DID Resolution:**
- Typical request: ~1-5 KB
- Typical response: ~5-20 KB
- Latency: 50-500ms (depending on resolver)

**Revocation Checking:**
- Typical request: ~1 KB
- Typical response: ~1-5 KB
- Latency: 50-500ms (depending on revocation service)

**Recommendations:**
- Cache DID documents (TTL: 1 hour recommended)
- Cache revocation status (TTL: 5 minutes recommended)
- Use connection pooling for HTTP clients

---

## Scalability Considerations

### Horizontal Scaling

**Stateless Operations:**
- ✅ Credential issuance: Fully stateless, scales horizontally
- ✅ Credential verification: Fully stateless, scales horizontally
- ✅ Presentation creation: Fully stateless, scales horizontally

**Stateful Considerations:**
- DID resolution cache (if shared)
- Revocation status cache (if shared)
- Rate limiting (if needed)

### Vertical Scaling

**CPU-Bound:**
- Canonicalization benefits from faster CPUs
- Cryptographic operations benefit from hardware acceleration

**Memory-Bound:**
- Large credentials may require more heap memory
- Batch operations benefit from more memory

### Caching Strategies

**Recommended Caches:**
1. **DID Document Cache:**
   - TTL: 1 hour
   - Max size: 10,000 entries
   - Key: DID string

2. **Canonicalized Document Cache:**
   - TTL: 1 hour
   - Max size: 1,000 entries
   - Key: Credential ID + canonicalization algorithm

3. **Revocation Status Cache:**
   - TTL: 5 minutes
   - Max size: 100,000 entries
   - Key: Credential ID

---

## Performance Best Practices

### For Application Developers

1. **Batch Operations:**
   - Use batch verification when verifying multiple credentials
   - Issue multiple credentials in parallel when possible

2. **Caching:**
   - Cache DID documents at application level
   - Cache revocation status when appropriate

3. **Async Operations:**
   - Use coroutines for concurrent operations
   - Don't block on DID resolution or revocation checks

4. **Credential Size:**
   - Keep credentials small (< 100 claims when possible)
   - Use selective disclosure for large credentials

### For Proof Engine Implementers

1. **Canonicalization:**
   - Cache canonicalized documents when possible
   - Optimize sorting and normalization

2. **Cryptographic Operations:**
   - Use hardware acceleration when available
   - Batch signature operations when possible

3. **Error Handling:**
   - Fail fast on invalid inputs
   - Provide clear error messages

---

## Performance Testing

### Benchmarks

Performance benchmarks should be run regularly to detect regressions. Key metrics to track:

1. **Issuance Time:** P50, P95, P99
2. **Verification Time:** P50, P95, P99
3. **Memory Usage:** Peak, average
4. **Throughput:** Credentials/second

### Load Testing

Recommended load test scenarios:

1. **Single Credential:**
   - 1000 credentials/sec (small)
   - 100 credentials/sec (medium)
   - 10 credentials/sec (large)

2. **Batch Verification:**
   - 100 batches/sec (10 credentials each)
   - 10 batches/sec (100 credentials each)

3. **Mixed Workload:**
   - 50% issuance, 50% verification
   - Vary credential sizes

---

## Future Optimizations

### Planned Improvements

1. **Canonicalization Optimization:**
   - Investigate faster JSON-LD canonicalization algorithms
   - Parallel canonicalization for large documents

2. **Caching Layer:**
   - Built-in DID document cache
   - Configurable cache providers

3. **Batch Operations:**
   - Native batch verification with parallelism
   - Batch DID resolution

4. **Streaming:**
   - Streaming JSON parsing for very large credentials
   - Streaming canonicalization

### Trade-offs

**Performance vs. Correctness:**
- Always prioritize correctness
- Performance optimizations should not compromise security

**Performance vs. Memory:**
- Caching improves performance but uses memory
- Balance based on available resources



