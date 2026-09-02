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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import io.sapl.attributeapi.attributes.service.AttributeApiService;

@ExtendWith(MockitoExtension.class)
class AttributeApiControllerTests {

    @Mock
    private AttributeApiService service;

    // TODO: build the controller under test once the fixture above is wired up,
    // e.g.: var controller = new AttributeApiController(service);

    @BeforeEach
    void setUp() {
        // TODO: SecurityContextHolder is a static/ThreadLocal-backed singleton.
        // Clear it here so tests don't leak an Authentication into each other,
        // and set a specific one per test (see the currentPdpId() tests below).
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("An IllegalArgumentException is translated into HTTP 400 with the exception message as body")
    void whenIllegalArgumentExceptionThenReturnsBadRequestWithMessage() {
        // TODO: call controller.handleInvalidArgument(new IllegalArgumentException("bad input"))
        // directly (it's just a public method) and assert status == 400 BAD_REQUEST
        // and body equals "bad input".
    }

    @Test
    @DisplayName("A NoSuchElementException is translated into HTTP 404 with an empty body")
    void whenNoSuchElementExceptionThenReturnsNotFoundWithoutBody() {
        // TODO: call controller.handleNotFound(new NoSuchElementException()) and
        // assert status == 404 NOT_FOUND and body == null.
    }

    @Test
    @DisplayName("An AttributeBackendUnavailableException is translated into HTTP 503 with the exception message as body")
    void whenAttributeBackendUnavailableExceptionThenReturnsServiceUnavailableWithMessage() {
        // TODO: call controller.handleBackendUnavailable(new AttributeBackendUnavailableException("...")) and
        // assert status == 503 SERVICE_UNAVAILABLE and body equals the exception's message.
        // (Note: RoutingAttributeStore already sanitizes this message upstream to a
        // generic constant, so the controller re-using e.getMessage() here is fine.)
    }

    @Test
    @DisplayName("Any other unexpected exception is translated into HTTP 500 with a generic body, never the raw exception message")
    void whenUnexpectedExceptionThenReturnsInternalServerErrorWithGenericMessage() {
        // TODO: call controller.handleUnexpected(new RuntimeException("some internal
        // driver detail, hostnames, stack info...")) and assert status == 500
        // INTERNAL_SERVER_ERROR and body equals the fixed generic message
        // ("An unexpected error occurred.") - explicitly assert the body does NOT
        // contain the original exception's message, this is the
        // information-disclosure guarantee for the catch-all handler.
    }

    @Test
    @DisplayName("Without an authenticated AttributeApiUserDetails principal the pdpId resolves to the empty NO_PDP_ID fallback")
    void whenNoAuthenticationPresentThenCurrentPdpIdResolvesToEmptyString() {
        // TODO: leave SecurityContextHolder empty/cleared (no-auth mode). Call an
        // endpoint method that surfaces currentPdpId(), e.g.
        // controller.getAllAttributesFromPdp(null, null, false), and verify
        // service.getAll(eq(""), any(), any()) was called - i.e. NO_PDP_ID ("").
    }

    @Test
    @DisplayName("With an authenticated AttributeApiUserDetails principal the pdpId resolves to the principal's pdpId")
    void whenAuthenticatedWithAttributeApiUserDetailsThenCurrentPdpIdResolvesToPrincipalsPdpId() {
        // TODO: put a SecurityContext into SecurityContextHolder whose
        // Authentication#getPrincipal() is an AttributeApiUserDetails with
        // pdpId "tenant-01" (e.g. via a TestingAuthenticationToken). Call
        // controller.getAllAttributesFromPdp(...) and verify
        // service.getAll(eq("tenant-01"), any(), any()) was called.
    }

    @Test
    @DisplayName("With an authenticated principal whose pdpId is null the pdpId still falls back to NO_PDP_ID")
    void whenAuthenticatedButPdpIdIsNullThenCurrentPdpIdFallsBackToEmptyString() {
        // TODO: same as above but the AttributeApiUserDetails has pdpId == null.
        // Verify service.getAll(eq(""), any(), any()) was called, not a literal
        // "null" string.
    }

    @Test
    @DisplayName("A publish that creates a new attribute returns HTTP 201 with a Location header")
    void whenPublishCreatesNewAttributeThenReturnsCreatedWithLocationHeader() {
        // TODO: stub service.publish(...) to return true (created), call
        // controller.publish("alice", "sapl.test", null, request) and assert
        // status == 201 CREATED and the Location header points at
        // /api/attributes/alice/sapl.test.
    }

    @Test
    @DisplayName("A publish that overwrites an existing attribute returns HTTP 200 without a Location header")
    void whenPublishOverwritesExistingAttributeThenReturnsOkWithoutLocationHeader() {
        // TODO: stub service.publish(...) to return false (overwritten), call
        // controller.publish(...) and assert status == 200 OK and no Location
        // header is set.
    }
}
