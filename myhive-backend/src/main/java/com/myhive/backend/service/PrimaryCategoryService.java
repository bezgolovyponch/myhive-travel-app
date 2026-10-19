package com.myhive.backend.service;

import com.myhive.backend.dto.PrimaryCategoryDTO;
import com.myhive.backend.entity.PrimaryCategory;
import com.myhive.backend.repository.PrimaryCategoryRepository;
import com.myhive.backend.util.Translations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrimaryCategoryService {

    private final PrimaryCategoryRepository primaryCategoryRepository;

    public List<PrimaryCategoryDTO> getAll(String locale) {
        String lc = Translations.normalize(locale);
        return primaryCategoryRepository.findAllByOrderBySortOrderAsc().stream()
                .map(pc -> toDTO(pc, lc))
                .toList();
    }

    /** {@code lc} is an already normalized locale (see {@link Translations#normalize}); null = English. */
    public static PrimaryCategoryDTO toDTO(PrimaryCategory pc, String lc) {
        return new PrimaryCategoryDTO(
                pc.getId(),
                pc.getSlug(),
                pc.getSlugDe(),
                Translations.pick(pc.getTranslations(), lc, "name", pc.getName()));
    }
}
