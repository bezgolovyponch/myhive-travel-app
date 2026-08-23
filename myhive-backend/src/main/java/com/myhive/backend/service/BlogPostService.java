package com.myhive.backend.service;

import com.myhive.backend.dto.BlogPostDTO;
import com.myhive.backend.entity.BlogPost;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.BlogPostRepository;
import com.myhive.backend.util.Translations;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlogPostService {

    private final BlogPostRepository blogPostRepository;

    // Without a locale: admin/raw view (base fields + translations map). With
    // one: public view, fields resolved for that locale. See ActivityService.

    public List<BlogPostDTO> getAllBlogPosts() {
        return getAllBlogPosts(null);
    }

    public List<BlogPostDTO> getAllBlogPosts(String locale) {
        return blogPostRepository.findAllByOrderByDateDesc().stream()
                .map(b -> convertToDTO(b, locale))
                .toList();
    }

    public Page<BlogPostDTO> getBlogPostsPaged(Pageable pageable) {
        return blogPostRepository.findAll(pageable)
                .map(this::convertToDTO);
    }

    public BlogPostDTO getBlogPostById(UUID id) {
        return getBlogPostById(id, null);
    }

    public BlogPostDTO getBlogPostById(UUID id, String locale) {
        BlogPost blogPost = blogPostRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BlogPost", id));
        return convertToDTO(blogPost, locale);
    }

    public BlogPostDTO getBlogPostBySlug(String slug) {
        return getBlogPostBySlug(slug, null);
    }

    public BlogPostDTO getBlogPostBySlug(String slug, String locale) {
        BlogPost blogPost = blogPostRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Blog post not found"));
        return convertToDTO(blogPost, locale);
    }

    @Transactional
    public BlogPostDTO createBlogPost(BlogPostDTO dto) {
        BlogPost blogPost = new BlogPost();
        applyDtoToEntity(dto, blogPost);
        SlugAssigner.assignOnCreate(blogPost, dto.getSlug(), dto.getTitle(), blogPostRepository);
        return convertToDTO(blogPostRepository.save(blogPost));
    }

    @Transactional
    public BlogPostDTO updateBlogPost(UUID id, BlogPostDTO dto) {
        BlogPost blogPost = blogPostRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BlogPost", id));

        SlugAssigner.assignOnUpdate(blogPost, dto.getSlug(), dto.getTitle(), blogPost.getTitle(), blogPostRepository);
        applyDtoToEntity(dto, blogPost);

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
        blogPost.setSeoIndexable(Boolean.TRUE.equals(dto.getSeoIndexable()));
        // null = "unchanged" (see ActivityService.applyDtoToEntity).
        if (dto.getTranslations() != null) {
            blogPost.setTranslations(dto.getTranslations());
        }
    }

    private BlogPostDTO convertToDTO(BlogPost blogPost) {
        return convertToDTO(blogPost, null);
    }

    private BlogPostDTO convertToDTO(BlogPost blogPost, String locale) {
        String lc = Translations.normalize(locale);
        Map<String, Map<String, String>> tr = blogPost.getTranslations();
        BlogPostDTO dto = new BlogPostDTO();
        dto.setId(blogPost.getId());
        dto.setSlug(blogPost.getSlug());
        dto.setTitle(Translations.pick(tr, lc, "title", blogPost.getTitle()));
        dto.setExcerpt(Translations.pick(tr, lc, "excerpt", blogPost.getExcerpt()));
        dto.setContent(Translations.pick(tr, lc, "content", blogPost.getContent()));
        dto.setCategory(Translations.pick(tr, lc, "category", blogPost.getCategory()));
        dto.setImageUrl(blogPost.getImageUrl());
        dto.setDate(blogPost.getDate());
        dto.setCreatedAt(blogPost.getCreatedAt());
        dto.setSeoIndexable(blogPost.isSeoIndexable());
        if (locale == null) {
            dto.setTranslations(tr);
        }
        return dto;
    }
}
