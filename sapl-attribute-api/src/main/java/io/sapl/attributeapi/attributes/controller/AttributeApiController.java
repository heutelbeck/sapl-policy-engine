/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.attributeapi.attributes.controller;

import io.sapl.attributeapi.attributes.dto.AttributePublishRequest;
import io.sapl.attributeapi.attributes.service.AttributeApiService;
import io.sapl.attributeapi.auth.AttributeApiUserDetails;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "io.sapl.attribute-api.enabled", havingValue = "true")
@RestController
@RequestMapping("/api/attributes")

public class AttributeApiController {
    private static final String NO_PDP_ID = "";

    private final AttributeApiService service;

    @PutMapping("/{entity}/{name}")
    public ResponseEntity<Void> publish(@PathVariable String entity, @PathVariable String name,
            @RequestBody AttributePublishRequest request) {
        boolean created = service.publish(entity, name, request, currentPdpId());

        return created ? ResponseEntity.created(URI.create("/api/attributes/" + entity + "/" + name)).build()
                : ResponseEntity.ok().build();
    }

    @PutMapping("/{name}")
    public ResponseEntity<Void> publishGlobalAttribute(@PathVariable String name,
            @RequestBody AttributePublishRequest request) {
        boolean created = service.publish(null, name, request, currentPdpId());

        return created ? ResponseEntity.created(URI.create("/api/attributes/" + name)).build()
                : ResponseEntity.ok().build();
    }

    // RFC 7231, Section 4.3.5: A payload within a DELETE request message has no
    // defined semantics;
    // sending a payload body on a DELETE request might cause some existing
    // implementations to reject the request
    // Some clients may ignore in Delete-Request the body, so it's an URL parameter
    @DeleteMapping("/{entity}/{name}")
    public ResponseEntity<Void> deleteAttribute(@PathVariable String entity, @PathVariable String name,
            @RequestParam(value = "arg", required = false) List<String> args) {
        service.delete(entity, name, args, currentPdpId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> deleteGlobalAttribute(@PathVariable String name,
            @RequestParam(value = "arg", required = false) List<String> args) {
        service.delete(null, name, args, currentPdpId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{entity}/{name}")
    public ResponseEntity<JsonNode> getAttribute(@PathVariable String entity, @PathVariable String name,
            @RequestParam(value = "arg", required = false) List<String> args) {
        return ResponseEntity.ok(service.get(entity, name, args, currentPdpId()));
    }

    @GetMapping("/{name}")
    public ResponseEntity<JsonNode> getGlobalAttribute(@PathVariable String name,
            @RequestParam(value = "arg", required = false) List<String> args) {
        return ResponseEntity.ok(service.get(null, name, args, currentPdpId()));
    }

    @GetMapping
    public ResponseEntity<?> getAllAttributesFromPdp(@RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false, defaultValue = "false") boolean count) {

        if (count) {
            return ResponseEntity.ok(service.count(currentPdpId()));
        }
        return ResponseEntity.ok(service.getAll(currentPdpId(), limit, offset));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalidArgument(IllegalArgumentException e) {
        log.warn(e.getMessage());
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Void> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.notFound().build();
    }

    // Resolves the pdpId of the authenticated principal. Falls back to
    // NO_PDP_ID (which AttributeApiService treats the same as null) when
    // no AttributeApiUserDetails is present, e.g. in no-auth mode.
    private String currentPdpId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AttributeApiUserDetails principal)) {
            return NO_PDP_ID;
        }
        var pdpId = principal.getPdpId();
        return pdpId != null ? pdpId : NO_PDP_ID;
    }
}
