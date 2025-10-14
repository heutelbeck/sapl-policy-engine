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
package io.sapl.functions;

import lombok.val;
import org.junit.jupiter.api.Test;

import static io.sapl.assertj.SaplAssertions.assertThatVal;
import static org.assertj.core.api.Assertions.assertThat;

class SaplFunctionLibraryTests {

    @Test
    void when_info_then_returnsObjectWithRequiredFields() {
        val result = SaplFunctionLibrary.info();

        assertThatVal(result).hasValue();
        assertThat(result.get().isObject()).isTrue();

        val infoObject = result.get();
        assertThat(infoObject.has("saplVersion")).isTrue();
        assertThat(infoObject.has("gitCommitId")).isTrue();
        assertThat(infoObject.has("gitBranch")).isTrue();
        assertThat(infoObject.has("gitBuildTime")).isTrue();
        assertThat(infoObject.has("jdkVersion")).isTrue();
        assertThat(infoObject.has("javaVersion")).isTrue();
        assertThat(infoObject.has("javaVendor")).isTrue();
        assertThat(infoObject.has("osName")).isTrue();
        assertThat(infoObject.has("osVersion")).isTrue();
        assertThat(infoObject.has("osArch")).isTrue();
    }

    @Test
    void when_info_then_jdkVersionIsNotEmpty() {
        val result     = SaplFunctionLibrary.info();
        val jdkVersion = result.get().get("jdkVersion").asText();

        assertThat(jdkVersion).isNotBlank().isNotEqualTo("unknown");
    }

    @Test
    void when_info_then_javaVersionIsNotEmpty() {
        val result      = SaplFunctionLibrary.info();
        val javaVersion = result.get().get("javaVersion").asText();

        assertThat(javaVersion).isNotBlank().isNotEqualTo("unknown");
    }

    @Test
    void when_info_then_javaVendorIsNotEmpty() {
        val result     = SaplFunctionLibrary.info();
        val javaVendor = result.get().get("javaVendor").asText();

        assertThat(javaVendor).isNotBlank().isNotEqualTo("unknown");
    }

    @Test
    void when_info_then_osNameIsNotEmpty() {
        val result = SaplFunctionLibrary.info();
        val osName = result.get().get("osName").asText();

        assertThat(osName).isNotBlank().isNotEqualTo("unknown");
    }

    @Test
    void when_info_then_osVersionIsNotEmpty() {
        val result    = SaplFunctionLibrary.info();
        val osVersion = result.get().get("osVersion").asText();

        assertThat(osVersion).isNotBlank().isNotEqualTo("unknown");
    }

    @Test
    void when_info_then_osArchIsNotEmpty() {
        val result = SaplFunctionLibrary.info();
        val osArch = result.get().get("osArch").asText();

        assertThat(osArch).isNotBlank().isNotEqualTo("unknown");
    }

    @Test
    void when_info_then_versionInfoMayBeUnknownIfPropertiesNotAvailable() {
        val result  = SaplFunctionLibrary.info();
        val version = result.get().get("saplVersion").asText();

        assertThat(version).isNotNull();
    }

    @Test
    void when_info_then_gitInfoMayBeUnknownIfPropertiesNotAvailable() {
        val result      = SaplFunctionLibrary.info();
        val gitCommitId = result.get().get("gitCommitId").asText();

        assertThat(gitCommitId).isNotNull();
    }

    @Test
    void when_infoCalledMultipleTimes_then_returnsConsistentResults() {
        val result1 = SaplFunctionLibrary.info();
        val result2 = SaplFunctionLibrary.info();

        assertThat(result1.get().get("javaVersion").asText()).isEqualTo(result2.get().get("javaVersion").asText());
        assertThat(result1.get().get("osName").asText()).isEqualTo(result2.get().get("osName").asText());
    }
}
