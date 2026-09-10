"""Pinned Python/Kotlin VI interoperability harness; no network or cloud credentials required."""
import argparse
import base64
import json
from pathlib import Path
import subprocess
import sys
from unittest.mock import patch

REVISION = "356c29635f1c44df7de02edb58699ca9f29bece6"
parser = argparse.ArgumentParser()
parser.add_argument("mode", choices=["generate", "generate-autonomous", "verify"])
parser.add_argument("--reference", type=Path, required=True)
parser.add_argument("--file", type=Path, required=True)
args = parser.parse_args()
revision = subprocess.check_output(["git", "-C", str(args.reference), "rev-parse", "HEAD"], text=True).strip()
if revision != REVISION:
    raise SystemExit("Reference revision differs from reviewed pin")
subprocess.run(["git", "-C", str(args.reference), "diff", "--exit-code", "--quiet"], check=True)
subprocess.run(["git", "-C", str(args.reference), "diff", "--cached", "--exit-code", "--quiet"], check=True)
sys.path[:0] = [str(args.reference / "src"), str(args.reference / "examples")]
from helpers import get_issuer_keys, get_user_keys, get_merchant_keys, PAYMENT_INSTRUMENT, MERCHANTS
from verifiable_intent.crypto.sd_jwt import decode_sd_jwt, resolve_disclosures
from verifiable_intent.crypto.signing import jwk_to_public_key, _jwt_encode
from verifiable_intent.crypto.disclosure import hash_bytes
from verifiable_intent.issuance.issuer import create_layer1
from verifiable_intent.issuance.user import create_layer2_immediate
from verifiable_intent.models.issuer_credential import IssuerCredential
from verifiable_intent.models.user_mandate import CheckoutMandate, PaymentMandate, UserMandate, MandateMode
from verifiable_intent.verification.chain import verify_chain


def check(case):
    kwargs = dict(l1=decode_sd_jwt(case["l1"]), l2=decode_sd_jwt(case["l2"]),
                  issuer_public_key=jwk_to_public_key(case["issuerJwk"]),
                  expected_l2_aud=case["aud"], expected_l2_nonce=case["nonce"],
                  l1_serialized=case["l1"], l2_serialized=case["l2"])
    if "l3Payment" in case:
        kwargs.update(l3_payment=decode_sd_jwt(case["l3Payment"]),
                      l2_payment_serialized=case["routedL2"],
                      expected_l3_payment_aud=case["paymentAud"], expected_l3_payment_nonce=case["paymentNonce"])
    if "l3Checkout" in case:
        kwargs.update(l3_checkout=decode_sd_jwt(case["l3Checkout"]), l2_checkout_serialized=case["routedCheckout"],
                      expected_l3_checkout_aud=case["checkoutAud"], expected_l3_checkout_nonce=case["checkoutNonce"])
    with patch("verifiable_intent.verification.chain.time.time", return_value=case["now"]):
        result = verify_chain(**kwargs)
    valid = result.valid
    if valid and case.get("profile") in ("autonomous-payment", "authenticated-checkout"):
        from verifiable_intent.verification.constraint_checker import check_constraints
        from verifiable_intent.crypto.disclosure import hash_disclosure
        decoded = decode_sd_jwt(case["l2"])
        mandates = resolve_disclosures(decoded)["delegate_payload"]
        constraints = next((v["constraints"] for v in mandates if isinstance(v,dict) and v.get("vct")=="mandate.payment.open.1"), None)
        if constraints is None:
            valid = False
        else:
            fulfillment = next(v for v in result.l3_payment_claims["delegate_payload"] if isinstance(v,dict) and v.get("vct")=="mandate.payment.1")
            by_hash = {hash_disclosure(d):v[-1] for d,v in zip(decoded.disclosures,decoded.disclosure_values)}
            allowed = next((c['allowed'] for c in constraints if c['type']=='mandate.payment.allowed_payees'),[])
            fulfillment['allowed_merchants'] = [by_hash[r['...']] for r in allowed if r.get('...') in by_hash]
            valid = check_constraints(constraints, fulfillment, is_open_mandate=True).satisfied
    expected = case.get("referenceExpected", case["expected"])
    if expected != case["expected"] and not case.get("difference"):
        raise AssertionError("Unexplained implementation difference")
    if valid != expected:
        raise AssertionError(f"{case['name']}: expected {case['expected']}, got {valid}: {result.errors}")
    return {"name": case["name"], "valid": valid, "sdk_expected": case["expected"], "difference": case.get("difference")}


if args.mode == "generate":
    now = 1780000000
    issuer, user, merchant = get_issuer_keys(), get_user_keys(), get_merchant_keys()
    l1 = create_layer1(IssuerCredential(iss="https://issuer.example", sub="test-user", iat=now,
                      exp=now+86400, cnf_jwk=user.public_jwk), issuer.private_key).serialize()
    checkout = _jwt_encode({"alg":"ES256","typ":"JWT"}, {"iss":"https://merchant.example","iat":now}, merchant.private_key)
    digest = hash_bytes(checkout.encode("ascii"))
    mandate = UserMandate(nonce="test-nonce", aud="https://merchant.example", iat=now, exp=now+900,
                         iss="https://wallet.example", mode=MandateMode.IMMEDIATE,
                         sd_hash=hash_bytes(l1.encode("ascii")),
                         checkout_mandate=CheckoutMandate(vct="mandate.checkout.1", checkout_jwt=checkout, checkout_hash=digest),
                         payment_mandate=PaymentMandate(vct="mandate.payment.1", payment_instrument=PAYMENT_INSTRUMENT,
                                                      payee=MERCHANTS[0], currency="USD", amount=100, transaction_id=digest))
    l2 = create_layer2_immediate(mandate, user.private_key).sd_jwt.serialize()
    base = dict(name="python-immediate", l1=l1, l2=l2, issuerJwk=issuer.public_jwk, now=now+60,
                aud="https://merchant.example", nonce="test-nonce", expected=True)
    cases = [base, dict(base, name="wrong-audience", aud="other", expected=False),
             dict(base, name="wrong-nonce", nonce="other", expected=False),
             dict(base, name="expired", now=now+2000, expected=False),
             dict(base, name="withheld-mandates", l2=l2.split("~")[0]+"~", expected=False)]
    segments=l2.split("~"); jwt=segments[0].split(".")
    sig=bytearray(base64.urlsafe_b64decode(jwt[2]+"=="));sig[0]^=1
    jwt[2]=base64.urlsafe_b64encode(sig).decode().rstrip("=")
    segments[0]=".".join(jwt)
    cases.append(dict(base,name="invalid-signature",l2="~".join(segments),expected=False))
    outcomes=[check(case) for case in cases]
    args.file.parent.mkdir(parents=True,exist_ok=True)
    args.file.write_text(json.dumps(dict(reference_revision=REVISION, test_keys_only=True, cases=cases),indent=2)+"\n",encoding="utf-8")
elif args.mode == "generate-autonomous":
    from vi_autonomous_vectors import generate
    cases = generate()
    outcomes = [check(case) for case in cases]
    args.file.parent.mkdir(parents=True, exist_ok=True)
    args.file.write_text(json.dumps(dict(reference_revision=REVISION, test_keys_only=True, cases=cases),indent=2)+"\n",encoding="utf-8")
else:
    payload=json.loads(args.file.read_text(encoding="utf-8"))
    cases = list(payload["cases"])
    for case in payload["cases"]:
        if case["expected"]:
            cases.extend([
                dict(case, name=case["name"]+"-wrong-audience", aud="https://wrong.example", expected=False),
                dict(case, name=case["name"]+"-wrong-nonce", nonce="wrong-nonce", expected=False),
            ])
            if "l3Payment" in case:
                cases.extend([
                    dict(case, name=case["name"]+"-wrong-payment-audience", paymentAud="https://wrong.example", expected=False),
                    dict(case, name=case["name"]+"-wrong-payment-nonce", paymentNonce="wrong-nonce", expected=False),
                ])
            if "l3Checkout" in case:
                cases.extend([
                    dict(case, name=case["name"]+"-wrong-checkout-audience", checkoutAud="wrong", expected=False),
                    dict(case, name=case["name"]+"-wrong-checkout-nonce", checkoutNonce="wrong", expected=False),
                ])
    outcomes=[check(case) for case in cases]
print(json.dumps(dict(reference_revision=REVISION, outcomes=outcomes),indent=2))
