package com.myhive.backend.repository;

import com.myhive.backend.entity.BlogPost;

import java.util.List;

public interface BlogPostRepository extends SluggedRepository<BlogPost> {

    List<BlogPost> findByCategory(String category);

    List<BlogPost> findAllByOrderByDateDesc();
}
