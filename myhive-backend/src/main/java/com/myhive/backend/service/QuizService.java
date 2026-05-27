package com.myhive.backend.service;

import com.myhive.backend.dto.QuizAnswerDTO;
import com.myhive.backend.dto.QuizAnswerWeightDTO;
import com.myhive.backend.dto.QuizDTO;
import com.myhive.backend.dto.QuizQuestionDTO;
import com.myhive.backend.entity.QuizAnswer;
import com.myhive.backend.entity.QuizQuestion;
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
}
