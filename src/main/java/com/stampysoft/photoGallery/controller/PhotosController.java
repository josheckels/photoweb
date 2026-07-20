package com.stampysoft.photoGallery.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.stampysoft.photoGallery.Photo;
import com.stampysoft.photoGallery.PhotoOperations;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * Returns photos filtered by a set of categories. A photo is included only if it appears in EVERY
 * selected category (intersection / AND), where membership in a category also counts photos that live in
 * any descendant category (recursively).
 */
@RestController
public class PhotosController extends AbstractController {

    @Autowired
    private PhotoOperations photoOperations;

    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE}, value = "/api/photos")
    public ResponseEntity<?> getPhotos(
            HttpServletRequest request,
            @RequestParam(value = "categories", required = false) List<Long> categoryIds,
            @RequestParam(value = "private", required = false) Boolean includePrivate) throws JsonProcessingException {

        boolean privateSetting = includePrivate(includePrivate, request);

        if (categoryIds == null || categoryIds.isEmpty()) {
            return ResponseEntity.ok(objectMapper.writeValueAsString(Collections.emptyList()));
        }

        List<Photo> photos = photoOperations.getPhotosInAllCategories(categoryIds, privateSetting);
        return ResponseEntity.ok(objectMapper.writeValueAsString(photos));
    }
}
