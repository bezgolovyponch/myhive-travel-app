package com.myhive.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Optional;
import java.util.UUID;

/** Common contract for repositories of slugged entities; used by {@code SlugAssigner}. */
@NoRepositoryBean
public interface SluggedRepository<T> extends JpaRepository<T, UUID> {

    Optional<T> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
