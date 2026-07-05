package com.myhive.backend;

import com.myhive.backend.dto.ActivityDTO;
import com.myhive.backend.dto.BlogPostDTO;
import com.myhive.backend.dto.CategoryDTO;
import com.myhive.backend.dto.CreateBookingRequest;
import com.myhive.backend.dto.DestinationDTO;
import com.myhive.backend.dto.TripExportRequest;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.BlogPost;
import com.myhive.backend.entity.Booking;
import com.myhive.backend.entity.BookingItem;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.Package;
import com.myhive.backend.model.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public final class TestDataFactory {

    private TestDataFactory() {
    }

    public static Category category() {
        return category("Adventure");
    }

    public static Category category(String name) {
        Category c = new Category();
        c.setId(UUID.randomUUID());
        c.setName(name);
        c.setSlug(name.toLowerCase().replace(' ', '-'));
        c.setCreatedAt(LocalDateTime.now());
        return c;
    }

    public static CategoryDTO categoryDTO() {
        CategoryDTO dto = new CategoryDTO();
        dto.setName("New Category");
        return dto;
    }

    public static Destination destination() {
        Destination d = new Destination();
        d.setId(UUID.randomUUID());
        d.setSlug("test-destination");
        d.setName("Test Destination");
        d.setDescription("A beautiful test destination");
        d.setCountry("Testland");
        d.setCity("Testville");
        d.setImageUrl("https://example.com/image.jpg");
        d.setRating(new BigDecimal("4.50"));
        d.setCreatedAt(LocalDateTime.now());
        return d;
    }

    /**
     * Unsaved destination with only {@code name} set — for vote-flow tests that persist it
     * themselves via {@code destinationRepository.save(...)} and let JPA assign the id.
     */
    public static Destination destination(String name) {
        Destination d = new Destination();
        d.setName(name);
        return d;
    }

    public static Activity activity(Destination destination) {
        Activity a = new Activity();
        a.setId(UUID.randomUUID());
        a.setSlug("test-activity");
        a.setDestination(destination);
        a.setName("Test Activity");
        a.setDescription("A fun test activity");
        a.setPrice(new BigDecimal("99.99"));
        a.setDuration(120);
        a.setImageUrl("https://example.com/activity.jpg");
        a.setIncludes("Guide, transport, lunch");
        a.setCreatedAt(LocalDateTime.now());
        return a;
    }

    public static Activity activity(Destination destination, Category... categories) {
        Activity a = activity(destination);
        a.setCategories(new java.util.HashSet<>(java.util.Arrays.asList(categories)));
        return a;
    }

    /**
     * Unsaved activity with only {@code destination}/{@code name}/{@code price} set (empty
     * category set) — for vote-flow tests that persist it themselves via
     * {@code activityRepository.saveAndFlush(...)} and let JPA assign the id.
     */
    public static Activity activity(Destination destination, String name, BigDecimal price) {
        Activity a = new Activity();
        a.setDestination(destination);
        a.setName(name);
        a.setPrice(price);
        a.setCategories(new HashSet<>());
        return a;
    }

    public static Package pkg(Destination destination) {
        Package p = new Package();
        p.setId(UUID.randomUUID());
        p.setSlug("test-package");
        p.setDestination(destination);
        p.setName("Test Package");
        p.setDescription("Test package description");
        p.setImageUrl("https://example.com/pkg.jpg");
        p.setIncludes("Hotel, transfers");
        p.setDuration(72);
        p.setDiscountPct(new BigDecimal("15.00"));
        p.setCreatedAt(LocalDateTime.now());
        return p;
    }

    public static Booking booking(BookingStatus status) {
        Booking b = new Booking();
        b.setId(UUID.randomUUID());
        b.setUserEmail("user@test.com");
        b.setStatus(status);
        b.setTotalAmount(new BigDecimal("199.98"));
        b.setCreatedAt(LocalDateTime.now());
        b.setCustomerName("Test User");
        b.setPhone("+1234567890");
        b.setNumberOfTravelers(2);
        b.setStartDate(LocalDate.now().plusDays(7));
        b.setEndDate(LocalDate.now().plusDays(14));
        return b;
    }

    public static BookingItem bookingItem(Booking booking, Activity activity) {
        BookingItem item = new BookingItem();
        item.setId(UUID.randomUUID());
        item.setBooking(booking);
        item.setActivity(activity);
        item.setActivityName(activity.getName());
        item.setDestinationName(activity.getDestination().getName());
        item.setPrice(activity.getPrice());
        item.setQuantity(2);
        return item;
    }

    public static BlogPost blogPost() {
        BlogPost bp = new BlogPost();
        bp.setId(UUID.randomUUID());
        bp.setSlug("test-blog-post");
        bp.setTitle("Test Blog Post");
        bp.setExcerpt("Test excerpt");
        bp.setContent("Full test content here");
        bp.setCategory("Travel");
        bp.setImageUrl("https://example.com/blog.jpg");
        bp.setDate(LocalDate.now());
        bp.setCreatedAt(LocalDateTime.now());
        return bp;
    }

    public static CreateBookingRequest createBookingRequest(UUID activityId) {
        CreateBookingRequest.BookingActivityItem item = new CreateBookingRequest.BookingActivityItem();
        item.setActivityId(activityId);
        item.setQuantity(2);

        CreateBookingRequest request = new CreateBookingRequest();
        request.setUserEmail("user@test.com");
        request.setActivities(List.of(item));
        return request;
    }

    public static TripExportRequest tripExportRequest() {
        TripExportRequest.ActivityExport actExport = new TripExportRequest.ActivityExport();
        actExport.setActivityId(UUID.randomUUID());
        actExport.setActivityName("Snorkeling");
        actExport.setCategory("Water Sports");
        actExport.setPrice(75.0);
        actExport.setDuration(90);

        TripExportRequest.DestinationExport destExport = new TripExportRequest.DestinationExport();
        destExport.setDestinationName("Maldives");
        destExport.setCountry("Maldives");
        destExport.setActivities(List.of(actExport));
        destExport.setStartDate("2026-05-01");
        destExport.setEndDate("2026-05-10");

        TripExportRequest request = new TripExportRequest();
        request.setTripName("Summer Trip");
        request.setUserEmail("user@test.com");
        request.setCustomerName("Test User");
        request.setPhone("+1234567890");
        request.setNumberOfTravelers(2);
        request.setDestinations(List.of(destExport));
        request.setNotes("Special requests");
        return request;
    }

    public static ActivityDTO activityDTO(UUID destinationId) {
        ActivityDTO dto = new ActivityDTO();
        dto.setDestinationId(destinationId);
        dto.setName("New Activity");
        dto.setDescription("Activity description");
        dto.setPrice(new BigDecimal("49.99"));
        dto.setDuration(60);
        dto.setImageUrl("https://example.com/new.jpg");
        dto.setIncludes("Tickets, guide");
        return dto;
    }

    public static DestinationDTO destinationDTO() {
        DestinationDTO dto = new DestinationDTO();
        dto.setName("New Destination");
        dto.setDescription("New destination description");
        dto.setCountry("Testland");
        dto.setCity("Testville");
        dto.setImageUrl("https://example.com/new-dest.jpg");
        dto.setRating(new BigDecimal("4.75"));
        return dto;
    }

    public static BlogPostDTO blogPostDTO() {
        BlogPostDTO dto = new BlogPostDTO();
        dto.setTitle("New Blog Post");
        dto.setExcerpt("New excerpt");
        dto.setContent("New content");
        dto.setCategory("Adventure");
        dto.setImageUrl("https://example.com/new-blog.jpg");
        dto.setDate(LocalDate.now());
        return dto;
    }
}
