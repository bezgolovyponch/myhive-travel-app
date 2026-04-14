package com.myhive.backend.service;

import com.myhive.backend.dto.BlogPostDTO;
import com.myhive.backend.entity.BlogPost;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.BlogPostRepository;
import com.myhive.backend.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlogPostService {

    private final BlogPostRepository blogPostRepository;

    public List<BlogPostDTO> getAllBlogPosts() {
        return blogPostRepository.findAllByOrderByDateDesc().stream()
                .map(this::convertToDTO)
                .toList();
    }

    public Page<BlogPostDTO> getBlogPostsPaged(Pageable pageable) {
        return blogPostRepository.findAll(pageable)
                .map(this::convertToDTO);
    }

    public BlogPostDTO getBlogPostById(UUID id) {
        BlogPost blogPost = blogPostRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BlogPost", id));
        return convertToDTO(blogPost);
    }

    public BlogPostDTO getBlogPostBySlug(String slug) {
        BlogPost blogPost = blogPostRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Blog post not found"));
        return convertToDTO(blogPost);
    }

    @Transactional
    public BlogPostDTO createBlogPost(BlogPostDTO dto) {
        BlogPost blogPost = new BlogPost();
        applyDtoToEntity(dto, blogPost);
        blogPost.setSlug(SlugUtils.resolveSlug(dto.getSlug(), dto.getTitle(), blogPostRepository::existsBySlug));

        try {
            return convertToDTO(blogPostRepository.save(blogPost));
        } catch (DataIntegrityViolationException e) {
            blogPost.setSlug(SlugUtils.resolveSlug(dto.getSlug(), dto.getTitle(), blogPostRepository::existsBySlug));
            return convertToDTO(blogPostRepository.save(blogPost));
        }
    }

    @Transactional
    public BlogPostDTO updateBlogPost(UUID id, BlogPostDTO dto) {
        BlogPost blogPost = blogPostRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BlogPost", id));

        boolean updateSlug = SlugUtils.needsUpdate(dto.getSlug(), blogPost.getSlug(), dto.getTitle(), blogPost.getTitle());
        applyDtoToEntity(dto, blogPost);
        if (updateSlug) {
            blogPost.setSlug(SlugUtils.resolveForUpdate(dto.getSlug(), dto.getTitle(), blogPost.getSlug(),
                    slug -> blogPostRepository.findBySlug(slug)
                            .filter(b -> !b.getId().equals(id))
                            .isPresent()));
        }

        return convertToDTO(blogPostRepository.save(blogPost));
    }

    @Transactional
    public void deleteBlogPost(UUID id) {
        if (!blogPostRepository.existsById(id)) {
            throw new ResourceNotFoundException("BlogPost", id);
        }
        blogPostRepository.deleteById(id);
    }

    private void applyDtoToEntity(BlogPostDTO dto, BlogPost blogPost) {
        blogPost.setTitle(dto.getTitle());
        blogPost.setExcerpt(dto.getExcerpt());
        blogPost.setContent(dto.getContent());
        blogPost.setCategory(dto.getCategory());
        blogPost.setImageUrl(dto.getImageUrl());
        blogPost.setDate(dto.getDate());
    }

    private BlogPostDTO convertToDTO(BlogPost blogPost) {
        BlogPostDTO dto = new BlogPostDTO();
        dto.setId(blogPost.getId());
        dto.setSlug(blogPost.getSlug());
        dto.setTitle(blogPost.getTitle());
        dto.setExcerpt(blogPost.getExcerpt());
        dto.setContent(blogPost.getContent());
        dto.setCategory(blogPost.getCategory());
        dto.setImageUrl(blogPost.getImageUrl());
        dto.setDate(blogPost.getDate());
        dto.setCreatedAt(blogPost.getCreatedAt());
        return dto;
    }
}
