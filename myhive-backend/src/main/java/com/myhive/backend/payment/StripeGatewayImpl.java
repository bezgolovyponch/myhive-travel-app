package com.myhive.backend.payment;

import com.myhive.backend.config.StripeProperties;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.PaymentGatewayException;
import com.myhive.backend.payment.StripeRefs.CheckoutSessionRef;
import com.myhive.backend.payment.StripeRefs.PaymentLinkRef;
import com.myhive.backend.payment.StripeRefs.StripeWebhookEvent;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentLink;
import com.stripe.model.Price;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentLinkCreateParams;
import com.stripe.param.PaymentLinkUpdateParams;
import com.stripe.param.PriceCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StripeGatewayImpl implements StripeGateway {

    private final StripeProperties stripeProperties;

    @Override
    public CheckoutSessionRef createCheckoutSession(long amountCents, String currency, String description,
            Map<String, String> metadata, String successUrl, String cancelUrl, String idempotencyKey) {
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .putAllMetadata(metadata)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(currency)
                                .setUnitAmount(amountCents)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(description)
                                        .build())
                                .build())
                        .build())
                .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                        .putAllMetadata(metadata)
                        .build())
                .build();
        try {
            RequestOptions options = RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();
            Session session = Session.create(params, options);
            return new CheckoutSessionRef(session.getId(), session.getUrl());
        } catch (StripeException e) {
            // M3: log the upstream detail server-side; surface a generic 502 (not a 400 with the raw message).
            log.error("Stripe Checkout session creation failed: {}", e.getMessage(), e);
            throw new PaymentGatewayException("Unable to start payment. Please try again later.");
        }
    }

    @Override
    public PaymentLinkRef createPaymentLink(long amountCents, String currency, String description,
            Map<String, String> metadata) {
        try {
            Price price = Price.create(PriceCreateParams.builder()
                    .setCurrency(currency)
                    .setUnitAmount(amountCents)
                    .setProductData(PriceCreateParams.ProductData.builder()
                            .setName(description)
                            .build())
                    .build());
            PaymentLink link = PaymentLink.create(PaymentLinkCreateParams.builder()
                    .addLineItem(PaymentLinkCreateParams.LineItem.builder()
                            .setPrice(price.getId())
                            .setQuantity(1L)
                            .build())
                    .putAllMetadata(metadata)
                    .setPaymentIntentData(PaymentLinkCreateParams.PaymentIntentData.builder()
                            .putAllMetadata(metadata)
                            .build())
                    .build());
            return new PaymentLinkRef(link.getId(), link.getUrl());
        } catch (StripeException e) {
            log.error("Stripe Payment Link creation failed: {}", e.getMessage(), e);
            throw new PaymentGatewayException("Unable to create payment link. Please try again later.");
        }
    }

    @Override
    public void deactivatePaymentLink(String paymentLinkId) {
        try {
            PaymentLink link = PaymentLink.retrieve(paymentLinkId);
            link.update(PaymentLinkUpdateParams.builder().setActive(false).build());
        } catch (StripeException e) {
            // Best-effort: a failed deactivation must not break webhook fulfilment (caller ignores).
            log.error("Stripe Payment Link deactivation failed for {}: {}", paymentLinkId, e.getMessage(), e);
            throw new PaymentGatewayException("Unable to deactivate payment link.");
        }
    }

    @Override
    public StripeWebhookEvent constructEvent(String payload, String signatureHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, stripeProperties.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            throw new BadRequestException("Invalid Stripe webhook signature");
        }

        String type = event.getType();
        if ("checkout.session.completed".equals(type) || "checkout.session.async_payment_succeeded".equals(type)) {
            Session session = (Session) deserialize(event);
            if (session == null) {
                return bare(event);
            }
            Map<String, String> md = session.getMetadata();
            String payerEmail = session.getCustomerDetails() != null ? session.getCustomerDetails().getEmail() : null;
            return new StripeWebhookEvent(event.getId(), type,
                    md != null ? md.get("share_id") : null,
                    session.getPaymentLink(),
                    session.getId(),
                    session.getPaymentIntent(),
                    payerEmail,
                    session.getPaymentStatus(),
                    session.getAmountTotal(),
                    null, false);
        }
        if ("charge.refunded".equals(type)) {
            Charge charge = (Charge) deserialize(event);
            if (charge == null) {
                return bare(event);
            }
            return new StripeWebhookEvent(event.getId(), type, null, null, null,
                    charge.getPaymentIntent(), null, null, null,
                    charge.getAmountRefunded(), Boolean.TRUE.equals(charge.getRefunded()));
        }
        return bare(event);
    }

    /**
     * Resolves the event's data object, falling back to deserializeUnsafe() when the typed
     * Optional is empty (STRIPE-2/3). The Optional is empty whenever the webhook's Stripe API
     * version differs from the SDK's pinned version; without the fallback that payment would be
     * silently dropped instead of fulfilled/retried.
     */
    private StripeObject deserialize(Event event) {
        StripeObject obj = event.getDataObjectDeserializer().getObject().orElse(null);
        if (obj == null) {
            try {
                obj = event.getDataObjectDeserializer().deserializeUnsafe();
            } catch (EventDataObjectDeserializationException e) {
                obj = null; // truly undeserializable
            }
        }
        return obj;
    }

    private StripeWebhookEvent bare(Event event) {
        return new StripeWebhookEvent(event.getId(), event.getType(), null, null, null, null, null, null, null, null, false);
    }
}
