/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.attributes.broker.impl;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Clock;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.attributes.pips.time.TimePolicyInformationPoint;
import io.sapl.validation.ValidatorFactory;

class AnnotationPolicyInformationPointLoaderTests {

    @Test
    void loadTest() {
        final var validatorFactory = new ValidatorFactory(new ObjectMapper());
        final var loader           = new AnnotationPolicyInformationPointLoader(null, validatorFactory);
        final var clock            = Clock.systemDefaultZone();
        assertThatCode(() -> {
            loader.loadPolicyInformationPoint(new TimePolicyInformationPoint(clock));
            loader.loadStaticPolicyInformationPoint(TestPolicyInformationPoint.class);
        }).doesNotThrowAnyException();
    }
}
