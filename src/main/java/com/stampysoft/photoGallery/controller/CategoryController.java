package com.stampysoft.photoGallery.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.stampysoft.photoGallery.Category;
import com.stampysoft.photoGallery.PhotoOperations;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
public class CategoryController extends AbstractController {

    private static final Category ROOT_CATEGORY;

    static {
        ROOT_CATEGORY = new Category();
        ROOT_CATEGORY.setDescription("All Categories");
        ROOT_CATEGORY.setPhotos(Collections.emptySet());
    }


    @Autowired
    private PhotoOperations photoOperations;

    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE}, value = {"/api/category", "/api/category/{categoryId}"})

    public ResponseEntity<?> getCategories(
            HttpServletRequest request,
            HttpServletResponse res,
            @RequestParam(value = "private", required = false) Boolean includePrivate,
            @PathVariable(value = "categoryId", required = false) Long categoryId) throws JsonProcessingException {

        boolean privateSetting = includePrivate(includePrivate, request);

        if (categoryId != null) {
            Category category = photoOperations.getCategoryByCategoryId(categoryId, privateSetting);
            if (category == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(objectMapper.writeValueAsString(category));
        } else {
            List<Category> categories = photoOperations.getRootCategories(privateSetting);
            return ResponseEntity.ok(objectMapper.writeValueAsString(categories));
        }
    }
}
