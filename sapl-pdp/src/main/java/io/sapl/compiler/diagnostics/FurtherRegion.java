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
package io.sapl.compiler.diagnostics;

/**
 * A further region of a SAPL document that helps to understand a problem.
 *
 * @param region the region of the document
 * @param label what the region is
 */
public record FurtherRegion(Region region, Label label) {

    /**
     * What a further region is. The texts belong to the catalogue of problems.
     */
    public enum Label {

        /**
         * The import that already introduced a short name.
         */
        FIRST_IMPORTED_HERE("first imported here"),

        /**
         * The opening bracket that a closing bracket is missing for or does not
         * match.
         */
        OPENED_HERE("opened here");

        private final String text;

        Label(String text) {
            this.text = text;
        }

        /**
         * @return the label as it is shown with the region
         */
        public String text() {
            return text;
        }
    }

}
