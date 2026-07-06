package com.myhive.backend.repository;

import com.myhive.backend.entity.Category;

public interface CategoryRepository extends SluggedRepository<Category> {

    boolean existsByNameIgnoreCase(String name);
}
