package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.dto.BlogPostDTO;
import com.myhive.backend.entity.BlogPost;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.BlogPostRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlogPostServiceTest {

    @Mock
    private BlogPostRepository blogPostRepository;

    @InjectMocks
    private BlogPostService blogPostService;

    @Test
    void getAllBlogPosts_orderedByDateDesc() {
        BlogPost bp = TestDataFactory.blogPost();
        when(blogPostRepository.findAllByOrderByDateDesc()).thenReturn(List.of(bp));

        List<BlogPostDTO> result = blogPostService.getAllBlogPosts();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getTitle()).isEqualTo(bp.getTitle());
    }

    @Test
    void getBlogPostById_found_returnsDTO() {
        BlogPost bp = TestDataFactory.blogPost();
        when(blogPostRepository.findById(bp.getId())).thenReturn(Optional.of(bp));

        BlogPostDTO result = blogPostService.getBlogPostById(bp.getId());

        assertThat(result.getId()).isEqualTo(bp.getId());
        assertThat(result.getContent()).isEqualTo(bp.getContent());
    }

    @Test
    void getBlogPostById_notFound_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(blogPostRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> blogPostService.getBlogPostById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("BlogPost");
    }

    @Test
    void createBlogPost_savesAndReturns() {
        BlogPostDTO dto = TestDataFactory.blogPostDTO();
        when(blogPostRepository.save(any(BlogPost.class))).thenAnswer(inv -> {
            BlogPost bp = inv.getArgument(0);
            bp.setId(UUID.randomUUID());
            return bp;
        });

        BlogPostDTO result = blogPostService.createBlogPost(dto);

        assertThat(result.getTitle()).isEqualTo("New Blog Post");
        verify(blogPostRepository).save(any(BlogPost.class));
    }

    @Test
    void updateBlogPost_found_updatesAndReturns() {
        BlogPost existing = TestDataFactory.blogPost();
        BlogPostDTO dto = TestDataFactory.blogPostDTO();
        when(blogPostRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(blogPostRepository.save(any(BlogPost.class))).thenAnswer(inv -> inv.getArgument(0));

        BlogPostDTO result = blogPostService.updateBlogPost(existing.getId(), dto);

        assertThat(result.getTitle()).isEqualTo("New Blog Post");
    }

    @Test
    void updateBlogPost_notFound_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(blogPostRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> blogPostService.updateBlogPost(id, TestDataFactory.blogPostDTO()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteBlogPost_exists_deletes() {
        UUID id = UUID.randomUUID();
        when(blogPostRepository.existsById(id)).thenReturn(true);

        blogPostService.deleteBlogPost(id);

        verify(blogPostRepository).deleteById(id);
    }

    @Test
    void deleteBlogPost_notFound_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(blogPostRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> blogPostService.deleteBlogPost(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
