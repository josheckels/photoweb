package com.stampysoft.photoGallery.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.stampysoft.photoGallery.Category;
import com.stampysoft.photoGallery.PhotoOperations;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class AllCategoryController extends AbstractController {

    @Autowired
    private PhotoOperations photoOperations;

    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE}, value = {"/api/categories"})

    public ResponseEntity<?> getCategories(
            HttpServletRequest request,
            HttpServletResponse res,
            @RequestParam(value = "private", required = false) Boolean includePrivate) throws JsonProcessingException {

        boolean privateSetting = includePrivate(includePrivate, request);

        List<Category> categories = photoOperations.getAllCategoriesAndDefaultPhotos(privateSetting);
        Map<Integer, Category> categoryMap = new HashMap<>();
        for (Category c : categories)
        {
            categoryMap.put(c.getCategoryId(), c);
        }

        SimpleFilterProvider filterProvider = new SimpleFilterProvider();
        filterProvider.addFilter("categoryFilter",
                SimpleBeanPropertyFilter.serializeAllExcept("photos", "subcategories"));
        filterProvider.addFilter("photoFilter",
                SimpleBeanPropertyFilter.serializeAllExcept("categories", "metadata"));

        // Serialize with the filter applied
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writer(filterProvider).writeValueAsString(categoryMap);

        return ResponseEntity.ok(json);
    }
}
