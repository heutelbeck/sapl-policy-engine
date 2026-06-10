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
package io.sapl.spring.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import lombok.val;

class ForwardedHeadersTests {

    private static Function<String, List<String>> sourceOf(Map<String, List<String>> headers) {
        return name -> headers.getOrDefault(name, List.of());
    }

    private static Map<String, List<String>> headers() {
        return new HashMap<>();
    }

    @Nested
    class WhenNoForwardedHeadersPresent {

        @Test
        void emptyResultIsReturned() {
            val parsed = ForwardedHeaders.parse(sourceOf(headers()));
            assertThat(parsed.isEmpty()).isTrue();
            assertThat(parsed.forChain()).isEmpty();
            assertThat(parsed.host()).isNull();
            assertThat(parsed.proto()).isNull();
            assertThat(parsed.port()).isNull();
        }
    }

    @Nested
    class LegacyXForwardedFamily {

        @Test
        void singleClientInChainIsExposedAsListOfOne() {
            val src = headers();
            src.put("x-forwarded-for", List.of("198.51.100.1"));
            val parsed = ForwardedHeaders.parse(sourceOf(src));
            assertThat(parsed.forChain()).containsExactly("198.51.100.1");
        }

        @Test
        void commaSeparatedClientsAreSplitLeftToRight() {
            val src = headers();
            src.put("x-forwarded-for", List.of("198.51.100.1, 203.0.113.7, 10.0.0.1"));
            val parsed = ForwardedHeaders.parse(sourceOf(src));
            assertThat(parsed.forChain()).containsExactly("198.51.100.1", "203.0.113.7", "10.0.0.1");
        }

        @Test
        void hostAndProtoAndPortAreParsed() {
            val src = headers();
            src.put("x-forwarded-host", List.of("api.example.com"));
            src.put("x-forwarded-proto", List.of("HTTPS"));
            src.put("x-forwarded-port", List.of("443"));
            val parsed = ForwardedHeaders.parse(sourceOf(src));
            assertThat(parsed.host()).isEqualTo("api.example.com");
            assertThat(parsed.proto()).isEqualTo("https");
            assertThat(parsed.port()).isEqualTo(443);
        }

        @Test
        void protoIsNormalisedToLowercase() {
            val src = headers();
            src.put("x-forwarded-proto", List.of("HTTPS"));
            val parsed = ForwardedHeaders.parse(sourceOf(src));
            assertThat(parsed.proto()).isEqualTo("https");
        }

        @Test
        void malformedPortIsTreatedAsAbsent() {
            val src = headers();
            src.put("x-forwarded-port", List.of("not-a-number"));
            val parsed = ForwardedHeaders.parse(sourceOf(src));
            assertThat(parsed.port()).isNull();
        }

        @Test
        void blankHostValueIsTreatedAsAbsent() {
            val src = headers();
            src.put("x-forwarded-host", List.of("   "));
            val parsed = ForwardedHeaders.parse(sourceOf(src));
            assertThat(parsed.host()).isNull();
        }
    }

    @Nested
    class Rfc7239ForwardedHeader {

        @Test
        void forHostProtoAreParsedFromASingleSemicolonSeparatedElement() {
            val src = headers();
            src.put("forwarded", List.of("for=198.51.100.1;host=api.example.com;proto=https"));
            val parsed = ForwardedHeaders.parse(sourceOf(src));
            assertThat(parsed.forChain()).containsExactly("198.51.100.1");
            assertThat(parsed.host()).isEqualTo("api.example.com");
            assertThat(parsed.proto()).isEqualTo("https");
        }

        @Test
        void quotedValuesAreUnquoted() {
            val src = headers();
            src.put("forwarded", List.of("for=\"198.51.100.1\";host=\"api.example.com\""));
            val parsed = ForwardedHeaders.parse(sourceOf(src));
            assertThat(parsed.forChain()).containsExactly("198.51.100.1");
            assertThat(parsed.host()).isEqualTo("api.example.com");
        }

        @Test
        void commaSeparatedElementsBuildTheForChain() {
            val src = headers();
            src.put("forwarded", List.of("for=198.51.100.1, for=203.0.113.7, for=10.0.0.1"));
            val parsed = ForwardedHeaders.parse(sourceOf(src));
            assertThat(parsed.forChain()).containsExactly("198.51.100.1", "203.0.113.7", "10.0.0.1");
        }

        @Test
        void hostAndProtoTakeTheFirstNonEmptyValueAcrossElements() {
            val src = headers();
            src.put("forwarded", List.of("for=198.51.100.1;proto=https;host=first.example.com",
                    "for=203.0.113.7;proto=http;host=second.example.com"));
            val parsed = ForwardedHeaders.parse(sourceOf(src));
            assertThat(parsed.host()).isEqualTo("first.example.com");
            assertThat(parsed.proto()).isEqualTo("https");
        }

        @Test
        void ipv6ForValueRetainsBracketsAndDropsPort() {
            val src = headers();
            src.put("forwarded", List.of("for=\"[2001:db8::1]:54402\""));
            val parsed = ForwardedHeaders.parse(sourceOf(src));
            assertThat(parsed.forChain()).containsExactly("[2001:db8::1]");
        }

        @Test
        void ipv4WithPortIsStrippedOfThePort() {
            val src = headers();
            src.put("forwarded", List.of("for=198.51.100.1:54402"));
            val parsed = ForwardedHeaders.parse(sourceOf(src));
            assertThat(parsed.forChain()).containsExactly("198.51.100.1");
        }

        @Test
        void unknownParametersAreIgnored() {
            val src = headers();
            src.put("forwarded", List.of("for=198.51.100.1;by=10.0.0.1;custom=value"));
            val parsed = ForwardedHeaders.parse(sourceOf(src));
            assertThat(parsed.forChain()).containsExactly("198.51.100.1");
            assertThat(parsed.host()).isNull();
            assertThat(parsed.proto()).isNull();
        }

        @Test
        void malformedSegmentsAreSilentlySkipped() {
            val src = headers();
            src.put("forwarded", List.of("for=198.51.100.1;notakeyvalue;proto=https"));
            val parsed = ForwardedHeaders.parse(sourceOf(src));
            assertThat(parsed.forChain()).containsExactly("198.51.100.1");
            assertThat(parsed.proto()).isEqualTo("https");
        }
    }

    @Nested
    class Precedence {

        @Test
        void rfc7239TakesPrecedenceWhenItContainsAnyParameter() {
            val src = headers();
            src.put("forwarded", List.of("for=198.51.100.1"));
            src.put("x-forwarded-host", List.of("legacy-only.example.com"));
            val parsed = ForwardedHeaders.parse(sourceOf(src));
            assertThat(parsed.forChain()).containsExactly("198.51.100.1");
            assertThat(parsed.host()).isNull();
        }

        @Test
        void legacyHeadersAreUsedWhenRfc7239IsAbsent() {
            val src = headers();
            src.put("x-forwarded-for", List.of("198.51.100.1"));
            src.put("x-forwarded-host", List.of("api.example.com"));
            val parsed = ForwardedHeaders.parse(sourceOf(src));
            assertThat(parsed.forChain()).containsExactly("198.51.100.1");
            assertThat(parsed.host()).isEqualTo("api.example.com");
        }

        @Test
        void legacyHeadersAreUsedWhenRfc7239IsPresentButYieldsNothing() {
            val src = headers();
            src.put("forwarded", List.of("custom=value;notakeyvalue"));
            src.put("x-forwarded-for", List.of("198.51.100.1"));
            val parsed = ForwardedHeaders.parse(sourceOf(src));
            assertThat(parsed.forChain()).containsExactly("198.51.100.1");
        }
    }
}
