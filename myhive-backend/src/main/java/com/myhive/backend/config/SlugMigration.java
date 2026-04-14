package com.myhive.backend.config;

import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.BlogPost;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.BlogPostRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.migration.slugs", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SlugMigration implements CommandLineRunner {

    private final DestinationRepository destinationRepository;
    private final ActivityRepository activityRepository;
    private final BlogPostRepository blogPostRepository;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting slug migration...");

        int destinationCount = 0;
        for (Destination dest : destinationRepository.findAll()) {
            if (dest.getSlug() == null || dest.getSlug().isBlank()) {
                String baseSlug = SlugUtils.generateSlug(dest.getName());
                dest.setSlug(SlugUtils.ensureUnique(baseSlug, destinationRepository::existsBySlug));
                destinationRepository.save(dest);
                destinationCount++;
            }
        }
        log.info("Migrated {} destination slugs", destinationCount);

        int activityCount = 0;
        for (Activity activity : activityRepository.findAll()) {
            if (activity.getSlug() == null || activity.getSlug().isBlank()) {
                String baseSlug = SlugUtils.generateSlug(activity.getName());
                activity.setSlug(SlugUtils.ensureUnique(baseSlug, activityRepository::existsBySlug));
                activityRepository.save(activity);
                activityCount++;
            }
        }
        log.info("Migrated {} activity slugs", activityCount);

        int blogPostCount = 0;
        for (BlogPost post : blogPostRepository.findAll()) {
            if (post.getSlug() == null || post.getSlug().isBlank()) {
                String baseSlug = SlugUtils.generateSlug(post.getTitle());
                post.setSlug(SlugUtils.ensureUnique(baseSlug, blogPostRepository::existsBySlug));
                blogPostRepository.save(post);
                blogPostCount++;
            }
        }
        log.info("Migrated {} blog post slugs", blogPostCount);

        log.info("Slug migration complete.");
    }
}
