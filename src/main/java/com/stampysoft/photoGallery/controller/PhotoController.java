package com.stampysoft.photoGallery.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.stampysoft.photoGallery.Category;
import com.stampysoft.photoGallery.Photo;
import com.stampysoft.photoGallery.PhotoOperations;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@RestController
public class PhotoController extends AbstractController {

    @Autowired
    private PhotoOperations photoOperations;

    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE}, value = {"/api/photo/{photoId}"})
    public ResponseEntity<?> getCategories(
            HttpServletRequest request,
            HttpServletResponse res,
            @PathVariable(value = "photoId") long photoId) throws JsonProcessingException {

        boolean privateSetting = includePrivate(null, request);

        Photo photo = photoOperations.getPhoto(photoId, privateSetting);
        if (photo == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(objectMapper.writeValueAsString(photo));
    }
}
