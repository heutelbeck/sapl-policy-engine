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

import io.sapl.attributeapi.attributes.BackendHandle;
import io.sapl.attributeapi.attributes.backend.AttributeBackendUnavailableException;
import io.sapl.attributeapi.attributes.dto.AttributePublishRequest;
import io.sapl.attributeapi.attributes.service.AttributeApiService;
import io.sapl.reactive.api.tenant.BlockingTenantResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * The API controller to serve the endpoints of the API and receive and answer
 * requests via HTTP. Forwards the requests to the API service layer.
 */
@Tag(name = "Attribute API", description = "REST API for managing attribute in the attribute store. Attributes can"
        + "be published, updated, deleted or queried. The attribute can be set with an optional time to live (TTL)"
        + "after which they expire automatically. The attributes are routed per tenant (pdpId) and the tenant is resolved"
        + "from the authenticated principal.")
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "io.sapl.attribute-api.enabled", havingValue = "true")
@RestController
@RequestMapping("/api/attributes")
public class AttributeApiController {
    private static final String NO_PDP_ID = "";

    private static final String DESC_ATTRIBUTE_NAME = "The fully qualified attribute name. At least two dot-separated segments, e.g. attribute.name.";
    private static final String DESC_ENTITY         = "The subject or resource the attribute belongs to.";
    private static final String DESC_ARGUMENTS      = "Optional attributes for the attribute. Repeat the parameter to pass multiple arguments, e.g. ?arg=X&arg=Y.";
    private static final String DESC_COUNT          = "If set to true, return the number of attributes for the tenant instead of an attribute list.";
    private static final String DESC_LIMIT          = "Maximum number of attributes to return. Must be a positive number greater than 0. Limited by 1000 entries per default.";
    private static final String DESC_OFFSET         = "Number of attributes to skip before returning results. Must be zero or a positive number.";
    private static final String DESC_HTTP_400       = "The request was invalid, e.g. the attribute name is not fully qualified or too many arguments were provided.";
    private static final String DESC_HTTP_503       = "The attribute store is currently unavailable. Retry should be done after the duration given in the Retry-After header.";
    private static final String DESC_HTTP_404       = "The attribute was not found in the attribute store.";

    private final AttributeApiService    service;
    private final BlockingTenantResolver tenantResolver;

    /**
     * Endpoint to publish an attribute into the store that contains an entity and attribute name. The arguments
     * are part of the request body because arguments can vary.
     *
     * @param entity The name of the entity.
     * @param name The name of the attribute.
     * @param request The request body that was sent. The body contains the value and the TTL.
     * @return {@code HTTP 201} if the attribute didn't exist before and {@code HTTP 200} if the attribute was updated.
     * See also RFC 9110 Section 9.3.
     */
    @Operation(summary = "Publish an attribute with an entity, attribute name and optional arguments", responses = {
            @ApiResponse(responseCode = "200", description = "The attribute already existed and was updated."),
            @ApiResponse(responseCode = "201", description = "The attribute did not exist and was created."),
            @ApiResponse(responseCode = "400", description = DESC_HTTP_400),
            @ApiResponse(responseCode = "503", description = DESC_HTTP_503) })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The attribute value to publish with an optional time to live (TTL)")
    @PutMapping("/{entity}/{name}")
    public ResponseEntity<Void> publish(@Parameter(description = DESC_ENTITY) @PathVariable String entity,
            @Parameter(description = DESC_ATTRIBUTE_NAME) @PathVariable String name,
            @Parameter(description = DESC_ARGUMENTS) @RequestParam(value = "arg", required = false) List<String> args,
            @RequestBody AttributePublishRequest request) {
        boolean created = service.publish(entity, name, args, request, currentPdpId());

        return created ? ResponseEntity.created(URI.create("/api/attributes/" + entity + "/" + name)).build()
                : ResponseEntity.ok().build();
    }

    /**
     * Endpoint to publish an attribute into the store that contains no entity but the mandatory attribute name.
     * The arguments are part of the request body because arguments can vary.
     *
     * @param name The name of the attribute.
     * @param request The request body that was sent. The body contains the value and the TTL.
     * @return {@code HTTP 201} if the attribute didn't exist before and {@code HTTP 200} if the attribute was updated.
     * See also RFC 9110 Section 9.3.
     */
    @Operation(summary = "Publish an attribute with an attribute name and optional arguments", responses = {
            @ApiResponse(responseCode = "200", description = "The attribute already existed and was updated."),
            @ApiResponse(responseCode = "201", description = "The attribute did not exist and was created."),
            @ApiResponse(responseCode = "400", description = DESC_HTTP_400),
            @ApiResponse(responseCode = "503", description = DESC_HTTP_503) })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The attribute value to publish with an optional time to live (TTL)")
    @PutMapping("/{name}")
    public ResponseEntity<Void> publishGlobalAttribute(
            @Parameter(description = DESC_ATTRIBUTE_NAME) @PathVariable String name,
            @Parameter(description = DESC_ARGUMENTS) @RequestParam(value = "arg", required = false) List<String> args,
            @RequestBody AttributePublishRequest request) {
        boolean created = service.publish(null, name, args, request, currentPdpId());

        return created ? ResponseEntity.created(URI.create("/api/attributes/" + name)).build()
                : ResponseEntity.ok().build();
    }

    /**
     * Endpoint to delete an attribute from the attribute store. Regarding to {@code RFC 7231 Section 4.3.5}
     * a payload within a Delete has no defined semantics. Depending on the implementation a delete request
     * with a body may be rejected or ignored. The recommend way is to sent it as URL parameter.
     *
     * @param entity The name of the entity.
     * @param name The attribute name.
     * @param args The arguments of the attribute
     * @return {@code HTTP 204} - no content if the resource was deleted.
     */
    @Operation(summary = "Delete an attribute with an entity, attribute name and optional arguments", responses = {
            @ApiResponse(responseCode = "204", description = "The attribute was deleted"),
            @ApiResponse(responseCode = "400", description = DESC_HTTP_400),
            @ApiResponse(responseCode = "404", description = DESC_HTTP_404),
            @ApiResponse(responseCode = "503", description = DESC_HTTP_503) })
    @DeleteMapping("/{entity}/{name}")
    public ResponseEntity<Void> deleteAttribute(@Parameter(description = DESC_ENTITY) @PathVariable String entity,
            @Parameter(description = DESC_ATTRIBUTE_NAME) @PathVariable String name,
            @Parameter(description = DESC_ARGUMENTS) @RequestParam(value = "arg", required = false) List<String> args) {
        service.delete(entity, name, args, currentPdpId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Endpoint to delete an attribute from the attribute store. Regarding to {@code RFC 7231 Section 4.3.5}
     * a payload within a Delete has no defined semantics. Depending on the implementation a delete request
     * with a body may be rejected or ignored. The recommend way is to sent it as URL parameter.
     *
     * @param name The attribute name.
     * @param args The arguments of the attribute
     * @return {@code HTTP 204} - no content if the resource was deleted.
     */
    @Operation(summary = "Delete an attribute with an entity, attribute name and optional arguments", responses = {
            @ApiResponse(responseCode = "204", description = "The attribute was deleted"),
            @ApiResponse(responseCode = "400", description = DESC_HTTP_400),
            @ApiResponse(responseCode = "404", description = DESC_HTTP_404),
            @ApiResponse(responseCode = "503", description = DESC_HTTP_503) })
    @DeleteMapping("/{name}")
    public ResponseEntity<Void> deleteGlobalAttribute(
            @Parameter(description = DESC_ATTRIBUTE_NAME) @PathVariable String name,
            @Parameter(description = DESC_ARGUMENTS) @RequestParam(value = "arg", required = false) List<String> args) {
        service.delete(null, name, args, currentPdpId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Get an attribute from the attribute store.
     *
     * @param entity The name of the entity.
     * @param name The name of the attribute.
     * @param args The arguments of the attribute.
     * @return {@code HTTP 200} and the value of the attribute.
     */
    @Operation(summary = "Get a single attribute with an entity, attribute name and optional arguments", responses = {
            @ApiResponse(responseCode = "200", description = "The attribute did exist in the attribute store.", content = @Content(schema = @Schema(type = "string", example = "\"IT\""))),
            @ApiResponse(responseCode = "400", description = DESC_HTTP_400),
            @ApiResponse(responseCode = "404", description = DESC_HTTP_404),
            @ApiResponse(responseCode = "503", description = DESC_HTTP_503) })
    @GetMapping("/{entity}/{name}")
    public ResponseEntity<JsonNode> getAttribute(@Parameter(description = DESC_ENTITY) @PathVariable String entity,
            @Parameter(description = DESC_ATTRIBUTE_NAME) @PathVariable String name,
            @Parameter(description = DESC_ARGUMENTS) @RequestParam(value = "arg", required = false) List<String> args) {
        return ResponseEntity.ok(service.get(entity, name, args, currentPdpId()));
    }

    /**
     * Get an attribute from the attribute store.
     *
     * @param name The name of the attribute.
     * @param args The arguments of the attribute.
     * @return {@code HTTP 200} and the value of the attribute.
     */
    @Operation(summary = "Get a single attribute with an attribute name and optional arguments", responses = {
            @ApiResponse(responseCode = "200", description = "The attribute did exist in the attribute store.", content = @Content(schema = @Schema(type = "string", example = "\"IT\""))),
            @ApiResponse(responseCode = "400", description = DESC_HTTP_400),
            @ApiResponse(responseCode = "404", description = DESC_HTTP_404),
            @ApiResponse(responseCode = "503", description = DESC_HTTP_503) })
    @GetMapping("/{name}")
    public ResponseEntity<JsonNode> getGlobalAttribute(
            @Parameter(description = DESC_ATTRIBUTE_NAME) @PathVariable String name,
            @Parameter(description = DESC_ARGUMENTS) @RequestParam(value = "arg", required = false) List<String> args) {
        return ResponseEntity.ok(service.get(null, name, args, currentPdpId()));
    }

    /**
     * Get all attributes for a given pdp id from the attribute store. Supports limit and offset
     * to split requests and control the amount of output. Attribute will be in the order they
     * were inserted into the attribute store.
     *
     * @param limit The number of attributes that are put into the response.
     * @param offset The position to start within the list. Used together with limit.
     * @param count Instead of sending a list of attributes just return the amount of attributes found.
     * @return {@code HTTP 200}. Without the count parameter a list of all attributes is returned that
     * contains for each entry the {@code AttributeKey} and the {@Value} of an attribute. With the count
     * parameter the amount of attributes will be returned.
     */
    @GetMapping
    @Operation(summary = "Get all attributes from the attribute store as JSON. The response contains the attribute key and value for each entry", responses = {
            @ApiResponse(responseCode = "200", description = "The list of attributes, or the total count if the count parameter is set to true.", content = @Content(examples = @ExampleObject(value = "[{\"entity\":\"faythe\",\"name\":\"company.department\",\"arguments\":[\"Intern\"],\"value\":\"IT\"}]"))),
            @ApiResponse(responseCode = "400", description = DESC_HTTP_400),
            @ApiResponse(responseCode = "503", description = DESC_HTTP_503) })
    public ResponseEntity<Object> getAllAttributesFromPdp(
            @Parameter(description = DESC_LIMIT) @RequestParam(required = false) Integer limit,
            @Parameter(description = DESC_OFFSET) @RequestParam(required = false) Integer offset,
            @Parameter(description = DESC_COUNT) @RequestParam(required = false, defaultValue = "false") boolean count) {

        if (count) {
            return ResponseEntity.ok(service.count(currentPdpId()));
        }
        return ResponseEntity.ok(service.getAll(currentPdpId(), limit, offset));
    }

    /**
     * Returns an exception if the given request was invalid
     *
     * @param e
     * @return {@code HTTP 400} - bad request with the exception message.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalidArgument(IllegalArgumentException e) {
        log.warn(e.getMessage());
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    /**
     * Return a {@code HTTP 404} - not found, if the given resource was not found in the store.
     *
     * @param e
     * @return {@code HTTP 404} - not found.
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Void> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.notFound().build();
    }

    // Resolves the pdpId of the authenticated principal. Falls back to
    // NO_PDP_ID (which AttributeApiService treats the same as null) when
    // no AttributeApiUserDetails is present, e.g. in no-auth mode.
    private String currentPdpId() {
        var pdpId = tenantResolver.resolve();
        return pdpId != null ? pdpId : NO_PDP_ID;
    }

    /**
     * Exception thrown when the backend storage isn't available. Only show a generic message to avoid
     * leaking internal information. Sends a {@code Retry-After:} header.
     * Also see {@link https://datatracker.ietf.org/doc/html/rfc9110#name-503-service-unavailable}
     *
     * @param e The exception message
     * @return {@code HTTP 503} - service unavailable. The current backend storage couldn't be reached.
     */
    @ExceptionHandler(AttributeBackendUnavailableException.class)
    public ResponseEntity<String> handleBackendUnavailable(AttributeBackendUnavailableException e) {
        log.warn("The attribute backend is unavailable", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(BackendHandle.RETRY_COOLDOWN.toSeconds()))
                .body(e.getMessage());
    }

    /**
     * Exception thrown when the backend isn't available but the it's not marked as service down.
     * Only show a generic message to avoid leaking internal information.
     *
     * @param e
     * @return
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred.");
    }
}
