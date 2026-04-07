package com.myhive.backend.controller;

import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.BlogPost;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.BlogPostRepository;
import com.myhive.backend.repository.DestinationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PublicControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DestinationRepository destinationRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private BlogPostRepository blogPostRepository;

    private UUID destinationId;
    private UUID activityId;
    private UUID blogPostId;

    @BeforeEach
    void setUp() {
        Destination dest = new Destination();
        dest.setName("Tokyo");
        dest.setCountry("Japan");
        dest = destinationRepository.save(dest);
        destinationId = dest.getId();

        Activity activity = new Activity();
        activity.setDestination(dest);
        activity.setName("Temple Visit");
        activity.setPrice(new BigDecimal("30.00"));
        activity.setCategory("Culture");
        activity = activityRepository.save(activity);
        activityId = activity.getId();

        BlogPost bp = new BlogPost();
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
    void getActivities_byCategory_filtersCorrectly() throws Exception {
        mockMvc.perform(get("/activities")
                        .param("category", "Culture"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void getActivities_byDestinationAndCategory_filtersCorrectly() throws Exception {
        mockMvc.perform(get("/activities")
                        .param("destinationId", destinationId.toString())
                        .param("category", "Culture"))
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
}
