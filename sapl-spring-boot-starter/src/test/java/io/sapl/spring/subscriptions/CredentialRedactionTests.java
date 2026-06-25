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
package io.sapl.spring.subscriptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import lombok.val;
import tools.jackson.databind.json.JsonMapper;

class CredentialRedactionTests {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    @DisplayName("credential-carrying HTTP headers (Authorization, Cookie) are redacted at any depth")
    void redactsAuthorizationAndCookieHeaders() {
        val node     = MAPPER.readTree("""
                {"headers": {"Authorization": "Bearer secret-bearer-token",
                             "Cookie": "SESSION=secret-session-id",
                             "Accept": "application/json"}}
                """);
        val redacted = CredentialRedaction.redact(node).toString();

        assertThat(redacted).doesNotContain("secret-bearer-token").doesNotContain("secret-session-id")
                .contains("application/json");
    }

    @Test
    @DisplayName("known credential fields stay redacted at any depth; benign fields are kept")
    void redactsKnownCredentialFields() {
        val node     = MAPPER.readTree("""
                {"username": "alice", "password": "secret-pw-value",
                 "principal": {"idToken": {"tokenValue": "secret-token-value"}}}
                """);
        val redacted = CredentialRedaction.redact(node).toString();

        assertThat(redacted).contains("alice").doesNotContain("secret-pw-value").doesNotContain("secret-token-value");
    }

    @Test
    @DisplayName("session tokens in the parsed cookies[] array (value field) are redacted while cookie names are kept")
    void redactsParsedCookieValues() {
        val node     = MAPPER.readTree("""
                {"cookies": [{"name": "SESSION", "value": "secret-session-id"},
                             {"name": "theme", "value": "benign-but-still-stripped"}]}
                """);
        val redacted = CredentialRedaction.redact(node).toString();

        assertThat(redacted).doesNotContain("secret-session-id").doesNotContain("benign-but-still-stripped")
                .contains("SESSION").contains("theme");
    }

    @Test
    @DisplayName("credential field names match separator-insensitively (access-token, id_token, client_secret)")
    void redactsSeparatorVariantsOfCredentialNames() {
        val node     = MAPPER.readTree("""
                {"a": {"access-token": "secret-a"}, "b": {"id_token": "secret-b"},
                 "c": {"client_secret": "secret-c"}}
                """);
        val redacted = CredentialRedaction.redact(node).toString();

        assertThat(redacted).doesNotContain("secret-a").doesNotContain("secret-b").doesNotContain("secret-c");
    }

    @Test
    @DisplayName("an OAuth access_token in the parsed query parameters and the raw query string is redacted")
    void redactsAccessTokenInQueryStringAndParameters() {
        val node     = MAPPER.readTree("""
                {"http": {"query": "access_token=ey-secret-token&page=2",
                          "queryParameters": {"access_token": ["ey-secret-token"], "page": ["2"]}}}
                """);
        val redacted = CredentialRedaction.redact(node).toString();

        assertThat(redacted).doesNotContain("ey-secret-token").doesNotContain("access_token").contains("page");
    }

    @Test
    @DisplayName("a query string carrying no credential is left untouched (verbatim)")
    void keepsBenignQueryStringVerbatim() {
        val node     = MAPPER.readTree("""
                {"http": {"query": "q=foo+bar&page=2", "queryParameters": {"q": ["foo bar"], "page": ["2"]}}}
                """);
        val redacted = CredentialRedaction.redact(node);

        assertThat(redacted.get("http").get("query").asString()).isEqualTo("q=foo+bar&page=2");
    }

    @Test
    @DisplayName("a malformed percent-escape in a query parameter name does not throw out of redaction")
    void malformedPercentEscapeInQueryNameDoesNotThrow() {
        val node = MAPPER.readTree("""
                {"http": {"query": "%zz=1&page=2", "queryParameters": {"%zz": ["1"], "page": ["2"]}}}
                """);

        assertThatCode(() -> CredentialRedaction.redact(node)).doesNotThrowAnyException();
        assertThat(node.get("http").get("query").asString()).isEqualTo("%zz=1&page=2");
    }
}
