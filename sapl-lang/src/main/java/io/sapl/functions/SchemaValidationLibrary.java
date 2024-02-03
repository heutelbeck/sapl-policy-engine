/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.functions;

import java.util.HashMap;

import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchemaException;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.resource.MapSchemaLoader;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.JsonObject;
import lombok.experimental.UtilityClass;

@UtilityClass
@FunctionLibrary(name = SchemaValidationLibrary.NAME, description = SchemaValidationLibrary.DESCRIPTION)
public class SchemaValidationLibrary {

    public static final String NAME = "jsonschema";

    public static final String DESCRIPTION = "This library contains the mandatory functions for testing the compliance of a JSON value with a JSON schema.";

    private static final String ISCOMPLIANTWITHSCHEMA_VAL_DOC = "isCompliantWithSchema(validationSubject, schema):"
            + "tests compliance of the validationSubject with the provided schema. The schema itself cannot be validated and improper schema definitions may lead to unexcpected results."
            + "If validationSubject is compliant with schema, returns TRUE, else returns FALSE.";

    private static final String ISCOMPLIANTWITHSCHEMA_VAL_EXTERNAL_DOC = "isCompliantWithSchema(validationSubject, schema, externalSchemas):"
            + "tests compliance of the validationSubject with the provided schema. The schema itself cannot be validated and improper schema definitions may lead to unexcpected results."
            + "If validationSubject is compliant with schema, returns TRUE, else returns FALSE. If the schema contains external references to other schemas, the validation function looks up the schemas in externalSchemas based on explicitly defined $id field in the schemas. If no $id$ field is provided, the schema will not be detectable.";

    private static final String RETURNS_BOOLEAN = """
            {
                "type": "boolean"
            }
            """;

    private static final String ID = "$id";

    @Function(docs = ISCOMPLIANTWITHSCHEMA_VAL_DOC, schema = RETURNS_BOOLEAN)
    public static Val isCompliant(Val validationSubject, @JsonObject Val jsonSchema) {
        return isCompliantWithExteralSchemas(validationSubject, jsonSchema, Val.ofEmptyArray());
    }

    @Function(docs = ISCOMPLIANTWITHSCHEMA_VAL_EXTERNAL_DOC, schema = RETURNS_BOOLEAN)
    public static Val isCompliantWithExteralSchemas(Val validationSubject, @JsonObject Val jsonSschema, Val externals) {
        if (validationSubject.isError()) {
            return validationSubject;
        }

        if (validationSubject.isUndefined()) {
            return Val.FALSE;
        }

        var schemaMap = new HashMap<String, String>();
        if (externals.isArray()) {
            for (var externalSchema : externals.getArrayNode()) {
                if (externalSchema.has(ID)) {
                    schemaMap.put(externalSchema.get(ID).asText(), externalSchema.toString());
                }
            }
        }

        var schemaLoader  = new MapSchemaLoader(schemaMap);
        var schemaFactory = JsonSchemaFactory.builder().schemaLoaders(schemaLoaders -> schemaLoaders.add(schemaLoader))
                .addMetaSchema(JsonMetaSchema.getV202012()).defaultMetaSchemaURI(JsonMetaSchema.getV202012().getUri())
                .build();

        try {
            var validator = schemaFactory.getSchema(jsonSschema.getJsonNode());
            var messages  = validator.validate(validationSubject.get());
            return Val.of(messages.isEmpty());
        } catch (JsonSchemaException e) {
            return Val.FALSE;
        }
    }

}
