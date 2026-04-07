package com.myhive.backend.repository;

import com.myhive.backend.entity.BlogPost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class BlogPostRepositoryTest {

    @Autowired
    private BlogPostRepository blogPostRepository;

    @BeforeEach
    void setUp() {
        BlogPost older = new BlogPost();
        older.setTitle("Old Post");
        older.setContent("Old content");
        older.setCategory("Travel");
        older.setDate(LocalDate.of(2026, 1, 1));
        blogPostRepository.save(older);

        BlogPost newer = new BlogPost();
        newer.setTitle("New Post");
        newer.setContent("New content");
        newer.setCategory("Tips");
        newer.setDate(LocalDate.of(2026, 3, 15));
        blogPostRepository.save(newer);
    }

    @Test
    void findAllByOrderByDateDesc_returnsSortedByDateDescending() {
        var result = blogPostRepository.findAllByOrderByDateDesc();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("New Post");
        assertThat(result.get(1).getTitle()).isEqualTo("Old Post");
    }

    @Test
    void findByCategory_returnsMatchingPosts() {
        var result = blogPostRepository.findByCategory("Travel");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getTitle()).isEqualTo("Old Post");
    }
}
