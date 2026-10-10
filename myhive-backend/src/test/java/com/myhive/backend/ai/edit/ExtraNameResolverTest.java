package com.myhive.backend.ai.edit;

import com.myhive.backend.ai.catalog.CatalogExtra;
import com.myhive.backend.entity.ExtraPricing;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExtraNameResolverTest {

    private static final CatalogExtra PRIVATE_SHOW =
            new CatalogExtra("private-show", "Private show", new BigDecimal("250.00"), ExtraPricing.PER_GROUP);
    private static final CatalogExtra OPEN_BAR =
            new CatalogExtra("open-bar", "Open bar", new BigDecimal("25.00"), ExtraPricing.PER_PERSON);
    private static final CatalogExtra PREMIUM_OPEN_BAR =
            new CatalogExtra("premium-open-bar", "Premium open bar", new BigDecimal("40.00"), ExtraPricing.PER_PERSON);
    private static final List<CatalogExtra> OFFERED = List.of(PRIVATE_SHOW, OPEN_BAR);

    @Test
    void resolve_anExactName_ignoringCaseAndSpacing() {
        assertThat(ExtraNameResolver.resolve("  private   SHOW ", OFFERED)).contains(PRIVATE_SHOW);
    }

    @Test
    void resolve_aNameThatContainsOrIsContainedInOneExtra() {
        assertThat(ExtraNameResolver.resolve("the private show please", OFFERED)).contains(PRIVATE_SHOW);
        assertThat(ExtraNameResolver.resolve("bar", OFFERED)).contains(OPEN_BAR);
    }

    @Test
    void resolve_prefersTheExactName_overAnotherThatContainsIt() {
        assertThat(ExtraNameResolver.resolve("Open bar", List.of(PREMIUM_OPEN_BAR, OPEN_BAR))).contains(OPEN_BAR);
    }

    @Test
    void resolve_isEmpty_whenSeveralExtrasMatchOrNoneDoes() {
        assertThat(ExtraNameResolver.resolve("bar", List.of(OPEN_BAR, PREMIUM_OPEN_BAR))).isEmpty();
        assertThat(ExtraNameResolver.resolve("stripper", OFFERED)).isEmpty();
        assertThat(ExtraNameResolver.resolve(null, OFFERED)).isEmpty();
        assertThat(ExtraNameResolver.resolve("Private show", List.of())).isEmpty();
    }
}
