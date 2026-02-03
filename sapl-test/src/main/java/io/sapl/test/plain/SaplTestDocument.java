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
package io.sapl.test.plain;

/**
 * A SAPLTEST document containing test definitions.
 * <p>
 * Used for programmatic test execution where test definitions are not loaded
 * from files but provided directly (e.g., from a database in a PAP
 * application).
 *
 * @param id identifier for result mapping (e.g., database ID)
 * @param name unique name for this test document
 * @param sourceCode the SAPLTEST source code
 */
public record SaplTestDocument(String id, String name, String sourceCode) {

    /**
     * Creates a test document with the same value for id and name.
     *
     * @param name the name (used as both id and name)
     * @param sourceCode the SAPLTEST source code
     * @return a new SaplTestDocument
     */
    public static SaplTestDocument of(String name, String sourceCode) {
        return new SaplTestDocument(name, name, sourceCode);
    }
}
