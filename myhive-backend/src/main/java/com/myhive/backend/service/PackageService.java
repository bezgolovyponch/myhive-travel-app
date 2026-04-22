package com.myhive.backend.service;

import com.myhive.backend.dto.CategoryDTO;
import com.myhive.backend.dto.PackageActivityRefDTO;
import com.myhive.backend.dto.PackageDTO;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.Package;
import com.myhive.backend.entity.PackageActivity;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.PackageRepository;
import com.myhive.backend.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PackageService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PackageRepository packageRepository;
    private final DestinationRepository destinationRepository;
    private final ActivityRepository activityRepository;
    private final CategoryRepository categoryRepository;

    public List<PackageDTO> getAllPackages() {
        return packageRepository.findAll().stream().map(this::toDTO).toList();
    }

    public Page<PackageDTO> getPackagesPaged(Pageable pageable) {
        return packageRepository.findAll(pageable).map(this::toDTO);
    }

    public PackageDTO getPackageById(UUID id) {
        Package p = packageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Package", id));
        return toDTO(p);
    }

    public PackageDTO getPackageBySlug(String slug) {
        Package p = packageRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found"));
        return toDTO(p);
    }

    public List<PackageDTO> getPackagesByDestination(UUID destinationId) {
        return packageRepository.findByDestinationId(destinationId).stream().map(this::toDTO).toList();
    }

    public List<PackageDTO> getPackagesByDestinationAndCategorySlug(UUID destinationId, String categorySlug) {
        return packageRepository.findByDestinationIdAndCategoriesSlug(destinationId, categorySlug).stream()
                .map(this::toDTO).toList();
    }

    public List<PackageDTO> getPackagesByCategorySlug(String categorySlug) {
        return packageRepository.findByCategoriesSlug(categorySlug).stream().map(this::toDTO).toList();
    }

    PackageDTO toDTO(Package p) {
        PackageDTO dto = new PackageDTO();
        dto.setId(p.getId());
        dto.setSlug(p.getSlug());
        dto.setDestinationId(p.getDestination().getId());
        dto.setDestinationName(p.getDestination().getName());
        dto.setDestinationSlug(p.getDestination().getSlug());
        dto.setName(p.getName());
        dto.setDescription(p.getDescription());
        dto.setImageUrl(p.getImageUrl());
        dto.setIncludes(p.getIncludes());
        dto.setDuration(p.getDuration());
        dto.setDiscountPct(p.getDiscountPct());

        List<PackageActivityRefDTO> refs = new ArrayList<>();
        for (PackageActivity pa : p.getPackageActivities()) {
            Activity a = pa.getActivity();
            refs.add(new PackageActivityRefDTO(
                    a.getId(), pa.getPosition(),
                    a.getSlug(), a.getName(), a.getPrice(), a.getDuration(), a.getImageUrl()));
        }
        dto.setActivities(refs);

        BigDecimal original = refs.stream()
                .map(PackageActivityRefDTO::getPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal discounted = original
                .multiply(HUNDRED.subtract(p.getDiscountPct()))
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal savings = original.subtract(discounted);
        dto.setOriginalPrice(original);
        dto.setDiscountedPrice(discounted);
        dto.setSavings(savings);

        List<CategoryDTO> cats = p.getCategories() == null ? new ArrayList<>()
                : p.getCategories().stream()
                .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                .map(c -> new CategoryDTO(c.getId(), c.getName(), c.getSlug()))
                .toList();
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
        p.setSlug(SlugUtils.resolveSlug(dto.getSlug(), dto.getName(), packageRepository::existsBySlug));
        try {
            return toDTO(packageRepository.save(p));
        } catch (DataIntegrityViolationException e) {
            p.setSlug(SlugUtils.resolveSlug(dto.getSlug(), dto.getName(), packageRepository::existsBySlug));
            return toDTO(packageRepository.save(p));
        }
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
        boolean updateSlug = SlugUtils.needsUpdate(dto.getSlug(), p.getSlug(), dto.getName(), p.getName());
        applyDtoToEntity(dto, p);
        if (updateSlug) {
            UUID currentId = id;
            p.setSlug(SlugUtils.resolveForUpdate(
                    dto.getSlug(), dto.getName(), p.getSlug(),
                    slug -> packageRepository.findBySlug(slug)
                            .filter(x -> !x.getId().equals(currentId))
                            .isPresent()));
        }
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
        p.setCategories(resolveCategories(dto.getCategoryIds()));
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

    private Set<Category> resolveCategories(List<UUID> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return new HashSet<>();
        }
        List<Category> found = categoryRepository.findAllById(categoryIds);
        if (found.size() != categoryIds.size()) {
            Set<UUID> foundIds = new HashSet<>();
            for (Category c : found) {
                foundIds.add(c.getId());
            }
            UUID missing = categoryIds.stream().filter(i -> !foundIds.contains(i)).findFirst().orElseThrow();
            throw new ResourceNotFoundException("Category", missing);
        }
        return new HashSet<>(found);
    }
}
