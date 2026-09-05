package com.geoknoesis.trustweave.saas.server.review

import com.geoknoesis.trustweave.saas.server.security.AudienceValidator
import com.geoknoesis.trustweave.saas.server.security.RateLimiter
import com.geoknoesis.trustweave.saas.server.verification.VerificationPolicy
import com.geoknoesis.trustweave.saas.server.domain.VerificationLevel
import com.geoknoesis.trustweave.saas.server.domain.IssuedCredential
import com.geoknoesis.trustweave.saas.server.controller.PublicCredentialOfferController
import com.geoknoesis.trustweave.saas.server.services.CredentialOfferService
import com.geoknoesis.trustweave.saas.server.repository.IssuedCredentialRepository
import com.geoknoesis.trustweave.saas.server.dto.RedeemCredentialOfferRequest
import com.geoknoesis.trustweave.saas.server.dto.WalletCredentialResponse
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.mock.web.MockHttpServletRequest
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class RoundTwoReviewProbeTest {
 @Test fun `probe accepts wrong audience when azp matches`() {
  val jwt=Jwt.withTokenValue("synthetic").header("alg","RS256").subject("person").audience(listOf("different-api")).claim("azp","trustweave").build()
  assertFalse(AudienceValidator("trustweave").validate(jwt).hasErrors())
 }
 @Test fun `probe strict policy disables issuer trust for empty allowlist`() {
  val policy=VerificationPolicy.forLevel(VerificationLevel.STRICT,emptySet())
  assertFalse(policy.checkIssuerTrust)
  assertTrue(policy.trustNotEvaluated)
 }
 @Test fun `probe public legacy lookup reveals holder and then returns full credential`() {
  val service=mock<CredentialOfferService>();val repo=mock<IssuedCredentialRepository>()
  val credential=IssuedCredential(organizationId=1,trustSpaceId=1,credentialId="public-vc-id",issuerDid="did:key:issuer",subjectDid="did:key:victim",credentialType="Employee",rawCredential="PRIVATE_CREDENTIAL",subjectClaims="{}",createdBy=1)
  whenever(service.redeemOffer("public-vc-id","did:key:attacker")).thenReturn(Result.failure(IllegalArgumentException("Unknown or expired offer code")))
  whenever(service.redeemOffer("public-vc-id","did:key:victim")).thenReturn(Result.failure(IllegalArgumentException("Unknown or expired offer code")))
  whenever(repo.findByCredentialId("public-vc-id")).thenReturn(credential)
  val controller=PublicCredentialOfferController(service,repo,RateLimiter())
  val first=controller.redeem(RedeemCredentialOfferRequest("public-vc-id","did:key:attacker"),MockHttpServletRequest())
  assertEquals("did:key:victim",(first.body as Map<*,*>)["expectedSubjectDid"])
  val second=controller.redeem(RedeemCredentialOfferRequest("public-vc-id","did:key:victim"),MockHttpServletRequest())
  assertEquals(200,second.statusCode.value())
  assertEquals("PRIVATE_CREDENTIAL",(second.body as WalletCredentialResponse).credential)
 }
}
