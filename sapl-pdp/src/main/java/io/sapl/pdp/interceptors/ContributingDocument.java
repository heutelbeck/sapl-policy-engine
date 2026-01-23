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

import io.sapl.api.model.AttributeRecord;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.pdp.Decision;

import java.util.List;

/**
 * A record capturing a contributing document's name, decision, attributes, and
 * errors.
 *
 * @param name the document name (policy or policy set)
 * @param decision the authorization decision from this document
 * @param attributes the attributes accessed during evaluation of this document
 * @param errors the errors encountered during evaluation of this document
 */
public record ContributingDocument(
        String name,
        Decision decision,
        List<AttributeRecord> attributes,
        List<ErrorValue> errors) {}
