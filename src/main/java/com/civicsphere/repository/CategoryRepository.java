package com.civicsphere.repository;

import com.civicsphere.model.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {

    List<Category> findAll();

    Optional<Category> findById(Integer categoryId);

    Category save(Category category);

    List<Category> findByActiveTrueOrderByNameAsc();

    long count();
}
