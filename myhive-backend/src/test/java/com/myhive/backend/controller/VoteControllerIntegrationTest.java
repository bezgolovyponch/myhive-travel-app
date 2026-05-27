package com.myhive.backend.controller;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.QuizAnswer;
import com.myhive.backend.entity.QuizAnswerWeight;
import com.myhive.backend.entity.QuizQuestion;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.QuizQuestionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestSecurityConfig.class)
class VoteControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DestinationRepository destinationRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private QuizQuestionRepository quizQuestionRepository;
    @Autowired
    private ActivityRepository activityRepository;

    @Test
    void getPublicQuiz_returnsQuestionsAndAnswers_withNoWeights() throws Exception {
        String expectedPrompt = "Daytime or 4am?";
        String expectedLabel = "4am";

        Destination destination = new Destination();
        destination.setName("Prague");
        destination = destinationRepository.save(destination);

        Category category = new Category();
        category.setName("Nightlife");
        category.setSlug("nightlife");
        category = categoryRepository.save(category);

        QuizQuestion question = new QuizQuestion();
        question.setDestination(destination);
        question.setPrompt(expectedPrompt);
        question.setSortOrder(0);
        QuizAnswer answer = new QuizAnswer();
        answer.setQuestion(question);
        answer.setLabel(expectedLabel);
        answer.setSortOrder(0);
        QuizAnswerWeight weight = new QuizAnswerWeight();
        weight.setAnswer(answer);
        weight.setCategory(category);
        weight.setWeight(2);
        answer.getWeights().add(weight);
        question.getAnswers().add(answer);
        quizQuestionRepository.saveAndFlush(question);

        mockMvc.perform(get("/vote/destinations/" + destination.getId() + "/quiz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions", hasSize(1)))
                .andExpect(jsonPath("$.questions[0].prompt", is(expectedPrompt)))
                .andExpect(jsonPath("$.questions[0].answers[0].label", is(expectedLabel)))
                // Critical: the weights field must not leak into the public response.
                .andExpect(jsonPath("$.questions[0].answers[0].weights").doesNotExist());
    }

    @Test
    void getPublicQuiz_unknownDestination_returns404() throws Exception {
        mockMvc.perform(get("/vote/destinations/" + UUID.randomUUID() + "/quiz"))
                .andExpect(status().isNotFound());
    }

    @Test
    void postPool_returnsFilteredActivitiesForDestination() throws Exception {
        String expectedName = "Club Crawl";

        Destination destination = new Destination();
        destination.setName("Prague");
        destination = destinationRepository.save(destination);
        UUID destinationId = destination.getId();

        Category nightlife = new Category();
        nightlife.setName("Nightlife");
        nightlife.setSlug("nightlife");
        nightlife = categoryRepository.save(nightlife);

        destination.setCategories(new java.util.HashSet<>(java.util.List.of(nightlife)));
        destinationRepository.saveAndFlush(destination);

        com.myhive.backend.entity.Activity activity = new com.myhive.backend.entity.Activity();
        activity.setDestination(destination);
        activity.setName(expectedName);
        activity.setPrice(new java.math.BigDecimal("100"));
        activity.setFeaturedWeight(5);
        activity.setCategories(new java.util.HashSet<>(java.util.List.of(nightlife)));
        activityRepository.saveAndFlush(activity);

        String body = """
                { "destinationId": "%s", "responses": [] }
                """.formatted(destinationId);

        mockMvc.perform(post("/vote/pool")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pool", hasSize(1)))
                .andExpect(jsonPath("$.pool[0].name", is(expectedName)));
    }

    @Test
    void postPool_unknownDestination_returns404() throws Exception {
        String body = """
                { "destinationId": "%s", "responses": [] }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/vote/pool")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }
}
