package com.stampysoft.photoGallery.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.stampysoft.photoGallery.Category;
import com.stampysoft.photoGallery.PhotoOperations;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@RestController
public class IncludePrivateController extends AbstractController {

    @GetMapping(value = {"/includePrivate"})

    public ResponseEntity<?> getCategories(
            HttpServletRequest request,
            HttpServletResponse res) throws JsonProcessingException {

        includePrivate(true, request);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", "/")
                .build();
    }
}
