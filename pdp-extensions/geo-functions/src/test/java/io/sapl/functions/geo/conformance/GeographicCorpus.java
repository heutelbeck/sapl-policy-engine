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
package io.sapl.functions.geo.conformance;

import io.sapl.conformance.ConformanceCorpus.CaseFile;
import lombok.experimental.UtilityClass;

/**
 * Where the cases of the geographic library are in the conformance corpus.
 */
@UtilityClass
class GeographicCorpus {

    /**
     * The corpus of the function libraries.
     */
    static final String CORPUS = "functions";

    private static final String CASE_FILE = "geo.cases";

    /**
     * @param file a case file of the function corpus
     * @return whether it holds the cases of the geographic library
     */
    static boolean isCaseFile(CaseFile file) {
        return CASE_FILE.equals(file.path().toFile().getName());
    }
}
