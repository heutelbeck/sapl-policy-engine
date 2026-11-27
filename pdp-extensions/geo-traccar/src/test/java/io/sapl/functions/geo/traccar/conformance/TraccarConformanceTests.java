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
package io.sapl.functions.geo.traccar.conformance;

import io.sapl.conformance.CaseFileWriter;
import io.sapl.conformance.ConformanceCorpus;
import io.sapl.conformance.ConformanceCorpus.CaseFile;
import io.sapl.conformance.FunctionCatalogue;
import io.sapl.conformance.FunctionOutcomes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Traccar functions and their catalogue")
class TraccarConformanceTests {

    private static List<CaseFile> files() {
        return ConformanceCorpus.caseFilesOf("functions").stream()
                .filter(file -> "traccar.cases".equals(file.path().getFileName().toString())).toList();
    }

    @Test
    @Tag("conformance-dump")
    @DisplayName("the Java function broker records Traccar outcomes and the full catalogue")
    void whenGeneratingTheCorpusThenTheCurrentEngineSuppliesOutcomes() {
        files().forEach(file -> CaseFileWriter.rewrite(file, FunctionOutcomes::withOutcome));
        FunctionCatalogue.write();
        assertOutcomes();
    }

    @Test
    @Tag("conformance")
    @DisplayName("the Java function broker agrees with the Traccar corpus and the full catalogue")
    void whenCallingTraccarFunctionsThenTheRecordedOutcomesMatch() {
        assertOutcomes();
    }

    private static void assertOutcomes() {
        assertThat(files()).hasSize(1).flatMap(CaseFile::cases).isNotEmpty()
                .allSatisfy(sample -> assertThat(FunctionOutcomes.agrees(sample)).as("%s", sample).isTrue());
        assertThat(FunctionCatalogue.stated()).isEqualTo(FunctionCatalogue.catalogue());
    }
}
