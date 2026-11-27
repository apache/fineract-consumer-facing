/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.fineract.consumer.infrastructure.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.constraints.Min;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Drives real Spring MVC argument binding and content negotiation against every production advice, so each framework
 * exception is raised by the framework itself and must win over {@link DefaultExceptionHandler}'s Throwable catch-all.
 */
class RequestExceptionHandlersTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
            .setControllerAdvice(new ConsumerExceptionHandler(), new HttpMessageNotReadableExceptionHandler(),
                    new MethodArgumentNotValidExceptionHandler(), new MissingRequestHeaderExceptionHandler(),
                    new MethodArgumentTypeMismatchExceptionHandler(), new MissingServletRequestParameterExceptionHandler(),
                    new HandlerMethodValidationExceptionHandler(), new NoResourceFoundExceptionHandler(),
                    new HttpRequestMethodNotSupportedExceptionHandler(), new HttpMediaTypeNotSupportedExceptionHandler(),
                    new HttpMediaTypeNotAcceptableExceptionHandler(), new DefaultExceptionHandler())
            .build();

    @Test
    void nonNumericPageMapsToParameterInvalid() throws Exception {
        mockMvc.perform(get("/probe/paged").param("page", "abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(MethodArgumentTypeMismatchExceptionHandler.CODE))
                .andExpect(jsonPath("$.defaultMessage").isNotEmpty());
    }

    @Test
    void missingRequiredParameterMapsToParameterMissing() throws Exception {
        mockMvc.perform(get("/probe/required")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(MissingServletRequestParameterExceptionHandler.CODE));
    }

    @Test
    void constraintViolatingParameterMapsToParameterInvalid() throws Exception {
        mockMvc.perform(get("/probe/bounded").param("size", "0")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(HandlerMethodValidationExceptionHandler.CODE));
    }

    @Test
    void missingResourceMapsToNotFound() throws Exception {
        mockMvc.perform(get("/probe/missing")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(NoResourceFoundExceptionHandler.CODE));
    }

    @Test
    void unsupportedMethodMapsToMethodNotAllowedWithAllowHeader() throws Exception {
        mockMvc.perform(post("/probe/paged")).andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"))
                .andExpect(jsonPath("$.code").value(HttpRequestMethodNotSupportedExceptionHandler.CODE));
    }

    @Test
    void unsupportedContentTypeMapsToUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/probe/body").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(HttpMediaTypeNotSupportedExceptionHandler.CODE));
    }

    @Test
    void unacceptableResponseTypeMapsToNotAcceptable() throws Exception {
        mockMvc.perform(get("/probe/text").accept(MediaType.APPLICATION_JSON)).andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.code").value(HttpMediaTypeNotAcceptableExceptionHandler.CODE));
    }

    @Test
    void unexpectedExceptionStillMapsToInternalError() throws Exception {
        mockMvc.perform(get("/probe/boom")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(DefaultExceptionHandler.CODE));
    }

    @RestController
    @RequestMapping("/probe")
    static class ProbeController {

        @GetMapping("/paged")
        Map<String, Integer> paged(@RequestParam(defaultValue = "0") Integer page) {
            return Map.of("page", page);
        }

        @GetMapping("/required")
        Map<String, String> required(@RequestParam String name) {
            return Map.of("name", name);
        }

        @GetMapping("/bounded")
        Map<String, Integer> bounded(@RequestParam @Min(1) Integer size) {
            return Map.of("size", size);
        }

        @GetMapping("/missing")
        Map<String, String> missing() throws NoResourceFoundException {
            // The standalone setup has no static-resource handler, so raise what the real one throws for an
            // unmapped path (ResourceHttpRequestHandler catches /** under Spring Boot's default resource mappings).
            throw new NoResourceFoundException(HttpMethod.GET, "/probe/missing", "probe/missing");
        }

        @PostMapping(path = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
        Map<String, String> body(@RequestBody Map<String, String> body) {
            return body;
        }

        @GetMapping(path = "/text", produces = MediaType.TEXT_PLAIN_VALUE)
        String text() {
            return "ok";
        }

        @GetMapping("/boom")
        Map<String, String> boom() {
            throw new IllegalStateException("unexpected");
        }
    }
}
