package com.myhive.backend.service;

import com.myhive.backend.dto.CategoryDTO;
import com.myhive.backend.dto.PackageActivityRefDTO;
import com.myhive.backend.dto.PackageDTO;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.Package;
import com.myhive.backend.entity.PackageActivity;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.PackageRepository;
import com.myhive.backend.util.MoneyMath;
import com.myhive.backend.util.Translations;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PackageService {

    private final PackageRepository packageRepository;
    private final DestinationRepository destinationRepository;
    private final ActivityRepository activityRepository;
    private final CategoryRepository categoryRepository;

    // Without a locale: admin/raw view (base fields + translations map). With
    // one: public view, fields resolved for that locale. See ActivityService.

    public List<PackageDTO> getAllPackages() {
        return getAllPackages(null);
    }

    public List<PackageDTO> getAllPackages(String locale) {
        return packageRepository.findAll().stream().map(p -> toDTO(p, locale)).toList();
    }

    public Page<PackageDTO> getPackagesPaged(Pageable pageable) {
        return packageRepository.findAll(pageable).map(this::toDTO);
    }

    public PackageDTO getPackageById(UUID id) {
        return getPackageById(id, null);
    }

    public PackageDTO getPackageById(UUID id, String locale) {
        Package p = packageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Package", id));
        return toDTO(p, locale);
    }

    public PackageDTO getPackageBySlug(String slug) {
        return getPackageBySlug(slug, null);
    }

    public PackageDTO getPackageBySlug(String slug, String locale) {
        Package p = packageRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Package", slug));
        return toDTO(p, locale);
    }

    public List<PackageDTO> getPackagesByDestination(UUID destinationId) {
        return getPackagesByDestination(destinationId, null);
    }

    public List<PackageDTO> getPackagesByDestination(UUID destinationId, String locale) {
        return packageRepository.findByDestinationId(destinationId).stream().map(p -> toDTO(p, locale)).toList();
    }

    public List<PackageDTO> getPackagesByDestinationAndCategorySlug(UUID destinationId, String categorySlug) {
        return getPackagesByDestinationAndCategorySlug(destinationId, categorySlug, null);
    }

    public List<PackageDTO> getPackagesByDestinationAndCategorySlug(UUID destinationId, String categorySlug, String locale) {
        return packageRepository.findByDestinationIdAndCategoriesSlug(destinationId, categorySlug).stream()
                .map(p -> toDTO(p, locale)).toList();
    }

    public List<PackageDTO> getPackagesByCategorySlug(String categorySlug) {
        return getPackagesByCategorySlug(categorySlug, null);
    }

    public List<PackageDTO> getPackagesByCategorySlug(String categorySlug, String locale) {
        return packageRepository.findByCategoriesSlug(categorySlug).stream().map(p -> toDTO(p, locale)).toList();
    }

    PackageDTO toDTO(Package p) {
        return toDTO(p, null);
    }

    PackageDTO toDTO(Package p, String locale) {
        String lc = Translations.normalize(locale);
        Map<String, Map<String, String>> tr = p.getTranslations();
        Destination destination = p.getDestination();
        PackageDTO dto = new PackageDTO();
        dto.setId(p.getId());
        dto.setSlug(p.getSlug());
        dto.setDestinationId(destination.getId());
        dto.setDestinationName(Translations.pick(destination.getTranslations(), lc, "name", destination.getName()));
        dto.setDestinationSlug(destination.getSlug());
        dto.setName(Translations.pick(tr, lc, "name", p.getName()));
        dto.setDescription(Translations.pick(tr, lc, "description", p.getDescription()));
        dto.setImageUrl(p.getImageUrl());
        dto.setIncludes(Translations.pick(tr, lc, "includes", p.getIncludes()));
        dto.setDuration(p.getDuration());
        dto.setDiscountPct(p.getDiscountPct());
        dto.setSeoIndexable(p.isSeoIndexable());
        if (locale == null) {
            dto.setTranslations(tr);
        }

        List<PackageActivityRefDTO> refs = new ArrayList<>();
        for (PackageActivity pa : p.getPackageActivities()) {
            Activity a = pa.getActivity();
            refs.add(new PackageActivityRefDTO(
                    a.getId(), pa.getPosition(),
                    a.getSlug(), Translations.pick(a.getTranslations(), lc, "name", a.getName()),
                    a.getPrice(), a.getDuration(), a.getImageUrl()));
        }
        dto.setActivities(refs);

        BigDecimal original = refs.stream()
                .map(PackageActivityRefDTO::getPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal discounted = MoneyMath.applyDiscountPct(original, p.getDiscountPct());
        BigDecimal savings = original.subtract(discounted);
        dto.setOriginalPrice(original);
        dto.setDiscountedPrice(discounted);
        dto.setSavings(savings);

        List<CategoryDTO> cats = CategoryResolver.toDTOs(p.getCategories(), lc);
        dto.setCategories(cats);
        dto.setCategoryIds(cats.stream().map(CategoryDTO::getId).toList());
        return dto;
    }

    @Transactional
    public PackageDTO createPackage(PackageDTO dto) {
        Destination destination = destinationRepository.findById(dto.getDestinationId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination", dto.getDestinationId()));
        Package p = new Package();
        p.setDestination(destination);
        applyDtoToEntity(dto, p);
        SlugAssigner.assignOnCreate(p, dto.getSlug(), dto.getName(), packageRepository);
        return toDTO(packageRepository.save(p));
    }

    @Transactional
    public PackageDTO updatePackage(UUID id, PackageDTO dto) {
        Package p = packageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Package", id));
        if (dto.getDestinationId() != null && !dto.getDestinationId().equals(p.getDestination().getId())) {
            Destination destination = destinationRepository.findById(dto.getDestinationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Destination", dto.getDestinationId()));
            p.setDestination(destination);
        }
        SlugAssigner.assignOnUpdate(p, dto.getSlug(), dto.getName(), p.getName(), packageRepository);
        applyDtoToEntity(dto, p);
        return toDTO(packageRepository.save(p));
    }

    @Transactional
    public void deletePackage(UUID id) {
        if (!packageRepository.existsById(id)) {
            throw new ResourceNotFoundException("Package", id);
        }
        packageRepository.deleteById(id);
    }

    private void applyDtoToEntity(PackageDTO dto, Package p) {
        p.setName(dto.getName());
        p.setDescription(dto.getDescription());
        p.setImageUrl(dto.getImageUrl());
        p.setIncludes(dto.getIncludes());
        p.setDuration(dto.getDuration());
        p.setDiscountPct(dto.getDiscountPct());
        p.setSeoIndexable(Boolean.TRUE.equals(dto.getSeoIndexable()));
        p.setCategories(CategoryResolver.resolve(dto.getCategoryIds(), categoryRepository));
        // null = "unchanged" (see ActivityService.applyDtoToEntity).
        if (dto.getTranslations() != null) {
            p.setTranslations(dto.getTranslations());
        }
        applyActivities(dto.getActivities(), p);
    }

    private void applyActivities(List<PackageActivityRefDTO> refs, Package p) {
        if (refs == null) {
            refs = List.of();
        }
        List<UUID> ids = refs.stream().map(PackageActivityRefDTO::getActivityId).toList();
        Map<UUID, Activity> byId = new HashMap<>();
        for (Activity a : activityRepository.findAllById(ids)) {
            byId.put(a.getId(), a);
        }
        for (UUID actId : ids) {
            Activity a = byId.get(actId);
            if (a == null) {
                throw new ResourceNotFoundException("Activity", actId);
            }
            if (!a.getDestination().getId().equals(p.getDestination().getId())) {
                throw new BadRequestException(
                        "Activity " + a.getName() + " belongs to another destination than the package");
            }
        }
        p.getPackageActivities().clear();
        for (PackageActivityRefDTO ref : refs) {
            p.getPackageActivities().add(new PackageActivity(p, byId.get(ref.getActivityId()), ref.getPosition()));
        }
    }

}
