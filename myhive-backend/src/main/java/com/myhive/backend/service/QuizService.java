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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizService {

    private final QuizQuestionRepository quizQuestionRepository;
    private final DestinationRepository destinationRepository;
    private final CategoryRepository categoryRepository;

    public QuizDTO getQuiz(UUID destinationId) {
        if (!destinationRepository.existsById(destinationId)) {
            throw new ResourceNotFoundException("Destination", destinationId);
        }
        List<QuizQuestionDTO> questions = quizQuestionRepository
                .findByDestinationIdOrderBySortOrder(destinationId)
                .stream()
                .map(this::convertToDTO)
                .toList();
        return new QuizDTO(questions);
    }

    private QuizQuestionDTO convertToDTO(QuizQuestion question) {
        List<QuizAnswerDTO> answers = question.getAnswers().stream()
                .sorted(Comparator.comparingInt(QuizAnswer::getSortOrder))
                .map(answer -> {
                    List<QuizAnswerWeightDTO> weights = answer.getWeights().stream()
                            .map(w -> new QuizAnswerWeightDTO(w.getCategory().getId(), w.getWeight()))
                            .toList();
                    return new QuizAnswerDTO(answer.getId(), answer.getLabel(),
                            answer.getSortOrder(), weights);
                })
                .toList();
        return new QuizQuestionDTO(question.getId(), question.getPrompt(),
                question.getSortOrder(), answers);
    }

    @Transactional
    public QuizDTO replaceQuiz(UUID destinationId, QuizDTO dto) {
        Destination destination = destinationRepository.findById(destinationId)
                .orElseThrow(() -> new ResourceNotFoundException("Destination", destinationId));

        for (QuizQuestionDTO questionDto : dto.getQuestions()) {
            for (QuizAnswerDTO answerDto : questionDto.getAnswers()) {
                if (answerDto.getWeights() == null) {
                    continue;
                }
                for (QuizAnswerWeightDTO weightDto : answerDto.getWeights()) {
                    if (!categoryRepository.existsById(weightDto.getCategoryId())) {
                        throw new BadRequestException(
                                "Category not found: " + weightDto.getCategoryId());
                    }
                }
            }
        }

        quizQuestionRepository.deleteAll(
                quizQuestionRepository.findByDestinationIdOrderBySortOrder(destinationId));
        quizQuestionRepository.flush();

        for (QuizQuestionDTO questionDto : dto.getQuestions()) {
            QuizQuestion question = new QuizQuestion();
            question.setDestination(destination);
            question.setPrompt(questionDto.getPrompt());
            question.setSortOrder(questionDto.getSortOrder());
            for (QuizAnswerDTO answerDto : questionDto.getAnswers()) {
                QuizAnswer answer = new QuizAnswer();
                answer.setQuestion(question);
                answer.setLabel(answerDto.getLabel());
                answer.setSortOrder(answerDto.getSortOrder());
                if (answerDto.getWeights() != null) {
                    for (QuizAnswerWeightDTO weightDto : answerDto.getWeights()) {
                        Category category = categoryRepository.findById(weightDto.getCategoryId())
                                .orElseThrow();
                        QuizAnswerWeight weight = new QuizAnswerWeight();
                        weight.setAnswer(answer);
                        weight.setCategory(category);
                        weight.setWeight(weightDto.getWeight());
                        answer.getWeights().add(weight);
                    }
                }
                question.getAnswers().add(answer);
            }
            quizQuestionRepository.save(question);
        }
        return getQuiz(destinationId);
    }
}
