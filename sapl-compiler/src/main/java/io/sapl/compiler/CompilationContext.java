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
package io.sapl.compiler;

import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.Value;
import io.sapl.grammar.sapl.Import;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Data
@ToString
@NoArgsConstructor
public class CompilationContext {
    final boolean                           dynamicLibrariesEnabled = false;
    final boolean                           debugInformationEnabled = false;
    List<Import>                            imports                 = new ArrayList<>();
    Map<SchemaTarget, List<CompiledSchema>> schemas                 = new HashMap<>();
    Map<String, CompiledExpression>         localVariablesInScope   = new HashMap<>();
    Map<Value, Value>                       constants               = new HashMap<>();
    Value                                   relativeValue;
    String                                  relativeKey;
    BigDecimal                              relativeIndex;

    public void addAllImports(List<Import> imports) {
        if (imports != null) {
            this.imports.addAll(imports);
        }
    }

    public void setRelativeValue(Value relativeValue, String relativeKey) {
        this.relativeValue = relativeValue;
        this.relativeKey   = relativeKey;
        this.relativeIndex = null;
    }

    public void setRelativeValue(Value relativeValue, int relativeIndex) {
        this.relativeValue = relativeValue;
        this.relativeKey   = null;
        this.relativeIndex = BigDecimal.valueOf(relativeIndex);
    }

    public void clearRelativeValue() {
        this.relativeIndex = null;
        this.relativeKey   = null;
        this.relativeValue = null;
    }

    public void resetForNextDocument() {
        imports.clear();
        localVariablesInScope.clear();
        schemas.clear();
        clearRelativeValue();
    }

    public CompiledExpression dedupe(Value constantValue) {
        if (debugInformationEnabled) {
            return constantValue;
        }
        return constants.computeIfAbsent(constantValue, Function.identity());
    }
}
