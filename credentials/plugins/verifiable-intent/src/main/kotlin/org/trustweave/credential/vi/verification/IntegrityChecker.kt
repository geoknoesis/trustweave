package org.trustweave.credential.vi.verification

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.trustweave.credential.vi.crypto.Disclosures
import org.trustweave.credential.vi.crypto.contentOrNull
import org.trustweave.credential.vi.crypto.sha256B64Url
import org.trustweave.credential.vi.model.Constraint
import org.trustweave.credential.vi.model.Vct

/**
 * Checkout↔payment integrity bindings (ports `verification/integrity.py`):
 * - [checkoutHashBinding]: `checkout_hash == SHA-256(checkout_jwt)` and `transaction_id == checkout_hash`.
 * - [l2ReferenceBinding]: `conditional_transaction_id == hash(checkout_disclosure)`.
 * - [l3CrossReference]: L3a `transaction_id == L3b checkout_hash`.
 */
internal object IntegrityChecker {

    /** Verify (valid, errorOrEmpty). */
    fun checkoutHashBinding(checkout: JsonObject, payment: JsonObject): Pair<Boolean, String> {
        val checkoutJwt = checkout["checkout_jwt"]?.contentOrNull() ?: return true to ""
        val checkoutHash = checkout["checkout_hash"]?.contentOrNull()
            ?: return false to "checkout_jwt present but checkout_hash missing"
        val computed = sha256B64Url(checkoutJwt.toByteArray(Charsets.US_ASCII))
        if (computed != checkoutHash) return false to "checkout_hash mismatch: $computed != $checkoutHash"
        val txId = payment["transaction_id"]?.contentOrNull()
            ?: return false to "checkout_jwt present but transaction_id missing from payment mandate"
        if (txId != checkoutHash) return false to "transaction_id mismatch: $txId != $checkoutHash"
        return true to ""
    }

    fun l2ReferenceBinding(payment: JsonObject, checkoutDisclosureB64: String): Pair<Boolean, String> {
        val ref = (payment["constraints"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it["type"]?.contentOrNull() == Constraint.Reference.TYPE }
            ?: return true to ""
        val expected = ref["conditional_transaction_id"]?.contentOrNull()
            ?: return false to "mandate.payment.reference missing conditional_transaction_id"
        val computed = Disclosures.hash(checkoutDisclosureB64)
        if (computed != expected) return false to "conditional_transaction_id mismatch: $computed != $expected"
        return true to ""
    }

    fun l3CrossReference(l3Payment: JsonObject, l3Checkout: JsonObject): Pair<Boolean, String> {
        val txId = mandateField(l3Payment, Vct.PAYMENT_FINAL, "transaction_id")
            ?: return false to "L3a payment mandate missing transaction_id"
        val checkoutHash = mandateField(l3Checkout, Vct.CHECKOUT_FINAL, "checkout_hash")
            ?: return false to "L3b checkout mandate missing checkout_hash"
        if (txId != checkoutHash) return false to "L3 cross-reference mismatch: $txId != $checkoutHash"
        return true to ""
    }

    private fun mandateField(claims: JsonObject, vct: String, field: String): String? =
        (claims["delegate_payload"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it["vct"]?.contentOrNull() == vct }
            ?.get(field)?.contentOrNull()
}
