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
package io.sapl.node.http.openidauthz;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.sapl.node.http.RequestBodyTooLargeException;

@RestControllerAdvice(assignableTypes = OpenIdAuthorizationApiController.class)
class OpenIdAuthorizationApiExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Void> unreadableRequestBody(HttpMessageNotReadableException error) {
        if (RequestBodyTooLargeException.isCausedBy(error)) {
            return contentTooLarge();
        }
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(RequestBodyTooLargeException.class)
    ResponseEntity<Void> requestBodyTooLarge() {
        return contentTooLarge();
    }

    private static ResponseEntity<Void> contentTooLarge() {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).build();
    }
}
