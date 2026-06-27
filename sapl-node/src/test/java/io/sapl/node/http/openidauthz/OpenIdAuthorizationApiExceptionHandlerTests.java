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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;

import io.sapl.node.http.RequestBodyTooLargeException;
import lombok.val;

@DisplayName("OpenID Authorization API exception handling")
class OpenIdAuthorizationApiExceptionHandlerTests {

    @Test
    @DisplayName("read-time request body limit failures return 413")
    void whenRequestBodyLimitIsExceededDuringReadThenContentTooLarge() {
        val overflow = new RequestBodyTooLargeException("Request body exceeds the configured limit of 64 bytes.");
        val error    = new HttpMessageNotReadableException("JSON body could not be read", overflow, null);

        val response = new OpenIdAuthorizationApiExceptionHandler().unreadableRequestBody(error);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
    }

    @Test
    @DisplayName("malformed JSON remains a bad request")
    void whenRequestBodyIsMalformedJsonThenBadRequest() {
        val error = new HttpMessageNotReadableException("JSON body could not be parsed", null);

        val response = new OpenIdAuthorizationApiExceptionHandler().unreadableRequestBody(error);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
