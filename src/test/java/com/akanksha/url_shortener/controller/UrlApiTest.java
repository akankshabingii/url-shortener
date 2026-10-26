package com.akanksha.url_shortener.controller;

import com.akanksha.url_shortener.dto.ShortenResponse;
import com.akanksha.url_shortener.exception.ExpiredException;
import com.akanksha.url_shortener.exception.NotFoundException;
import com.akanksha.url_shortener.service.UrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({UrlController.class, RedirectController.class})
class UrlApiTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private UrlService service;

    @Test
    void create_valid_returns201() throws Exception {
        when(service.shorten(any())).thenReturn(
                new ShortenResponse("abc1234", "http://localhost:8080/abc1234",
                        "https://example.com", null));

        mvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("abc1234"));
    }

    @Test
    void create_badScheme_returns400() throws Exception {
        mvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"ftp://x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("http")));
    }

    @Test
    void create_malformedJson_returns400() throws Exception {
        mvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("valid JSON")));
    }

    @Test
    void redirect_returns302WithLocation() throws Exception {
        when(service.resolveAndCount("abc1234")).thenReturn("https://example.com");

        mvc.perform(get("/abc1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com"));
    }

    @Test
    void redirect_unknownCode_returns404() throws Exception {
        when(service.resolveAndCount("nope")).thenThrow(new NotFoundException("Short URL not found"));

        mvc.perform(get("/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Short URL not found"));
    }

    @Test
    void redirect_expired_returns410() throws Exception {
        when(service.resolveAndCount("old")).thenThrow(new ExpiredException("This short URL has expired"));

        mvc.perform(get("/old")).andExpect(status().isGone());
    }

    // These two prove the catch-all handler does not turn Spring's own errors into 500
    @Test
    void unknownPath_returns404NotServerError() throws Exception {
        mvc.perform(get("/api/nothing/here")).andExpect(status().isNotFound());
    }

    @Test
    void wrongMethod_returns405NotServerError() throws Exception {
        mvc.perform(post("/api/urls/abc/stats")).andExpect(status().isMethodNotAllowed());
    }
}