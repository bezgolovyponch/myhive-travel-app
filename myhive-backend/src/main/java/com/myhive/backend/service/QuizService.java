package com.myhive.backend.service;

import com.myhive.backend.dto.PublicQuizAnswerDTO;
import com.myhive.backend.dto.PublicQuizDTO;
import com.myhive.backend.dto.PublicQuizQuestionDTO;
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
import com.myhive.backend.repository.QuizAnswerWeightRepository;
import com.myhive.backend.repository.QuizQuestionRepository;
import com.myhive.backend.repository.VoteSessionQuizResponseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizService {

    private static final int TOP_K = 3;

    private final QuizQuestionRepository quizQuestionRepository;
    private final DestinationRepository destinationRepository;
    private final CategoryRepository categoryRepository;
    private final QuizAnswerWeightRepository quizAnswerWeightRepository;
    private final VoteSessionQuizResponseRepository voteSessionQuizResponseRepository;

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

    public PublicQuizDTO getPublicQuiz(UUID destinationId) {
        if (!destinationRepository.existsById(destinationId)) {
            throw new ResourceNotFoundException("Destination", destinationId);
        }
        List<PublicQuizQuestionDTO> questions = quizQuestionRepository
                .findByDestinationIdOrderBySortOrder(destinationId)
                .stream()
                .map(this::toPublicQuestion)
                .toList();
        return new PublicQuizDTO(questions);
    }

    private PublicQuizQuestionDTO toPublicQuestion(QuizQuestion question) {
        List<PublicQuizAnswerDTO> answers = question.getAnswers().stream()
                .sorted(Comparator.comparingInt(QuizAnswer::getSortOrder))
                .map(a -> new PublicQuizAnswerDTO(a.getId(), a.getLabel()))
                .toList();
        return new PublicQuizQuestionDTO(question.getId(), question.getPrompt(), answers);
    }

    public List<UUID> snapshot(Collection<UUID> answerIds) {
        if (answerIds == null || answerIds.isEmpty()) {
            return List.of();
        }
        List<QuizAnswerWeight> weights = quizAnswerWeightRepository.findAllByAnswerIdIn(answerIds);

        Map<UUID, Integer> scores = new HashMap<>();
        Map<UUID, Boolean> votableByCategory = new HashMap<>();
        for (QuizAnswerWeight weight : weights) {
            UUID categoryId = weight.getCategory().getId();
            scores.merge(categoryId, weight.getWeight(), Integer::sum);
            votableByCategory.putIfAbsent(categoryId, weight.getCategory().isVotable());
        }

        return scores.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .filter(e -> Boolean.TRUE.equals(votableByCategory.get(e.getKey())))
                .sorted(Map.Entry.<UUID, Integer>comparingByValue()
                        .reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_K)
                .map(Map.Entry::getKey)
                .toList();
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

        // Participants' quiz responses reference the old answers via a non-cascading FK. Wipe them first
        // so the old questions/answers can be deleted. Editing a quiz discards existing responses by design.
        int discardedResponses = voteSessionQuizResponseRepository.deleteByDestinationId(destinationId);
        voteSessionQuizResponseRepository.flush();
        if (discardedResponses > 0) {
            log.warn("Replacing quiz for destination {} discarded {} existing quiz response(s)",
                    destinationId, discardedResponses);
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
