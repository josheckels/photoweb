package com.stampysoft.photoGallery.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaController {

    // Handle client-side routing - forward to index.html
    @RequestMapping(value = {
        "/",
        "/categoryList",
        "/category/**",
        "/filter",
        "/photo/**"
    })

    public String spa() {
        return "forward:/index.html";
    }
}
