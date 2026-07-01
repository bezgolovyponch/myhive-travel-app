# Admin Payment Link Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an admin create a Stripe Payment Link for an editable amount against a booking, copy the URL to send manually, and have the payment credited to the booking on completion.

**Architecture:** New `POST /admin/bookings/{id}/payment-link` (admin JWT) → `PaymentService.createAdminPaymentLink` creates a Stripe Payment Link via the existing `StripeGateway` seam and persists a `BALANCE` `BookingPaymentShare` (which already carries `stripePaymentLinkId` + `paymentUrl`). Payment fulfilment reuses the existing `handlePaymentSucceeded` webhook path — `resolveShare` already maps a Payment-Link payment by `findByStripePaymentLinkId` — plus a new best-effort link deactivation. The admin UI lives on `AdminBookingDetail`.

**Tech Stack:** Spring Boot 4 / Java 25 / Gradle / stripe-java 32.2.0 / JUnit 5 + Mockito; React 19 + react-bootstrap + Jest/RTL.

## Global Constraints

- Build on branch `feat/vote-prepayment`. Do NOT touch `feat/vote-balance-collection`.
- Money crosses the Stripe boundary in integer cents; entity money is `BigDecimal(10,2)`.
- Min charge = `STRIPE_MIN_CHARGE_CENTS = 50L` (already defined in `PaymentService`). Max = new `ADMIN_PAYMENT_LINK_MAX_CENTS = 5_000_000L` (€50,000).
- Admin is trusted (JWT `ADMIN`/`MANAGER`); no catalog-price (C1) enforcement here.
- Currency + deposit config come from `StripeProperties` (`getCurrency()`).
- No wildcard imports; braces always; `UPPER_SNAKE_CASE` constants; `@Override` on overrides.
- Backend tests for every changed unit (CLAUDE.md). Run a single class: `./gradlew test --tests '*ClassName'`.
- Frontend tests via `CI=true npx react-scripts test --watchAll=false --testPathPattern="X"`.

---

### Task 1: `BALANCE` share type + gateway Payment-Link seam

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/model/PaymentShareType.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/payment/StripeRefs.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/payment/StripeGateway.java`

**Interfaces:**
- Produces: `PaymentShareType.BALANCE`; `StripeRefs.PaymentLinkRef(String id, String url)`; `StripeGateway.createPaymentLink(long amountCents, String currency, String description, Map<String,String> metadata) → PaymentLinkRef`; `StripeGateway.deactivatePaymentLink(String paymentLinkId)`.

- [ ] **Step 1: Add the enum value**

In `PaymentShareType.java`, add `BALANCE` to the enum (the existing `BALANCE_SHARE`/`BALANCE_FULL` are unused split-branch leftovers — leave them):

```java
public enum PaymentShareType {
    DEPOSIT,
    BALANCE_SHARE,
    BALANCE_FULL,
    BALANCE
}
```

- [ ] **Step 2: Add the `PaymentLinkRef` record**

In `StripeRefs.java`, add next to `CheckoutSessionRef`:

```java
    public record PaymentLinkRef(String id, String url) {
    }
```

- [ ] **Step 3: Add the gateway methods**

In `StripeGateway.java`, import `com.myhive.backend.payment.StripeRefs.PaymentLinkRef;` and add to the interface:

```java
    /** Creates a reusable Stripe Payment Link for a one-off amount (admin balance/add-on collection). */
    PaymentLinkRef createPaymentLink(long amountCents, String currency, String description,
            Map<String, String> metadata);

    /** Deactivates a Payment Link so its URL can no longer be paid. */
    void deactivatePaymentLink(String paymentLinkId);
```

- [ ] **Step 4: Compile**

Run: `cd myhive-backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL (StripeGatewayImpl will not yet compile — it's abstract-incomplete; if the compiler complains about unimplemented methods, that's fixed in Task 2. Continue.)

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/model/PaymentShareType.java myhive-backend/src/main/java/com/myhive/backend/payment/StripeRefs.java myhive-backend/src/main/java/com/myhive/backend/payment/StripeGateway.java
git commit -m "feat(payments): add BALANCE share type + Payment Link gateway seam"
```

---

### Task 2: Implement Payment-Link creation + deactivation in `StripeGatewayImpl`

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/payment/StripeGatewayImpl.java`

**Interfaces:**
- Consumes: `PaymentLinkRef`, `StripeProperties`.
- Produces: concrete `createPaymentLink` / `deactivatePaymentLink`.

> No unit test: `StripeGatewayImpl` is the network seam and is mocked everywhere else (no existing test). Verified by compile + the service tests in Task 3 that mock the gateway.

- [ ] **Step 1: Add imports**

Add to `StripeGatewayImpl.java`:

```java
import com.myhive.backend.payment.StripeRefs.PaymentLinkRef;
import com.stripe.model.Price;
import com.stripe.model.PaymentLink;
import com.stripe.param.PriceCreateParams;
import com.stripe.param.PaymentLinkCreateParams;
import com.stripe.param.PaymentLinkUpdateParams;
```

- [ ] **Step 2: Implement `createPaymentLink`**

A Stripe Payment Link line item requires a `Price` object (no inline price_data), so create an ad-hoc Price first, then the link. Metadata is put on both the link and its PaymentIntent so the paid PaymentIntent carries `share_id`/`booking_id`:

```java
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
```

- [ ] **Step 3: Implement `deactivatePaymentLink`**

```java
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
```

- [ ] **Step 4: Compile**

Run: `cd myhive-backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/payment/StripeGatewayImpl.java
git commit -m "feat(payments): implement Stripe Payment Link create + deactivate"
```

---

### Task 3: `PaymentService.createAdminPaymentLink`

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/AdminPaymentLinkResponse.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/PaymentService.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/PaymentServiceTest.java`

**Interfaces:**
- Consumes: `StripeGateway.createPaymentLink`, `BookingRepository.findById`, `BookingPaymentShareRepository.save`, `StripeProperties.getCurrency()`, `PaymentCalculator.centsToBig` (or existing `centsToBig` helper in PaymentService).
- Produces: `PaymentService.createAdminPaymentLink(UUID bookingId, long amountCents) → AdminPaymentLinkResponse`; `AdminPaymentLinkResponse(String url, BigDecimal amount, UUID shareId)`.

- [ ] **Step 1: Create the response DTO**

`AdminPaymentLinkResponse.java`:

```java
package com.myhive.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AdminPaymentLinkResponse(String url, BigDecimal amount, UUID shareId) {
}
```

- [ ] **Step 2: Write the failing test**

Add to `PaymentServiceTest.java` (mirrors `createTripDepositSession_...`; the class already has `@Mock` fields `bookingRepository`, `shareRepository`, `stripeGateway`, `stripeProperties` and the `paymentService` under test):

```java
    @Test
    void createAdminPaymentLink_createsLinkAndBalanceShare() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setTripId("TRV-ADMIN1");
        booking.setStatus(BookingStatus.DEPOSIT_PAID);
        when(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(booking));
        when(stripeProperties.getCurrency()).thenReturn("eur");
        when(stripeGateway.createPaymentLink(eq(2800L), eq("eur"), anyString(), anyMap()))
                .thenReturn(new com.myhive.backend.payment.StripeRefs.PaymentLinkRef("plink_1", "https://pay/plink_1"));
        when(shareRepository.save(any(BookingPaymentShare.class))).thenAnswer(inv -> {
            BookingPaymentShare s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
            }
            return s;
        });

        AdminPaymentLinkResponse response = paymentService.createAdminPaymentLink(bookingId, 2800L);

        assertThat(response.url()).isEqualTo("https://pay/plink_1");
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("28.00"));
        ArgumentCaptor<BookingPaymentShare> shareCaptor = ArgumentCaptor.forClass(BookingPaymentShare.class);
        verify(shareRepository, org.mockito.Mockito.atLeastOnce()).save(shareCaptor.capture());
        BookingPaymentShare saved = shareCaptor.getValue();
        assertThat(saved.getType()).isEqualTo(PaymentShareType.BALANCE);
        assertThat(saved.getStripePaymentLinkId()).isEqualTo("plink_1");
        assertThat(saved.getPaymentUrl()).isEqualTo("https://pay/plink_1");
        assertThat(saved.isPaid()).isFalse();
    }

    @Test
    void createAdminPaymentLink_belowMinimum_throwsBadRequest() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setStatus(BookingStatus.DEPOSIT_PAID);
        when(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(booking));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> paymentService.createAdminPaymentLink(bookingId, 40L))
                .isInstanceOf(com.myhive.backend.exception.BadRequestException.class);
        verify(stripeGateway, never()).createPaymentLink(anyLong(), anyString(), anyString(), anyMap());
    }

    @Test
    void createAdminPaymentLink_cancelledBooking_throwsBadRequest() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setStatus(BookingStatus.CANCELLED);
        when(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(booking));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> paymentService.createAdminPaymentLink(bookingId, 2800L))
                .isInstanceOf(com.myhive.backend.exception.BadRequestException.class);
        verify(stripeGateway, never()).createPaymentLink(anyLong(), anyString(), anyString(), anyMap());
    }
```

Add imports if missing: `com.myhive.backend.dto.AdminPaymentLinkResponse`, `com.myhive.backend.model.PaymentShareType`, `static org.mockito.ArgumentMatchers.anyLong/anyMap/eq`, `org.mockito.ArgumentCaptor`. Confirm `BookingStatus.CANCELLED` exists in the enum; if the value is named differently, use the actual cancelled state.

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd myhive-backend && ./gradlew test --tests '*PaymentServiceTest'`
Expected: FAIL — `createAdminPaymentLink` does not exist.

- [ ] **Step 4: Implement the method**

Add to `PaymentService.java` (constant near the other constants, method near `createTripDepositSession`). Uses the existing `centsToBig` helper already in the class:

```java
    private static final long ADMIN_PAYMENT_LINK_MAX_CENTS = 5_000_000L; // €50,000 typo guard

    /** Admin-created Stripe Payment Link for an arbitrary amount on a booking (balance or add-on). */
    @Transactional
    public AdminPaymentLinkResponse createAdminPaymentLink(UUID bookingId, long amountCents) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BadRequestException("Cannot collect payment on a cancelled booking");
        }
        if (amountCents < STRIPE_MIN_CHARGE_CENTS) {
            throw new BadRequestException("Amount is below the minimum chargeable value");
        }
        if (amountCents > ADMIN_PAYMENT_LINK_MAX_CENTS) {
            throw new BadRequestException("Amount exceeds the maximum allowed value");
        }

        BookingPaymentShare share = new BookingPaymentShare();
        share.setBooking(booking);
        share.setType(PaymentShareType.BALANCE);
        share.setAmount(centsToBig(amountCents));
        share.setPaid(false);
        shareRepository.save(share);

        Map<String, String> metadata = Map.of(
                "booking_id", booking.getId().toString(),
                "share_id", share.getId().toString());
        StripeRefs.PaymentLinkRef ref = stripeGateway.createPaymentLink(amountCents,
                stripeProperties.getCurrency(), "Payment for trip " + booking.getTripId(), metadata);

        share.setStripePaymentLinkId(ref.id());
        share.setPaymentUrl(ref.url());
        shareRepository.save(share);

        return new AdminPaymentLinkResponse(ref.url(), centsToBig(amountCents), share.getId());
    }
```

Add imports: `com.myhive.backend.dto.AdminPaymentLinkResponse`, `com.myhive.backend.payment.StripeRefs` (or `StripeRefs.PaymentLinkRef`), `com.myhive.backend.exception.ResourceNotFoundException` (confirm not already imported).

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd myhive-backend && ./gradlew test --tests '*PaymentServiceTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/dto/AdminPaymentLinkResponse.java myhive-backend/src/main/java/com/myhive/backend/service/PaymentService.java myhive-backend/src/test/java/com/myhive/backend/service/PaymentServiceTest.java
git commit -m "feat(payments): admin payment-link creation service"
```

---

### Task 4: Deactivate the Payment Link on fulfilment

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/PaymentService.java` (inside `handlePaymentSucceeded`)
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/PaymentServiceTest.java`

**Interfaces:**
- Consumes: `StripeGateway.deactivatePaymentLink`, `BookingPaymentShare.getStripePaymentLinkId`.

- [ ] **Step 1: Write the failing test**

Add to `PaymentServiceTest.java` — a `BALANCE` share paid via a Payment Link deactivates the link. Mirrors `handleStripeEvent_depositPaid_...` but the share has a `stripePaymentLinkId` and the event is resolved by link id:

```java
    @Test
    void handleStripeEvent_balanceLinkPaid_deactivatesLink() {
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setTripId("TRV-ADMIN1");
        booking.setStatus(BookingStatus.DEPOSIT_PAID);
        booking.setTotalAmount(new BigDecimal("40.00"));
        booking.setAmountPaid(new BigDecimal("12.00"));

        BookingPaymentShare balance = new BookingPaymentShare();
        balance.setId(UUID.randomUUID());
        balance.setBooking(booking);
        balance.setType(PaymentShareType.BALANCE);
        balance.setAmount(new BigDecimal("28.00"));
        balance.setPaid(false);
        balance.setStripePaymentLinkId("plink_1");

        when(processedEventRepository.existsById("evt_bal")).thenReturn(false);
        when(stripeGateway.constructEvent("body", "sig"))
                .thenReturn(balanceLinkEvent("evt_bal", "plink_1", 2800L));
        when(shareRepository.findByStripePaymentLinkId("plink_1")).thenReturn(java.util.Optional.of(balance));
        when(shareRepository.findByBookingId(booking.getId())).thenReturn(java.util.List.of(balance));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingService.toExportRequest(booking)).thenReturn(new TripExportRequest());

        paymentService.handleStripeEvent("body", "sig");

        assertThat(balance.isPaid()).isTrue();
        verify(stripeGateway).deactivatePaymentLink("plink_1");
    }

    private com.myhive.backend.payment.StripeRefs.StripeWebhookEvent balanceLinkEvent(
            String eventId, String linkId, long cents) {
        return new com.myhive.backend.payment.StripeRefs.StripeWebhookEvent(
                eventId, "checkout.session.completed", null, linkId, "cs_bal", "pi_bal",
                "payer@example.com", "paid", cents, null, false);
    }
```

Note: the event `shareId` is `null` and `paymentLinkId` is `"plink_1"`, so `resolveShare` uses `findByStripePaymentLinkId`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd myhive-backend && ./gradlew test --tests '*PaymentServiceTest'`
Expected: FAIL — `deactivatePaymentLink` is never called.

- [ ] **Step 3: Implement — deactivate after fulfilment**

In `handlePaymentSucceeded`, immediately AFTER the `bookingRepository.save(booking);` that persists the new status and BEFORE the email block, add:

```java
        // A Payment-Link share (admin balance/add-on) is single-use: deactivate the link so its URL
        // cannot be paid again. Best-effort — never let this break webhook fulfilment.
        if (share.getStripePaymentLinkId() != null) {
            try {
                stripeGateway.deactivatePaymentLink(share.getStripePaymentLinkId());
            } catch (Exception e) {
                log.error("Failed to deactivate payment link {} for booking {}: {}",
                        share.getStripePaymentLinkId(), booking.getId(), e.getMessage(), e);
            }
        }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd myhive-backend && ./gradlew test --tests '*PaymentServiceTest'`
Expected: PASS (including the existing deposit test — a DEPOSIT share has `stripePaymentLinkId == null`, so deactivation is skipped and `verify`s there are unaffected).

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/PaymentService.java myhive-backend/src/test/java/com/myhive/backend/service/PaymentServiceTest.java
git commit -m "feat(payments): deactivate Payment Link once paid"
```

---

### Task 5: Admin endpoint `POST /admin/bookings/{id}/payment-link`

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/AdminPaymentLinkRequest.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/controller/AdminController.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/controller/AdminControllerTest.java` (or the existing admin controller test class — confirm its name with `Glob AdminController*Test.java`; if none exists, create `AdminControllerPaymentLinkTest.java` following the `@SpringBootTest`+`@AutoConfigureMockMvc`+`TestSecurityConfig` pattern used by `PaymentControllerTest`).

**Interfaces:**
- Consumes: `PaymentService.createAdminPaymentLink`.
- Produces: `POST /admin/bookings/{id}/payment-link` body `{ "amountCents": <long> }` → 201 `AdminPaymentLinkResponse`.

- [ ] **Step 1: Create the request DTO**

`AdminPaymentLinkRequest.java`:

```java
package com.myhive.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AdminPaymentLinkRequest(
        @NotNull(message = "amountCents is required")
        @Positive(message = "amountCents must be positive")
        Long amountCents) {
}
```

- [ ] **Step 2: Write the failing test**

Add a test that the endpoint requires the injected `PaymentService` and returns 201 with the URL. Follow the existing admin-controller test setup (mock `PaymentService`, ADMIN JWT via `TestSecurityConfig`/`@WithMockUser` — match whatever the repo's admin tests already use). Example body:

```java
    @Test
    void createPaymentLink_returns201WithUrl() throws Exception {
        UUID bookingId = UUID.randomUUID();
        UUID shareId = UUID.randomUUID();
        when(paymentService.createAdminPaymentLink(eq(bookingId), eq(2800L)))
                .thenReturn(new AdminPaymentLinkResponse("https://pay/plink_1", new java.math.BigDecimal("28.00"), shareId));

        mockMvc.perform(post("/admin/bookings/" + bookingId + "/payment-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountCents\": 2800}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value("https://pay/plink_1"));
    }
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd myhive-backend && ./gradlew test --tests '*AdminController*Test'`
Expected: FAIL — no such mapping / 404.

- [ ] **Step 4: Implement the endpoint**

In `AdminController.java`, inject `PaymentService` (add `private final PaymentService paymentService;` if not present — the class uses `@RequiredArgsConstructor`) and add near the other `/bookings` mappings:

```java
    @PostMapping("/bookings/{id}/payment-link")
    public ResponseEntity<AdminPaymentLinkResponse> createBookingPaymentLink(
            @PathVariable UUID id,
            @Valid @RequestBody AdminPaymentLinkRequest request) {
        AdminPaymentLinkResponse response = paymentService.createAdminPaymentLink(id, request.amountCents());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
```

Add imports: `com.myhive.backend.dto.AdminPaymentLinkRequest`, `com.myhive.backend.dto.AdminPaymentLinkResponse`, `com.myhive.backend.service.PaymentService`, `jakarta.validation.Valid`, `org.springframework.web.bind.annotation.PostMapping`/`RequestBody`/`PathVariable`, `org.springframework.http.HttpStatus` (most already present).

- [ ] **Step 5: Run test to verify it passes**

Run: `cd myhive-backend && ./gradlew test --tests '*AdminController*Test'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/dto/AdminPaymentLinkRequest.java myhive-backend/src/main/java/com/myhive/backend/controller/AdminController.java myhive-backend/src/test/java/com/myhive/backend/controller/
git commit -m "feat(admin): endpoint to create a booking payment link"
```

---

### Task 6: Surface amount-paid + payment links on the booking DTO

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/dto/BookingDTO.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/BookingService.java` (the DTO mapping used by `getBookingById`)
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/BookingServiceTest.java`

**Interfaces:**
- Produces: `BookingDTO.amountPaid` (BigDecimal), `BookingDTO.depositAmount` (BigDecimal), `BookingDTO.paymentLinks` (`List<PaymentLinkDTO>`); nested `PaymentLinkDTO(UUID id, BigDecimal amount, boolean paid, String url)` populated from the booking's `BALANCE` shares.

- [ ] **Step 1: Read the current DTO + mapper**

Run: open `BookingDTO.java` and find the private mapping method in `BookingService` (search for `new BookingDTO` / `toDTO`). Confirm the exact field/builder style (record vs `@Data` class) before editing — match it.

- [ ] **Step 2: Write the failing test**

Add to `BookingServiceTest.java`:

```java
    @Test
    void getBookingById_includesAmountPaidAndPaymentLinks() {
        Booking booking = TestDataFactory.booking(BookingStatus.DEPOSIT_PAID);
        booking.setTotalAmount(new BigDecimal("40.00"));
        booking.setAmountPaid(new BigDecimal("12.00"));
        booking.setBookingItems(List.of());
        BookingPaymentShare link = new BookingPaymentShare();
        link.setId(UUID.randomUUID());
        link.setType(com.myhive.backend.model.PaymentShareType.BALANCE);
        link.setAmount(new BigDecimal("28.00"));
        link.setPaid(false);
        link.setPaymentUrl("https://pay/plink_1");
        // however BookingService loads shares for the DTO — stub that repository call to return this link
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(shareRepository.findByBookingId(booking.getId())).thenReturn(List.of(link));

        BookingDTO dto = bookingService.getBookingById(booking.getId());

        assertThat(dto.getAmountPaid()).isEqualByComparingTo(new BigDecimal("12.00"));
        assertThat(dto.getPaymentLinks()).hasSize(1);
        assertThat(dto.getPaymentLinks().get(0).getUrl()).isEqualTo("https://pay/plink_1");
        assertThat(dto.getPaymentLinks().get(0).isPaid()).isFalse();
    }
```

If `BookingService` does not currently hold a `BookingPaymentShareRepository`, add it as a constructor dependency (the class uses field injection / `@RequiredArgsConstructor` — match the style) and register the `@Mock` in the test. Adjust the accessor style (`getAmountPaid()` vs record `amountPaid()`) to match the DTO.

- [ ] **Step 3: Run test to verify it fails**

Run: `cd myhive-backend && ./gradlew test --tests '*BookingServiceTest'`
Expected: FAIL — no `getPaymentLinks`/`getAmountPaid`.

- [ ] **Step 4: Implement**

Add to `BookingDTO.java`: `amountPaid`, `depositAmount` fields and a nested `PaymentLinkDTO`:

```java
    private BigDecimal amountPaid;
    private BigDecimal depositAmount;
    private java.util.List<PaymentLinkDTO> paymentLinks;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentLinkDTO {
        private java.util.UUID id;
        private BigDecimal amount;
        private boolean paid;
        private String url;
    }
```

In the `BookingService` DTO mapper for `getBookingById`, set `amountPaid`/`depositAmount` from the entity and map the booking's `BALANCE` shares to `PaymentLinkDTO` (skip shares with a null `paymentUrl`):

```java
        dto.setAmountPaid(booking.getAmountPaid());
        dto.setDepositAmount(booking.getDepositAmount());
        dto.setPaymentLinks(shareRepository.findByBookingId(booking.getId()).stream()
                .filter(s -> s.getType() == PaymentShareType.BALANCE && s.getPaymentUrl() != null)
                .map(s -> new BookingDTO.PaymentLinkDTO(s.getId(), s.getAmount(), s.isPaid(), s.getPaymentUrl()))
                .toList());
```

(Match the DTO's actual construction style — builder/setters/record.)

- [ ] **Step 5: Run test to verify it passes**

Run: `cd myhive-backend && ./gradlew test --tests '*BookingServiceTest'`
Expected: PASS.

- [ ] **Step 6: Full backend suite + commit**

Run: `cd myhive-backend && ./gradlew test`
Expected: BUILD SUCCESSFUL.

```bash
git add myhive-backend/src/main/java/com/myhive/backend/dto/BookingDTO.java myhive-backend/src/main/java/com/myhive/backend/service/BookingService.java myhive-backend/src/test/java/com/myhive/backend/service/BookingServiceTest.java
git commit -m "feat(admin): expose amountPaid + payment links on booking DTO"
```

---

### Task 7: Admin API client method

**Files:**
- Modify: `myhive-react-app/src/services/adminApi.js`
- Test: `myhive-react-app/src/services/adminApi.test.js`

**Interfaces:**
- Produces: `adminApi.createBookingPaymentLink(id, amountCents) → Promise<{ url, amount, shareId }>` (POST with JWT).

- [ ] **Step 1: Read the pattern**

Open `adminApi.js` and copy the exact shape of an existing POST method (headers/JWT/error handling) — match it precisely.

- [ ] **Step 2: Write the failing test**

Add to `adminApi.test.js`, mirroring an existing POST test (mock `fetch`, assert URL + method + body):

```javascript
test('createBookingPaymentLink POSTs amountCents and returns the link', async () => {
    global.fetch = jest.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ url: 'https://pay/plink_1', amount: 28, shareId: 's1' }),
    });
    const api = makeAdminApi(); // however the test constructs the client with a token
    const result = await api.createBookingPaymentLink('b1', 2800);
    expect(result.url).toBe('https://pay/plink_1');
    const [url, opts] = global.fetch.mock.calls[0];
    expect(url).toContain('/admin/bookings/b1/payment-link');
    expect(opts.method).toBe('POST');
    expect(JSON.parse(opts.body)).toEqual({ amountCents: 2800 });
});
```

(Adapt construction/mocking to the existing `adminApi.test.js` conventions.)

- [ ] **Step 3: Run to verify it fails**

Run: `cd myhive-react-app && CI=true npx react-scripts test --watchAll=false --testPathPattern="adminApi"`
Expected: FAIL.

- [ ] **Step 4: Implement**

Add to `adminApi.js`, matching the existing POST helper style:

```javascript
    createBookingPaymentLink(id, amountCents) {
        return this.post(`/admin/bookings/${id}/payment-link`, { amountCents });
    },
```

(Use the module's actual request helper — if methods call a shared `request`/`post` with JWT headers, reuse it; otherwise replicate the fetch+auth block from a sibling method.)

- [ ] **Step 5: Run to verify it passes**

Run: `cd myhive-react-app && CI=true npx react-scripts test --watchAll=false --testPathPattern="adminApi"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add myhive-react-app/src/services/adminApi.js myhive-react-app/src/services/adminApi.test.js
git commit -m "feat(admin): adminApi.createBookingPaymentLink"
```

---

### Task 8: Payment panel on `AdminBookingDetail`

**Files:**
- Modify: `myhive-react-app/src/pages/AdminBookingDetail.js`
- Test: Create `myhive-react-app/src/pages/AdminBookingDetail.test.js`

**Interfaces:**
- Consumes: `adminApi.createBookingPaymentLink`, `booking.totalAmount`, `booking.amountPaid`, `booking.status`, `booking.paymentLinks`, `formatAmount`, the shared clipboard util (search `utils` for `copyToClipboard`/`clipboard`; reuse it).

- [ ] **Step 1: Write the failing test**

`AdminBookingDetail.test.js` — render with a mocked `useAdminApi` returning a `DEPOSIT_PAID` booking (total 40, amountPaid 12, no links), assert the balance (28.00) shows and "Create payment link" calls the API with cents:

```javascript
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import AdminBookingDetail from './AdminBookingDetail';

const createBookingPaymentLink = jest.fn();
const getBookingById = jest.fn();
jest.mock('../hooks/useAdminApi', () => ({
    useAdminApi: () => ({ getBookingById, createBookingPaymentLink }),
}));
jest.mock('../hooks/useAuthErrorHandler', () => ({ useAuthErrorHandler: () => () => false }));

function renderPage() {
    return render(
        <MemoryRouter initialEntries={['/admin/bookings/b1']}>
            <Routes>
                <Route path="/admin/bookings/:id" element={<AdminBookingDetail />} />
            </Routes>
        </MemoryRouter>
    );
}

test('shows balance and creates a payment link with cents', async () => {
    const user = userEvent.setup();
    getBookingById.mockResolvedValue({
        id: 'b1', status: 'DEPOSIT_PAID', userEmail: 'x@y.z',
        totalAmount: 40, amountPaid: 12, items: [], paymentLinks: [],
    });
    createBookingPaymentLink.mockResolvedValue({ url: 'https://pay/plink_1', amount: 28, shareId: 's1' });

    renderPage();

    expect(await screen.findByText(/Payment/i)).toBeInTheDocument();
    // balance due 40 - 12 = 28
    expect(screen.getByLabelText(/Amount/i)).toHaveValue(28);

    await user.click(screen.getByRole('button', { name: /Create payment link/i }));

    await waitFor(() => expect(createBookingPaymentLink).toHaveBeenCalledWith('b1', 2800));
    expect(await screen.findByText('https://pay/plink_1')).toBeInTheDocument();
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd myhive-react-app && CI=true npx react-scripts test --watchAll=false --testPathPattern="AdminBookingDetail"`
Expected: FAIL — no Payment panel.

- [ ] **Step 3: Implement the Payment card**

In `AdminBookingDetail.js`: add state + handler near the top of the component:

```javascript
    const balanceDue = Math.max(0, (booking?.totalAmount || 0) - (booking?.amountPaid || 0));
    const [amount, setAmount] = useState('');
    const [creating, setCreating] = useState(false);
    const [linkError, setLinkError] = useState('');

    useEffect(() => {
        if (booking) {
            setAmount(balanceDue > 0 ? String(balanceDue) : '');
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [booking?.id]);

    const handleCreateLink = async () => {
        const cents = Math.round(parseFloat(amount) * 100);
        if (!Number.isFinite(cents) || cents < 50) {
            setLinkError('Enter a valid amount (min €0.50)');
            return;
        }
        try {
            setCreating(true);
            setLinkError('');
            await adminApi.createBookingPaymentLink(id, cents);
            await fetchBooking();
        } catch (err) {
            if (handleAuthError(err)) return;
            setLinkError(err.message || 'Failed to create payment link');
        } finally {
            setCreating(false);
        }
    };
```

Render a Payment `Card` (place it after the "Booking Details" card, hidden only for `CANCELLED`):

```jsx
                    {booking.status?.toUpperCase() !== 'CANCELLED' && (
                        <Card className="shadow-sm mt-3">
                            <Card.Header className="border-bottom">
                                <h6 className="fw-semibold mb-0">Payment</h6>
                            </Card.Header>
                            <Card.Body>
                                <Row className="g-3 mb-3">
                                    <Col sm={4}><div className="text-muted small">Total</div>
                                        <div className="fw-semibold">{formatAmount(booking.totalAmount)}</div></Col>
                                    <Col sm={4}><div className="text-muted small">Paid</div>
                                        <div className="fw-semibold">{formatAmount(booking.amountPaid || 0)}</div></Col>
                                    <Col sm={4}><div className="text-muted small">Balance due</div>
                                        <div className="fw-bold">{formatAmount(balanceDue)}</div></Col>
                                </Row>
                                {linkError && <Alert variant="danger" className="py-2">{linkError}</Alert>}
                                <div className="d-flex gap-2 align-items-end">
                                    <div>
                                        <label htmlFor="pl-amount" className="text-muted small d-block">Amount (€)</label>
                                        <input id="pl-amount" type="number" step="0.01" min="0.5"
                                               className="form-control" style={{maxWidth: '10rem'}}
                                               value={amount} onChange={(e) => setAmount(e.target.value)}/>
                                    </div>
                                    <Button variant="primary" onClick={handleCreateLink} disabled={creating}>
                                        {creating ? 'Creating…' : 'Create payment link'}
                                    </Button>
                                </div>
                                {booking.paymentLinks?.length > 0 && (
                                    <Table responsive className="mt-3 mb-0 align-middle">
                                        <thead><tr>
                                            <th className="small text-muted text-uppercase">Amount</th>
                                            <th className="small text-muted text-uppercase">Status</th>
                                            <th className="small text-muted text-uppercase">Link</th>
                                        </tr></thead>
                                        <tbody>
                                        {booking.paymentLinks.map((pl) => (
                                            <tr key={pl.id}>
                                                <td className="small">{formatAmount(pl.amount)}</td>
                                                <td><Badge bg={pl.paid ? 'success' : 'secondary'}>
                                                    {pl.paid ? 'Paid' : 'Unpaid'}</Badge></td>
                                                <td className="small">
                                                    {pl.paid ? <span className="text-muted">—</span> : (
                                                        <div className="d-flex align-items-center gap-2">
                                                            <span className="text-break">{pl.url}</span>
                                                            <Button size="sm" variant="outline-secondary"
                                                                    onClick={() => navigator.clipboard.writeText(pl.url)}>
                                                                Copy</Button>
                                                        </div>
                                                    )}
                                                </td>
                                            </tr>
                                        ))}
                                        </tbody>
                                    </Table>
                                )}
                            </Card.Body>
                        </Card>
                    )}
```

Ensure `useState`/`useEffect` are imported (they are) and `Alert`/`Badge`/`Table`/`Button`/`Card`/`Row`/`Col` are already imported (they are). If the repo has a shared clipboard util, use it instead of `navigator.clipboard.writeText`.

- [ ] **Step 4: Run to verify it passes**

Run: `cd myhive-react-app && CI=true npx react-scripts test --watchAll=false --testPathPattern="AdminBookingDetail"`
Expected: PASS.

- [ ] **Step 5: Full frontend suite + commit**

Run: `cd myhive-react-app && CI=true npx react-scripts test --watchAll=false`
Expected: all pass.

```bash
git add myhive-react-app/src/pages/AdminBookingDetail.js myhive-react-app/src/pages/AdminBookingDetail.test.js
git commit -m "feat(admin): payment link panel on booking detail"
```

---

## Manual verification (local)

1. `npm run dev` (root) — backend + frontend + `stripe listen`.
2. Open a `DEPOSIT_PAID` booking in the admin panel → Payment card shows Total / Paid / Balance due; Amount prefilled with the balance.
3. Edit the amount, click **Create payment link** → a row appears with the URL + Copy.
4. Open the URL, pay with `4242 4242 4242 4242` → webhook fires; refresh the booking → link shows **Paid**, status `PAID`/`PARTIALLY_PAID`, `amountPaid` updated, and the Stripe dashboard shows the link deactivated.
5. On a `PAID` booking, the panel still allows creating an add-on link.

## Notes / follow-ups

- The existing `BALANCE_SHARE`/`BALANCE_FULL` enum values remain unused; cleaning them up is out of scope.
- `stripe-java` 32.2.0 Payment Link API surface (`Price.create`, `PaymentLink.create`, `PaymentLinkUpdateParams.setActive`) — verify exact builder names against the pinned version during Task 2; adjust if the SDK differs.
