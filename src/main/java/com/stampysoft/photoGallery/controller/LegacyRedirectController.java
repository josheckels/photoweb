package com.stampysoft.photoGallery.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URI;

/**
 * Handles legacy links and redirects them to the new SPA routes.
 */
@Controller
public class LegacyRedirectController {

    @GetMapping("/photoDetail")
    public ResponseEntity<Void> redirectPhotoDetail(
            @RequestParam(value = "PhotoId", required = false) Long photoId,
            @RequestParam(value = "ReferringCategoryId", required = false) Long referringCategoryId,
            // Be lenient and also accept lowercase params if present
            @RequestParam(value = "photoId", required = false) Long photoIdLower,
            @RequestParam(value = "referringCategoryId", required = false) Long referringCategoryIdLower
    ) {
        Long pid = photoId != null ? photoId : photoIdLower;
        Long cid = referringCategoryId != null ? referringCategoryId : referringCategoryIdLower;

        if (pid == null || cid == null) {
            // If the parameters are missing, fall back to index without redirect to avoid errors
            // Using 302 to index since we cannot construct target; alternative is 400 Bad Request
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create("/"));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        }

        String target = "/category/" + cid + "/" + pid;
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(target));
        return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
    }
}
