package com.myhive.backend.controller;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.myhive.backend.util.JwtTestHelper.adminJwt;
import static com.myhive.backend.util.JwtTestHelper.managerJwt;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestSecurityConfig.class)
class QuizAdminControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DestinationRepository destinationRepository;
    @Autowired
    private CategoryRepository categoryRepository;

    private UUID destinationId;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        Destination destination = new Destination();
        destination.setName("Prague");
        destination = destinationRepository.save(destination);
        destinationId = destination.getId();

        Category category = new Category();
        category.setName("Nightlife");
        category.setSlug("nightlife");
        category = categoryRepository.save(category);
        categoryId = category.getId();
    }

    @Test
    void putThenGetQuiz_withAdminAuth_roundTrips() throws Exception {
        String expectedPrompt = "Daytime hero or 4am legend?";
        String body = """
                {
                  "questions": [
                    {
                      "prompt": "%s",
                      "sortOrder": 0,
                      "answers": [
                        { "label": "4am legend", "sortOrder": 0,
                          "weights": [ { "categoryId": "%s", "weight": 2 } ] }
                      ]
                    }
                  ]
                }
                """.formatted(expectedPrompt, categoryId);

        mockMvc.perform(put("/admin/destinations/" + destinationId + "/quiz")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].prompt", is(expectedPrompt)));

        mockMvc.perform(get("/admin/destinations/" + destinationId + "/quiz")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].answers[0].weights[0].weight", is(2)));
    }

    @Test
    void getQuiz_withoutAuth_isUnauthorized() throws Exception {
        mockMvc.perform(get("/admin/destinations/" + destinationId + "/quiz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getQuiz_withManagerAuth_isForbidden() throws Exception {
        mockMvc.perform(get("/admin/destinations/" + destinationId + "/quiz")
                        .with(managerJwt()))
                .andExpect(status().isForbidden());
    }
}
