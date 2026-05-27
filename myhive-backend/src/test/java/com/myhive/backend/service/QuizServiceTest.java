package com.myhive.backend.service;

import com.myhive.backend.dto.QuizAnswerDTO;
import com.myhive.backend.dto.QuizAnswerWeightDTO;
import com.myhive.backend.dto.QuizDTO;
import com.myhive.backend.dto.QuizQuestionDTO;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.QuizAnswer;
import com.myhive.backend.entity.QuizAnswerWeight;
import com.myhive.backend.entity.QuizQuestion;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.QuizQuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class QuizServiceTest {

    @Autowired
    private QuizQuestionRepository quizQuestionRepository;
    @Autowired
    private DestinationRepository destinationRepository;
    @Autowired
    private CategoryRepository categoryRepository;

    private QuizService quizService;

    @BeforeEach
    void setUp() {
        quizService = new QuizService(quizQuestionRepository, destinationRepository, categoryRepository);
    }

    @Test
    void getQuiz_unknownDestination_throwsNotFound() {
        assertThatThrownBy(() -> quizService.getQuiz(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getQuiz_returnsQuestionsOrderedWithAnswersAndWeights() {
        String expectedPrompt = "Daytime hero or 4am legend?";
        int expectedWeight = 2;

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
        answer.setLabel("4am legend");
        answer.setSortOrder(0);
        QuizAnswerWeight weight = new QuizAnswerWeight();
        weight.setAnswer(answer);
        weight.setCategory(category);
        weight.setWeight(expectedWeight);
        answer.getWeights().add(weight);
        question.getAnswers().add(answer);
        quizQuestionRepository.saveAndFlush(question);

        QuizDTO quiz = quizService.getQuiz(destination.getId());

        assertThat(quiz.getQuestions()).hasSize(1);
        assertThat(quiz.getQuestions().get(0).getPrompt()).isEqualTo(expectedPrompt);
        assertThat(quiz.getQuestions().get(0).getAnswers()).hasSize(1);
        assertThat(quiz.getQuestions().get(0).getAnswers().get(0).getWeights()).hasSize(1);
        assertThat(quiz.getQuestions().get(0).getAnswers().get(0).getWeights().get(0).getCategoryId())
                .isEqualTo(category.getId());
        assertThat(quiz.getQuestions().get(0).getAnswers().get(0).getWeights().get(0).getWeight())
                .isEqualTo(expectedWeight);
    }

    @Test
    void getQuiz_noQuiz_returnsEmptyQuestions() {
        Destination destination = new Destination();
        destination.setName("Berlin");
        destination = destinationRepository.save(destination);

        QuizDTO quiz = quizService.getQuiz(destination.getId());

        assertThat(quiz.getQuestions()).isEmpty();
    }

    @Test
    void replaceQuiz_unknownCategoryWeight_throwsBadRequest() {
        Destination destination = new Destination();
        destination.setName("Prague");
        destination = destinationRepository.save(destination);

        QuizQuestion old = new QuizQuestion();
        old.setDestination(destination);
        old.setPrompt("OLD QUESTION");
        old.setSortOrder(0);
        QuizAnswer oldAnswer = new QuizAnswer();
        oldAnswer.setQuestion(old);
        oldAnswer.setLabel("old");
        oldAnswer.setSortOrder(0);
        old.getAnswers().add(oldAnswer);
        quizQuestionRepository.saveAndFlush(old);

        QuizAnswerWeightDTO weight = new QuizAnswerWeightDTO(UUID.randomUUID(), 2);
        QuizAnswerDTO answer = new QuizAnswerDTO(null, "4am legend", 0, List.of(weight));
        QuizQuestionDTO question = new QuizQuestionDTO(null, "Daytime or 4am?", 0, List.of(answer));
        QuizDTO dto = new QuizDTO(List.of(question));

        UUID destinationId = destination.getId();
        assertThatThrownBy(() -> quizService.replaceQuiz(destinationId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Category not found");

        assertThat(quizQuestionRepository.findByDestinationIdOrderBySortOrder(destinationId))
                .extracting(QuizQuestion::getPrompt)
                .containsExactly("OLD QUESTION");
    }

    @Test
    void replaceQuiz_replacesExistingQuiz() {
        String expectedPrompt = "NEW QUESTION";

        Destination destination = new Destination();
        destination.setName("Prague");
        destination = destinationRepository.save(destination);

        Category category = new Category();
        category.setName("Nightlife");
        category.setSlug("nightlife");
        category = categoryRepository.save(category);

        QuizQuestion old = new QuizQuestion();
        old.setDestination(destination);
        old.setPrompt("OLD QUESTION");
        old.setSortOrder(0);
        QuizAnswer oldAnswer = new QuizAnswer();
        oldAnswer.setQuestion(old);
        oldAnswer.setLabel("old");
        oldAnswer.setSortOrder(0);
        old.getAnswers().add(oldAnswer);
        quizQuestionRepository.saveAndFlush(old);

        QuizAnswerWeightDTO weight = new QuizAnswerWeightDTO(category.getId(), 2);
        QuizAnswerDTO answer = new QuizAnswerDTO(null, "4am legend", 0, List.of(weight));
        QuizQuestionDTO question = new QuizQuestionDTO(null, expectedPrompt, 0, List.of(answer));
        QuizDTO dto = new QuizDTO(List.of(question));

        QuizDTO result = quizService.replaceQuiz(destination.getId(), dto);

        assertThat(result.getQuestions()).hasSize(1);
        assertThat(result.getQuestions().get(0).getPrompt()).isEqualTo(expectedPrompt);
        assertThat(quizQuestionRepository.findByDestinationIdOrderBySortOrder(destination.getId()))
                .extracting(QuizQuestion::getPrompt)
                .containsExactly(expectedPrompt);
    }

    @Test
    void replaceQuiz_emptyQuestions_clearsQuiz() {
        Destination destination = new Destination();
        destination.setName("Prague");
        destination = destinationRepository.save(destination);

        QuizQuestion old = new QuizQuestion();
        old.setDestination(destination);
        old.setPrompt("OLD");
        old.setSortOrder(0);
        QuizAnswer oldAnswer = new QuizAnswer();
        oldAnswer.setQuestion(old);
        oldAnswer.setLabel("old");
        oldAnswer.setSortOrder(0);
        old.getAnswers().add(oldAnswer);
        quizQuestionRepository.saveAndFlush(old);

        QuizDTO result = quizService.replaceQuiz(destination.getId(), new QuizDTO(List.of()));

        assertThat(result.getQuestions()).isEmpty();
    }
}
