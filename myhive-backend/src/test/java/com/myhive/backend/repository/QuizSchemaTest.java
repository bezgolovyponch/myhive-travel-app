package com.myhive.backend.repository;

import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.QuizAnswer;
import com.myhive.backend.entity.QuizAnswerWeight;
import com.myhive.backend.entity.QuizQuestion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class QuizSchemaTest {

    @Autowired
    private QuizQuestionRepository quizQuestionRepository;
    @Autowired
    private DestinationRepository destinationRepository;
    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void quizTree_persistsAndCascades() {
        Destination destination = new Destination();
        destination.setName("Prague");
        destination = destinationRepository.save(destination);

        Category category = new Category();
        category.setName("Nightlife");
        category.setSlug("nightlife");
        category = categoryRepository.save(category);

        QuizQuestion question = new QuizQuestion();
        question.setDestination(destination);
        question.setPrompt("Daytime hero or 4am legend?");
        question.setSortOrder(0);

        QuizAnswer answer = new QuizAnswer();
        answer.setQuestion(question);
        answer.setLabel("4am legend");
        answer.setSortOrder(0);

        QuizAnswerWeight weight = new QuizAnswerWeight();
        weight.setAnswer(answer);
        weight.setCategory(category);
        weight.setWeight(2);

        answer.getWeights().add(weight);
        question.getAnswers().add(answer);
        QuizQuestion saved = quizQuestionRepository.saveAndFlush(question);

        QuizQuestion reloaded = quizQuestionRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getAnswers()).hasSize(1);
        assertThat(reloaded.getAnswers().get(0).getWeights()).hasSize(1);
        assertThat(reloaded.getAnswers().get(0).getWeights().get(0).getWeight()).isEqualTo(2);

        quizQuestionRepository.delete(reloaded);
        quizQuestionRepository.flush();
        List<QuizQuestion> remaining = quizQuestionRepository.findByDestinationIdOrderBySortOrder(destination.getId());
        assertThat(remaining).isEmpty();
    }
}
