package com.myhive.backend.service;

import com.myhive.backend.dto.TripExportRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmailServiceTest {

    private final EmailService emailService = new EmailService(null, null);

    @Test
    void buildDestinationViewsGroupsByPackageAndComputesDiscount() {
        UUID packageId = UUID.randomUUID();

        TripExportRequest.ActivityExport a1 = new TripExportRequest.ActivityExport();
        a1.setActivityName("A1");
        a1.setPrice(100.0);
        a1.setPackageId(packageId);
        a1.setPackageName("My Package");
        a1.setPackageDiscountPct(new BigDecimal("10.00"));

        TripExportRequest.ActivityExport a2 = new TripExportRequest.ActivityExport();
        a2.setActivityName("A2");
        a2.setPrice(200.0);
        a2.setPackageId(packageId);
        a2.setPackageName("My Package");
        a2.setPackageDiscountPct(new BigDecimal("10.00"));

        TripExportRequest.ActivityExport standalone = new TripExportRequest.ActivityExport();
        standalone.setActivityName("Standalone");
        standalone.setPrice(50.0);

        TripExportRequest.DestinationExport dest = new TripExportRequest.DestinationExport();
        dest.setDestinationName("Bali");
        dest.setActivities(List.of(a1, a2, standalone));

        TripExportRequest req = new TripExportRequest();
        req.setDestinations(List.of(dest));

        List<EmailService.DestinationView> views = emailService.buildDestinationViews(req);

        assertThat(views).hasSize(1);
        EmailService.DestinationView view = views.getFirst();
        assertThat(view.packageGroups).hasSize(1);
        EmailService.PackageGroup group = view.packageGroups.getFirst();
        assertThat(group.packageName).isEqualTo("My Package");
        assertThat(group.subtotal).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(group.discounted).isEqualByComparingTo(new BigDecimal("270.00"));
        assertThat(group.activities).hasSize(2);
        assertThat(view.standaloneActivities).hasSize(1);
        assertThat(view.standaloneActivities.getFirst().getActivityName()).isEqualTo("Standalone");
    }
}
