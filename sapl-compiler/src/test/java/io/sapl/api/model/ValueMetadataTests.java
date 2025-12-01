/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.api.model;

import io.sapl.api.pdp.internal.AttributeRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ValueMetadataTests {

    static final AttributeRecord SUBJECT_ROLE = new AttributeRecord("subject.role", Value.UNDEFINED, List.of(),
            Value.of("admin"), Instant.now(), null);

    static final AttributeRecord RESOURCE_OWNER = new AttributeRecord("resource.owner", Value.UNDEFINED, List.of(),
            Value.of("alice"), Instant.now(), null);

    static final AttributeRecord ENVIRONMENT_TIME = new AttributeRecord("environment.time", Value.UNDEFINED, List.of(),
            Value.of("2025-01-01"), Instant.now(), null);

    @Test
    void emptySingletonIsNotSecret() {
        assertThat(ValueMetadata.EMPTY.secret()).isFalse();
        assertThat(ValueMetadata.EMPTY.attributeTrace()).isEmpty();
    }

    @Test
    void secretEmptySingletonIsSecret() {
        assertThat(ValueMetadata.SECRET_EMPTY.secret()).isTrue();
        assertThat(ValueMetadata.SECRET_EMPTY.attributeTrace()).isEmpty();
    }

    @Test
    void mergeEmptyWithEmptyReturnsEmpty() {
        assertThat(ValueMetadata.EMPTY.merge(ValueMetadata.EMPTY)).isSameAs(ValueMetadata.EMPTY);
    }

    @Test
    void mergeEmptyWithNonSecretReturnsOther() {
        var other = ValueMetadata.ofAttribute(SUBJECT_ROLE);
        assertThat(ValueMetadata.EMPTY.merge(other)).isSameAs(other);
    }

    @Test
    void mergeEmptyWithSecretReturnsOther() {
        var secretOther = ValueMetadata.ofSecretAttribute(SUBJECT_ROLE);
        assertThat(ValueMetadata.EMPTY.merge(secretOther)).isSameAs(secretOther);
    }

    @Test
    void mergeNonSecretWithEmptyReturnsThis() {
        var metadata = ValueMetadata.ofAttribute(SUBJECT_ROLE);
        assertThat(metadata.merge(ValueMetadata.EMPTY)).isSameAs(metadata);
    }

    @Test
    void mergeSecretWithEmptyReturnsThis() {
        var secretMetadata = ValueMetadata.ofSecretAttribute(SUBJECT_ROLE);
        assertThat(secretMetadata.merge(ValueMetadata.EMPTY)).isSameAs(secretMetadata);
    }

    @Test
    void mergeSecretEmptyWithNonSecretEmptyReturnsSecretEmpty() {
        assertThat(ValueMetadata.SECRET_EMPTY.merge(ValueMetadata.EMPTY)).isSameAs(ValueMetadata.SECRET_EMPTY);
    }

    @Test
    void mergeNonSecretEmptyWithSecretEmptyReturnsSecretEmpty() {
        assertThat(ValueMetadata.EMPTY.merge(ValueMetadata.SECRET_EMPTY)).isSameAs(ValueMetadata.SECRET_EMPTY);
    }

    @Test
    void mergeTwoNonSecretWithTracesCombinesTraces() {
        var first  = ValueMetadata.ofAttribute(SUBJECT_ROLE);
        var second = ValueMetadata.ofAttribute(RESOURCE_OWNER);
        var result = first.merge(second);

        assertThat(result.secret()).isFalse();
        assertThat(result.attributeTrace()).containsExactly(SUBJECT_ROLE, RESOURCE_OWNER);
    }

    @Test
    void mergeTwoSecretWithTracesCombinesTracesAndKeepsSecret() {
        var first  = ValueMetadata.ofSecretAttribute(SUBJECT_ROLE);
        var second = ValueMetadata.ofSecretAttribute(RESOURCE_OWNER);
        var result = first.merge(second);

        assertThat(result.secret()).isTrue();
        assertThat(result.attributeTrace()).containsExactly(SUBJECT_ROLE, RESOURCE_OWNER);
    }

    @Test
    void mergeSecretWithNonSecretResultsInSecret() {
        var secret    = ValueMetadata.ofSecretAttribute(SUBJECT_ROLE);
        var nonSecret = ValueMetadata.ofAttribute(RESOURCE_OWNER);
        var result    = secret.merge(nonSecret);

        assertThat(result.secret()).isTrue();
        assertThat(result.attributeTrace()).containsExactly(SUBJECT_ROLE, RESOURCE_OWNER);
    }

    @Test
    void mergeNonSecretWithSecretResultsInSecret() {
        var nonSecret = ValueMetadata.ofAttribute(SUBJECT_ROLE);
        var secret    = ValueMetadata.ofSecretAttribute(RESOURCE_OWNER);
        var result    = nonSecret.merge(secret);

        assertThat(result.secret()).isTrue();
        assertThat(result.attributeTrace()).containsExactly(SUBJECT_ROLE, RESOURCE_OWNER);
    }

    @Test
    void mergeDeduplicatesByReferenceIdentity() {
        var first  = ValueMetadata.ofAttribute(SUBJECT_ROLE);
        var second = new ValueMetadata(false, List.of(SUBJECT_ROLE, RESOURCE_OWNER));
        var result = first.merge(second);

        assertThat(result.attributeTrace()).containsExactly(SUBJECT_ROLE, RESOURCE_OWNER);
    }

    @Test
    void mergePreservesOrderFirstThenSecond() {
        var first  = new ValueMetadata(false, List.of(SUBJECT_ROLE, RESOURCE_OWNER));
        var second = ValueMetadata.ofAttribute(ENVIRONMENT_TIME);
        var result = first.merge(second);

        assertThat(result.attributeTrace()).containsExactly(SUBJECT_ROLE, RESOURCE_OWNER, ENVIRONMENT_TIME);
    }

    @Test
    void mergeWithEmptyTraceAndDifferentSecretReturnsNewMetadata() {
        var emptyNonSecret = new ValueMetadata(false, List.of());
        var withTrace      = ValueMetadata.ofSecretAttribute(SUBJECT_ROLE);
        var result         = emptyNonSecret.merge(withTrace);

        assertThat(result.secret()).isTrue();
        assertThat(result.attributeTrace()).containsExactly(SUBJECT_ROLE);
    }

    @Test
    void mergeWithTraceAndEmptyOtherAndDifferentSecretReturnsNewMetadata() {
        var withTrace   = ValueMetadata.ofAttribute(SUBJECT_ROLE);
        var emptySecret = ValueMetadata.SECRET_EMPTY;
        var result      = withTrace.merge(emptySecret);

        assertThat(result.secret()).isTrue();
        assertThat(result.attributeTrace()).containsExactly(SUBJECT_ROLE);
    }

    static Stream<Arguments> varargsMergeCases() {
        return Stream.of(arguments(new ValueMetadata[0], ValueMetadata.EMPTY),
                arguments(new ValueMetadata[] { ValueMetadata.EMPTY }, ValueMetadata.EMPTY),
                arguments(new ValueMetadata[] { ValueMetadata.SECRET_EMPTY }, ValueMetadata.SECRET_EMPTY),
                arguments(new ValueMetadata[] { ValueMetadata.ofAttribute(SUBJECT_ROLE) },
                        ValueMetadata.ofAttribute(SUBJECT_ROLE)));
    }

    @ParameterizedTest
    @MethodSource("varargsMergeCases")
    void mergeVarargsHandlesEdgeCases(ValueMetadata[] sources, ValueMetadata expected) {
        var result = ValueMetadata.merge(sources);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void mergeVarargsCombinesMultiple() {
        var first  = ValueMetadata.ofAttribute(SUBJECT_ROLE);
        var second = ValueMetadata.ofAttribute(RESOURCE_OWNER);
        var third  = ValueMetadata.ofSecretAttribute(ENVIRONMENT_TIME);
        var result = ValueMetadata.merge(first, second, third);

        assertThat(result.secret()).isTrue();
        assertThat(result.attributeTrace()).containsExactly(SUBJECT_ROLE, RESOURCE_OWNER, ENVIRONMENT_TIME);
    }

    @Test
    void mergeCollectionEmptyReturnsEmpty() {
        assertThat(ValueMetadata.merge(List.of())).isSameAs(ValueMetadata.EMPTY);
    }

    @Test
    void mergeCollectionSingleReturnsElement() {
        var single = ValueMetadata.ofAttribute(SUBJECT_ROLE);
        assertThat(ValueMetadata.merge(List.of(single))).isSameAs(single);
    }

    @Test
    void mergeCollectionCombinesMultiple() {
        var first  = ValueMetadata.ofAttribute(SUBJECT_ROLE);
        var second = ValueMetadata.ofSecretAttribute(RESOURCE_OWNER);
        var result = ValueMetadata.merge(List.of(first, second));

        assertThat(result.secret()).isTrue();
        assertThat(result.attributeTrace()).containsExactly(SUBJECT_ROLE, RESOURCE_OWNER);
    }

    @Test
    void asSecretOnNonSecretReturnsSecretWithSameTrace() {
        var nonSecret = ValueMetadata.ofAttribute(SUBJECT_ROLE);
        var secret    = nonSecret.asSecret();

        assertThat(secret.secret()).isTrue();
        assertThat(secret.attributeTrace()).isEqualTo(nonSecret.attributeTrace());
    }

    @Test
    void asSecretOnSecretReturnsSameInstance() {
        var secret = ValueMetadata.ofSecretAttribute(SUBJECT_ROLE);
        assertThat(secret.asSecret()).isSameAs(secret);
    }

    @Test
    void asSecretOnEmptyReturnsSecretEmpty() {
        assertThat(ValueMetadata.EMPTY.asSecret()).isSameAs(ValueMetadata.SECRET_EMPTY);
    }

    @Test
    void asSecretOnSecretEmptyReturnsSameInstance() {
        assertThat(ValueMetadata.SECRET_EMPTY.asSecret()).isSameAs(ValueMetadata.SECRET_EMPTY);
    }

    @Test
    void ofAttributeCreatesNonSecretMetadata() {
        var metadata = ValueMetadata.ofAttribute(SUBJECT_ROLE);

        assertThat(metadata.secret()).isFalse();
        assertThat(metadata.attributeTrace()).containsExactly(SUBJECT_ROLE);
    }

    @Test
    void ofSecretAttributeCreatesSecretMetadata() {
        var metadata = ValueMetadata.ofSecretAttribute(SUBJECT_ROLE);

        assertThat(metadata.secret()).isTrue();
        assertThat(metadata.attributeTrace()).containsExactly(SUBJECT_ROLE);
    }

    @Test
    void mergeEmptyTraceWithNonEmptyTraceSameSecretReturnsOther() {
        var emptyTrace = new ValueMetadata(false, List.of());
        var withTrace  = ValueMetadata.ofAttribute(SUBJECT_ROLE);
        assertThat(emptyTrace.merge(withTrace)).isSameAs(withTrace);
    }

    @Test
    void mergeNonEmptyTraceWithEmptyTraceSameSecretReturnsThis() {
        var withTrace  = ValueMetadata.ofAttribute(SUBJECT_ROLE);
        var emptyTrace = new ValueMetadata(false, List.of());
        assertThat(withTrace.merge(emptyTrace)).isSameAs(withTrace);
    }

    @Test
    void mergeEmptyTraceSecretWithNonEmptyTraceNonSecretReturnsNewSecret() {
        var emptySecret        = new ValueMetadata(true, List.of());
        var withTraceNonSecret = ValueMetadata.ofAttribute(SUBJECT_ROLE);
        var result             = emptySecret.merge(withTraceNonSecret);

        assertThat(result.secret()).isTrue();
        assertThat(result.attributeTrace()).containsExactly(SUBJECT_ROLE);
        assertThat(result).isNotSameAs(emptySecret).isNotSameAs(withTraceNonSecret);
    }

    @Test
    void mergeNonEmptyTraceNonSecretWithEmptyTraceSecretReturnsNewSecret() {
        var withTraceNonSecret = ValueMetadata.ofAttribute(SUBJECT_ROLE);
        var emptySecret        = new ValueMetadata(true, List.of());
        var result             = withTraceNonSecret.merge(emptySecret);

        assertThat(result.secret()).isTrue();
        assertThat(result.attributeTrace()).containsExactly(SUBJECT_ROLE);
        assertThat(result).isNotSameAs(withTraceNonSecret).isNotSameAs(emptySecret);
    }
}
