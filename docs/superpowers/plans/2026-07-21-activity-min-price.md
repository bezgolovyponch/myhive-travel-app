# Activity Minimum Price (Group Minimum) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Per-activity group minimum price: a cart line never bills below `minPrice`; UI shows "from" pricing plus a "Group minimum €X" note.

**Architecture:** `Activity.min_price` (null/0 = no minimum) with a `BookingItem.min_price` snapshot (same pattern as `price`). The floor `line = max(price × travelers, minPrice)` is applied per line before any package discount, in the two existing total calculators: `BookingService.calculateTotal` (backend) and `tripPricing.js` (frontend cart). Spec: `docs/superpowers/specs/2026-07-21-activity-min-price-design.md`.

**Tech Stack:** Spring Boot 4.0 / Java 25 / JUnit 5 + Mockito + AssertJ; React 19 (CRA) / Jest + RTL.

## Global Constraints

- Branch: `feat/activity-min-price` (already created, spec committed).
- Floor semantics: `line = max(price × travelers, minPrice)`; `minPrice` null **or** 0 = no minimum. Package groups: floor each line first, then apply `discountPct` to the group sum.
- The client's `minPrice` value is NEVER copied into the `BookingItem` snapshot — only `activity.getMinPrice()` from the catalog (SEC-1). Custom lines (no `activityId`) get a null snapshot.
- UI copy (exact strings): price prefix `from ` (e.g. `from €50 / person`), note `Group minimum €300`, Trip Builder line marker ` (group min)`.
- No wildcard imports, one variable per declaration, braces always (Google Java Style / CLAUDE.md).
- Test style: `expected`-prefixed variables for arrange+assert values; DTOs built inline when field values matter.
- All money values `BigDecimal` (backend), `precision = 10, scale = 2` for min_price columns. No DB migration files — prod runs `ddl-auto=update`, dev/test `create-drop`.
- Backend tests: `cd myhive-backend && ./gradlew test --tests '<pattern>'`. Frontend: `cd myhive-react-app && npm test -- --watchAll=false <pattern>`.
- Commit after every task (small, conventional-commit messages, `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>` footer).

---

### Task 1: `Activity.minPrice` + `ActivityDTO` + service mapping + dev sample data

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/entity/Activity.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/dto/ActivityDTO.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/ActivityService.java`
- Modify: `myhive-backend/src/main/resources/data.sql`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/ActivityServiceTest.java`

**Interfaces:**
- Produces: `Activity.getMinPrice()` / `setMinPrice(BigDecimal)`; `ActivityDTO.getMinPrice()` / `setMinPrice(BigDecimal)`. Every later backend task depends on `Activity.getMinPrice()`.

- [ ] **Step 1: Write the failing tests**

Add to `ActivityServiceTest.java` (uses the existing `activity`/`destination` fixtures from `setUp()`):

```java
@Test
void getActivityById_mapsMinPrice() {
    BigDecimal expectedMinPrice = new BigDecimal("300.00");
    activity.setMinPrice(expectedMinPrice);
    when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));

    ActivityDTO result = activityService.getActivityById(activity.getId());

    assertThat(result.getMinPrice()).isEqualByComparingTo(expectedMinPrice);
}

@Test
void createActivity_appliesMinPriceToEntity() {
    BigDecimal expectedMinPrice = new BigDecimal("250.00");
    ActivityDTO dto = new ActivityDTO();
    dto.setDestinationId(destination.getId());
    dto.setName("Quad Safari");
    dto.setPrice(new BigDecimal("50.00"));
    dto.setMinPrice(expectedMinPrice);
    when(destinationRepository.findById(destination.getId())).thenReturn(Optional.of(destination));
    when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

    ActivityDTO result = activityService.createActivity(dto);

    assertThat(result.getMinPrice()).isEqualByComparingTo(expectedMinPrice);
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd myhive-backend && ./gradlew test --tests '*ActivityServiceTest'`
Expected: COMPILATION FAILURE — `setMinPrice`/`getMinPrice` not defined.

- [ ] **Step 3: Implement**

`Activity.java` — after the `price` field (line ~56):

```java
    /** Group minimum for one booking line: the line never bills below this. Null or 0 = no minimum. */
    @Column(name = "min_price", precision = 10, scale = 2)
    private BigDecimal minPrice;
```

`ActivityDTO.java` — after the `price` field; add `import jakarta.validation.constraints.PositiveOrZero;`:

```java
    @PositiveOrZero(message = "Minimum price must not be negative")
    private BigDecimal minPrice;
```

`ActivityService.java` — in `applyDtoToEntity` after `activity.setPrice(dto.getPrice());`:

```java
        activity.setMinPrice(dto.getMinPrice());
```

and in `convertToDTO` after `dto.setPrice(activity.getPrice());`:

```java
        dto.setMinPrice(activity.getMinPrice());
```

`data.sql` — append after the last activity INSERT block (gives dev a floored example: €120 × small groups < €600):

```sql
-- Group-minimum example: sunset-boat-party requires a €600 minimum order
UPDATE activities SET min_price = 600.00 WHERE slug = 'sunset-boat-party';
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd myhive-backend && ./gradlew test --tests '*ActivityServiceTest'`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/entity/Activity.java myhive-backend/src/main/java/com/myhive/backend/dto/ActivityDTO.java myhive-backend/src/main/java/com/myhive/backend/service/ActivityService.java myhive-backend/src/main/resources/data.sql myhive-backend/src/test/java/com/myhive/backend/service/ActivityServiceTest.java
git commit -m "feat(activity): min_price field with DTO mapping and dev sample"
```

---

### Task 2: `BookingItem.minPrice` snapshot + floored `calculateTotal`

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/entity/BookingItem.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/BookingService.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/BookingServiceTest.java`

**Interfaces:**
- Consumes: `Activity.getMinPrice()` (Task 1).
- Produces: `BookingItem.getMinPrice()` / `setMinPrice(BigDecimal)` (used by Task 4's `toExportRequest`). `calculateTotal` floors every line; `verifyChargeablePricing` keeps passing for floored bookings because it recomputes via the same method.

- [ ] **Step 1: Write the failing tests**

Add to `BookingServiceTest.java`. The `TestDataFactory.tripExportRequest()` fixture has 2 travelers and one activity line (established by the existing `createBookingEntity_buildsPendingEntityWithTotal_andSendsNoEmail` test: price 75.00 → total 150.00). Add `import static org.assertj.core.api.Assertions.assertThatCode;` if not present.

```java
@Test
void createBookingEntity_groupMinimum_floorsLineTotal() {
    // 2 travelers × €50 = €100 < €300 group minimum -> the line bills €300.
    TripExportRequest request = TestDataFactory.tripExportRequest();
    activity.setPrice(new BigDecimal("50.00"));
    activity.setMinPrice(new BigDecimal("300.00"));
    UUID activityId = request.getDestinations().getFirst().getActivities().getFirst().getActivityId();
    when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
    when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
        Booking b = inv.getArgument(0);
        b.setId(UUID.randomUUID());
        return b;
    });

    Booking result = bookingService.createBookingEntity(request);

    BigDecimal expectedTotal = new BigDecimal("300.00");
    assertThat(result.getTotalAmount()).isEqualByComparingTo(expectedTotal);
    // SEC-1: the snapshot comes from the catalog entity, not the request body.
    assertThat(result.getBookingItems().getFirst().getMinPrice()).isEqualByComparingTo("300.00");
}

@Test
void createBookingEntity_groupMinimumBelowLineTotal_keepsRegularPricing() {
    // 2 travelers × €50 = €100 >= €80 minimum -> per-person math unchanged.
    TripExportRequest request = TestDataFactory.tripExportRequest();
    activity.setPrice(new BigDecimal("50.00"));
    activity.setMinPrice(new BigDecimal("80.00"));
    UUID activityId = request.getDestinations().getFirst().getActivities().getFirst().getActivityId();
    when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
    when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

    Booking result = bookingService.createBookingEntity(request);

    assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
}

@Test
void createBookingEntity_packageGroup_floorsLinesBeforeDiscount() {
    // Line A: max(2×€50, €300) = 300; line B: 2×€40 = 80. (300+80) − 10% = 342.00
    Activity floored = TestDataFactory.activity(destination);
    floored.setPrice(new BigDecimal("50.00"));
    floored.setMinPrice(new BigDecimal("300.00"));
    Activity regular = TestDataFactory.activity(destination);
    regular.setPrice(new BigDecimal("40.00"));
    com.myhive.backend.entity.Package pkg = TestDataFactory.pkg(destination);

    TripExportRequest request = TestDataFactory.tripExportRequest();
    TripExportRequest.ActivityExport ae1 = new TripExportRequest.ActivityExport();
    ae1.setActivityId(floored.getId());
    ae1.setActivityName("Boat Rental");
    ae1.setPackageId(pkg.getId());
    ae1.setPackageDiscountPct(new BigDecimal("10.00"));
    TripExportRequest.ActivityExport ae2 = new TripExportRequest.ActivityExport();
    ae2.setActivityId(regular.getId());
    ae2.setActivityName("Bar Crawl");
    ae2.setPackageId(pkg.getId());
    ae2.setPackageDiscountPct(new BigDecimal("10.00"));
    request.getDestinations().getFirst().setActivities(List.of(ae1, ae2));

    when(activityRepository.findById(floored.getId())).thenReturn(Optional.of(floored));
    when(activityRepository.findById(regular.getId())).thenReturn(Optional.of(regular));
    when(packageRepository.findById(pkg.getId())).thenReturn(Optional.of(pkg));
    when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

    Booking result = bookingService.createBookingEntity(request);

    BigDecimal expectedTotal = new BigDecimal("342.00");
    assertThat(result.getTotalAmount()).isEqualByComparingTo(expectedTotal);
}

@Test
void verifyChargeablePricing_flooredStoredTotal_passes() {
    // A booking whose stored total came from a floored line must recompute identically.
    Booking booking = new Booking();
    BookingItem item = new BookingItem();
    item.setActivity(activity);
    item.setPrice(new BigDecimal("50.00"));
    item.setQuantity(2);
    item.setMinPrice(new BigDecimal("300.00"));
    booking.setBookingItems(List.of(item));
    booking.setTotalAmount(new BigDecimal("300.00"));

    assertThatCode(() -> bookingService.verifyChargeablePricing(booking))
            .doesNotThrowAnyException();
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd myhive-backend && ./gradlew test --tests '*BookingServiceTest'`
Expected: COMPILATION FAILURE — `BookingItem.setMinPrice` not defined.

- [ ] **Step 3: Implement**

`BookingItem.java` — after the `price` field:

```java
    /** Snapshot of the activity's group minimum at booking time (same pattern as {@link #price}). */
    @Column(name = "min_price", precision = 10, scale = 2)
    private BigDecimal minPrice;
```

`BookingService.java` — in `createBookingEntity`, inside the `if (act.getActivityId() != null)` branch, directly after `item.setPrice(activity.getPrice());`:

```java
                    item.setMinPrice(activity.getMinPrice()); // trusted server-side floor (SEC-1)
```

Replace `getGroupTotal` with a floored per-line computation (keep the `@NonNull` annotation import already present):

```java
    private static @NonNull BigDecimal getGroupTotal(Map.Entry<UUID, List<BookingItem>> entry) {
        BigDecimal groupTotal = BigDecimal.ZERO;
        for (BookingItem it : entry.getValue()) {
            groupTotal = groupTotal.add(lineTotal(it));
        }
        if (entry.getKey() != null) {
            groupTotal = MoneyMath.applyDiscountPct(groupTotal,
                    entry.getValue().getFirst().getPackageDiscountPct());
        }
        return groupTotal;
    }

    /** Group-minimum floor: a line never bills below the activity's minPrice snapshot. */
    private static BigDecimal lineTotal(BookingItem it) {
        BigDecimal qty = BigDecimal.valueOf(it.getQuantity() == null ? 1 : it.getQuantity());
        BigDecimal line = it.getPrice().multiply(qty);
        BigDecimal minPrice = it.getMinPrice();
        if (minPrice != null && line.compareTo(minPrice) < 0) {
            return minPrice;
        }
        return line;
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd myhive-backend && ./gradlew test --tests '*BookingServiceTest' --tests '*PaymentServiceTest'`
Expected: BUILD SUCCESSFUL (PaymentServiceTest included because deposits derive from `totalAmount`).

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/entity/BookingItem.java myhive-backend/src/main/java/com/myhive/backend/service/BookingService.java myhive-backend/src/test/java/com/myhive/backend/service/BookingServiceTest.java
git commit -m "feat(booking): min_price snapshot and group-minimum floor in totals"
```

---

### Task 3: Vote flow — `ResultActivityDTO.minPrice`, `VoteActivityResponse.minPrice`, floored result total

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/dto/ResultActivityDTO.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/dto/VoteActivityResponse.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionGetResultTest.java`

**Interfaces:**
- Consumes: `Activity.getMinPrice()` (Task 1).
- Produces: `ResultActivityDTO.getMinPrice()` (JSON `minPrice` — Task 8's hydration reads `row.minPrice`); `VoteActivityResponse.getMinPrice()` (swipe deck cards carry `minPrice` — Task 7's SwipeCard reads `card.minPrice`).

- [ ] **Step 1: Write the failing test**

Add to `VoteSessionGetResultTest.java` (uses the file's existing helpers `saveDest`/`saveCat`/`attachCat`/`saveAct`/`createAndPopulate`/`like`):

```java
@Test
void getResult_groupMinimum_floorsTotalAndExposesMinPrice() {
    Destination destination = saveDest();
    Category nightlife = saveCat("Nightlife", "nightlife", true);
    attachCat(destination, nightlife);
    Activity activity = saveAct(destination, "Boat Rental", new BigDecimal("50.00"), 5, Set.of(nightlife));
    activity.setMinPrice(new BigDecimal("300.00"));
    activityRepository.save(activity);

    VoteSession session = createAndPopulate(destination, null,
            List.of(activity.getId()), List.of(), 2);
    like(session, activity, true);

    voteSessionService.closeSession(session.getShareToken(), session.getManagerToken());
    VoteResultResponse response = voteSessionService.getResult(session.getShareToken());

    assertThat(response.getResult().getFirst().getMinPrice()).isEqualByComparingTo("300.00");
    // 2 travelers × €50 = €100 < €300 -> the estimate uses the group minimum,
    // matching what the Trip Builder will compute after hydration.
    assertThat(response.getTotalPrice()).isEqualByComparingTo("300.00");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteSessionGetResultTest'`
Expected: COMPILATION FAILURE — `ResultActivityDTO.getMinPrice` not defined.

- [ ] **Step 3: Implement**

`ResultActivityDTO.java` — after the `price` field:

```java
    // Live catalog value (like slug/imageUrl below): the floor that will apply at booking
    // time comes from the catalog anyway, so the result mirrors it rather than snapshotting.
    private BigDecimal minPrice;
```

`VoteActivityResponse.java` — after the `price` field:

```java
    private BigDecimal minPrice;
```

`VoteSessionService.java`:
1. `getResult` — the `ResultActivityDTO` constructor call gains `activity.getMinPrice()` right after `curated.getPrice()`:

```java
            return new ResultActivityDTO(activityId, curated.getActivityName(),
                    curated.getPrice(), activity.getMinPrice(), like, skip,
                    activity.getSlug(), destinationSlug, activity.getImageUrl(),
                    activity.getDuration(), activity.getDescription(), activity.getIncludes());
```

2. `getResult` — floor the estimate (replace the `totalPrice` computation):

```java
        BigDecimal totalPrice = result.stream()
                .map(r -> flooredLine(r.getPrice(), r.getMinPrice(), travelers))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
```

and add the private helper next to the other private helpers at the bottom of the class:

```java
    /** Group-minimum floor for the result estimate: mirrors BookingService.lineTotal. */
    private static BigDecimal flooredLine(BigDecimal price, BigDecimal minPrice, BigDecimal travelers) {
        BigDecimal line = price.multiply(travelers);
        if (minPrice != null && line.compareTo(minPrice) < 0) {
            return minPrice;
        }
        return line;
    }
```

3. `toActivityResponse` — add `activity.getMinPrice()` after `activity.getPrice()`:

```java
        return new VoteActivityResponse(
                activity.getId(),
                activity.getName(),
                activity.getDescription(),
                activity.getPrice(),
                activity.getMinPrice(),
                activity.getDuration(),
                activity.getImageUrl(),
                activity.getSlug(),
                destinationSlug);
```

(The `VoteActivityResponse` field must sit between `price` and `duration` to match this constructor order.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteSession*'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/dto/ResultActivityDTO.java myhive-backend/src/main/java/com/myhive/backend/dto/VoteActivityResponse.java myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionGetResultTest.java
git commit -m "feat(vote): expose minPrice on deck and result, floor the result estimate"
```

---

### Task 4: Emails — `ActivityExport.minPrice`, `toExportRequest`, "Group minimum applies" note

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/dto/TripExportRequest.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/BookingService.java` (`toExportRequest`)
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/EmailService.java`
- Modify: `myhive-backend/src/main/resources/templates/email/itinerary-confirmation.html`
- Modify: `myhive-backend/src/main/resources/templates/email/booking-notification.html`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/EmailServiceTest.java`, `myhive-backend/src/test/java/com/myhive/backend/service/BookingServiceTest.java`

**Interfaces:**
- Consumes: `BookingItem.getMinPrice()` (Task 2).
- Produces: `TripExportRequest.ActivityExport.getMinPrice()` (JSON `minPrice` — Task 8's export payload sends it); `EmailService.ActivityLineView` with public fields `activity` (`TripExportRequest.ActivityExport`) and `groupMinApplies` (boolean). `DestinationView.standaloneActivities` and `PackageGroup.activities` become `List<ActivityLineView>`.

- [ ] **Step 1: Write the failing tests**

`EmailServiceTest.java`:

```java
@Test
void buildDestinationViews_flagsGroupMinimumOnlyWhenFloorBinds() {
    TripExportRequest.ActivityExport floored = new TripExportRequest.ActivityExport();
    floored.setActivityName("Boat Rental");
    floored.setPrice(50.0);
    floored.setMinPrice(new BigDecimal("300.00"));
    TripExportRequest.ActivityExport regular = new TripExportRequest.ActivityExport();
    regular.setActivityName("Bar Crawl");
    regular.setPrice(40.0);

    TripExportRequest.DestinationExport dest = new TripExportRequest.DestinationExport();
    dest.setDestinationName("Tenerife");
    dest.setActivities(List.of(floored, regular));
    TripExportRequest req = new TripExportRequest();
    req.setNumberOfTravelers(2);
    req.setDestinations(List.of(dest));

    List<EmailService.DestinationView> views = emailService.buildDestinationViews(req);

    List<EmailService.ActivityLineView> lines = views.getFirst().standaloneActivities;
    assertThat(lines.getFirst().groupMinApplies).isTrue();  // 2 × €50 = €100 < €300
    assertThat(lines.get(1).groupMinApplies).isFalse();     // no minimum set
}
```

`BookingServiceTest.java`:

```java
@Test
void toExportRequest_carriesMinPriceSnapshot() {
    Booking booking = new Booking();
    booking.setUserEmail("u@example.com");
    booking.setCustomerName("User");
    BookingItem item = new BookingItem();
    item.setActivity(activity);
    item.setActivityName("Boat Rental");
    item.setDestinationName("Tenerife");
    item.setPrice(new BigDecimal("50.00"));
    item.setQuantity(2);
    item.setMinPrice(new BigDecimal("300.00"));
    booking.setBookingItems(List.of(item));

    TripExportRequest result = bookingService.toExportRequest(booking);

    TripExportRequest.ActivityExport exported =
            result.getDestinations().getFirst().getActivities().getFirst();
    assertThat(exported.getMinPrice()).isEqualByComparingTo("300.00");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd myhive-backend && ./gradlew test --tests '*EmailServiceTest' --tests '*BookingServiceTest'`
Expected: COMPILATION FAILURE — `ActivityExport.setMinPrice` / `ActivityLineView` not defined.

- [ ] **Step 3: Implement**

`TripExportRequest.ActivityExport` — after the `price` field:

```java
        // Display-only in emails; pricing always uses the server-side snapshot (SEC-1).
        @PositiveOrZero(message = "Activity minimum price must not be negative")
        private BigDecimal minPrice;
```

`BookingService.toExportRequest` — after `activity.setPrice(...)`:

```java
            activity.setMinPrice(item.getMinPrice());
```

`EmailService.java` — replace the two view classes and `buildDestinationViews`:

```java
    public static class DestinationView {
        public String destinationName;
        public String country;
        public Integer duration;
        public String startDate;
        public String endDate;
        public List<PackageGroup> packageGroups = new ArrayList<>();
        public List<ActivityLineView> standaloneActivities = new ArrayList<>();
    }

    public static class PackageGroup {
        public String packageName;
        public BigDecimal discountPct;
        public BigDecimal subtotal;
        public BigDecimal discounted;
        public List<ActivityLineView> activities = new ArrayList<>();
    }

    /** One itinerary line: the exported activity plus whether its group minimum binds. */
    public static class ActivityLineView {
        public TripExportRequest.ActivityExport activity;
        public boolean groupMinApplies;

        ActivityLineView(TripExportRequest.ActivityExport activity, boolean groupMinApplies) {
            this.activity = activity;
            this.groupMinApplies = groupMinApplies;
        }
    }
```

`buildDestinationViews` — wrap each line and compute the flag (per-person subtotals stay untouched):

```java
    List<DestinationView> buildDestinationViews(TripExportRequest tripData) {
        List<DestinationView> views = new ArrayList<>();
        if (tripData.getDestinations() == null) {
            return views;
        }
        int travelers = tripData.getNumberOfTravelers() != null && tripData.getNumberOfTravelers() > 0
                ? tripData.getNumberOfTravelers() : 1;
        for (TripExportRequest.DestinationExport dest : tripData.getDestinations()) {
            DestinationView view = new DestinationView();
            view.destinationName = dest.getDestinationName();
            view.country = dest.getCountry();
            view.duration = dest.getDuration();
            view.startDate = dest.getStartDate();
            view.endDate = dest.getEndDate();

            if (dest.getActivities() != null) {
                Map<UUID, PackageGroup> groupMap = new LinkedHashMap<>();
                for (TripExportRequest.ActivityExport activity : dest.getActivities()) {
                    ActivityLineView line = new ActivityLineView(activity, groupMinApplies(activity, travelers));
                    UUID packageId = activity.getPackageId();
                    if (packageId == null) {
                        view.standaloneActivities.add(line);
                    } else {
                        PackageGroup group = groupMap.computeIfAbsent(packageId, id -> {
                            PackageGroup g = new PackageGroup();
                            g.packageName = activity.getPackageName();
                            g.discountPct = activity.getPackageDiscountPct();
                            return g;
                        });
                        group.activities.add(line);
                    }
                }
                for (PackageGroup group : groupMap.values()) {
                    BigDecimal subtotal = BigDecimal.ZERO;
                    for (ActivityLineView line : group.activities) {
                        BigDecimal activityPrice = line.activity.getPrice() != null
                                ? BigDecimal.valueOf(line.activity.getPrice())
                                : BigDecimal.ZERO;
                        subtotal = subtotal.add(activityPrice);
                    }
                    group.subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
                    group.discounted = MoneyMath.applyDiscountPct(subtotal, group.discountPct);
                    view.packageGroups.add(group);
                }
            }
            views.add(view);
        }
        return views;
    }

    /** True when price × travelers < minPrice — the booked total for this line is the group minimum. */
    private static boolean groupMinApplies(TripExportRequest.ActivityExport activity, int travelers) {
        if (activity.getMinPrice() == null || activity.getPrice() == null) {
            return false;
        }
        BigDecimal line = BigDecimal.valueOf(activity.getPrice())
                .multiply(BigDecimal.valueOf(travelers));
        return line.compareTo(activity.getMinPrice()) < 0;
    }
```

Templates — in **both** `itinerary-confirmation.html` and `booking-notification.html`, the two activity loops change from iterating `ActivityExport` to iterating `ActivityLineView` (rename loop var to `line`, prefix field refs with `line.activity.`, add the note). Example for the standalone block of `itinerary-confirmation.html` (apply the same mechanical change to the package block and to both blocks of `booking-notification.html`):

```html
<div th:each="line : ${destinationView.standaloneActivities}" class="activity">
    <h4 th:text="${line.activity.activityName}">Activity Name</h4>
    <p><strong>Category:</strong> <span th:text="${line.activity.category}">Category</span></p>
    <p><strong>Description:</strong> <span th:text="${line.activity.description ?: 'No description available'}">Description</span></p>
    <p><strong>Duration:</strong> <span th:text="${line.activity.duration ?: 0}">0</span> hours</p>
    <p><strong>Price:</strong> <span class="price">€<span th:text="${#numbers.formatDecimal(line.activity.price ?: 0, 1, 2)}">0.00</span></span></p>
    <p th:if="${line.groupMinApplies}"><strong>Group minimum:</strong>
        <span class="price">€<span th:text="${#numbers.formatDecimal(line.activity.minPrice, 1, 2)}">0.00</span></span> applies</p>
</div>
```

Update the **existing** `EmailServiceTest` assertions that read `view.standaloneActivities.getFirst().getActivityName()` / `group.activities` to go through `.activity.` (e.g. `lines.getFirst().activity.getActivityName()`).

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd myhive-backend && ./gradlew test --tests '*EmailServiceTest' --tests '*BookingServiceTest'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/dto/TripExportRequest.java myhive-backend/src/main/java/com/myhive/backend/service/BookingService.java myhive-backend/src/main/java/com/myhive/backend/service/EmailService.java myhive-backend/src/main/resources/templates/email/itinerary-confirmation.html myhive-backend/src/main/resources/templates/email/booking-notification.html myhive-backend/src/test/java/com/myhive/backend/service/EmailServiceTest.java myhive-backend/src/test/java/com/myhive/backend/service/BookingServiceTest.java
git commit -m "feat(email): group-minimum note on itinerary lines"
```

---

### Task 5: CSV export/import — optional `min_price` column

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/activity/ActivityCsvExporter.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/activity/ActivityCsvParser.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/activity/CsvImportTypes.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/activity/ActivityCsvRowValidator.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/activity/ActivityCsvDiffer.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/activity/ActivityCsvImporter.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/activity/ActivityCsvExporterTest.java`, `ActivityCsvImporterTest.java`

**Interfaces:**
- Consumes: `Activity.getMinPrice()` / `setMinPrice` (Task 1).
- Produces: CSV column `min_price` (optional, mutable): absent column → field untouched; blank cell → minimum cleared; value ≥ 0 with ≤ 2 decimals.

- [ ] **Step 1: Write the failing tests**

`ActivityCsvExporterTest.java` — update the existing header assertion (it asserts the exact header string) to:

```java
                "id,slug,destination_slug,name,description,price,min_price,duration,category_slugs,image_url,includes,featured_weight");
```

and add:

```java
@Test
void export_writesMinPriceAndBlankWhenNull() {
    // Arrange two activities following this test class's existing fixture pattern:
    // one with minPrice 300.00, one with minPrice null.
    // Assert the data rows contain ",300.00," for the first and ",," (blank min_price cell)
    // for the second, at the column position right after price.
    Activity withMin = TestDataFactory.activity(destination);
    withMin.setMinPrice(new BigDecimal("300.00"));
    Activity withoutMin = TestDataFactory.activity(destination);
    when(activityRepository.findAll()).thenReturn(List.of(withMin, withoutMin));

    String csv = exporter.exportAll();

    String[] lines = csv.split("\r?\n");
    assertThat(lines[1]).contains(",300.00,");
    assertThat(lines[2]).doesNotContain("300.00");
}
```

(Adapt the arrange lines to this test class's existing repository-mock fixtures — the class already exports activities built via `TestDataFactory`.)

`ActivityCsvImporterTest.java` — the existing header helpers omit `min_price` (column optional → old sheets keep working; those tests stay green). Add, following the file's existing preview/apply test pattern with a header that includes the new column:

```java
@Test
void minPrice_updatedWhenColumnPresent() {
    // Row with min_price=250.00 for an activity whose DB value is null ->
    // preview diff contains "min_price" and apply persists 250.00.
}

@Test
void minPrice_blankCellClearsMinimum() {
    // DB value 300.00, CSV cell blank (column present) -> diff old=300.00 new=0.00,
    // apply persists null.
}

@Test
void minPrice_absentColumnLeavesValueUntouched() {
    // DB value 300.00, CSV without the min_price column -> no "min_price" diff,
    // apply keeps 300.00.
}

@Test
void minPrice_negative_isRejected() {
    // min_price=-5 -> RowError INVALID_DECIMAL for column "min_price".
}
```

Write these four bodies following the exact arrange/act/assert style of the file's existing `featured_weight` tests (same helpers for building CSV strings, running preview, and applying) — mirror them 1:1, substituting column name `min_price` and `BigDecimal` values.

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd myhive-backend && ./gradlew test --tests '*ActivityCsv*'`
Expected: FAIL — exporter header mismatch; importer tests fail on unknown column `min_price`.

- [ ] **Step 3: Implement**

`ActivityCsvExporter.java` — HEADER gains `"min_price"` right after `"price"`:

```java
    static final String[] HEADER = {
            "id", "slug", "destination_slug", "name", "description",
            "price", "min_price", "duration", "category_slugs", "image_url", "includes",
            "featured_weight"
    };
```

and `toRow` gains `formatPrice(a.getMinPrice()),` right after `formatPrice(a.getPrice()),` (the existing `formatPrice` already renders null as `""`).

`ActivityCsvParser.java`:

```java
    static final Set<String> OPTIONAL_COLUMNS = Set.of("featured_weight", "min_price");
```

`CsvImportTypes.java` — `ValidatedRow` gains a field after `featuredWeight`:

```java
        // Optional mutable field: null means "column absent from CSV; do not update".
        // BigDecimal.ZERO means "blank cell -> clear the minimum". Otherwise the new value.
        BigDecimal minPrice,
```

`ActivityCsvRowValidator.java` — in `validate(...)` add `BigDecimal minPrice = parseMinPrice(raw, errors);` after the `featuredWeight` line, pass `minPrice` into the `ValidatedRow` constructor (position matching the record), and add:

```java
    /**
     * Optional column (same convention as featured_weight): null = column absent -> do not
     * update; BigDecimal.ZERO = blank cell -> clear the minimum; otherwise the parsed value.
     */
    private BigDecimal parseMinPrice(RawRow raw, List<ActivityImportPreviewDTO.RowError> errors) {
        if (!raw.hasColumn("min_price")) {
            return null;
        }
        String rawValue = raw.get("min_price");
        if (rawValue.isEmpty()) {
            return BigDecimal.ZERO;
        }
        if (rawValue.contains(",")) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.INVALID_DECIMAL,
                    "min_price must use '.' as decimal separator: " + rawValue, "min_price"));
            return null;
        }
        BigDecimal minPrice;
        try {
            minPrice = new BigDecimal(rawValue);
        } catch (NumberFormatException e) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.INVALID_DECIMAL,
                    "min_price is not a valid decimal: " + rawValue, "min_price"));
            return null;
        }
        if (minPrice.scale() > 2) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.INVALID_DECIMAL,
                    "min_price has more than 2 decimal places: " + rawValue, "min_price"));
        }
        if (minPrice.signum() < 0) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.INVALID_DECIMAL,
                    "min_price must be non-negative: " + rawValue, "min_price"));
        }
        return minPrice;
    }
```

`ActivityCsvDiffer.java` — in `computeFieldChanges`, after the `featured_weight` block (null and 0 are both "no minimum", so normalize before comparing to keep export→import roundtrips diff-free):

```java
        if (v.minPrice() != null) {
            BigDecimal dbMin = normalizeMinPrice(db.getMinPrice());
            BigDecimal csvMin = normalizeMinPrice(v.minPrice());
            if (dbMin.compareTo(csvMin) != 0) {
                changes.put("min_price", new ActivityImportPreviewDTO.FieldChange(dbMin, csvMin));
            }
        }
```

and the helper:

```java
    /** Null and 0 both mean "no minimum" — normalize so they never diff against each other. */
    private BigDecimal normalizeMinPrice(BigDecimal value) {
        BigDecimal base = value == null ? BigDecimal.ZERO : value;
        return base.setScale(2, RoundingMode.HALF_UP);
    }
```

`ActivityCsvImporter.java` — in `apply`, after the `featuredWeight` block:

```java
            // Optional column: null means "column absent from CSV — do not touch".
            if (v.minPrice() != null) {
                activity.setMinPrice(v.minPrice().signum() == 0 ? null : v.minPrice());
            }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd myhive-backend && ./gradlew test --tests '*ActivityCsv*'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/activity/ myhive-backend/src/test/java/com/myhive/backend/service/activity/
git commit -m "feat(csv): optional mutable min_price column in activity export/import"
```

---

### Task 6: Frontend cart math — `lineTotal` floor in `tripPricing.js`

**Files:**
- Modify: `myhive-react-app/src/utils/tripPricing.js`
- Test: `myhive-react-app/src/utils/tripPricing.test.js`

**Interfaces:**
- Produces: `lineTotal(item, travelers)` → number (floored line); `groupMinApplied(item, travelers)` → boolean. Both exported; Task 8's TripBuilder consumes them. `computeTripTotal` signature unchanged.

- [ ] **Step 1: Write the failing tests**

Add to `tripPricing.test.js` (import gains the new helpers):

```js
import {computeTripTotal, groupTripItems, lineTotal, groupMinApplied} from './tripPricing';

describe('lineTotal', () => {
    test('floors a line to the group minimum', () => {
        expect(lineTotal({id: 'a1', price: 50, minPrice: 300}, 4)).toBe(300);
    });

    test('uses regular math once travelers clear the minimum', () => {
        expect(lineTotal({id: 'a1', price: 50, minPrice: 300}, 7)).toBe(350);
    });

    test('missing minPrice keeps legacy behavior (old localStorage carts)', () => {
        expect(lineTotal({id: 'a1', price: 50}, 2)).toBe(100);
    });
});

describe('groupMinApplied', () => {
    test('true only while the floor binds', () => {
        expect(groupMinApplied({id: 'a1', price: 50, minPrice: 300}, 4)).toBe(true);
        expect(groupMinApplied({id: 'a1', price: 50, minPrice: 300}, 7)).toBe(false);
        expect(groupMinApplied({id: 'a1', price: 50}, 2)).toBe(false);
    });
});

describe('computeTripTotal with group minimums', () => {
    test('floors a standalone line to the minimum', () => {
        const expectedTotal = 300;
        expect(computeTripTotal([{id: 'a1', price: 50, minPrice: 300}], 4)).toBe(expectedTotal);
    });

    test('floors package lines before the discount', () => {
        const items = [
            {id: 'a1', price: 50, minPrice: 300, packageId: 'p1', packageDiscountPct: 10},
            {id: 'a2', price: 40, packageId: 'p1', packageDiscountPct: 10},
        ];
        // max(2×50, 300) + 2×40 = 380, minus 10% = 342
        const expectedTotal = 342;
        expect(computeTripTotal(items, 2)).toBe(expectedTotal);
    });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd myhive-react-app && npm test -- --watchAll=false tripPricing`
Expected: FAIL — `lineTotal is not a function`.

- [ ] **Step 3: Implement**

In `tripPricing.js`, add next to `priceOf` and rewrite `computeTripTotal`:

```js
function minPriceOf(item) {
    // Same hardening as priceOf: old localStorage carts have no minPrice.
    const n = Number(item.minPrice);
    return Number.isFinite(n) && n > 0 ? n : 0;
}

// The group-minimum floor: a line never totals below the activity's minPrice.
export function lineTotal(item, travelers) {
    return Math.max(priceOf(item) * travelers, minPriceOf(item));
}

export function groupMinApplied(item, travelers) {
    return lineTotal(item, travelers) > priceOf(item) * travelers;
}

export function computeTripTotal(tripItems, travelers) {
    const {standalone, groups} = groupTripItems(tripItems);
    let total = 0;
    standalone.forEach(it => {
        total += lineTotal(it, travelers);
    });
    groups.forEach(g => {
        // Floor each line first, then discount the group sum (floor-before-discount).
        const sub = g.items.reduce((s, it) => s + lineTotal(it, travelers), 0);
        total += sub * (100 - g.packageDiscountPct) / 100;
    });
    return Math.round(total * 100) / 100;
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd myhive-react-app && npm test -- --watchAll=false tripPricing`
Expected: PASS (all existing tests too).

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/utils/tripPricing.js myhive-react-app/src/utils/tripPricing.test.js
git commit -m "feat(pricing): group-minimum floor in cart line totals"
```

---

### Task 7: Frontend display — format helpers + card/detail/preview/swipe surfaces

**Files:**
- Modify: `myhive-react-app/src/utils/format.js`
- Modify: `myhive-react-app/src/components/ActivityCard.js`
- Modify: `myhive-react-app/src/components/ActivityCard.css`
- Modify: `myhive-react-app/src/pages/ActivityDetailPage.js`
- Modify: `myhive-react-app/src/components/ActivityPreviewModal.js`
- Modify: `myhive-react-app/src/components/SwipeCard.js`
- Test: `myhive-react-app/src/utils/format.test.js`, `myhive-react-app/src/components/ActivityCard.test.js`

**Interfaces:**
- Produces: `hasGroupMin(activity)` → boolean; `groupMinNote(activity)` → `'Group minimum €300'` or `null`. Exported from `utils/format.js`; Task 8 may also use them.
- Consumes: `activity.minPrice` delivered by the API (Task 1) and vote deck DTOs (Task 3).

- [ ] **Step 1: Write the failing tests**

`format.test.js`:

```js
import {hasGroupMin, groupMinNote} from './format';

describe('group minimum helpers', () => {
    test('hasGroupMin true only for a positive minPrice', () => {
        expect(hasGroupMin({minPrice: 300})).toBe(true);
        expect(hasGroupMin({minPrice: 0})).toBe(false);
        expect(hasGroupMin({minPrice: null})).toBe(false);
        expect(hasGroupMin({})).toBe(false);
        expect(hasGroupMin(undefined)).toBe(false);
    });

    test('groupMinNote renders the canonical copy', () => {
        expect(groupMinNote({minPrice: 300})).toBe('Group minimum €300');
        expect(groupMinNote({minPrice: 299.5})).toBe('Group minimum €299.50');
        expect(groupMinNote({minPrice: 0})).toBeNull();
    });
});
```

`ActivityCard.test.js` (follow the file's existing render helper for providing TripContext/router):

```js
test('shows from-price and group minimum note when minPrice is set', () => {
    renderCard({id: 'a1', name: 'Boat Rental', price: 50, minPrice: 300});
    expect(screen.getByText(/from €50 \/ person/)).toBeInTheDocument();
    expect(screen.getByText('Group minimum €300')).toBeInTheDocument();
});

test('no from-prefix or note without minPrice', () => {
    renderCard({id: 'a1', name: 'Bar Crawl', price: 40});
    expect(screen.getByText('€40 / person')).toBeInTheDocument();
    expect(screen.queryByText(/Group minimum/)).not.toBeInTheDocument();
});
```

(`renderCard` = the file's existing helper that wraps `ActivityCard` in `MemoryRouter` + `TripContext.Provider`; reuse its actual name.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd myhive-react-app && npm test -- --watchAll=false format ActivityCard`
Expected: FAIL — helpers not exported / note not rendered.

- [ ] **Step 3: Implement**

`format.js` — after `formatPricePerPerson`:

```js
export function hasGroupMin(activity) {
    return Number(activity?.minPrice) > 0;
}

export function groupMinNote(activity) {
    if (!hasGroupMin(activity)) return null;
    return `Group minimum ${formatAmount(Number(activity.minPrice))}`;
}
```

`ActivityCard.js` — import the helpers, change the price computation and footer:

```js
import {capitalizeFirst, DEFAULT_ACTIVITY_IMAGE, formatPricePerPerson, groupMinNote, hasGroupMin} from '../utils/format';
```

```js
    const formattedPrice = hasGroupMin(activity)
        ? `from ${formatPricePerPerson(activity.price)}`
        : formatPricePerPerson(activity.price);
```

```jsx
                    <span className="activity-price">
                        {formattedPrice}
                        {hasGroupMin(activity) && (
                            <span className="activity-min-note">{groupMinNote(activity)}</span>
                        )}
                    </span>
```

`ActivityCard.css` — add (reuse the file's existing muted-text color variable if it defines one):

```css
.activity-min-note {
    display: block;
    font-size: 0.72rem;
    font-weight: 400;
    color: #6c757d;
}
```

`ActivityDetailPage.js` — import `groupMinNote, hasGroupMin` from `'../utils/format'`; the aside panel label (currently the static `Group price · from` span) becomes:

```jsx
                        <span className="activity-detail-price-from">
                            {hasGroupMin(activity) ? groupMinNote(activity) : 'Group price · from'}
                        </span>
```

and the price line amount gets the prefix:

```jsx
                        <div className="activity-detail-price-line">
                            {hasGroupMin(activity) && <span className="per">from </span>}
                            <span className="amt">{formattedPrice}</span>
                            <span className="per">/ person</span>
                        </div>
```

`ActivityPreviewModal.js` — replace the price meta push:

```js
    if (activity.price != null) {
        meta.push(hasGroupMin(activity)
            ? `from ${formatPricePerPerson(activity.price)}`
            : formatPricePerPerson(activity.price));
    }
    if (hasGroupMin(activity)) {
        meta.push(groupMinNote(activity));
    }
```

(import gains `groupMinNote, hasGroupMin`).

`SwipeCard.js` — the price span (line ~148) becomes, with imports extended the same way:

```jsx
                                        {card.price && (
                                            <span>
                                                {hasGroupMin(card)
                                                    ? `from ${formatPricePerPerson(card.price)}`
                                                    : formatPricePerPerson(card.price)}
                                            </span>
                                        )}
                                        {card.price && hasGroupMin(card) && (
                                            <span> · {groupMinNote(card)}</span>
                                        )}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd myhive-react-app && npm test -- --watchAll=false format ActivityCard ActivityPreviewModal SwipeCard ActivityDetailPage`
Expected: PASS (existing suites for these components must stay green).

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/utils/format.js myhive-react-app/src/utils/format.test.js myhive-react-app/src/components/ActivityCard.js myhive-react-app/src/components/ActivityCard.css myhive-react-app/src/components/ActivityCard.test.js myhive-react-app/src/pages/ActivityDetailPage.js myhive-react-app/src/components/ActivityPreviewModal.js myhive-react-app/src/components/SwipeCard.js
git commit -m "feat(ui): from-pricing and group minimum note on activity surfaces"
```

---

### Task 8: TripBuilder — floored line display, vote hydration, export payload

**Files:**
- Modify: `myhive-react-app/src/components/TripBuilder.js`
- Test: `myhive-react-app/src/components/TripBuilder.test.js`

**Interfaces:**
- Consumes: `lineTotal`, `groupMinApplied` from `tripPricing.js` (Task 6); `row.minPrice` from `ResultActivityDTO` (Task 3); `ActivityExport.minPrice` accepted by the backend (Task 4).

Note: all other add-to-cart paths (ActivityCard, ActivityDetailPage, ActivityPreviewModal, CuratePage) dispatch `ADD_TO_TRIP` with the **whole activity object** (`TripContext.js` stores `action.activity` as the trip item), so `minPrice` from the API rides along automatically — only the two hand-built item shapes below need changes.

- [ ] **Step 1: Write the failing test**

Add to `TripBuilder.test.js`, using the file's existing pattern for rendering with a seeded `TripContext.Provider` (reuse its render helper; the essential seeded state is below):

```jsx
test('floored line shows the group minimum total with a marker', async () => {
    renderTripBuilder({
        tripItems: [{id: 'a1', name: 'Boat Rental', price: 50, minPrice: 300, destinationSlug: 'tenerife'}],
        tripTravelers: 4,
        tripBuilderModalOpen: true,
    });

    // 4 × €50 = €200 -> floored to €300
    expect(await screen.findByText('€50 × 4 = €300 (group min)')).toBeInTheDocument();
});

test('regular line keeps the per-person math', async () => {
    renderTripBuilder({
        tripItems: [{id: 'a1', name: 'Bar Crawl', price: 40, destinationSlug: 'tenerife'}],
        tripTravelers: 2,
        tripBuilderModalOpen: true,
    });

    expect(await screen.findByText('€40 × 2 = €80')).toBeInTheDocument();
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd myhive-react-app && npm test -- --watchAll=false TripBuilder`
Expected: FAIL — floored text not found (renders `€50 × 4 = €200`).

- [ ] **Step 3: Implement**

`TripBuilder.js`:

1. Import gains the helpers:

```js
import {computeTripTotal, groupTripItems, lineTotal, groupMinApplied} from '../utils/tripPricing';
```

(adjust to the file's actual existing import of `computeTripTotal`/`groupTripItems`.)

2. Inside the component body (near `const travelers = state.tripTravelers || 1;`), one label builder used by both itinerary blocks (DRY):

```js
  // One price label for both standalone and package lines; shows the floored
  // total with a marker whenever the group minimum binds — including travelers = 1.
  const itemPriceLabel = (item) => {
    if (groupMinApplied(item, travelers)) {
      return `${formatPrice(item.price)} × ${travelers} = ${formatPrice(lineTotal(item, travelers))} (group min)`;
    }
    return travelers > 1
        ? `${formatPrice(item.price)} × ${travelers} = ${formatPrice(item.price * travelers)}`
        : formatPricePerPerson(item.price);
  };
```

3. Replace **both** itinerary price blocks (package items ~line 659 and standalone ~line 676) — the JSX inside `<div className="itinerary-item-price">` becomes:

```jsx
                          <div className="itinerary-item-price">
                            {itemPriceLabel(item)}
                          </div>
```

4. Vote-result hydration (the `ADD_TO_TRIP` dispatch mapping `ResultActivityDTO`) gains one field after `price`:

```js
                        price: row.price,
                        minPrice: row.minPrice,
```

5. Booking export payload (`activities: state.tripItems.map(...)`) gains one field after `price`:

```js
          price: item.price || 0,
          minPrice: item.minPrice ?? null,
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd myhive-react-app && npm test -- --watchAll=false TripBuilder tripPricing`
Expected: PASS, including the file's existing suite.

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/components/TripBuilder.js myhive-react-app/src/components/TripBuilder.test.js
git commit -m "feat(trip-builder): floored line totals, minPrice hydration and export"
```

---

### Task 9: Admin UI — Min price field and table column

**Files:**
- Modify: `myhive-react-app/src/pages/AdminActivities.js`

**Interfaces:**
- Consumes: `ActivityDTO.minPrice` (Task 1) via the existing admin CRUD payload.

No test file exists for this page (repo convention); backend `@PositiveOrZero` validation is covered by Task 1. Manual check happens in Step 2.

- [ ] **Step 1: Implement**

`AdminActivities.js`:

1. `EMPTY_FORM` gains `minPrice: '',` after `price: '',`.
2. `COLUMNS` gains `{key: 'minPrice', label: 'Min price'},` after the `price` entry.
3. `validate` gains (after the price check):

```js
            if (form.minPrice !== '' && Number(form.minPrice) < 0) {
                errors.minPrice = 'Min price must be 0 or more.';
            }
```

4. `mapItemToForm` gains `minPrice: a.minPrice ?? '',` after `price`.
5. `buildPayload` gains `minPrice: form.minPrice !== '' ? Number(form.minPrice) : null,` after the `price` line.
6. Table row: add the cell right after the price `<td>` (renders an em-dash when no minimum):

```jsx
                                <td className="small">{Number(activity.minPrice) > 0 ? formatAmount(activity.minPrice) : '—'}</td>
```

7. Form: after the Price/Duration `Row`, add:

```jsx
                        <Row className="g-3 mb-3">
                            <Col sm={6}>
                                <Form.Label className="small fw-semibold text-white">Minimum price per group (€)</Form.Label>
                                <Form.Control
                                    type="number"
                                    step="0.01"
                                    min="0"
                                    value={form.minPrice}
                                    onChange={e => updateField('minPrice', e.target.value)}
                                    isInvalid={!!fieldErrors.minPrice}
                                    placeholder="No minimum"
                                />
                                <Form.Control.Feedback type="invalid">{fieldErrors.minPrice}</Form.Control.Feedback>
                            </Col>
                        </Row>
```

- [ ] **Step 2: Verify**

Run: `cd myhive-react-app && npm test -- --watchAll=false`
Expected: PASS (no regressions). Then a quick manual smoke: `npm start` + backend `./gradlew bootRun --args='--spring.profiles.active=dev'`, open `/admin/activities`, edit `sunset-boat-party` — Min price shows 600, table shows €600, clearing the field saves null.

- [ ] **Step 3: Commit**

```bash
git add myhive-react-app/src/pages/AdminActivities.js
git commit -m "feat(admin): minimum price per group field on activity form"
```

---

### Task 10: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Full backend suite**

Run: `cd myhive-backend && ./gradlew test`
Expected: BUILD SUCCESSFUL, zero failures.

- [ ] **Step 2: Full frontend suite**

Run: `cd myhive-react-app && npm test -- --watchAll=false`
Expected: all suites pass.

- [ ] **Step 3: End-to-end smoke (dev)**

Start backend (`./gradlew bootRun --args='--spring.profiles.active=dev'`) and frontend (`npm start`). Verify:
1. Card for `sunset-boat-party` shows `from €120 / person` + `Group minimum €600`.
2. Add it to a trip with 2 travelers → Trip Builder line `€120 × 2 = €600 (group min)`, cart total 600.
3. Raise travelers to 6 → line `€120 × 6 = €720`, no marker.
4. Submit the booking → backend booking total 600 for 2 travelers (check the admin booking detail or logs).

- [ ] **Step 4: Request code review** (per CLAUDE.md workflow rule 1) — run the project's code review before declaring done. Documentation/memory updates (README, `infrastructure.md`, `project_overview.md`) happen **after user approval** per workflow rule 3.

---

## Self-Review Notes (already applied)

- Spec coverage: data model (T1, T2), backend floor + C1 (T2), vote result (T3), emails (T4), CSV (T5), cart math (T6), display (T7), Trip Builder + payload (T8), admin (T9). `VoteActivityResponse.minPrice` (T3) is a small, deliberate extension beyond the spec so the swipe deck (SwipeCard) can render the note — the spec's display requirement covers SwipeCard.
- Naming consistency: `minPrice` (Java/JS), `min_price` (DB/CSV), helpers `lineTotal`/`groupMinApplied` (JS), `lineTotal`/`flooredLine` (Java), `hasGroupMin`/`groupMinNote` (format.js).
- Known judgement calls for the implementer: exact CSS variable for the muted note color (match the file), the exact name of existing render helpers in `ActivityCard.test.js` / `TripBuilder.test.js` (reuse, don't duplicate), and the `ActivityCsvImporterTest` fixture helpers (mirror the `featured_weight` tests).
