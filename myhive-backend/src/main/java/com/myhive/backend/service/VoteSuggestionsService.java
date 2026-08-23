package com.myhive.backend.service;

import com.myhive.backend.dto.SuggestionDTO;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.VoteSessionActivityRepository;
import com.myhive.backend.repository.VoteSessionQuizResponseRepository;
import com.myhive.backend.util.Translations;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoteSuggestionsService {

    static final int SUGGESTION_CAP = 10;

    private final QuizService quizService;
    private final ActivityRepository activityRepository;
    private final VoteSessionActivityRepository voteSessionActivityRepository;
    private final VoteSessionQuizResponseRepository voteSessionQuizResponseRepository;

    public List<SuggestionDTO> buildSuggestions(VoteSession session) {
        return buildSuggestions(session, null);
    }

    /** @param locale request locale (en/de/…); names, descriptions, includes and category names come back resolved for it. */
    public List<SuggestionDTO> buildSuggestions(VoteSession session, String locale) {
        String lc = Translations.normalize(locale);
        List<UUID> curatedActivityIds = voteSessionActivityRepository
                .findBySessionIdOrderBySortOrder(session.getId()).stream()
                .map(row -> row.getActivity().getId())
                .toList();
        Collection<UUID> exclusion = curatedActivityIds.isEmpty()
                ? List.of(UUID.randomUUID())   // defensive: never NOT IN ()
                : curatedActivityIds;

        List<UUID> answerIds = voteSessionQuizResponseRepository.findBySessionId(session.getId()).stream()
                .map(r -> r.getAnswer().getId())
                .toList();
        List<UUID> groupCats = quizService.snapshot(answerIds);

        List<Activity> quizSuggestions = groupCats.isEmpty()
                ? List.of()
                : activityRepository.findSuggestionCandidates(
                        session.getDestination().getId(), groupCats, exclusion,
                        PageRequest.of(0, SUGGESTION_CAP));

        List<Activity> chosen = quizSuggestions;
        if (chosen.isEmpty()) {
            chosen = activityRepository.findSuggestionCandidatesAnyCategory(
                    session.getDestination().getId(), exclusion,
                    PageRequest.of(0, SUGGESTION_CAP));
        }

        return chosen.stream().map(a -> toDTO(a, lc)).toList();
    }

    private SuggestionDTO toDTO(Activity activity, String lc) {
        List<String> categories = activity.getCategories().stream()
                .map(c -> Translations.pick(c.getTranslations(), lc, "name", c.getName()))
                .sorted()
                .toList();
        String destinationSlug = activity.getDestination() == null
                ? null : activity.getDestination().getSlug();
        Map<String, Map<String, String>> tr = activity.getTranslations();
        return new SuggestionDTO(activity.getId(),
                Translations.pick(tr, lc, "name", activity.getName()),
                activity.getPrice(), activity.getMinPrice(), activity.getImageUrl(),
                activity.getSlug(), destinationSlug,
                Translations.pick(tr, lc, "description", activity.getDescription()),
                Translations.pick(tr, lc, "includes", activity.getIncludes()),
                categories);
    }
}
