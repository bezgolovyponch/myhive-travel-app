package com.myhive.backend.repository;

import com.myhive.backend.entity.BlogPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BlogPostRepository extends JpaRepository<BlogPost, UUID> {

    List<BlogPost> findByCategory(String category);

    List<BlogPost> findAllByOrderByDateDesc();
}
