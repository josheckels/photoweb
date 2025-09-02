package com.stampysoft.photoGallery.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class AbstractController {

    private static final String INCLUDE_PRIVATE = "includePrivate";

    protected final ObjectMapper objectMapper;

    public AbstractController() {
        this.objectMapper = new ObjectMapper();
        objectMapper.setFilterProvider(new SimpleFilterProvider().
                addFilter("photoFilter", SimpleBeanPropertyFilter.serializeAllExcept()).
                addFilter("categoryFilter", SimpleBeanPropertyFilter.serializeAllExcept()));
    }

    protected boolean includePrivate(Boolean includePrivate, HttpServletRequest request)
    {
        HttpSession session = request.getSession(true);
         if (includePrivate != null)
        {
            session.setAttribute(INCLUDE_PRIVATE, includePrivate);
            return includePrivate;
        }
        Boolean result = (Boolean) session.getAttribute(INCLUDE_PRIVATE);
        return result != null && result;
    }
}
