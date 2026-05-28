package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.dto.ActivityDTO;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.PackageRepository;
import com.myhive.backend.repository.VoteSessionActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private DestinationRepository destinationRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private PackageRepository packageRepository;

    @Mock
    private VoteSessionActivityRepository voteSessionActivityRepository;

    @InjectMocks
    private ActivityService activityService;

    private Destination destination;
    private Activity activity;

    @BeforeEach
    void setUp() {
        destination = TestDataFactory.destination();
        activity = TestDataFactory.activity(destination);
    }

    @Test
    void getAllActivities_returnsDTOList() {
        when(activityRepository.findAll()).thenReturn(List.of(activity));

        List<ActivityDTO> result = activityService.getAllActivities();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getName()).isEqualTo(activity.getName());
    }

    @Test
    void getActivityById_found_returnsDTO() {
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));

        ActivityDTO result = activityService.getActivityById(activity.getId());

        assertThat(result.getId()).isEqualTo(activity.getId());
        assertThat(result.getDestinationName()).isEqualTo(destination.getName());
        assertThat(result.getIncludes()).isEqualTo("Guide, transport, lunch");
    }

    @Test
    void getActivityById_notFound_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(activityRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> activityService.getActivityById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Activity");
    }

    @Test
    void getActivitiesByDestination_returnsList() {
        when(activityRepository.findByDestinationId(destination.getId())).thenReturn(List.of(activity));

        List<ActivityDTO> result = activityService.getActivitiesByDestination(destination.getId());

        assertThat(result).hasSize(1);
    }

    @Test
    void getActivitiesByCategorySlug_returnsList() {
        when(activityRepository.findByCategoriesSlug("adventure")).thenReturn(List.of(activity));

        List<ActivityDTO> result = activityService.getActivitiesByCategorySlug("adventure");

        assertThat(result).hasSize(1);
    }

    @Test
    void getActivityBySlug_found_returnsDTO() {
        when(activityRepository.findBySlug("test-activity")).thenReturn(Optional.of(activity));

        ActivityDTO result = activityService.getActivityBySlug("test-activity");

        assertThat(result.getId()).isEqualTo(activity.getId());
        assertThat(result.getSlug()).isEqualTo("test-activity");
        assertThat(result.getDestinationSlug()).isEqualTo(destination.getSlug());
    }

    @Test
    void getActivityBySlug_notFound_throwsResourceNotFound() {
        when(activityRepository.findBySlug("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> activityService.getActivityBySlug("nonexistent"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createActivity_validDTO_savesAndReturns() {
        String expectedName = "New Activity";
        String expectedIncludes = "Tickets, guide";
        String expectedDestinationName = destination.getName();

        ActivityDTO dto = TestDataFactory.activityDTO(destination.getId());
        dto.setName(expectedName);

        when(destinationRepository.findById(destination.getId())).thenReturn(Optional.of(destination));
        when(activityRepository.existsBySlug("new-activity")).thenReturn(false);
        when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> {
            Activity a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        ActivityDTO result = activityService.createActivity(dto);

        assertThat(result.getName()).isEqualTo(expectedName);
        assertThat(result.getIncludes()).isEqualTo(expectedIncludes);
        assertThat(result.getDestinationName()).isEqualTo(expectedDestinationName);
        assertThat(result.getSlug()).isEqualTo("new-activity");
    }

    @Test
    void createActivity_slugCollision_appendsSuffix() {
        ActivityDTO dto = TestDataFactory.activityDTO(destination.getId());

        when(destinationRepository.findById(destination.getId())).thenReturn(Optional.of(destination));
        when(activityRepository.existsBySlug("new-activity")).thenReturn(true);
        when(activityRepository.existsBySlug("new-activity-2")).thenReturn(false);
        when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> {
            Activity a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        ActivityDTO result = activityService.createActivity(dto);

        assertThat(result.getSlug()).isEqualTo("new-activity-2");
    }

    @Test
    void updateActivity_sameNameKeepsSlug() {
        ActivityDTO dto = TestDataFactory.activityDTO(destination.getId());
        dto.setName(activity.getName());
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

        ActivityDTO result = activityService.updateActivity(activity.getId(), dto);

        assertThat(result.getSlug()).isEqualTo("test-activity");
    }

    @Test
    void createActivity_nonexistentDestination_throwsResourceNotFound() {
        UUID fakeId = UUID.randomUUID();
        ActivityDTO dto = TestDataFactory.activityDTO(fakeId);
        when(destinationRepository.findById(fakeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> activityService.createActivity(dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Destination");
    }

    @Test
    void updateActivity_sameDestination_doesNotRelookup() {
        ActivityDTO dto = TestDataFactory.activityDTO(destination.getId());
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(activityRepository.findBySlug("new-activity")).thenReturn(Optional.empty());
        when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

        activityService.updateActivity(activity.getId(), dto);

        verify(destinationRepository, never()).findById(any());
    }

    @Test
    void updateActivity_differentDestination_looksUpNew() {
        Destination newDest = TestDataFactory.destination();
        ActivityDTO dto = TestDataFactory.activityDTO(newDest.getId());
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(destinationRepository.findById(newDest.getId())).thenReturn(Optional.of(newDest));
        when(activityRepository.findBySlug("new-activity")).thenReturn(Optional.empty());
        when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

        ActivityDTO result = activityService.updateActivity(activity.getId(), dto);

        assertThat(result.getDestinationName()).isEqualTo(newDest.getName());
        verify(destinationRepository).findById(newDest.getId());
    }

    @Test
    void updateActivity_nonexistentActivity_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        ActivityDTO dto = TestDataFactory.activityDTO(destination.getId());
        when(activityRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> activityService.updateActivity(id, dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteActivity_exists_deletes() {
        when(activityRepository.existsById(activity.getId())).thenReturn(true);
        when(packageRepository.findPackageNamesByActivityId(activity.getId())).thenReturn(List.of());

        activityService.deleteActivity(activity.getId());

        verify(activityRepository).deleteById(activity.getId());
    }

    @Test
    void deleteActivity_notFound_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(activityRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> activityService.deleteActivity(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createActivity_withCategoryIds_attachesCategories() {
        Category cat1 = TestDataFactory.category("Adventure");
        Category cat2 = TestDataFactory.category("Nightlife");
        ActivityDTO dto = TestDataFactory.activityDTO(destination.getId());
        dto.setCategoryIds(List.of(cat1.getId(), cat2.getId()));

        when(destinationRepository.findById(destination.getId())).thenReturn(Optional.of(destination));
        when(categoryRepository.findAllById(dto.getCategoryIds())).thenReturn(List.of(cat1, cat2));
        when(activityRepository.existsBySlug("new-activity")).thenReturn(false);
        when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> {
            Activity a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        ActivityDTO result = activityService.createActivity(dto);

        assertThat(result.getCategories()).extracting("name")
                .containsExactlyInAnyOrder("Adventure", "Nightlife");
        assertThat(result.getCategoryIds()).containsExactlyInAnyOrder(cat1.getId(), cat2.getId());
    }

    @Test
    void createActivity_unknownCategoryId_throwsResourceNotFound() {
        UUID unknownCategoryId = UUID.randomUUID();
        ActivityDTO dto = TestDataFactory.activityDTO(destination.getId());
        dto.setCategoryIds(List.of(unknownCategoryId));

        when(destinationRepository.findById(destination.getId())).thenReturn(Optional.of(destination));
        when(categoryRepository.findAllById(dto.getCategoryIds())).thenReturn(List.of());

        assertThatThrownBy(() -> activityService.createActivity(dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category");
    }

    @Test
    void getActivityById_includesFeaturedWeight() {
        UUID activityId = UUID.randomUUID();
        int expectedWeight = 7;

        Destination dest = new Destination();
        dest.setId(UUID.randomUUID());

        Activity act = new Activity();
        act.setId(activityId);
        act.setName("Tank Driving");
        act.setPrice(new BigDecimal("150"));
        act.setDestination(dest);
        act.setFeaturedWeight(expectedWeight);
        act.setCategories(new java.util.HashSet<>());

        when(activityRepository.findById(activityId)).thenReturn(Optional.of(act));

        ActivityDTO dto = activityService.getActivityById(activityId);

        assertThat(dto.getFeaturedWeight()).isEqualTo(expectedWeight);
    }

    @Test
    void updateActivity_persistsFeaturedWeight() {
        UUID activityId = UUID.randomUUID();
        int expectedWeight = 5;

        Destination dest = new Destination();
        dest.setId(UUID.randomUUID());

        Activity existing = new Activity();
        existing.setId(activityId);
        existing.setDestination(dest);
        existing.setName("Old");
        existing.setPrice(new BigDecimal("100"));
        existing.setFeaturedWeight(0);
        existing.setCategories(new java.util.HashSet<>());

        when(activityRepository.findById(activityId)).thenReturn(Optional.of(existing));
        when(activityRepository.save(any(Activity.class))).thenAnswer(i -> i.getArgument(0));

        ActivityDTO input = new ActivityDTO();
        input.setName("Old");
        input.setPrice(new BigDecimal("100"));
        input.setDestinationId(dest.getId());
        input.setFeaturedWeight(expectedWeight);

        activityService.updateActivity(activityId, input);

        ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
        verify(activityRepository).save(captor.capture());
        assertThat(captor.getValue().getFeaturedWeight()).isEqualTo(expectedWeight);
    }

    @Test
    void deleteActivityThrowsWhenUsedInPackages() {
        UUID id = UUID.randomUUID();
        when(activityRepository.existsById(id)).thenReturn(true);
        when(packageRepository.findPackageNamesByActivityId(id))
                .thenReturn(List.of("Honeymoon Bali", "Adventure Java"));

        assertThatThrownBy(() -> activityService.deleteActivity(id))
                .isInstanceOf(com.myhive.backend.exception.ActivityInUseException.class);
    }
}
