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
package io.sapl.functions.libraries;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;

@DisplayName("SaplFunctionLibrary")
class SaplFunctionLibraryTests {

    @Test
    void whenLoadedIntoBrokerThenNoError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.loadStaticFunctionLibrary(SaplFunctionLibrary.class))
                .doesNotThrowAnyException();
    }

    @Test
    void whenInfoThenReturnsObjectWithRequiredFields() {
        val result = SaplFunctionLibrary.info();

        assertThat(result).isInstanceOf(ObjectValue.class);

        ObjectValue infoObject = (ObjectValue) result;
        assertThat(infoObject).containsKey("saplVersion").containsKey("gitCommitId").containsKey("gitBranch")
                .containsKey("gitBuildTime").containsKey("jdkVersion").containsKey("javaVersion")
                .containsKey("javaVendor").containsKey("osName").containsKey("osVersion").containsKey("osArch");
    }

    @Test
    void whenInfoThenJdkVersionIsNotEmpty() {
        val result = SaplFunctionLibrary.info();

        assertThat(result).isInstanceOf(ObjectValue.class);
        ObjectValue infoObject = (ObjectValue) result;

        val jdkVersion = infoObject.get("jdkVersion");
        assertThat(jdkVersion).isInstanceOf(TextValue.class).isNotNull().isNotEqualTo("").isNotEqualTo("unknown");
    }

    @Test
    void whenInfoThenJavaVersionIsNotEmpty() {
        val result = SaplFunctionLibrary.info();

        assertThat(result).isInstanceOf(ObjectValue.class);
        ObjectValue infoObject = (ObjectValue) result;

        val javaVersion = infoObject.get("javaVersion");
        assertThat(javaVersion).isInstanceOf(TextValue.class).isNotNull().isNotEqualTo("").isNotEqualTo("unknown");
    }

    @Test
    void whenInfoThenJavaVendorIsNotEmpty() {
        val result = SaplFunctionLibrary.info();

        assertThat(result).isInstanceOf(ObjectValue.class);
        ObjectValue infoObject = (ObjectValue) result;

        val javaVendor = infoObject.get("javaVendor");
        assertThat(javaVendor).isInstanceOf(TextValue.class).isNotNull().isNotEqualTo("").isNotEqualTo("unknown");
    }

    @Test
    void whenInfoThenOsNameIsNotEmpty() {
        val result = SaplFunctionLibrary.info();

        assertThat(result).isInstanceOf(ObjectValue.class);
        ObjectValue infoObject = (ObjectValue) result;

        val osName = infoObject.get("osName");
        assertThat(osName).isInstanceOf(TextValue.class).isNotNull().isNotEqualTo("").isNotEqualTo("unknown");
    }

    @Test
    void whenInfoThenOsVersionIsNotEmpty() {
        val result = SaplFunctionLibrary.info();

        assertThat(result).isInstanceOf(ObjectValue.class);
        ObjectValue infoObject = (ObjectValue) result;

        val osVersion = infoObject.get("osVersion");
        assertThat(osVersion).isInstanceOf(TextValue.class).isNotNull().isNotEqualTo("").isNotEqualTo("unknown");
    }

    @Test
    void whenInfoThenOsArchIsNotEmpty() {
        val result = SaplFunctionLibrary.info();

        assertThat(result).isInstanceOf(ObjectValue.class);
        ObjectValue infoObject = (ObjectValue) result;

        val osArch = infoObject.get("osArch");
        assertThat(osArch).isInstanceOf(TextValue.class).isNotNull().isNotEqualTo("").isNotEqualTo("unknown");
    }

    @Test
    void whenInfoThenVersionInfoMayBeUnknownIfPropertiesNotAvailable() {
        val result = SaplFunctionLibrary.info();

        assertThat(result).isInstanceOf(ObjectValue.class);
        ObjectValue infoObject = (ObjectValue) result;

        val saplArch = infoObject.get("saplVersion");
        assertThat(saplArch).isInstanceOf(TextValue.class).isNotNull().isNotEqualTo("").isNotEqualTo("unknown");
    }

    @Test
    void whenInfoThenGitInfoMayBeUnknownIfPropertiesNotAvailable() {
        val result = SaplFunctionLibrary.info();

        assertThat(result).isInstanceOf(ObjectValue.class);
        ObjectValue infoObject = (ObjectValue) result;

        val gitCommitId = infoObject.get("gitCommitId");
        assertThat(gitCommitId).isInstanceOf(TextValue.class).isNotNull().isNotEqualTo("").isNotEqualTo("unknown");

    }

    @Test
    void whenInfoCalledMultipleTimesThenReturnsConsistentResults() {
        val result1 = SaplFunctionLibrary.info();
        val result2 = SaplFunctionLibrary.info();

        assertThat(result1).isInstanceOf(ObjectValue.class);
        assertThat(result2).isInstanceOf(ObjectValue.class);
        ObjectValue infoObject1 = (ObjectValue) result1;
        ObjectValue infoObject2 = (ObjectValue) result2;

        assertThat(infoObject1.get("javaVersion")).isNotNull().isInstanceOf(TextValue.class).isNotEqualTo(Value.of(""))
                .isNotEqualTo(Value.of("unknown"));
        assertThat(infoObject2.get("javaVersion")).isNotNull().isInstanceOf(TextValue.class).isNotEqualTo(Value.of(""))
                .isNotEqualTo(Value.of("unknown"));
        val javaVersion1 = (TextValue) infoObject1.get("javaVersion");
        val javaVersion2 = (TextValue) infoObject2.get("javaVersion");
        assertThat(infoObject1.get("osName")).isNotNull().isInstanceOf(TextValue.class).isNotEqualTo(Value.of(""))
                .isNotEqualTo(Value.of("unknown"));
        assertThat(infoObject2.get("osName")).isNotNull().isInstanceOf(TextValue.class).isNotEqualTo(Value.of(""))
                .isNotEqualTo(Value.of("unknown"));
        val osName1 = (TextValue) infoObject1.get("osName");
        val osName2 = (TextValue) infoObject2.get("osName");

        assertThat(javaVersion1).isEqualTo(javaVersion2);
        assertThat(osName1).isEqualTo(osName2);
    }
}
