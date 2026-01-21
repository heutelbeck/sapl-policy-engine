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
package io.sapl.ast;

/**
 * Base interface for path elements in filter target expressions.
 * <p>
 * Path elements represent navigation steps in filter targets (e.g.,
 * {@code @.foo.bar[0]}).
 * Unlike regular Steps which are expressions with a base and produce values,
 * PathElements are pure navigation descriptors that identify locations for
 * modification.
 * <p>
 * Note: Attribute finder steps are NOT allowed in filter paths per SAPL
 * specification.
 */
public sealed interface PathElement extends AstNode
        permits KeyPath, IndexPath, WildcardPath, SlicePath, RecursiveKeyPath, RecursiveWildcardPath,
        RecursiveIndexPath, ExpressionPath, ConditionPath, IndexUnionPath, AttributeUnionPath {
}
