package com.myhive.backend.controller;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.BlogPost;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.Package;
import com.myhive.backend.entity.PackageActivity;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.BlogPostRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.PackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestSecurityConfig.class)
class PublicControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DestinationRepository destinationRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private BlogPostRepository blogPostRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PackageRepository packageRepository;

    private UUID destinationId;
    private UUID activityId;
    private UUID blogPostId;

    @BeforeEach
    void setUp() {
        Destination dest = new Destination();
        dest.setSlug("tokyo");
        dest.setName("Tokyo");
        dest.setCountry("Japan");
        dest = destinationRepository.save(dest);
        destinationId = dest.getId();

        Category culture = new Category();
        culture.setName("Culture");
        culture.setSlug("culture");
        culture = categoryRepository.save(culture);

        Activity activity = new Activity();
        activity.setSlug("temple-visit");
        activity.setDestination(dest);
        activity.setName("Temple Visit");
        activity.setPrice(new BigDecimal("30.00"));
        Set<Category> activityCategories = new HashSet<>();
        activityCategories.add(culture);
        activity.setCategories(activityCategories);
        activity = activityRepository.save(activity);
        activityId = activity.getId();

        BlogPost bp = new BlogPost();
        bp.setSlug("tokyo-guide");
        bp.setTitle("Tokyo Guide");
        bp.setContent("Full guide");
        bp.setCategory("Travel");
        bp.setDate(LocalDate.now());
        bp = blogPostRepository.save(bp);
        blogPostId = bp.getId();
    }

    // --- Destinations ---

    @Test
    void getAllDestinations_returns200() throws Exception {
        mockMvc.perform(get("/destinations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Tokyo")));
    }

    @Test
    void getDestinationById_existing_returns200() throws Exception {
        mockMvc.perform(get("/destinations/" + destinationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Tokyo")));
    }

    @Test
    void getDestinationById_nonexistent_returns404() throws Exception {
        mockMvc.perform(get("/destinations/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // --- Activities ---

    @Test
    void getAllActivities_returns200() throws Exception {
        mockMvc.perform(get("/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Temple Visit")));
    }

    @Test
    void getActivities_byDestination_filtersCorrectly() throws Exception {
        mockMvc.perform(get("/activities")
                        .param("destinationId", destinationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void getActivities_byCategorySlug_filtersCorrectly() throws Exception {
        mockMvc.perform(get("/activities")
                        .param("categorySlug", "culture"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void getActivities_byDestinationAndCategorySlug_filtersCorrectly() throws Exception {
        mockMvc.perform(get("/activities")
                        .param("destinationId", destinationId.toString())
                        .param("categorySlug", "culture"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void getActivityById_existing_returns200() throws Exception {
        mockMvc.perform(get("/activities/" + activityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Temple Visit")));
    }

    @Test
    void getActivityById_nonexistent_returns404() throws Exception {
        mockMvc.perform(get("/activities/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // --- Blog ---

    @Test
    void getAllBlogPosts_returns200() throws Exception {
        mockMvc.perform(get("/blog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("Tokyo Guide")));
    }

    @Test
    void getBlogPostById_existing_returns200() throws Exception {
        mockMvc.perform(get("/blog/" + blogPostId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Tokyo Guide")));
    }

    @Test
    void getBlogPostById_nonexistent_returns404() throws Exception {
        mockMvc.perform(get("/blog/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // --- Slug Endpoints ---

    @Test
    void getDestinationBySlug_existing_returns200() throws Exception {
        mockMvc.perform(get("/destinations/slug/tokyo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Tokyo")))
                .andExpect(jsonPath("$.slug", is("tokyo")));
    }

    @Test
    void getDestinationBySlug_nonexistent_returns404() throws Exception {
        mockMvc.perform(get("/destinations/slug/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getActivityBySlug_existing_returns200() throws Exception {
        mockMvc.perform(get("/activities/slug/temple-visit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Temple Visit")))
                .andExpect(jsonPath("$.slug", is("temple-visit")))
                .andExpect(jsonPath("$.destinationSlug", is("tokyo")));
    }

    @Test
    void getActivityBySlug_nonexistent_returns404() throws Exception {
        mockMvc.perform(get("/activities/slug/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBlogPostBySlug_existing_returns200() throws Exception {
        mockMvc.perform(get("/blog/slug/tokyo-guide"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Tokyo Guide")))
                .andExpect(jsonPath("$.slug", is("tokyo-guide")));
    }

    @Test
    void getBlogPostBySlug_nonexistent_returns404() throws Exception {
        mockMvc.perform(get("/blog/slug/nonexistent"))
                .andExpect(status().isNotFound());
    }

    // --- Sitemap ---

    @Test
    void sitemap_returnsValidXml() throws Exception {
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andExpect(content().string(containsString("<urlset")))
                .andExpect(content().string(containsString("/destination/tokyo")))
                .andExpect(content().string(containsString("/destination/tokyo/activity/temple-visit")))
                .andExpect(content().string(containsString("/blog/tokyo-guide")));
    }

    @Test
    void sitemapIncludesPackageUrls() throws Exception {
        Destination dest = destinationRepository.findById(destinationId).orElseThrow();
        Activity act = activityRepository.findById(activityId).orElseThrow();
        Package pkg = new Package();
        pkg.setSlug("sitemap-pkg");
        pkg.setName("Sitemap Package");
        pkg.setDestination(dest);
        pkg.setDiscountPct(new BigDecimal("5.00"));
        pkg.getPackageActivities().add(new PackageActivity(pkg, act, 0));
        packageRepository.save(pkg);

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/destination/" + dest.getSlug() + "/package/sitemap-pkg")));
    }

    @Test
    void getActivities_featuredTrue_returnsOnlyFeatured() throws Exception {
        String expectedSlug = "featured-act";
        Destination dest = destinationRepository.findById(destinationId).orElseThrow();
        Activity featuredActivity = new Activity();
        featuredActivity.setSlug(expectedSlug);
        featuredActivity.setDestination(dest);
        featuredActivity.setName("Featured Act");
        featuredActivity.setPrice(new BigDecimal("99.00"));
        featuredActivity.setFeatured(true);
        activityRepository.save(featuredActivity);

        mockMvc.perform(get("/activities").param("featured", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].slug", is(expectedSlug)))
                .andExpect(jsonPath("$[0].featured", is(true)));
    }

    @Test
    void getActivities_withoutFeaturedParam_returnsAllIncludingNonFeatured() throws Exception {
        mockMvc.perform(get("/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].featured", is(false)));
    }

    // --- Packages ---

    @Test
    void getPackageBySlugReturnsPackage() throws Exception {
        Destination dest = destinationRepository.findById(destinationId).orElseThrow();
        Activity act = activityRepository.findById(activityId).orElseThrow();

        Package pkg = new Package();
        pkg.setSlug("public-pkg");
        pkg.setName("Public Package");
        pkg.setDestination(dest);
        pkg.setDiscountPct(new BigDecimal("10.00"));
        pkg.getPackageActivities().add(new PackageActivity(pkg, act, 0));
        packageRepository.save(pkg);

        mockMvc.perform(get("/packages/slug/public-pkg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("public-pkg"))
                .andExpect(jsonPath("$.activities", hasSize(1)));
    }
}
