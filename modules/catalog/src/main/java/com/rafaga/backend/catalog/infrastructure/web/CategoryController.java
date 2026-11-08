package com.rafaga.backend.catalog.infrastructure.web;

import com.rafaga.backend.catalog.application.service.CategoryNode;
import com.rafaga.backend.catalog.application.service.CategoryService;
import com.rafaga.backend.catalog.application.service.CategoryView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService categories;

    public CategoryController(CategoryService categories) {
        this.categories = categories;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryView create(@Valid @RequestBody CategoryRequest request) {
        return categories.createCategory(request.name(), request.slug(), request.parentId(), request.displayOrder());
    }

    @GetMapping("/{categoryId}")
    public CategoryView get(@PathVariable("categoryId") UUID categoryId) {
        return categories.getCategoryById(categoryId);
    }

    @PutMapping("/{categoryId}")
    public CategoryView update(@PathVariable("categoryId") UUID categoryId, @Valid @RequestBody CategoryRequest request) {
        return categories.updateCategory(categoryId, request.name(), request.slug(),
                request.parentId(), request.displayOrder());
    }

    @GetMapping
    public List<CategoryNode> tree() {
        return categories.getCategoryTree();
    }

    public record CategoryRequest(@NotBlank String name, @NotBlank String slug,
            UUID parentId, int displayOrder) {
    }
}
