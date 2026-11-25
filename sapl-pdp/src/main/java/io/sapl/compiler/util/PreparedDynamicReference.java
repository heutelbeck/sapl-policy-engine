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
package io.sapl.compiler.util;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaContext;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.Specification;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.dialect.Dialect;
import com.networknt.schema.keyword.AbstractKeyword;
import com.networknt.schema.keyword.DynamicRefValidator;
import com.networknt.schema.keyword.KeywordType;
import com.networknt.schema.keyword.KeywordValidator;
import com.networknt.schema.keyword.RefValidator;
import tools.jackson.databind.JsonNode;

/** Preloads the static target of an external dynamic reference; evaluation retains native dynamic scope. */
final class PreparedDynamicReference extends AbstractKeyword {
    static final Dialect DIALECT = Dialect.builder(Specification.getDialect(SpecificationVersion.DRAFT_2020_12))
            .keyword(new PreparedDynamicReference()).build();

    private PreparedDynamicReference() {
        super(KeywordType.DYNAMIC_REF.getValue());
    }

    @Override
    public KeywordValidator newValidator(SchemaLocation location, JsonNode node, Schema parent, SchemaContext context) {
        return new DynamicRefValidator(location, node, parent, context) {
            @Override
            public void preloadSchema() {
                if (node.isString() && !node.asString().isEmpty() && !node.asString().startsWith("#")) {
                    new RefValidator(location, node, parent, context).preloadSchema();
                }
            }
        };
    }
}
