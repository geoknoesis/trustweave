"""Python reference-issued autonomous VI vectors using synthetic demonstration keys."""
from helpers import get_issuer_keys, get_user_keys, get_agent_keys, get_merchant_keys, MERCHANTS, PAYMENT_INSTRUMENT
from verifiable_intent.crypto.disclosure import build_selective_presentation, hash_bytes
from verifiable_intent.crypto.signing import _jwt_encode
from verifiable_intent.issuance.issuer import create_layer1
from verifiable_intent.issuance.user import create_layer2_autonomous
from verifiable_intent.issuance.agent import create_layer3_payment, create_layer3_checkout
from verifiable_intent.models.issuer_credential import IssuerCredential
from verifiable_intent.models.user_mandate import UserMandate, MandateMode, CheckoutMandate, PaymentMandate
from verifiable_intent.models.agent_mandate import PaymentL3Mandate, FinalPaymentMandate, CheckoutL3Mandate, FinalCheckoutMandate
from verifiable_intent.models.constraints import PaymentAmountConstraint, AllowedPayeeConstraint, AllowedMerchantConstraint, CheckoutLineItemsConstraint


def generate():
    now = 1780000000
    issuer, user, agent, merchant = get_issuer_keys(), get_user_keys(), get_agent_keys(), get_merchant_keys()
    identity = MERCHANTS[0]
    product = {'id': 'item-1', 'title': 'Synthetic qualification item'}
    l1 = create_layer1(IssuerCredential(iss='https://issuer.example', sub='fixture-user', iat=now,
        exp=now+86400, cnf_jwk=user.public_jwk), issuer.private_key).serialize()
    l2 = create_layer2_autonomous(UserMandate(nonce='user-challenge', aud='https://agent.example', iat=now,
        exp=now+900, iss='https://wallet.example', mode=MandateMode.AUTONOMOUS,
        sd_hash=hash_bytes(l1.encode('ascii')), merchants=[identity], acceptable_items=[product],
        checkout_mandate=CheckoutMandate(vct='mandate.checkout.open.1', cnf_jwk=agent.public_jwk, cnf_kid='agent-key-1',
            constraints=[AllowedMerchantConstraint(allowed=[identity]), CheckoutLineItemsConstraint(items=[{'id':'line-1','acceptable_items':[product],'quantity':1}])]),
        payment_mandate=PaymentMandate(vct='mandate.payment.open.1', cnf_jwk=agent.public_jwk, cnf_kid='agent-key-1',
            payment_instrument=PAYMENT_INSTRUMENT, constraints=[PaymentAmountConstraint(currency='USD', min=1, max=200), AllowedPayeeConstraint(allowed=[identity])])), user.private_key)
    def disclosure(predicate):
        return next(encoded for encoded,value in zip(l2.disclosures,l2.disclosure_values) if predicate(value[-1]))
    payment = disclosure(lambda v: isinstance(v,dict) and v.get('vct')=='mandate.payment.open.1')
    checkout = disclosure(lambda v: isinstance(v,dict) and v.get('vct')=='mandate.checkout.open.1')
    merchant_disc = disclosure(lambda v: v==identity)
    item_disc = disclosure(lambda v: v==product)
    base_jwt = l2.serialize().split('~')[0]
    routed = build_selective_presentation(base_jwt,[payment,merchant_disc])
    checkout_routed = build_selective_presentation(base_jwt,[checkout,item_disc])
    trust = dict(issuer='https://merchant.example',audience='https://verifier.example',nonce='checkout-challenge',jwk=merchant.public_jwk,merchant=identity)
    def issue(amount=100, currency='USD', quantity=1, merchant_aud=None):
        token = _jwt_encode({'alg':'ES256','typ':'JWT'}, {'iss':trust['issuer'], 'aud':merchant_aud or trust['audience'],
            'nonce':trust['nonce'],'iat':now,'exp':now+300,'cart':{'items':[{'id':'item-1','quantity':quantity}]}}, merchant.private_key)
        digest = hash_bytes(token.encode('ascii'))
        pay = create_layer3_payment(PaymentL3Mandate(nonce='payment-challenge',aud='https://network.example',iat=now,exp=now+300,
            final_payment=FinalPaymentMandate(transaction_id=digest,payee=identity,payment_amount={'currency':currency,'amount':amount},payment_instrument=PAYMENT_INSTRUMENT),
            final_merchant=identity),agent.private_key,base_jwt,payment,merchant_disc).serialize()
        cart = create_layer3_checkout(CheckoutL3Mandate(nonce='cart-challenge',aud='https://merchant.example',iat=now,exp=now+300,
            final_checkout=FinalCheckoutMandate(checkout_jwt=token,checkout_hash=digest)),agent.private_key,base_jwt,checkout,item_disc).serialize()
        return pay,cart
    pay,cart=issue()
    base=dict(name='python-autonomous-payment',l1=l1,l2=l2.serialize(),issuerJwk=issuer.public_jwk,now=now+60,
        aud='https://agent.example',nonce='user-challenge',l3Payment=pay,routedL2=routed,
        paymentAud='https://network.example',paymentNonce='payment-challenge',expected=True,profile='autonomous-payment')
    cases=[base]
    for field,bad in [('aud','wrong'),('nonce','wrong'),('paymentAud','wrong'),('paymentNonce','wrong'),('routedL2',l2.serialize()),('now',now+2000)]:
        cases.append(dict(base,name='autonomous-'+field,**{field:bad},expected=False))
    parts=l2.serialize().split('~')
    cases.append(dict(base,name='autonomous-reordered-disclosures',l2='~'.join([parts[0],*reversed(parts[1:-1]),''])))
    cases.append(dict(base,name='autonomous-withheld-payment',l2=build_selective_presentation(base_jwt,[checkout,item_disc,merchant_disc]),expected=False))
    for amount,expected in [(1,True),(200,True),(0,False),(201,False)]:
        payment_token,_=issue(amount=amount)
        cases.append(dict(base,name='autonomous-amount-'+str(amount),l3Payment=payment_token,expected=expected))
    wrong_currency,_=issue(currency='EUR')
    cases.append(dict(base,name='autonomous-currency',l3Payment=wrong_currency,expected=False))
    checkout_base=dict(base,name='python-autonomous-checkout',l3Checkout=cart,routedCheckout=checkout_routed,
        checkoutAud='https://merchant.example',checkoutNonce='cart-challenge',checkoutTrust=trust,profile='authenticated-checkout')
    cases.append(checkout_base)
    for field in ['checkoutAud','checkoutNonce','routedCheckout']:
        cases.append(dict(checkout_base,name='checkout-'+field,**{field:'wrong'},expected=False))
    for name,kwargs in [('cart-over-quantity',{'quantity':2}),('merchant-wrong-audience',{'merchant_aud':'https://attacker.example'})]:
        payment_token,cart_token=issue(**kwargs)
        cases.append(dict(checkout_base,name=name,l3Payment=payment_token,l3Checkout=cart_token,expected=False,
            referenceExpected=True,difference='Python chain verifier does not enforce the SDK authenticated merchant/cart profile',
            expectedError='Cart exceeds authorized quantities' if name=='cart-over-quantity' else 'Checkout audience mismatch'))
    return cases
