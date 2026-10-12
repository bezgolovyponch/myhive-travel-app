package com.myhive.backend.ai.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.catalog.CatalogPreset;
import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.graph.nodes.FallbackNode;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.DayEdge;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Reverse test of the generation pipeline's deterministic half against a real catalogue export
 * ({@code PLANNER_FUZZ_DIR=<dir with acts.json and pk.json>}, the public /activities and /packages
 * answers for one destination). The model is replaced by an adversary: whatever it writes, the plan that
 * ships is either the checked draft, the trimmed draft, or the fallback - and each is judged here by an
 * oracle that shares no code with {@link PlanValidator}. Reports to {@code fuzz-report.json} and fails when
 * a plan that breaks a rule would have shipped.
 */
@EnabledIfEnvironmentVariable(named = "PLANNER_FUZZ_DIR", matches = ".+")
class PlannerInvariantFuzzTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int BUFFER = 30;

    private final PlanValidator validator = new PlanValidator();
    private final PlanAssembler assembler = new PlanAssembler();
    private final PlanTrimmer trimmer = new PlanTrimmer(validator, assembler);
    private final FallbackPlanComposer fallback = new FallbackPlanComposer();

    private List<CatalogActivity> all;
    private List<JsonNode> packages;

    private enum Outcome { ACCEPTED, CORRECTED, FALLBACK }

    private record Shipped(Outcome outcome, ComposedPlan plan) {
    }

    @Test
    void report() throws Exception {
        Path dir = Path.of(System.getenv("PLANNER_FUZZ_DIR"));
        load(dir);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("catalogSize", all.size());
        report.put("fallbackGrid", fallbackGrid());
        report.put("adversarialModel", fuzz(20_000, 42, false));
        report.put("plausibleModel", fuzz(20_000, 43, true));
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(dir.toFile(), "fuzz-report.json"), report);
        // The gate: whatever the model writes, no plan that breaks a rule may ship.
        Map<?, ?> grid = (Map<?, ?>) report.get("fallbackGrid");
        assertThat(grid.get("fallbackNodePlansBreakingARule")).as("plans from FallbackNode that break a rule").isEqualTo(0);
        for (String model : List.of("adversarialModel", "plausibleModel")) {
            Map<?, ?> run = (Map<?, ?>) report.get(model);
            assertThat(run.get("acceptedByValidatorButRejectedByOracle")).as(model + ": accepted but wrong").isEqualTo(0);
            assertThat(run.get("proposedShippedPlansBreakingARule")).as(model + ": shipped but wrong").isEqualTo(0);
        }
    }

    /** The patched FallbackNode on every other destination's catalogue: does the chain still end in a sound plan? */
    @Test
    void otherDestinations() throws Exception {
        Path dir = Path.of(System.getenv("PLANNER_FUZZ_DIR"));
        File[] slugs = dir.resolve("others").toFile().listFiles(File::isDirectory);
        if (slugs == null) {
            return;
        }
        Map<String, Object> report = new TreeMap<>();
        for (File slug : slugs) {
            weights.clear();
            load(slug.toPath());
            int runs = 0;
            int bad = 0;
            Map<String, Integer> rules = new TreeMap<>();
            List<CatalogActivity> catalog = snapshot(List.of());
            List<CatalogPreset> presets = presets(catalog);
            for (int days = Brief.MIN_DAYS; days <= Brief.MAX_DAYS; days++) {
                for (int group = Brief.MIN_GROUP; group <= Brief.MAX_GROUP; group++) {
                    for (DayEdge arrival : edges()) {
                        for (DayEdge departure : edges()) {
                            Brief brief = new Brief(days, group, List.of(), "x", null, null, arrival, departure, null);
                            Map<String, Object> values = new HashMap<>();
                            values.put(PlannerState.BRIEF, JsonCodec.write(brief));
                            values.put(PlannerState.CATALOG, JsonCodec.write(catalog));
                            values.put(PlannerState.PRESETS, JsonCodec.write(presets));
                            Map<String, Object> update = new FallbackNode(fallback).apply(new PlannerState(values));
                            ComposedPlan shipped = JsonCodec.read((String) update.get(PlannerState.RESULT), ComposedPlan.class);
                            List<String> found = oracle(shipped, brief, byId(catalog));
                            runs++;
                            if (!found.isEmpty()) {
                                bad++;
                                String shape = "days=" + days;
                                found.stream().map(x -> x.split(":")[0]).distinct()
                                        .forEach(code -> rules.merge(code + " " + shape, 1, Integer::sum));
                            }
                        }
                    }
                }
            }
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("catalog", catalog.size());
            one.put("presets", presets.size());
            one.put("runs", runs);
            one.put("broken", bad);
            one.put("rules", rules);
            report.put(slug.getName(), one);
        }
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(dir.toFile(), "fuzz-report-others.json"), report);
    }

    // ---- experiment 1: the fallback alone, over every brief shape the UI can send ----

    private Map<String, Object> fallbackGrid() {
        Map<String, Integer> oracle = new TreeMap<>();
        Map<String, Integer> byDays = new TreeMap<>();
        List<String> examples = new ArrayList<>();
        int runs = 0;
        int bad = 0;
        int chainBad = 0;
        int nodeBad = 0;
        Map<String, Integer> chain = new TreeMap<>();
        for (boolean withPresets : new boolean[] {true, false}) {
            for (List<String> categories : categorySets()) {
                for (int days = Brief.MIN_DAYS; days <= Brief.MAX_DAYS; days++) {
                    for (int group = Brief.MIN_GROUP; group <= Brief.MAX_GROUP; group++) {
                        for (DayEdge arrival : edges()) {
                            for (DayEdge departure : edges()) {
                                Brief brief = new Brief(days, group, categories, "x", null, null, arrival, departure, null);
                                List<CatalogActivity> catalog = snapshot(categories);
                                List<CatalogPreset> presets = withPresets ? presets(catalog) : List.of();
                                ComposedPlan plan = fallbackPlan(brief, catalog, presets);
                                List<String> found = oracle(plan, brief, byId(catalog));
                                runs++;
                                Closed closed = closedFallback(brief, catalog, presets);
                                chain.merge(closed.step(), 1, Integer::sum);
                                if (!oracle(closed.plan(), brief, byId(catalog)).isEmpty()) {
                                    chainBad++;
                                }
                                Map<String, Object> values = new HashMap<>();
                                values.put(PlannerState.BRIEF, JsonCodec.write(brief));
                                values.put(PlannerState.CATALOG, JsonCodec.write(catalog));
                                values.put(PlannerState.PRESETS, JsonCodec.write(presets));
                                Map<String, Object> update = new FallbackNode(fallback).apply(new PlannerState(values));
                                ComposedPlan shipped = JsonCodec.read((String) update.get(PlannerState.RESULT), ComposedPlan.class);
                                if (!oracle(shipped, brief, byId(catalog)).isEmpty()) {
                                    nodeBad++;
                                }
                                if (!found.isEmpty()) {
                                    bad++;
                                    byDays.merge("days=" + days + (withPresets ? " presets" : " free"), 1, Integer::sum);
                                    found.stream().map(f -> f.split(":")[0]).distinct()
                                            .forEach(code -> oracle.merge(code, 1, Integer::sum));
                                    if (examples.size() < 12) {
                                        examples.add("presets=" + withPresets + " cats=" + categories + " days=" + days
                                                + " group=" + group + " arr=" + arrival + " dep=" + departure + " -> " + found);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runs", runs);
        out.put("plansBreakingARule", bad);
        out.put("closedChainSteps", chain);
        out.put("closedChainPlansBreakingARule", chainBad);
        out.put("fallbackNodePlansBreakingARule", nodeBad);
        out.put("byRule", oracle);
        out.put("byShape", byDays);
        out.put("examples", examples);
        return out;
    }

    // ---- experiments 2 and 3: a model that writes anything the schema allows / a model that overfills ----

    private Map<String, Object> fuzz(int runs, long seed, boolean plausible) {
        Random random = new Random(seed);
        Map<Outcome, Integer> outcomes = new TreeMap<>();
        Map<String, Integer> shippedBroken = new TreeMap<>();
        Map<String, Integer> brokenBy = new TreeMap<>();
        Map<String, Integer> rejectedFor = new TreeMap<>();
        List<String> examples = new ArrayList<>();
        int broken = 0;
        int validatorDisagrees = 0;
        int proposedBroken = 0;
        Map<String, Integer> proposedSteps = new TreeMap<>();
        for (int i = 0; i < runs; i++) {
            List<String> categories = categorySets().get(random.nextInt(categorySets().size()));
            int days = 1 + random.nextInt(plausible ? 4 : Brief.MAX_DAYS);
            int group = 2 + random.nextInt(29);
            Brief brief = new Brief(days, group, categories, "x", null, null, edges().get(random.nextInt(edges().size())),
                    edges().get(random.nextInt(edges().size())), null);
            List<CatalogActivity> catalog = snapshot(categories);
            List<CatalogPreset> presets = presets(catalog);
            Map<UUID, CatalogActivity> byId = byId(catalog);
            PlanDraft draft = plausible ? plausibleDraft(random, brief, catalog, presets) : wildDraft(random, brief, catalog);

            List<Violation> asWritten = check(draft, brief, byId);
            Shipped shipped = ship(draft, asWritten, brief, catalog, presets, byId);
            outcomes.merge(shipped.outcome(), 1, Integer::sum);
            if (shipped.outcome() == Outcome.FALLBACK) {
                asWritten.stream().map(v -> v.code().name()).distinct()
                        .forEach(code -> rejectedFor.merge(code, 1, Integer::sum));
            }
            Closed proposed = shipProposed(draft, asWritten, brief, catalog, presets, byId);
            proposedSteps.merge(proposed.step(), 1, Integer::sum);
            if (!oracle(proposed.plan(), brief, byId).isEmpty()) {
                proposedBroken++;
            }
            List<String> found = oracle(shipped.plan(), brief, byId);
            if (!found.isEmpty()) {
                broken++;
                brokenBy.merge(shipped.outcome().name(), 1, Integer::sum);
                found.stream().map(f -> f.split(":")[0]).distinct()
                        .forEach(code -> shippedBroken.merge(code, 1, Integer::sum));
                if (shipped.outcome() != Outcome.FALLBACK) {
                    validatorDisagrees++;
                }
                if (examples.size() < 8) {
                    examples.add(shipped.outcome() + " days=" + days + " group=" + group + " cats=" + categories + " -> " + found);
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runs", runs);
        out.put("outcomes", outcomes);
        out.put("fallbackCausedBy", rejectedFor);
        out.put("shippedPlansBreakingARule", broken);
        out.put("brokenByOutcome", brokenBy);
        out.put("brokenByRule", shippedBroken);
        out.put("acceptedByValidatorButRejectedByOracle", validatorDisagrees);
        out.put("proposedSteps", proposedSteps);
        out.put("proposedShippedPlansBreakingARule", proposedBroken);
        out.put("examples", examples);
        return out;
    }

    /** What ValidateNode does, with the repair call assumed to fail: as written, else trimmed, else fallback. */
    private Shipped ship(PlanDraft draft, List<Violation> asWritten, Brief brief, List<CatalogActivity> catalog,
            List<CatalogPreset> presets, Map<UUID, CatalogActivity> byId) {
        if (asWritten.isEmpty()) {
            return new Shipped(Outcome.ACCEPTED, assembler.assemble(draft, brief, byId, false).plan());
        }
        try {
            Optional<PlanTrimmer.Trimmed> trimmed = trimmer.trim(draft, brief, byId);
            if (trimmed.isPresent() && check(trimmed.get().draft(), brief, byId).isEmpty()) {
                return new Shipped(Outcome.CORRECTED, assembler.assemble(trimmed.get().draft(), brief, byId, false).plan());
            }
        } catch (RuntimeException e) {
            // ValidateNode treats a throwing correction as "not corrected"
        }
        return new Shipped(Outcome.FALLBACK, fallbackPlan(brief, catalog, presets));
    }

    private record Closed(String step, ComposedPlan plan) {
    }

    /** The fallback as a chain in which every link is checked: ready-made, the same trimmed, then free. */
    private Closed closedFallback(Brief brief, List<CatalogActivity> catalog, List<CatalogPreset> presets) {
        Map<UUID, CatalogActivity> byId = byId(catalog);
        PlanDraft withPresets = fallback.compose(brief, catalog, presets, "en");
        if (check(withPresets, brief, byId).isEmpty()) {
            return new Closed("1_FALLBACK_PRESETS", assembler.assemble(withPresets, brief, byId, true).plan());
        }
        try {
            Optional<PlanTrimmer.Trimmed> trimmed = trimmer.trim(withPresets, brief, byId);
            if (trimmed.isPresent() && check(trimmed.get().draft(), brief, byId).isEmpty()) {
                return new Closed("2_FALLBACK_PRESETS_TRIMMED",
                        assembler.assemble(trimmed.get().draft(), brief, byId, true).plan());
            }
        } catch (RuntimeException e) {
            // next link
        }
        PlanDraft free = fallback.compose(brief, catalog, List.of(), "en");
        String step = check(free, brief, byId).isEmpty() ? "3_FALLBACK_FREE" : "4_NOTHING_VALID";
        return new Closed(step, assembler.assemble(free, brief, byId, true).plan());
    }

    /** As {@link #ship}, plus "the model picks, the code schedules" before giving up, and the closed fallback. */
    private Closed shipProposed(PlanDraft draft, List<Violation> asWritten, Brief brief, List<CatalogActivity> catalog,
            List<CatalogPreset> presets, Map<UUID, CatalogActivity> byId) {
        if (asWritten.isEmpty()) {
            return new Closed("A_ACCEPTED", assembler.assemble(draft, brief, byId, false).plan());
        }
        try {
            Optional<PlanTrimmer.Trimmed> trimmed = trimmer.trim(draft, brief, byId);
            if (trimmed.isPresent() && check(trimmed.get().draft(), brief, byId).isEmpty()) {
                return new Closed("B_CORRECTED", assembler.assemble(trimmed.get().draft(), brief, byId, false).plan());
            }
        } catch (RuntimeException e) {
            // next step
        }
        PlanDraft placed = replace(draft, brief, byId);
        if (check(placed, brief, byId).isEmpty()) {
            return new Closed("C_RESCHEDULED", assembler.assemble(placed, brief, byId, false).plan());
        }
        Closed closed = closedFallback(brief, catalog, presets);
        return new Closed("D_" + closed.step(), closed.plan());
    }

    /**
     * The model's picks kept, its schedule thrown away: per tier the distinct activities in the order
     * written, each put on the day the model wanted if it fits there, else the first day with room.
     */
    private static PlanDraft replace(PlanDraft draft, Brief brief, Map<UUID, CatalogActivity> byId) {
        List<PlanDraft.PackageDraft> packages = new ArrayList<>();
        for (Tier tier : Tier.values()) {
            PlanDraft.PackageDraft written = draft.packages().stream().filter(p -> p.key() == tier).findFirst().orElse(null);
            int dayCount = brief.days();
            List<List<PlanDraft.ItemDraft>> perDay = new ArrayList<>();
            int[] minutes = new int[dayCount];
            for (int d = 0; d < dayCount; d++) {
                perDay.add(new ArrayList<>());
            }
            Set<UUID> seen = new HashSet<>();
            if (written != null) {
                for (PlanDraft.DayDraft day : written.days()) {
                    for (PlanDraft.ItemDraft item : day.items()) {
                        CatalogActivity a = byId.get(item.activityId());
                        if (a == null || !seen.add(a.id())) {
                            continue;
                        }
                        int wanted = Math.min(Math.max(day.dayNumber(), 1), dayCount) - 1;
                        for (int k = 0; k < dayCount; k++) {
                            int d = (wanted + k) % dayCount;
                            List<Slot> open = new ArrayList<>(PlanValidator.allowedSlots(d + 1, brief));
                            perDay.get(d).forEach(placedItem -> open.remove(placedItem.slot()));
                            int buffer = perDay.get(d).isEmpty() ? 0 : BUFFER;
                            if (open.isEmpty() || perDay.get(d).size() >= tier.maxItemsPerDay()
                                    || minutes[d] + buffer + a.durationMinutes() > tier.maxMinutesPerDay()) {
                                continue;
                            }
                            Slot slot = open.contains(item.slot()) ? item.slot() : open.get(0);
                            perDay.get(d).add(new PlanDraft.ItemDraft(slot, null, a.id(), null));
                            minutes[d] += buffer + a.durationMinutes();
                            break;
                        }
                    }
                }
            }
            List<PlanDraft.DayDraft> days = new ArrayList<>();
            for (int d = 0; d < dayCount; d++) {
                days.add(new PlanDraft.DayDraft(d + 1, null, null, perDay.get(d)));
            }
            packages.add(new PlanDraft.PackageDraft(tier, null, null, null, days));
        }
        return new PlanDraft(packages);
    }

    private List<Violation> check(PlanDraft draft, Brief brief, Map<UUID, CatalogActivity> byId) {
        List<Violation> violations = new ArrayList<>(validator.validate(draft, brief, byId));
        if (violations.isEmpty()) {
            violations.addAll(assembler.assemble(draft, brief, byId, false).violations());
        }
        return violations;
    }

    private ComposedPlan fallbackPlan(Brief brief, List<CatalogActivity> catalog, List<CatalogPreset> presets) {
        return assembler.assemble(fallback.compose(brief, catalog, presets, "en"), brief, byId(catalog), true).plan();
    }

    /** Anything the strict schema lets through: real codes and slots, no sense of the rules. */
    private static PlanDraft wildDraft(Random random, Brief brief, List<CatalogActivity> catalog) {
        List<PlanDraft.PackageDraft> packages = new ArrayList<>();
        for (Tier tier : Tier.values()) {
            if (random.nextInt(100) < 3) {
                continue;
            }
            int dayCount = random.nextInt(100) < 5 ? Math.max(1, brief.days() + random.nextInt(3) - 1) : brief.days();
            List<PlanDraft.DayDraft> days = new ArrayList<>();
            for (int day = 1; day <= dayCount; day++) {
                List<PlanDraft.ItemDraft> items = new ArrayList<>();
                int count = random.nextInt(7);
                for (int k = 0; k < count; k++) {
                    items.add(new PlanDraft.ItemDraft(Slot.values()[random.nextInt(4)], null,
                            catalog.get(random.nextInt(catalog.size())).id(), null));
                }
                days.add(new PlanDraft.DayDraft(day, null, null, items));
            }
            packages.add(new PlanDraft.PackageDraft(tier, null, null, null, days));
        }
        return new PlanDraft(packages);
    }

    /**
     * The mistakes the live runs of 2026-09-29 made: the tier's ready-made package laid out day by day,
     * then one to three more activities pushed in, now and then a repeat or a slot outside the window.
     */
    private static PlanDraft plausibleDraft(Random random, Brief brief, List<CatalogActivity> catalog,
            List<CatalogPreset> presets) {
        List<PlanDraft.PackageDraft> packages = new ArrayList<>();
        for (Tier tier : Tier.values()) {
            List<UUID> picks = new ArrayList<>();
            List<CatalogPreset> ofTier = presets.stream().filter(p -> p.tier() == tier).toList();
            if (!ofTier.isEmpty()) {
                picks.addAll(ofTier.get(random.nextInt(ofTier.size())).activityIds());
            }
            int more = 1 + random.nextInt(3) + (ofTier.isEmpty() ? 2 + tier.ordinal() : 0);
            for (int k = 0; k < more; k++) {
                picks.add(catalog.get(random.nextInt(catalog.size())).id());
            }
            if (random.nextInt(100) < 10 && !picks.isEmpty()) {
                picks.add(picks.get(0));
            }
            List<List<PlanDraft.ItemDraft>> perDay = new ArrayList<>();
            for (int day = 1; day <= brief.days(); day++) {
                perDay.add(new ArrayList<>());
            }
            for (int k = 0; k < picks.size(); k++) {
                int day = k % brief.days();
                List<Slot> allowed = new ArrayList<>(PlanValidator.allowedSlots(day + 1, brief));
                Slot slot = random.nextInt(100) < 10 ? Slot.values()[random.nextInt(4)]
                        : allowed.get(Math.min(perDay.get(day).size(), allowed.size() - 1));
                perDay.get(day).add(new PlanDraft.ItemDraft(slot, null, picks.get(k), null));
            }
            List<PlanDraft.DayDraft> days = new ArrayList<>();
            for (int day = 1; day <= brief.days(); day++) {
                days.add(new PlanDraft.DayDraft(day, null, null, perDay.get(day - 1)));
            }
            packages.add(new PlanDraft.PackageDraft(tier, null, null, null, days));
        }
        return new PlanDraft(packages);
    }

    // ---- the oracle: the rules of planner-system.st, restated without PlanValidator ----

    private static List<String> oracle(ComposedPlan plan, Brief brief, Map<UUID, CatalogActivity> catalog) {
        List<String> found = new ArrayList<>();
        int n = brief.groupSize();
        Map<Tier, ComposedPlan.PackageResult> byTier = new TreeMap<>();
        plan.packages().forEach(p -> byTier.put(p.key(), p));
        if (byTier.size() != 3 || plan.packages().size() != 3) {
            found.add("THREE_PACKAGES:" + byTier.keySet());
            return found;
        }
        Map<Tier, Set<UUID>> ids = new TreeMap<>();
        for (ComposedPlan.PackageResult p : plan.packages()) {
            Tier tier = p.key();
            Set<UUID> seen = new HashSet<>();
            BigDecimal total = BigDecimal.ZERO;
            if (p.days().size() != brief.days()) {
                found.add("DAY_COUNT:" + tier);
            }
            int items = 0;
            for (ComposedPlan.DayResult day : p.days()) {
                int d = day.dayNumber();
                int first = d == 1 ? brief.arrivalOrDefault().slot().ordinal() : 0;
                int last = d == brief.days() ? brief.departureOrDefault().slot().ordinal() : 3;
                if (last < first) {
                    last = 3;
                }
                Set<Slot> used = EnumSet.noneOf(Slot.class);
                int minutes = 0;
                for (ComposedPlan.ItemResult item : day.items()) {
                    items++;
                    CatalogActivity a = catalog.get(item.activityId());
                    if (a == null) {
                        found.add("NOT_IN_CATALOG:" + tier + "/d" + d);
                        continue;
                    }
                    if (!a.name().equals(item.name()) || a.price().compareTo(item.price()) != 0) {
                        found.add("NAME_OR_PRICE_NOT_CATALOG:" + tier + "/d" + d);
                    }
                    BigDecimal line = a.price().multiply(BigDecimal.valueOf(n));
                    if (a.minPrice() != null && a.minPrice().signum() > 0) {
                        line = line.max(a.minPrice());
                    }
                    if (line.setScale(2, RoundingMode.HALF_UP).compareTo(item.lineTotal()) != 0) {
                        found.add("LINE_PRICE:" + tier + "/d" + d);
                    }
                    total = total.add(item.lineTotal());
                    if (!seen.add(a.id())) {
                        found.add("REPEATED:" + tier + "/d" + d);
                    }
                    if (item.slot() == null || item.slot().ordinal() < first || item.slot().ordinal() > last) {
                        found.add("OUTSIDE_WINDOW:" + tier + "/d" + d);
                    } else if (!used.add(item.slot())) {
                        found.add("SLOT_TWICE:" + tier + "/d" + d);
                    }
                    minutes += a.durationMinutes();
                }
                minutes += BUFFER * Math.max(0, day.items().size() - 1);
                if (day.items().size() > tier.maxItemsPerDay()) {
                    found.add("TOO_MANY:" + tier + "/d" + d);
                }
                if (minutes > tier.maxMinutesPerDay()) {
                    found.add("TOO_LONG:" + tier + "/d" + d);
                }
                if (day.items().isEmpty() && d != 1 && d != brief.days()) {
                    found.add("EMPTY_MIDDLE_DAY:" + tier + "/d" + d);
                }
            }
            if (items == 0) {
                found.add("EMPTY_PACKAGE:" + tier);
            }
            if (total.compareTo(p.totalPrice()) != 0
                    || total.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP).compareTo(p.pricePerPerson()) != 0) {
                found.add("PACKAGE_PRICE:" + tier);
            }
            ids.put(tier, seen);
        }
        if (byTier.get(Tier.MEDIUM).pricePerPerson().compareTo(byTier.get(Tier.BASIC).pricePerPerson()) <= 0
                || byTier.get(Tier.PREMIUM).pricePerPerson().compareTo(byTier.get(Tier.MEDIUM).pricePerPerson()) <= 0) {
            found.add("TIER_PRICE_ORDER:" + byTier.get(Tier.BASIC).pricePerPerson() + "/"
                    + byTier.get(Tier.MEDIUM).pricePerPerson() + "/" + byTier.get(Tier.PREMIUM).pricePerPerson());
        }
        for (Tier tier : Tier.values()) {
            Set<UUID> own = new HashSet<>(ids.get(tier));
            for (Tier other : Tier.values()) {
                if (other != tier) {
                    own.removeAll(ids.get(other));
                }
            }
            if (own.isEmpty()) {
                found.add("TIER_NOT_DISTINCT:" + tier);
            }
        }
        return found;
    }

    // ---- catalogue export -> the snapshot the planner sees ----

    private void load(Path dir) throws Exception {
        all = new ArrayList<>();
        for (JsonNode a : MAPPER.readTree(Files.readString(dir.resolve("acts.json")))) {
            List<String> slugs = new ArrayList<>();
            a.path("categories").forEach(c -> slugs.add(c.path("slug").asText()));
            slugs.sort(Comparator.naturalOrder());
            int duration = a.path("duration").asInt(0);
            all.add(new CatalogActivity(UUID.fromString(a.get("id").asText()), a.path("slug").asText(),
                    a.get("name").asText(), "", duration > 0 ? duration : 120, duration > 0,
                    new BigDecimal(a.get("price").asText()),
                    a.path("minPrice").isNumber() ? new BigDecimal(a.get("minPrice").asText()) : null, null, slugs,
                    null, List.of()));
            weights.put(UUID.fromString(a.get("id").asText()), a.path("featuredWeight").asInt(0));
        }
        packages = new ArrayList<>();
        MAPPER.readTree(Files.readString(dir.resolve("pk.json"))).forEach(packages::add);
    }

    private final Map<UUID, Integer> weights = new LinkedHashMap<>();

    /** CatalogSnapshotter.snapshot: category overlap, then featured weight, then name; first 80. */
    private List<CatalogActivity> snapshot(List<String> wanted) {
        Comparator<CatalogActivity> order = Comparator
                .comparingInt((CatalogActivity a) -> (int) a.categorySlugs().stream().filter(wanted::contains).count())
                .reversed()
                .thenComparing(Comparator.comparingInt((CatalogActivity a) -> weights.get(a.id())).reversed())
                .thenComparing(CatalogActivity::name);
        return all.stream().sorted(order).limit(80).toList();
    }

    private List<CatalogPreset> presets(List<CatalogActivity> catalog) {
        Set<UUID> known = new HashSet<>();
        catalog.forEach(a -> known.add(a.id()));
        List<CatalogPreset> presets = new ArrayList<>();
        for (JsonNode p : packages) {
            if (!p.hasNonNull("tier")) {
                continue;
            }
            List<UUID> ids = new ArrayList<>();
            for (JsonNode a : p.path("activities")) {
                UUID id = UUID.fromString(a.get("activityId").asText());
                if (known.contains(id) && !ids.contains(id)) {
                    ids.add(id);
                }
            }
            List<String> slugs = new ArrayList<>();
            p.path("categories").forEach(c -> slugs.add(c.path("slug").asText()));
            if (!ids.isEmpty()) {
                presets.add(new CatalogPreset(UUID.fromString(p.get("id").asText()), p.get("name").asText(),
                        Tier.valueOf(p.get("tier").asText()), ids, slugs));
            }
        }
        presets.sort(Comparator.comparing(CatalogPreset::tier).thenComparing(CatalogPreset::name));
        return presets;
    }

    private static Map<UUID, CatalogActivity> byId(List<CatalogActivity> catalog) {
        Map<UUID, CatalogActivity> byId = new LinkedHashMap<>();
        catalog.forEach(a -> byId.put(a.id(), a));
        return byId;
    }

    private static List<List<String>> categorySets() {
        return List.of(List.of(),
                List.of("food-and-drink", "stag-hot-babies-and-pranks", "nightlife"),
                List.of("extreme", "guns-and-bullets"),
                List.of("wellness", "czech-beer", "food-and-drink"));
    }

    private static List<DayEdge> edges() {
        List<DayEdge> edges = new ArrayList<>(List.of(DayEdge.values()));
        edges.add(null);
        return edges;
    }
}
