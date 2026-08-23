package com.myhive.backend.service;

import com.myhive.backend.dto.QuizResponseDTO;
import com.myhive.backend.dto.VotePoolActivityDTO;
import com.myhive.backend.dto.VotePoolRequest;
import com.myhive.backend.dto.VotePoolResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.util.Translations;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VotePoolService {

    static final int POOL_CAP = 20;

    private final QuizService quizService;
    private final DestinationRepository destinationRepository;
    private final ActivityRepository activityRepository;

    public VotePoolResponse buildPool(VotePoolRequest request) {
        return buildPool(request, null);
    }

    /** @param locale request locale (en/de/…); names, descriptions and category names come back resolved for it. */
    public VotePoolResponse buildPool(VotePoolRequest request, String locale) {
        String lc = Translations.normalize(locale);
        Destination destination = destinationRepository.findById(request.getDestinationId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination", request.getDestinationId()));

        List<UUID> answerIds = request.getResponses().stream()
                .map(QuizResponseDTO::getAnswerId)
                .toList();
        List<UUID> organizerCats = quizService.snapshot(answerIds);

        if (organizerCats.isEmpty()) {
            organizerCats = destination.getCategories().stream()
                    .filter(Category::isVotable)
                    .map(Category::getId)
                    .toList();
        }

        if (organizerCats.isEmpty()) {
            return new VotePoolResponse(List.of());
        }

        List<Activity> activities = activityRepository.findPoolCandidates(
                destination.getId(), organizerCats, PageRequest.of(0, POOL_CAP));

        List<VotePoolActivityDTO> pool = activities.stream()
                .map(a -> toDTO(a, lc))
                .toList();
        return new VotePoolResponse(pool);
    }

    private VotePoolActivityDTO toDTO(Activity activity, String lc) {
        List<String> categories = activity.getCategories().stream()
                .map(c -> Translations.pick(c.getTranslations(), lc, "name", c.getName()))
                .sorted()
                .toList();
        String destinationSlug = activity.getDestination() == null
                ? null : activity.getDestination().getSlug();
        Map<String, Map<String, String>> tr = activity.getTranslations();
        return new VotePoolActivityDTO(activity.getId(),
                Translations.pick(tr, lc, "name", activity.getName()),
                activity.getPrice(), activity.getMinPrice(), activity.getImageUrl(),
                activity.getSlug(), destinationSlug, categories,
                Translations.pick(tr, lc, "description", activity.getDescription()), activity.getDuration());
    }
}
