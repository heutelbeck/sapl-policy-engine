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
package io.sapl.api.attributes;

import io.sapl.api.SaplVersion;
import io.sapl.api.interpreter.PolicyEvaluationException;
import lombok.experimental.StandardException;

import java.io.Serial;

/**
 * Exception thrown when attribute broker operations fail.
 * <p>
 * This exception is raised during:
 * <ul>
 * <li>PIP registration failures (namespace collisions, duplicate
 * attributes)</li>
 * <li>Invalid PIP class processing (@PolicyInformationPoint annotation
 * missing)</li>
 * <li>Attribute method processing errors (invalid signatures, type
 * mismatches)</li>
 * <li>Library loading failures (missing @Attribute methods, invalid
 * configurations)</li>
 * </ul>
 *
 * @see AttributeBroker
 */
@StandardException
public class AttributeBrokerException extends PolicyEvaluationException {
    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;
}
