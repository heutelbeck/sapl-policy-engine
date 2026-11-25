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
package io.sapl.test.coverage;

import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Source document identity shared by coverage aggregators and report links.
 */
@UtilityClass
public class CoverageKey {

    /**
     * Identifies a document without merging distinct source files.
     *
     * @param documentName the policy or set name
     * @param sourceHash the existing source content hash
     * @param filePath the source path, or null if unavailable
     * @return an opaque coverage map key
     */
    public static String forDocument(String documentName, int sourceHash, String filePath) {
        val name = String.valueOf(documentName);
        // Frame the free name before the fixed numeric hash and optional path.
        val key = name.length() + ":" + name + ":" + sourceHash;
        return filePath == null ? key : key + ":" + filePath;
    }
}
