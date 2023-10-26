/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class SaplTestsFixtureTemplateTestsImplTest {

    private SaplTestsFixtureTemplateTestsImpl sut;

    private ObjectMapper mapper;

    @BeforeEach
    void setup() {
        this.sut    = new SaplTestsFixtureTemplateTestsImpl();
        this.mapper = new ObjectMapper();
    }

    @Test
    void test() {
        assertThat(this.sut.resolveCoverageBaseDir()).isEqualTo(Paths.get("target", "sapl-coverage"));
    }

    @Test
    void test_withJavaProperty() {
        System.setProperty("io.sapl.test.outputDir", "test-target");
        assertThat(this.sut.resolveCoverageBaseDir()).isEqualTo(Paths.get("test-target", "sapl-coverage"));
        System.clearProperty("io.sapl.test.outputDir");
    }

    @Test
    void test_registerVariable() {
        this.sut.registerVariable("test", this.mapper.createObjectNode());
        assertThat(this.sut.getVariablesMap()).containsKey("test");
    }

    @Test
    void test_registerVariable_twoTimes() {
        this.sut.registerVariable("test", this.mapper.createObjectNode());
        var objectNode = this.mapper.createObjectNode();
        assertThatExceptionOfType(SaplTestException.class)
                .isThrownBy(() -> this.sut.registerVariable("test", objectNode))
                .withMessage("The VariableContext already contains a key \"test\"");
    }

}
