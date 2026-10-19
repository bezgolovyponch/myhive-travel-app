package com.myhive.backend.repository;

import com.myhive.backend.entity.BlogPost;

import java.util.List;
import java.util.Optional;

public interface BlogPostRepository extends SluggedRepository<BlogPost> {

    List<BlogPost> findByCategory(String category);

    List<BlogPost> findAllByOrderByDateDesc();

    Optional<BlogPost> findBySlugDe(String slugDe);

    boolean existsBySlugDe(String slugDe);
}
