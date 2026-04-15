package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.dto.ActivityDTO;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.DestinationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    void getActivitiesByCategory_returnsList() {
        when(activityRepository.findByCategory("Adventure")).thenReturn(List.of(activity));

        List<ActivityDTO> result = activityService.getActivitiesByCategory("Adventure");

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
}
