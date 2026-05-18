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
package io.sapl.pdp.interceptors;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.pdp.Decision;
import io.sapl.compiler.document.AttributeContribution;

import java.util.List;

/**
 * One contributing document's name, decision, errors, and the
 * attribute reads referenced from this document's source.
 *
 * @param name the document name (policy or policy set)
 * @param decision the authorization decision from this document
 * @param errors errors encountered evaluating this document
 * @param attributes attribute reads referenced from this document's
 * source, each with the value and publish timestamp present at the
 * time of the read
 */
public record ContributingDocument(
        String name,
        Decision decision,
        List<ErrorValue> errors,
        List<AttributeContribution> attributes) {}
