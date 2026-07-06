package com.myhive.backend.service;

import com.myhive.backend.entity.Slugged;
import com.myhive.backend.repository.SluggedRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlugAssignerTest {

    private static final class TestEntity implements Slugged {

        private final UUID id = UUID.randomUUID();
        private String slug;

        @Override
        public UUID getId() {
            return id;
        }

        @Override
        public String getSlug() {
            return slug;
        }

        @Override
        public void setSlug(String slug) {
            this.slug = slug;
        }
    }

    @Mock
    private SluggedRepository<TestEntity> repository;

    @Test
    void assignOnCreate_noCustomSlug_generatesFromName() {
        String expectedSlug = "prague-tours";
        TestEntity entity = new TestEntity();
        when(repository.existsBySlug(expectedSlug)).thenReturn(false);

        SlugAssigner.assignOnCreate(entity, null, "Prague Tours", repository);

        assertThat(entity.getSlug()).isEqualTo(expectedSlug);
    }

    @Test
    void assignOnCreate_slugCollision_appendsSuffix() {
        String expectedSlug = "prague-2";
        TestEntity entity = new TestEntity();
        when(repository.existsBySlug("prague")).thenReturn(true);
        when(repository.existsBySlug(expectedSlug)).thenReturn(false);

        SlugAssigner.assignOnCreate(entity, null, "Prague", repository);

        assertThat(entity.getSlug()).isEqualTo(expectedSlug);
    }

    @Test
    void assignOnCreate_customSlug_sanitizesAndUses() {
        String expectedSlug = "best-of-prague";
        TestEntity entity = new TestEntity();
        when(repository.existsBySlug(expectedSlug)).thenReturn(false);

        SlugAssigner.assignOnCreate(entity, "Best of Prague", "ignored", repository);

        assertThat(entity.getSlug()).isEqualTo(expectedSlug);
    }

    @Test
    void assignOnUpdate_nothingChanged_keepsSlugWithoutRepositoryCalls() {
        String expectedSlug = "prague";
        TestEntity entity = new TestEntity();
        entity.setSlug(expectedSlug);

        SlugAssigner.assignOnUpdate(entity, null, "Prague", "Prague", repository);

        assertThat(entity.getSlug()).isEqualTo(expectedSlug);
        verifyNoInteractions(repository);
    }

    @Test
    void assignOnUpdate_nameChanged_regeneratesSlug() {
        String expectedSlug = "vienna";
        TestEntity entity = new TestEntity();
        entity.setSlug("prague");
        when(repository.findBySlug(expectedSlug)).thenReturn(Optional.empty());

        SlugAssigner.assignOnUpdate(entity, null, "Vienna", "Prague", repository);

        assertThat(entity.getSlug()).isEqualTo(expectedSlug);
    }

    @Test
    void assignOnUpdate_generatedSlugMatchesSelf_keepsSlugWithoutSuffix() {
        String expectedSlug = "prague";
        TestEntity entity = new TestEntity();
        entity.setSlug(expectedSlug);
        when(repository.findBySlug(expectedSlug)).thenReturn(Optional.of(entity));

        SlugAssigner.assignOnUpdate(entity, null, "PRAGUE", "Prague", repository);

        assertThat(entity.getSlug()).isEqualTo(expectedSlug);
    }

    @Test
    void assignOnUpdate_slugTakenByOtherEntity_appendsSuffix() {
        String expectedSlug = "vienna-2";
        TestEntity entity = new TestEntity();
        entity.setSlug("prague");
        when(repository.findBySlug("vienna")).thenReturn(Optional.of(new TestEntity()));
        when(repository.findBySlug(expectedSlug)).thenReturn(Optional.empty());

        SlugAssigner.assignOnUpdate(entity, null, "Vienna", "Prague", repository);

        assertThat(entity.getSlug()).isEqualTo(expectedSlug);
    }

    @Test
    void assignOnUpdate_slugCleared_regeneratesFromName() {
        String expectedSlug = "prague";
        TestEntity entity = new TestEntity();
        entity.setSlug("custom-slug");
        when(repository.findBySlug(expectedSlug)).thenReturn(Optional.empty());

        SlugAssigner.assignOnUpdate(entity, "", "Prague", "Prague", repository);

        assertThat(entity.getSlug()).isEqualTo(expectedSlug);
    }
}
