# Activity Extras (optional add-ons) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Per-activity optional extras (e.g. "Private show, €250 per group") that a customer can select on the detail page and the Trip Builder line, that the AI chat can add/remove on request, that are priced into quotes and bookings, snapshotted on booking items, shown in emails and round-tripped through the admin CSV.

**Architecture:** Extras live as a JSON list on `activities.extras` (Jackson `AttributeConverter`, same mechanism as `translations`) and are snapshotted as JSON on `booking_items.extras`. Pricing rule everywhere: `line = max(price × N, minPrice) + Σ extras`, extras never floored and never package-discounted. The AI planner carries `extraCodes` on draft items and priced `extras` on result items; the chat gets two new edit ops (`ADD_EXTRA`, `REMOVE_EXTRA`) applied in Java by `PackageEditor`. The CRA cart item gains `extras` (available) and `selectedExtras` (codes).

**Tech Stack:** Spring Boot 4 / Java 25 / Jackson 3 (`tools.jackson.*`) / JUnit 5 + H2; React 19 CRA (Jest + RTL); Flyway V14 for prod.

**Spec:** `docs/superpowers/specs/2026-10-10-activity-extras-design.md`

## Global Constraints

- Code style per `CLAUDE.md`: no wildcard imports, `@Override`, braces always, one variable per declaration, `UPPER_SNAKE_CASE` constants, K&R braces.
- Tests: `expected`-prefixed variables for values asserted later; DTOs built inline when asserting field values; `TestDataFactory` only for incidental entities.
- Backend tests: `cd myhive-backend && ./gradlew test --tests '*ClassName*'`. Frontend tests: `cd myhive-react-app && CI=true npm test -- --watchAll=false <pattern>`.
- Extras limits (verbatim from spec): ≤ 10 extras per activity; `code` ≤ 40 chars `[a-z0-9-]`, generated from the name with `SlugUtils` when blank; `name` ≤ 80; `description` ≤ 300; `price` ≥ 0, scale ≤ 2; `pricing` ∈ {`PER_GROUP`, `PER_PERSON`}.
- Write semantics (DTO and CSV): `null` = unchanged, empty = cleared.
- Nothing is pre-selected anywhere (EU CRD Art. 22).
- Every copy of the line formula changes together: `BookingService`, `PriceQuoteService`, `PlanAssembler`/`PlanPricer`, `tripPricing.js`. `VoteSessionService.flooredLine` stays untouched.
- Record changes in the AI plan model keep the old constructors so the existing ~28 test call sites compile.
- Port 8080 is taken by Steam on the dev machine: run the backend with `--server.port=8081`.

## Review Focus

1. **A cart item added before this release** (no `extras`/`selectedExtras` fields in localStorage) must price and render exactly as today — pinned in Task 9 (`tripPricing` legacy-item test, reducer defaults).
2. **An extra removed by the admin while it sits in a customer's cart**: the quote must still return a price (unknown codes ignored, Task 3) and the paid booking must fail with a clear 400 (`UNKNOWN_EXTRA`, Task 2) rather than silently drop money.
3. **A `PER_PERSON` extra on a line whose group minimum binds**: extras are added on top of the floored base, not folded into the comparison — pinned in Task 2 and Task 9 tests.
4. **An AI `EDITED` generation stored before this release** (`ItemResult` JSON without `extras`): must read back with an empty list, never fail — pinned in Task 7 (`JsonCodec` read of a fixture without the field).
5. **A CSV cell with extras JSON edited in a spreadsheet** (smart quotes, trailing comma): must produce a row error naming the row, never a 500 — pinned in Task 5.

---

## Task dependency map

```
Task 1 (shared types, entity, DTO, mapping, Flyway)  ── do first, alone
   ├── Task 2 booking ── Task 3 quote ── Task 4 emails ── Task 5 CSV ── Task 6 vote DTOs   [agent "backend"]
   ├── Task 7 AI model/pricing/DTO ── Task 8 AI chat edits + prompt + docs               [agent "ai"]
   └── Task 9 cart + pricing ── Task 10 surfaces ── Task 11 admin + AI views              [agent "frontend"]
Task 12 dev seed, README, graphify ── after all three agents
```

---

### Task 1: Shared types, entity columns, DTOs, mapping, migration

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/entity/ExtraPricing.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/entity/PricedExtra.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/entity/ActivityExtra.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/entity/BookingItemExtra.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/util/JsonListConverter.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/util/ActivityExtrasConverter.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/util/BookingItemExtrasConverter.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/util/ActivityExtras.java` (normalise / validate / localise / canonical JSON)
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/ActivityExtraDTO.java`
- Create: `myhive-backend/src/main/resources/db/migration/V14__activity_extras.sql`
- Modify: `entity/Activity.java` (field after `translations`), `entity/BookingItem.java` (field after `packageDiscountPct`), `dto/ActivityDTO.java`, `dto/BookingItemDTO.java`, `dto/VotePoolActivityDTO.java`, `dto/ResultActivityDTO.java`, `util/MoneyMath.java`, `service/ActivityService.java:205-260`
- Test: `src/test/java/com/myhive/backend/util/ActivityExtrasTest.java`, `util/ActivityExtrasConverterTest.java`, `util/MoneyMathTest.java` (extend), `service/ActivityServiceTest.java` (extend)

**Interfaces:**
- Produces:
  ```java
  public enum ExtraPricing { PER_GROUP, PER_PERSON; public BigDecimal amount(BigDecimal price, int travelers); }
  public interface PricedExtra { BigDecimal price(); ExtraPricing pricing(); default BigDecimal amount(int travelers); }
  public record ActivityExtra(String code, String name, String description, BigDecimal price, ExtraPricing pricing,
                              Map<String, Map<String, String>> translations) implements PricedExtra
  public record BookingItemExtra(String code, String name, BigDecimal price, ExtraPricing pricing) implements PricedExtra
      { public static BookingItemExtra of(ActivityExtra extra); }
  public final class ActivityExtras {
      public static final int MAX_PER_ACTIVITY = 10;
      public static List<ActivityExtra> normalize(List<ActivityExtra> extras);        // throws IllegalArgumentException
      public static List<ActivityExtra> fromDtos(List<ActivityExtraDTO> dtos);        // null -> null (unchanged); [] -> []
      public static List<ActivityExtraDTO> toDtos(List<ActivityExtra> extras, String lc); // localised; lc null = raw + translations
      public static List<ActivityExtra> parseJson(String json);                       // throws IllegalArgumentException
      public static String canonicalJson(List<ActivityExtra> extras);                 // "" when null/empty
      public static Optional<ActivityExtra> byCode(List<ActivityExtra> extras, String code);
  }
  public static BigDecimal MoneyMath.extrasTotal(List<? extends PricedExtra> extras, int travelers); // null-safe, scale 2
  Activity#getExtras()/setExtras(List<ActivityExtra>)   BookingItem#getExtras()/setExtras(List<BookingItemExtra>)
  ActivityDTO#extras (List<ActivityExtraDTO>, null on admin write = unchanged)
  ActivityExtraDTO { String code; String name; String description; BigDecimal price; ExtraPricing pricing;
                     @JsonInclude(NON_NULL) Map<String, Map<String, String>> translations; }
  BookingItemDTO#extras (List<BookingItemExtra>)   VotePoolActivityDTO#extras (List<ActivityExtraDTO>), #selectedExtras (List<String>, nullable)
  ResultActivityDTO#extras (List<ActivityExtraDTO>)
  ```

- [ ] **Step 1: Write the failing tests**

`ActivityExtrasTest.java`:
```java
@Test
void normalizeGeneratesCodeFromNameAndKeepsOrder() {
    ActivityExtra given = new ActivityExtra(null, "Private Show", "15 min", new BigDecimal("250"), ExtraPricing.PER_GROUP, null);
    List<ActivityExtra> result = ActivityExtras.normalize(List.of(given));
    String expectedCode = "private-show";
    assertEquals(expectedCode, result.getFirst().code());
    assertEquals(new BigDecimal("250.00"), result.getFirst().price());
}

@Test
void normalizeRejectsDuplicateCodes() {
    ActivityExtra a = new ActivityExtra("show", "Show", null, BigDecimal.TEN, ExtraPricing.PER_GROUP, null);
    ActivityExtra b = new ActivityExtra("show", "Other", null, BigDecimal.ONE, ExtraPricing.PER_PERSON, null);
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ActivityExtras.normalize(List.of(a, b)));
    assertTrue(e.getMessage().contains("show"));
}

@Test
void normalizeRejectsTooManyNegativePriceAndMissingPricing() {
    List<ActivityExtra> eleven = IntStream.range(0, 11)
            .mapToObj(i -> new ActivityExtra("e" + i, "E" + i, null, BigDecimal.ONE, ExtraPricing.PER_GROUP, null)).toList();
    assertThrows(IllegalArgumentException.class, () -> ActivityExtras.normalize(eleven));
    assertThrows(IllegalArgumentException.class, () -> ActivityExtras.normalize(List.of(
            new ActivityExtra("x", "X", null, new BigDecimal("-1"), ExtraPricing.PER_GROUP, null))));
    assertThrows(IllegalArgumentException.class, () -> ActivityExtras.normalize(List.of(
            new ActivityExtra("x", "X", null, BigDecimal.ONE, null, null))));
}

@Test
void parseJsonAndCanonicalJsonRoundTrip() {
    String expectedJson = "[{\"code\":\"private-show\",\"name\":\"Private show\",\"description\":\"15 min\","
            + "\"price\":250.00,\"pricing\":\"PER_GROUP\",\"translations\":{\"de\":{\"name\":\"Private Show\"}}}]";
    List<ActivityExtra> parsed = ActivityExtras.parseJson(expectedJson);
    assertEquals(expectedJson, ActivityExtras.canonicalJson(parsed));
    assertEquals("", ActivityExtras.canonicalJson(null));
    assertEquals("", ActivityExtras.canonicalJson(List.of()));
}

@Test
void parseJsonRejectsMalformedInputWithAMessage() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ActivityExtras.parseJson("[{\"name\": “Show”}]"));
    assertNotNull(e.getMessage());
}

@Test
void toDtosLocalisesNameAndDescriptionAndHidesTranslationsForALocale() {
    ActivityExtra extra = new ActivityExtra("show", "Private show", "15 min", new BigDecimal("250.00"), ExtraPricing.PER_GROUP,
            Map.of("de", Map.of("name", "Private Show", "description", "15 Minuten")));
    ActivityExtraDTO de = ActivityExtras.toDtos(List.of(extra), "de").getFirst();
    assertEquals("Private Show", de.getName());
    assertEquals("15 Minuten", de.getDescription());
    assertNull(de.getTranslations());
    ActivityExtraDTO raw = ActivityExtras.toDtos(List.of(extra), null).getFirst();
    assertEquals("Private show", raw.getName());
    assertNotNull(raw.getTranslations());
}
```

`MoneyMathTest.java` (add):
```java
@Test
void extrasTotalMultipliesOnlyPerPersonExtras() {
    BookingItemExtra perGroup = new BookingItemExtra("show", "Show", new BigDecimal("250"), ExtraPricing.PER_GROUP);
    BookingItemExtra perPerson = new BookingItemExtra("photo", "Photo", new BigDecimal("15"), ExtraPricing.PER_PERSON);
    BigDecimal expected = new BigDecimal("310.00"); // 250 + 15 × 4
    assertEquals(expected, MoneyMath.extrasTotal(List.of(perGroup, perPerson), 4));
    assertEquals(new BigDecimal("0.00"), MoneyMath.extrasTotal(null, 4));
}
```

`ActivityExtrasConverterTest.java`: round trip a two-element list through `convertToDatabaseColumn` / `convertToEntityAttribute`; `null` and `List.of()` → `null` column; a column holding `"[]"` → empty list.

`ActivityServiceTest.java` (add): create with `extras` → read back with codes; update with `extras == null` leaves them; update with `[]` clears; duplicate codes → `BadRequestException`; `?locale=de` read localises the extra's name.

- [ ] **Step 2: Run the tests to verify they fail** — `./gradlew test --tests '*ActivityExtrasTest*' --tests '*MoneyMathTest*'` → compilation errors.

- [ ] **Step 3: Implement**

`ExtraPricing.java`:
```java
public enum ExtraPricing {
    PER_GROUP, PER_PERSON;

    /** What this extra costs for a group of {@code travelers}; a group of fewer than one is priced as one. */
    public BigDecimal amount(BigDecimal price, int travelers) {
        BigDecimal base = price == null ? BigDecimal.ZERO : price;
        BigDecimal amount = this == PER_PERSON ? base.multiply(BigDecimal.valueOf(Math.max(1, travelers))) : base;
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
```
`PricedExtra.java`: interface with `price()`, `pricing()`, `default BigDecimal amount(int travelers) { return pricing() == null ? BigDecimal.ZERO.setScale(2) : pricing().amount(price(), travelers); }`.

`ActivityExtra.java`: the record above, annotated `@JsonInclude(JsonInclude.Include.NON_NULL)` on `translations` (use `com.fasterxml.jackson.annotation.JsonInclude` — the annotations module is shared by Jackson 2 and 3). `BookingItemExtra.of(extra)` copies code/name/price/pricing.

`JsonListConverter.java` (abstract, implements `AttributeConverter<List<T>, String>` with a `TypeReference<List<T>>` constructor argument; `JsonMapper.builder().build()` like `TranslationsConverter`; `null`/empty → `null`; blank column → `null`); `ActivityExtrasConverter` and `BookingItemExtrasConverter` are `@Converter` subclasses with the two type references.

`ActivityExtras.java` rules: trims strings; `code` = `SlugUtils.generateSlug(name)` when blank, else `SlugUtils.generateSlug(code)`; reject blank name, name > 80, description > 300, code > 40, price null/negative/scale > 2, pricing null, > `MAX_PER_ACTIVITY`, duplicate codes ("duplicate extra code 'show'"); price set to scale 2; translations kept as given (null when empty). `parseJson` uses `JsonMapper.builder().build().readValue(json, new TypeReference<List<ActivityExtra>>() {})` inside `try { ... } catch (RuntimeException e) { throw new IllegalArgumentException("extras is not a JSON array of extras: " + e.getMessage(), e); }` then `normalize`. `canonicalJson` writes a `LinkedHashMap` per extra in the order code, name, description, price (scale 2 as a number), pricing, translations (only when non-empty) with `mapper.writeValueAsString`. `toDtos` uses `Translations.pick(extra.translations(), lc, "name", extra.name())` and the same for description; sets `translations` on the DTO only when `lc == null`.

`V14__activity_extras.sql`:
```sql
ALTER TABLE activities    ADD COLUMN IF NOT EXISTS extras TEXT;
ALTER TABLE booking_items ADD COLUMN IF NOT EXISTS extras TEXT;
```

Entity fields:
```java
/** Optional add-ons a customer can select for this activity; null or empty = none. JSON list, see ActivityExtras. */
@Convert(converter = ActivityExtrasConverter.class)
@Column(columnDefinition = "TEXT")
private List<ActivityExtra> extras;
```
and on `BookingItem` the `BookingItemExtra` list with the comment "Snapshot of the extras selected at booking time (same pattern as price/minPrice)."

`ActivityService.applyDtoToEntity`: after translations
```java
// null = "unchanged" (forms that predate extras), [] = clear, like translations.
if (dto.getExtras() != null) {
    try {
        activity.setExtras(ActivityExtras.fromDtos(dto.getExtras()));
    } catch (IllegalArgumentException e) {
        throw new BadRequestException(e.getMessage());
    }
}
```
`convertToDTO`: `dto.setExtras(ActivityExtras.toDtos(activity.getExtras(), lc));` (never null in the response: empty list when none).

`MoneyMath.extrasTotal`: sum of `extra.amount(travelers)` over a null-safe list, `setScale(2, HALF_UP)`.

- [ ] **Step 4: Run the tests** — `./gradlew test --tests '*ActivityExtras*' --tests '*MoneyMathTest*' --tests '*ActivityServiceTest*'` → PASS.
- [ ] **Step 5: Commit** — `git commit -m "feat(extras): activity extras model, converters, DTOs, V14"`.

---

### Task 2: Booking pricing, snapshot, export round trip, chargeable check

**Files:**
- Modify: `service/BookingService.java:148-166` (resolve + snapshot), `:284-325` (`toExportRequest`), `:447-496` (DTO + pricing), `dto/TripExportRequest.java:100-122`
- Test: `service/BookingServiceTest.java`, `controller/BookingControllerIntegrationTest.java`

**Interfaces:**
- Consumes: Task 1 types.
- Produces: `TripExportRequest.ExtraExport { @NotBlank @Size(max=40) String code; @Size(max=80) String name; @PositiveOrZero BigDecimal price; ExtraPricing pricing; }`, `ActivityExport#extras: @Valid @Size(max=10) List<ExtraExport>`; `BookingItem.extras` snapshot filled server-side; `BookingItemDTO.extras`.

- [ ] **Step 1: Failing tests** (`BookingServiceTest`):
```java
@Test
void calculateTotalAddsExtrasOnTopOfTheFlooredBaseAndOutsideThePackageDiscount() {
    // activity 40 €/pp, min 120, 2 travelers -> base floored to 120; extras 250 per group + 15 pp × 2 = 280
    ... build an Activity with price 40, minPrice 120, extras [private-show 250 PER_GROUP, photographer 15 PER_PERSON]
    ... TripExportRequest with 2 travelers, one ActivityExport {activityId, extras:[{code:"private-show"},{code:"photographer"}]}
    Booking booking = bookingService.createBookingEntity(request, true);
    BigDecimal expectedTotal = new BigDecimal("400.00");
    assertEquals(expectedTotal, booking.getTotalAmount());
    assertEquals(2, booking.getBookingItems().getFirst().getExtras().size());
    assertEquals(new BigDecimal("250.00"), booking.getBookingItems().getFirst().getExtras().getFirst().price());
}

@Test
void packageDiscountNeverTouchesExtras() {
    // package 10 % with one activity 100 €/pp × 2 and an extra 50 PER_GROUP -> 180 + 50 = 230
    BigDecimal expectedTotal = new BigDecimal("230.00");
    ...
}

@Test
void unknownExtraCodeIsA400UnderTrustedPricingAndDroppedOtherwise() {
    assertThrows(BadRequestException.class, () -> bookingService.createBookingEntity(requestWithCode("nope"), true));
    Booking lenient = bookingService.createBookingEntity(requestWithCode("nope"), false);
    assertTrue(lenient.getBookingItems().getFirst().getExtras().isEmpty());
}

@Test
void clientSuppliedExtraPriceIsIgnored() { // SEC-1: request says price 1, catalog says 250
    ... assertEquals(new BigDecimal("250.00"), item.getExtras().getFirst().price());
}

@Test
void toExportRequestCarriesTheSnapshotExtrasAndVerifyChargeablePricingAcceptsIt() {
    ... create booking with extras, then
    TripExportRequest exported = bookingService.toExportRequest(saved);
    assertEquals("private-show", exported.getDestinations().getFirst().getActivities().getFirst().getExtras().getFirst().getCode());
    assertDoesNotThrow(() -> bookingService.verifyChargeablePricing(saved));
}
```
Integration test: `POST /bookings/export` (the existing Trip Builder endpoint used in `BookingControllerIntegrationTest`) with `extras` → response item carries `extras`.

- [ ] **Step 2: Run** → fails (no `getExtras` on `ActivityExport`).
- [ ] **Step 3: Implement**

`BookingService`:
```java
item.setMinPrice(activity.getMinPrice());
item.setExtras(resolveExtras(activity, act.getExtras(), enforceTrustedPricing));
...
/**
 * The extras a line carries, priced from the catalog (SEC-1): the request names them by code only. Under
 * trusted pricing a code the activity no longer offers is a 400 (stale cart); the lenient lead flow drops it.
 */
private static List<BookingItemExtra> resolveExtras(Activity activity,
        List<TripExportRequest.ExtraExport> requested, boolean enforceTrustedPricing) {
    if (activity == null || requested == null || requested.isEmpty()) {
        return List.of();
    }
    Map<String, BookingItemExtra> resolved = new LinkedHashMap<>();
    for (TripExportRequest.ExtraExport wanted : requested) {
        Optional<ActivityExtra> extra = ActivityExtras.byCode(activity.getExtras(), wanted.getCode());
        if (extra.isEmpty()) {
            if (enforceTrustedPricing) {
                throw new BadRequestException("Unknown extra '" + wanted.getCode() + "' for activity " + activity.getId());
            }
            continue;
        }
        resolved.put(extra.get().code(), BookingItemExtra.of(extra.get()));
    }
    return List.copyOf(resolved.values());
}
```
Pricing:
```java
private static @NonNull BigDecimal getGroupTotal(Map.Entry<UUID, List<BookingItem>> entry) {
    BigDecimal bases = BigDecimal.ZERO;
    BigDecimal extras = BigDecimal.ZERO;
    for (BookingItem it : entry.getValue()) {
        bases = bases.add(lineBase(it));
        extras = extras.add(lineExtras(it));
    }
    if (entry.getKey() != null) {
        bases = MoneyMath.applyDiscountPct(bases, entry.getValue().getFirst().getPackageDiscountPct());
    }
    // Extras are add-ons at list price: never floored by the group minimum, never discounted by a package.
    return bases.add(extras);
}

/** Group-minimum floor: a line never bills below the activity's minPrice snapshot. Mirrors PlanPricer.lineTotal and tripPricing.js. */
private static BigDecimal lineBase(BookingItem it) { ...existing body of lineTotal... }

private static BigDecimal lineExtras(BookingItem it) {
    int qty = it.getQuantity() == null ? 1 : it.getQuantity();
    return MoneyMath.extrasTotal(it.getExtras(), qty);
}
```
`toExportRequest`: `activity.setExtras(item.getExtras() == null ? List.of() : item.getExtras().stream().map(e -> new TripExportRequest.ExtraExport(e.code(), e.name(), e.price(), e.pricing())).toList());`
`convertItemToDTO`: `dto.setExtras(item.getExtras() == null ? List.of() : item.getExtras());`
`TripExportRequest.ExtraExport` as in Interfaces (Lombok `@Data @NoArgsConstructor @AllArgsConstructor`).

- [ ] **Step 4: Run** `./gradlew test --tests '*BookingService*' --tests '*BookingController*' --tests '*PaymentService*'` → PASS.
- [ ] **Step 5: Commit** — `feat(extras): price, snapshot and export booking extras`.

---

### Task 3: Price quote with extras

**Files:** Modify `service/PriceQuoteService.java`, `dto/PriceQuoteRequest.java`; Test `service/PriceQuoteServiceTest.java`.

**Interfaces:** `PriceQuoteRequest.Item#extraCodes: @Size(max=10) List<@Size(max=40) String>` (nullable).

- [ ] **Step 1: Failing tests**: (a) item with `extraCodes ["private-show"]` on an activity with that extra at 250 PER_GROUP, 2 travelers, price 40, no minimum → total 330 → response `fromPrice == FromPrice.of(330)`; (b) same activity twice with different codes is **two** lines (the `Line` key includes the sorted codes) — assert total 80 + 250 + 80 (second line without the extra) = 410... (write the arithmetic you set up in the test as `expectedTotal`); (c) unknown code ignored, total unchanged; (d) extra inside a package line is not discounted: package 50 % with activity 100 × 2 + extra 50 → 100 + 50 = 150.
- [ ] **Step 2: Run** → fails.
- [ ] **Step 3: Implement**: `record Line(UUID activityId, UUID packageId, List<String> extraCodes)` built with `codes == null ? List.of() : codes.stream().filter(Objects::nonNull).map(String::strip).filter(c -> !c.isEmpty()).distinct().sorted().toList()`. In `quote`, keep `basesByPackage` and a separate `BigDecimal extras` accumulator:
```java
BigDecimal base = PlanPricer.lineTotal(activity.getPrice(), activity.getMinPrice(), travelers);
basesByPackage.merge(line.packageId(), base, BigDecimal::add);
extras = extras.add(PlanPricer.extrasTotal(activity.getExtras(), line.extraCodes(), travelers));
```
(`PlanPricer.extrasTotal(List<ActivityExtra>, List<String> codes, int travelers)` is added in **Task 7**; to keep this task independent, implement here with `MoneyMath.extrasTotal(ActivityExtras.select(activity.getExtras(), line.extraCodes()), travelers)` where `ActivityExtras.select(extras, codes)` returns the extras whose code is in `codes`, in catalog order — add `select` to `ActivityExtras` in this task.) Total = Σ (discounted package bases + standalone bases) + extras. Update the class comment.
- [ ] **Step 4: Run** `--tests '*PriceQuote*'` → PASS. **Step 5: Commit** — `feat(extras): quote prices selected extras`.

---

### Task 4: Emails

**Files:** Modify `service/EmailService.java:108-117, 606-662`; `templates/email/itinerary-confirmation.html:156-187`; `templates/email/booking-notification.html:43-70`; `messages.properties`, `messages_de.properties`. Test: `service/EmailServiceTest.java` (+ the template render support already there).

- [ ] **Step 1: Failing tests**: `buildDestinationViews` on a request whose activity has `extras [{code:"private-show", name:"Private show", price:250, pricing:PER_GROUP}, {photographer 15 PER_PERSON}]` with 4 travelers → `line.extras` has 2 entries, first `name == "Private show"`, `amount == 250.00`, `perPerson == false`; second `amount == 60.00`, `perPerson == true`. Render test: the itinerary HTML contains `Private show` and `per group`; the German render contains `pro Gruppe`.
- [ ] **Step 2: Run** → fails.
- [ ] **Step 3: Implement**: add `public static class ExtraLineView { public String name; public BigDecimal amount; public boolean perPerson; }`, `ActivityLineView.extras = new ArrayList<>()` filled in `buildDestinationViews` from `activity.getExtras()` with `amount = extra.getPricing().amount(extra.getPrice(), travelers)` (skip entries with null pricing/price). Template fragment (both package and standalone blocks of `itinerary-confirmation.html`, directly under the group-minimum `<p>`):
```html
<p th:each="extra : ${line.extras}" class="extra">+ <span th:text="${extra.name}">Extra</span>:
    <span class="price" th:text="#{email.common.amount(${#numbers.formatDecimal(extra.amount, 1, 2)})}">€0.00</span>
    <span th:text="${extra.perPerson} ? #{email.itinerary.activity.extra.perPerson} : #{email.itinerary.activity.extra.perGroup}">per group</span></p>
```
`booking-notification.html` (English, hard-coded): `<p th:each="extra : ${line.extras}">+ <span th:text="${extra.name}">Extra</span>: €<span th:text="${#numbers.formatDecimal(extra.amount, 1, 2)}">0.00</span> <span th:text="${extra.perPerson} ? 'per person' : 'per group'">per group</span></p>`.
Keys: `email.itinerary.activity.extra.perGroup=per group` / `=pro Gruppe`; `email.itinerary.activity.extra.perPerson=per person` / `=pro Person`.
- [ ] **Step 4: Run** `--tests '*EmailService*' --tests '*TemplateRender*'` → PASS. **Step 5: Commit** — `feat(extras): extras on itinerary and booking emails`.

---

### Task 5: CSV import / export `extras` column

**Files:** Modify `service/activity/ActivityCsvParser.java:34`, `CsvImportTypes.java` (`ValidatedRow`), `ActivityCsvRowValidator.java:66-105, 275-290`, `ActivityCsvDiffer.java:85-130`, `ActivityImportWriter.java:90-105`, `ActivityCsvExporter.java:26-30, 64-79`, `ImportErrorCode.java`; `myhive-react-app/src/components/admin/ImportActivitiesModal.js:11-22` (label). Tests: `service/activity/ActivityCsvImporterTest.java`, `ActivityCsvExporterTest.java`, `controller/ActivityCsvImportExportIntegrationTest.java`.

**Interfaces:** `ValidatedRow#extras: List<ActivityExtra>` — null = column absent (untouched), empty = clear, else set. `ImportErrorCode.INVALID_EXTRAS`. Export header gains `"extras"` as the **last** column (after `featured_weight`, so existing positional assertions hold).

- [ ] **Step 1: Failing tests**: (a) CSV without the column → preview shows no `extras` diff, apply leaves extras; (b) blank cell → diff `extras: {old: "<json>", new: ""}` and apply clears; (c) valid JSON cell → diff shows canonical JSON, apply sets 2 extras with generated codes; (d) malformed JSON (`[{"name": “Show”}]` with curly quotes) → row error `INVALID_EXTRAS` with `field == "extras"` and the row number, HTTP 200 preview; (e) 11 extras → `INVALID_EXTRAS`; (f) export → import round trip of an activity with extras produces **no** `extras` diff; (g) exporter header's last column is `extras` and the cell is the canonical JSON.
- [ ] **Step 2: Run** → fails.
- [ ] **Step 3: Implement**: `OPTIONAL_COLUMNS = Set.of("featured_weight", "min_price", "extras")`. Validator:
```java
/** Optional column: null = absent -> untouched; empty list = blank cell -> clear; else the parsed, normalised extras. */
private List<ActivityExtra> parseExtras(RawRow raw, List<ActivityImportPreviewDTO.RowError> errors) {
    if (!raw.hasColumn("extras")) {
        return null;
    }
    String rawValue = raw.get("extras");
    if (rawValue.isEmpty()) {
        return List.of();
    }
    try {
        return ActivityExtras.parseJson(rawValue);
    } catch (IllegalArgumentException e) {
        errors.add(new ActivityImportPreviewDTO.RowError(raw.csvRowNumber(), ImportErrorCode.INVALID_EXTRAS,
                "extras: " + e.getMessage(), "extras"));
        return null;
    }
}
```
Differ: `if (v.extras() != null) { String dbJson = ActivityExtras.canonicalJson(db.getExtras()); String csvJson = ActivityExtras.canonicalJson(v.extras()); if (!dbJson.equals(csvJson)) { changes.put("extras", new FieldChange(dbJson, csvJson)); } }`. Writer: `if (row.extras() != null) { activity.setExtras(row.extras().isEmpty() ? null : row.extras()); }`. Exporter: append `ActivityExtras.canonicalJson(a.getExtras())` as the last cell (through `CsvCells.sanitize` so a leading `[` is not formula-treated — check `CsvCells` and keep the existing sanitiser behaviour). `ImportActivitiesModal.fieldLabel`: `extras: 'Extras'`.
- [ ] **Step 4: Run** `--tests '*ActivityCsv*'` → PASS. **Step 5: Commit** — `feat(extras): extras column in activity CSV import/export`.

---

### Task 6: Vote result / pool DTOs carry available extras

**Files:** Modify `service/VoteSessionService.java` (where `ResultActivityDTO` is built, ~`:587-600`), `service/VotePoolService.java` (where `VotePoolActivityDTO` is built), and any other `new VotePoolActivityDTO(` / `new ResultActivityDTO(` call sites (grep). Tests: `service/VoteSessionGetResultTest.java`, `service/VotePoolServiceTest.java`.

- [ ] **Step 1: Failing tests**: result row for an activity with extras → `extras.size() == 1`, localised name under `locale=de`; pool activity likewise; `selectedExtras` is null on vote surfaces.
- [ ] **Step 2–5**: add `extras` via `ActivityExtras.toDtos(activity.getExtras(), lc)` at each site; keep all-args constructors compiling (prefer setters after construction over widening the Lombok constructor if call sites are many). Run `--tests '*Vote*'` → PASS. Commit — `feat(extras): vote result and pool rows list available extras`.

---

### Task 7: AI catalog snapshot, plan model, pricing, API projection, selection

**Files:**
- Create: `ai/catalog/CatalogExtra.java`
- Modify: `ai/catalog/CatalogActivity.java`, `ai/catalog/CatalogSnapshotter.java:90-104`, `ai/plan/PlanDraft.java`, `ai/plan/ComposedPlan.java`, `ai/plan/PlanPricer.java`, `ai/plan/PlanAssembler.java:61-87`, `ai/plan/PlanDrafts.java:36-41`, `ai/plan/PlanTextWriter.java:149-156`, `ai/plan/PlanTrimmer.java:333,408`, `ai/edit/PackageEditor.java:144-150` (`fromPlanItem`), `ai/dto/PackageDTO.java`, `ai/dto/AiDtoMapper.java:233-246, 354-374`, `ai/service/AiSessionService.java:139, 234-283` (`Selection`), `ai/controller/AiPlannerController.java:84-91`, `docs/api/ai-planner-api.md` (Package type, select)
- Tests: `ai/catalog/CatalogSnapshotterTest`, `ai/plan/PlanPricerTest`, `ai/plan/PlanAssemblerTest`, `ai/plan/PlanDraftsTest`, `ai/dto/AiDtoMapperTest`, `ai/service/AiSessionServiceTest`, `ai/graph/JsonCodec` read test (fixture without `extras`)

**Interfaces:**
```java
public record CatalogExtra(String code, String name, BigDecimal price, ExtraPricing pricing)
CatalogActivity(..., String includes, List<CatalogExtra> extras)       // 10- and 11-arg ctors stay -> extras = List.of(); compact ctor nulls -> List.of()
PlanDraft.ItemDraft(Slot slot, String startHint, UUID activityId, String why, List<String> extraCodes) // 4-arg ctor stays; compact ctor null -> List.of()
ComposedPlan.ItemExtra(String code, String name, BigDecimal price, ExtraPricing pricing, BigDecimal total)
ComposedPlan.ItemResult(..., String includes, List<ItemExtra> extras)  // 12- and 13-arg ctors stay; compact ctor null -> List.of()
PlanPricer.extrasTotal(List<? extends PricedExtra> extras, int travelers)  -> MoneyMath.extrasTotal
PlanPricer.itemExtras(List<CatalogExtra> offered, List<String> codes, int travelers) -> List<ComposedPlan.ItemExtra> (catalog order, unknown codes skipped)
PackageDTO.ItemExtraDTO(String code, String name, BigDecimal price, String pricing, BigDecimal total); PackageDTO.ItemDTO(..., includes, List<ItemExtraDTO> extras)
AiSessionService.Selection(AiGeneration generation, Tier key, int groupSize, List<UUID> activityIds, Map<UUID, List<String>> selectedExtras)
AiDtoMapper.tripItems(List<UUID> activityIds, Map<UUID, List<String>> selectedExtras, String locale) -> VotePoolActivityDTO with extras + selectedExtras
```
`CatalogExtra` implements `PricedExtra`.

- [ ] **Step 1: Failing tests**: snapshotter copies extras with localised names; `PlanPricer.itemExtras` prices PER_PERSON × travelers and skips unknown codes; assembler: item with `extraCodes ["private-show"]` → `lineTotal == base + 250`, `groupMinApplied` judged on the base only, package `totalPrice` includes it, `pricePerPerson` follows; `PlanDrafts.fromComposed` round-trips `extraCodes`; `PlanTextWriter.withWhy` keeps `extras`; `JsonCodec.read` of an `ItemResult` JSON without `extras` yields an empty list; mapper: `ItemDTO.extras` populated; `tripItems` sets `selectedExtras` for the chosen package's items and `extras` from the catalog; `select` returns the map built from the chosen package's items.
- [ ] **Step 2: Run** → fails.
- [ ] **Step 3: Implement** per Interfaces. `PlanAssembler.assemblePackage`:
```java
BigDecimal base = PlanPricer.lineTotal(a.price(), a.minPrice(), travelers);
boolean floored = base.compareTo(a.price().multiply(BigDecimal.valueOf(travelers))) > 0;
List<ComposedPlan.ItemExtra> extras = PlanPricer.itemExtras(a.extras(), item.extraCodes(), travelers);
BigDecimal line = base.add(PlanPricer.extrasTotal(extras, travelers)); // ItemExtra.total already × travelers -> sum totals instead; pick one and test it
```
(Define `ItemExtra implements PricedExtra` so `MoneyMath.extrasTotal` works, **or** sum `ItemExtra::total` — do the latter; `total` is the authoritative per-line number.) `PlanTrimmer:333` re-slots an item: pass `item.extraCodes()` through; `:408` likewise. `PackageEditor.fromPlanItem`: `new CatalogActivity(..., item.includes(), item.extras().stream().map(e -> new CatalogExtra(e.code(), e.name(), e.price(), e.pricing())).toList())`. `AiSessionService.select` builds `selectedExtras` from `chosen.days()` → items → `activityId -> extras codes` (first occurrence wins). `AiDtoMapper.tripItem` sets `extras` (`ActivityExtras.toDtos(activity.getExtras(), lc)`) and `selectedExtras` (the map's list or `List.of()`). Update `docs/api/ai-planner-api.md` Package type (`extras` on items, `lineTotal` now includes them) and select (`tripItems[].extras`, `selectedExtras`).
- [ ] **Step 4: Run** `./gradlew test --tests 'com.myhive.backend.ai.*'` → PASS. **Step 5: Commit** — `feat(extras): extras in the AI catalog snapshot, plan model and selection`.

---

### Task 8: AI chat edits `ADD_EXTRA` / `REMOVE_EXTRA`

**Files:**
- Create: `ai/edit/ExtraNameResolver.java`
- Modify: `ai/edit/EditOp.java`, `ai/edit/EditRequest.java`, `ai/edit/EditRejectionReason.java`, `ai/edit/AppliedEdit.java`, `ai/edit/RejectedEdit.java`, `ai/edit/PackageEditor.java:170-248, 532-543`, `ai/edit/EditMessages.java`, `ai/edit/PackagesView.java`, `ai/llm/LlmOutputParser.java:154-175`, `ai/llm/ChatTurnRequest.java`, `ai/llm/PromptRenderer.java:48-67, 95-102, 333-338`, `resources/prompts/ai/chat-system.st:66-70`, `ai/graph/nodes/ChatTurnNode.java:144-170, 423-432, 447-462`, `ai/dto/EditDTO.java`, `ai/dto/AiDtoMapper.java:312-327`, `docs/api/ai-planner-api.md` (Editing packages)
- Tests: `ai/edit/ExtraNameResolverTest` (new), `ai/edit/PackageEditorTest`, `ai/edit/EditMessagesTest`, `ai/edit/PackagesViewTest`, `ai/llm/LlmOutputParserTest` (+ fixture `chat-turn-with-extra-edits.json`), `ai/llm/PromptRendererTest`, `ai/graph/nodes/ChatTurnNodeTest`, `ai/controller/AiPlannerControllerIntegrationTest`

**Interfaces:**
```java
enum EditOp { ADD, REMOVE, REPLACE, MOVE, ADD_EXTRA, REMOVE_EXTRA }
EditRequest(op, activity, replacement, packageKey, dayNumber, slot, alternatives, activityId, String extra) // all older ctors stay (extra = null)
enum EditRejectionReason { ..., UNKNOWN_EXTRA, ALREADY_HAS_EXTRA, EXTRA_NOT_SELECTED }
AppliedEdit(op, activityName, replacementName, packageKey, dayNumber, slot, placedActivityId, String extraName) // 7-arg ctor stays
RejectedEdit(op, activityName, packageKey, reason, detail, alternatives, String extraName)                   // 5/6-arg ctors stay
ExtraNameResolver.resolve(String name, List<CatalogExtra> offered) -> Optional<CatalogExtra>  // exact (case/space-insensitive), then unique contains
ChatTurnRequest(..., List<String> catalogNames, String extrasCatalog)  // older ctor stays (extrasCatalog = "")
PackagesView.extrasCatalog(List<CatalogActivity>) -> "River Boat Cruise -> Private show (EUR 250 per group), Open bar (EUR 25 per person); Tiki Boat -> ..." or "none"
EditDTO.AppliedEditDTO(..., String slot, String extra); EditDTO.RejectedEditDTO(..., List<String> alternatives, String extra)
```

- [ ] **Step 1: Failing tests**
  - Parser: fixture with `{"op":"ADD_EXTRA","activity":"River Boat Cruise","extra":"Private show"}` parses; `ADD_EXTRA` without `extra` is dropped; the turn still parses.
  - `PackageEditor`: ADD_EXTRA on a package holding the activity → applied with `extraName == "Private show"`, the item's `extraCodes == ["private-show"]`, package total raised by 250; packageKey null → every package holding it; activity not in any package → `NOT_IN_PACKAGE`; unknown extra → `UNKNOWN_EXTRA` with `detail` naming the offered extras; already selected → `ALREADY_HAS_EXTRA`; REMOVE_EXTRA → applied, `EXTRA_NOT_SELECTED` when absent; `tierRulesRelaxed` true after an applied extra edit (via `EditReport.of`).
  - `EditMessages`: EN "Added Private show to River Boat Cruise in the Basic package." (reuse the existing package-phrase helper so the wording of "in the Basic package" / "in every package" / "to your trip plan" matches the current ADD sentence); DE "Private show zu River Boat Cruise hinzugefügt (Basic-Paket)." — copy the exact phrase pattern the existing `DE_APPLIED` entries use. Rejections: EN `UNKNOWN_EXTRA` → "River Boat Cruise has no 'stripper' — it offers: Private show, Open bar."; `ALREADY_HAS_EXTRA` → "River Boat Cruise already has Private show."; `EXTRA_NOT_SELECTED` → "River Boat Cruise has no Private show to remove."; DE: "River Boat Cruise hat kein 'stripper' – angeboten wird: …", "River Boat Cruise hat Private show schon.", "River Boat Cruise hat kein Private show, das entfernt werden könnte."
  - `PackagesView.render` prints `EVENING River Boat Cruise (+ Private show)`; `extrasCatalog` prints the line above and `none` for a catalog without extras.
  - `PromptRenderer.chatSystem` contains the extras line and the three new rules only when packages exist.
  - `ChatTurnNode`: a turn with `ADD` River Boat Cruise + `ADD_EXTRA` Private show on a split wish keeps both edits (`ACTION_EDIT`, `EDITS` holds 2); an `ADD_EXTRA` alone is carried out without `asksToAdd`.
  - Controller integration: generate → message "add a private show to the river boat cruise" (fake chat reply with the `ADD_EXTRA` edit) → 200, `edit.applied[0].op == "ADD_EXTRA"`, `edit.applied[0].extra == "Private show"`, `generation.packages[*].days[*].items[*].extras` carries it and `totalPrice` rose by 250.
- [ ] **Step 2: Run** → fails.
- [ ] **Step 3: Implement**
  - Parser: drop `ADD_EXTRA`/`REMOVE_EXTRA` without `extra`; pass `capped(edit.extra())` into the 9-arg constructor; `intoTheDraft` copies `extra`.
  - `PackageEditor.applyResolved`: for the two extra ops, resolve the activity (by id or name), `targets` = packages containing it (same branch as REMOVE), reject `NOT_IN_PACKAGE` when none; `applyToPackage` switch gains `case ADD_EXTRA -> applyAddExtra(current.get(), activity, edit.extra())` and `case REMOVE_EXTRA -> applyRemoveExtra(current.get(), activity, edit.extra())`; skip `violationIntroducedBy` for the two ops (no schedule change); `AppliedEdit` gets `extraName`.
  ```java
  private static Outcome applyAddExtra(PlanDraft.PackageDraft pkg, CatalogActivity activity, String extraName) {
      Optional<CatalogExtra> extra = ExtraNameResolver.resolve(extraName, activity.extras());
      if (extra.isEmpty()) {
          return Outcome.rejected(EditRejectionReason.UNKNOWN_EXTRA, offeredExtras(activity));
      }
      ... find the item(s) with activity.id(); none -> NOT_IN_PACKAGE; any already holding the code -> ALREADY_HAS_EXTRA;
      ... rebuild the day with new ItemDraft(slot, startHint, activityId, why, codes + code);
      return Outcome.ok(withDays(pkg, days), dayNumber, slot, activity.id());
  }
  ```
  (`Outcome` needs to carry the resolved `extraName` for the applied record: add a field or have `applyToPackage` resolve it once before the switch.)
  - `PackagesView.items`: append ` (+ a, b)` when an item has extras; `extrasCatalog` as specified.
  - `PromptRenderer.PACKAGES_BLOCK`: add after the catalog names line: `Optional extras per activity (activity -> extra (price)): %s` and these rules:
    ```
    - An extra is an add-on to an activity listed in "Optional extras" above, never an activity of its own. "add a private show to the boat", "with a stripper on the cruise", "put the photographer on the karting" is {"op": "ADD_EXTRA", "activity": the carrier's exact catalog name, "extra": the extra's exact name}. "no photographer", "drop the private show from the dinner" is {"op": "REMOVE_EXTRA", ...}.
    - "river cruise with a private show" when the cruise is NOT in the package yet is TWO edits: an ADD of the cruise and an ADD_EXTRA on it.
    - An extra the activity does not offer: still emit the ADD_EXTRA with the organizer's words as "extra"; the system answers with what the activity does offer.
    ```
    `packagesBlock(r)` formats the third `%s` with `r.extrasCatalog()`; `ChatTurnNode.request` passes `PackagesView.extrasCatalog(catalog)`.
  - `chat-system.st` edits shape: `"op": "ADD"|"REMOVE"|"REPLACE"|"MOVE"|"ADD_EXTRA"|"REMOVE_EXTRA", "activity": string, "replacement": string|null, "extra": string|null, ...`.
  - `ChatTurnNode` gate: `ADD_EXTRA`/`REMOVE_EXTRA` go straight into `carriedOut` (they are `!= ADD`, already true). Split-wish guard becomes:
    ```java
    if (found.split() && adds) {
        Set<String> carriers = carriedOut.stream().filter(e -> e.op() == EditOp.ADD_EXTRA)
                .map(e -> normalised(e.activity())).collect(Collectors.toSet());
        // "A river cruise with a private show" is one carrier plus its extra when the catalog offers the
        // extra on that cruise: both edits stand. Without such an extra the old rule holds.
        carriedOut.removeIf(edit -> edit.op() == EditOp.ADD && !carriers.contains(normalised(edit.activity())));
    }
    ```
  - `AiDtoMapper.appliedEdit/rejectedEdit`: pass `extraName`. `docs/api/ai-planner-api.md` "Editing packages": the two ops, the `extra` field, the three reasons, `AppliedEditDTO.extra`.
- [ ] **Step 4: Run** `./gradlew test --tests 'com.myhive.backend.ai.*'` → PASS, then the whole suite `./gradlew test` → PASS. **Step 5: Commit** — `feat(extras): chat adds and removes extras on package activities`.

---

### Task 9: CRA cart model and pricing

**Files:** Modify `myhive-react-app/src/utils/tripPricing.js`, `context/TripContext.js:69-141, 219-243`, `hooks/usePlanFromPrice.js:17-18`, `components/TripBuilder.js:48-61, 573-587`, `pages/AiPlannerPage.js:260-265`. Tests: `utils/tripPricing.test.js`, `context/TripContext.test.js`, `hooks/usePlanFromPrice.test.js`, `components/TripBuilder.test.js` (payload test), `pages/AiPlannerPage.test.js`.

**Interfaces (cart item):** `extras: [{code, name, description, price, pricing}]` (available), `selectedExtras: ['code']`. Reducer action `SET_ITEM_EXTRAS {id, selectedExtras}`. `tripPricing` exports `extrasOf(item)`, `extraAmount(extra, travelers)`, `extrasTotal(item, travelers)`, `baseLineTotal(item, travelers)`; `lineTotal` = base + extras; `groupMinApplied` judges the base.

- [ ] **Step 1: Failing tests**
```js
test('extras are added on top of the floored base and never discounted', () => {
  const item = {id: 'a', price: 40, minPrice: 120, extras: [
    {code: 'show', name: 'Show', price: 250, pricing: 'PER_GROUP'},
    {code: 'photo', name: 'Photo', price: 15, pricing: 'PER_PERSON'}], selectedExtras: ['show', 'photo']};
  expect(lineTotal(item, 2)).toBe(400);          // 120 + 250 + 30
  expect(groupMinApplied(item, 2)).toBe(true);
  const packaged = {...item, id: 'b', packageId: 'p', packageDiscountPct: 50, minPrice: 0, price: 100};
  expect(computeTripTotal([packaged], 2)).toBe(380); // 200 × 50 % + 250 + 30
});
test('a legacy cart item without extras fields prices as before', () => {
  expect(lineTotal({id: 'a', price: 50}, 3)).toBe(150);
});
```
Reducer: `SET_ITEM_EXTRAS` replaces the codes on that item only; `SET_TRIP_ITEMS_FROM_VOTE` keeps `selectedExtras` (and `extras` when the winner lacks them) from the previous item with the same id; `RESTORE_FROM_STORAGE` with items lacking the fields leaves them undefined and pricing still works (covered by the pricing test). `usePlanFromPrice`: the request body carries `extraCodes: ['show']` and changes when the selection changes. `TripBuilder` payload: `activities[0].extras == [{code, name, price, pricing}]` for selected ones only. `AiPlannerPage.pickIntoCart`: items dispatched carry `extras` and `selectedExtras` from the response.
- [ ] **Step 2: Run** `CI=true npm test -- --watchAll=false tripPricing TripContext usePlanFromPrice` → fails.
- [ ] **Step 3: Implement**
```js
// tripPricing.js
function selectedCodes(item) { return Array.isArray(item.selectedExtras) ? item.selectedExtras : []; }
export function extrasOf(item) {
    const selected = new Set(selectedCodes(item));
    return (Array.isArray(item.extras) ? item.extras : []).filter(extra => selected.has(extra.code));
}
export function extraAmount(extra, travelers) {
    const price = Number(extra.price) || 0;
    return extra.pricing === 'PER_PERSON' ? price * Math.max(1, travelers) : price;
}
export function extrasTotal(item, travelers) {
    return extrasOf(item).reduce((sum, extra) => sum + extraAmount(extra, travelers), 0);
}
export function baseLineTotal(item, travelers) {
    return Math.max(priceOf(item) * travelers, minPriceOf(item));
}
// Mirrors BookingService.lineBase + lineExtras and PlanPricer: floor the base, add extras at list price.
export function lineTotal(item, travelers) {
    return baseLineTotal(item, travelers) + extrasTotal(item, travelers);
}
export function groupMinApplied(item, travelers) {
    return baseLineTotal(item, travelers) > priceOf(item) * travelers;
}
export function computeTripTotal(tripItems, travelers) {
    const {standalone, groups} = groupTripItems(tripItems);
    let total = 0;
    standalone.forEach(it => { total += lineTotal(it, travelers); });
    groups.forEach(g => {
        // Floor each base first, discount the group's bases, then add extras undiscounted.
        const bases = g.items.reduce((s, it) => s + baseLineTotal(it, travelers), 0);
        const extras = g.items.reduce((s, it) => s + extrasTotal(it, travelers), 0);
        total += bases * (100 - g.packageDiscountPct) / 100 + extras;
    });
    return Math.round(total * 100) / 100;
}
```
Reducer:
```js
case 'SET_ITEM_EXTRAS':
    return {...state, tripItems: state.tripItems.map(item => (
        item.id === action.id ? {...item, selectedExtras: action.selectedExtras} : item))};
```
and in `SET_TRIP_ITEMS_FROM_VOTE`: `const standaloneWinners = action.winners.filter(...).map(winner => withExtrasFrom(state.tripItems, winner));` with
```js
// A winner is a fresh row built from the vote result; the extras the organizer ticked before the vote
// belong to the activity, not to the ballot, so they ride over from the cart entry it replaces.
function withExtrasFrom(previousItems, winner) {
    const previous = previousItems.find(item => item.id === winner.id);
    if (!previous) {
        return winner;
    }
    return {...winner,
        extras: winner.extras && winner.extras.length > 0 ? winner.extras : (previous.extras || []),
        selectedExtras: previous.selectedExtras || []};
}
```
Relocalise: `{...item, name, description, includes, extras: fresh[i].extras ?? item.extras}`. `usePlanFromPrice` key: `{activityId: item.id, packageId: item.packageId || null, extraCodes: [...(item.selectedExtras || [])].sort()}`. `voteWinnersAsTripItems`: `extras: row.extras || []`. `buildBookingData`: `extras: extrasOf(item).map(e => ({code: e.code, name: e.name, price: e.price, pricing: e.pricing}))`. `pickIntoCart`: `({...a, id: a.activityId, extras: a.extras || [], selectedExtras: a.selectedExtras || []})`.
- [ ] **Step 4: Run** → PASS. **Step 5: Commit** — `feat(extras): cart carries and prices selected extras`.

---

### Task 10: Customer-facing surfaces

**Files:**
- Create: `components/ItemExtrasPicker.js` + `ItemExtrasPicker.css` (checklist: one row per extra — checkbox, name, one-line description, `+€250 per group` / `+€15 per person`; props `extras`, `selected`, `onChange(codes)`, `compact`)
- Modify: `components/ActivityCard.js` (+ `.css`), `pages/ActivityDetailPage.js:100-110, 211-230`, `components/ActivityPreviewModal.js:118-127`, `components/TripBuilder.js:955-984` (row: chips + "Add extras" toggle revealing the picker inline), `components/TripBuilderDropdown.js:93-100`, `i18n/messages/en.json`, `i18n/messages/de.json`, `utils/format.js` (add `extraPriceLabel(extra, t)` → `+€250 per group`)
- Tests: `components/ItemExtrasPicker.test.js` (new), `components/ActivityCard.test.js`, `pages/ActivityDetailPage.test.js`, `components/ActivityPreviewModal.test.js`, `components/TripBuilder.test.js`, `components/TripBuilderDropdown.test.js`

**i18n keys** (`en` / `de`), new top-level namespace `extras`:
```
extras.title            "Optional extras"            / "Optionale Extras"
extras.perGroup         "per group"                  / "pro Gruppe"
extras.perPerson        "per person"                 / "pro Person"
extras.add              "Add extras"                 / "Extras hinzufügen"
extras.done             "Done"                       / "Fertig"
extras.removeAria       "Remove {name}"              / "{name} entfernen"
extras.selectedSummary  "+ {amount} extras"          / "+ {amount} Extras"
extras.none             "No extras selected"         / "Keine Extras gewählt"
cards.extrasChip        "Optional extras · {names}"  / "Optionale Extras · {names}"
cards.extrasMore        "+{count} more"              / "+{count} weitere"
```
Checkboxes start unticked (A3 and CRD Art. 22). Card: chip only when `activity.extras?.length > 0`, first two names, then `cards.extrasMore`; still no price on the card (existing test must keep passing). Detail page: `<section className="activity-detail-extras">` with the picker above the add panel on mobile / inside the aside above the button on desktop; `handleAddToTrip` dispatches `{...activity, selectedExtras}`; the price panel shows `extras.selectedSummary` with `formatAmount(extrasTotal({...activity, selectedExtras}, 1))` — one person, since the page has no group size, and the label stays "+ €X extras". Preview modal: read-only list under "What's included" with `extraPriceLabel`. Trip Builder standalone row: under the title, `<div className="itinerary-item-extras">` with one chip per selected extra (`name · +€250`, × button → `SET_ITEM_EXTRAS` without that code) and a text button `extras.add` that toggles the inline picker (`compact`), present only when `item.extras?.length > 0`. Dropdown: `extras.selectedSummary` under the price when any is selected.

- [ ] **Step 1: Failing tests** (RTL): picker renders unticked rows with the unit label and calls `onChange(['show'])` on a tick; card shows the chip for an activity with extras and nothing for one without, and still no `€`; detail page add dispatches `selectedExtras` after ticking; modal lists extras read-only; Trip Builder row shows the chip after `SET_ITEM_EXTRAS` and the × dispatches the removal; dropdown shows "+ €250 extras".
- [ ] **Step 2: Run** → fails. **Step 3: Implement** as described (follow the existing class naming `itinerary-item-*`, `activity-detail-*`, `activity-preview-*`; CSS tokens from `ActivityCard.css`/`TripBuilder.css`). **Step 4: Run** `CI=true npm test -- --watchAll=false` → PASS. **Step 5: Commit** — `feat(extras): extras on cards, detail page, preview and Trip Builder rows`.

---

### Task 11: Admin and AI views

**Files:**
- Create: `components/admin/ExtrasEditor.js` (rows: name, price, pricing select, description; add/remove row; `value`/`onChange`; dark-theme admin classes)
- Modify: `pages/AdminActivities.js:13-39, 85-107, 312-326` (form field `extras: []`, `mapItemToForm`, `buildPayload` with `price: Number(...)`, `validate` each row name/price, table column `{key: 'extras', label: 'Extras'}` rendering the count), `pages/AdminBookingDetail.js:425-436` (extras lines under each item: `+ name · €amount per group/person`), `pages/AdminAiPlanner.js:578-581` (`item.extras` lines with `money(extra.total)`), `components/ai/AiPackageView.js:238-247` (chips `+ name` under the sub line)
- Tests: `components/admin/ExtrasEditor.test.js` (new), `pages/AdminActivities.test.js` (if present; else `hooks/useAdminCrud.test.js` payload test), `pages/AdminBookingDetail.test.js`, `pages/AdminAiPlanner.test.js`

- [ ] **Step 1: Failing tests**: editor adds a row and emits `[{name:'Private show', price: 250, pricing:'PER_GROUP', description:''}]`; admin save payload carries `extras` (and `[]` when the user removed all, never `null`); booking detail renders `+ Private show · €250 per group`; AI package view renders the `+ Private show` chip; admin bench renders the extra line with its amount.
- [ ] **Step 2–5**: implement; run `CI=true npm test -- --watchAll=false admin Ai`; commit — `feat(extras): admin extras editor, booking detail and AI views`.

---

### Task 12: Dev seed, README, graph

**Files:** Modify `myhive-backend/src/main/resources/data-prague.sql` (append `UPDATE activities SET extras = '[...]' WHERE slug = '...'` for `river-boat-cruise`, `tiki-boat`, `swimming-beer-bike`, `party-limo-ride`, `hummer-limo`, `czech-lunch-or-dinner`, `medieval-dinner`, `succulent-bbq-ribs`, `party-starter-on-terrace`, `private-game-room-session`, `vip-club-entrance`, `go-karting-experience` using the extras catalogue from the content CSV), `README.md` (feature line), `CLAUDE.md` (Key Architectural Patterns: one "Activity extras" bullet with the pricing rule and the synced copies), run `graphify update .`.

- [ ] Steps: add the seed, boot `./gradlew bootRun --args='--spring.profiles.active=dev --server.port=8081'`, `curl localhost:8081/activities/slug/river-boat-cruise` shows `extras`; `./gradlew test` full suite PASS; `CI=true npm test -- --watchAll=false` PASS; commit — `chore(extras): dev seed, docs, graph`.

---

## Content and import phase (after Task 12; operations, not code)

### Task 13: Catalogue content CSV
- Export the prod catalogue through the admin **Export CSV** (or the dev seed as a stand-in for the row ids: prod ids differ — use the prod export for the final file).
- Rewrite `description` (hook sentence + 2 short paragraphs ≤ 800 chars) and `includes` (5–9 `;`-separated bullets with duration and drink counts) for every Prague row per the research recommendations; keep names/slugs/prices/min_price.
- Fill `extras` JSON for the carrier activities (boats, dinners, limos, beer bike, club, terrace, game room, karting) with EN + DE names.
- Add net-new rows (blank `id`): Pub crawl with guide, Beer bike (classic), Escape room, Medieval sword fighting, Segway tour, Brewery tour, Ice bar, Rage room, Bungee / bobsled, Football match tickets — only those the research shows on ≥ 3 operators and not already in the catalogue.
- Save to `docs/research/prague-catalogue-2026-10.csv` (UTF-8 with BOM) and a `docs/research/prague-images-manifest.json` (source, id, photographer, licence URL, fetch date).

### Task 14: Image set
- One Pexels/Unsplash photo per new row, 4:3, `?auto=compress&cs=tinysrgb&w=1200&h=900&fit=crop` (Pexels) / `?w=1200&h=900&fit=crop&crop=entropy&q=80` (Unsplash); props-and-venue convention for adult categories; warm night palette for nightlife, daylight for sport.
- Record every image in the manifest; never a competitor image.

### Task 15: Local import regression
- Backend dev on 8081, CRA on 3100 with `REACT_APP_API_URL=http://localhost:8081`; admin login; **Import CSV → preview**: 0 errors, the warnings list is only `READ_ONLY_FIELD_CHANGED`/`SLUG_TAKEN` as expected (dev: new rows with external `image_url` show `IMAGE_UPLOAD_UNAVAILABLE` — expected in dev, re-check the row count on prod); **apply** on dev; browser check of a card with the chip, the detail picker, a Trip Builder row with an extra and the quote update, the AI bench "add a private show to the river boat cruise".
- Prod import is **owner-triggered** after both deploys (rollout order in the spec).
