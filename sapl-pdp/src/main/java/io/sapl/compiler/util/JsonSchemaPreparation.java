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

import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaException;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaContext;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.Specification;
import com.networknt.schema.SpecificationVersion;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;
import lombok.val;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static io.sapl.api.model.ValueJsonMarshaller.JSON_FACTORY;
import static io.sapl.api.model.ValueJsonMarshaller.toJsonNode;

/** Prepares a schema and its resolved references before validating SAPL values. */
@UtilityClass
public class JsonSchemaPreparation {

    private static final SchemaLocation INLINE    = SchemaLocation.of("mem://inline");
    private static final JsonMapper     MAPPER    = JsonMapper.builder(JSON_FACTORY)
            .enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    private static final SchemaRegistry BUILT_INS = confinedRegistry(Map.of());

    /**
     * Creates the registry for a set of locally supplied schemas.
     *
     * @param externalSchemas schemas supplied by the configuration or the function caller
     * @return a registry confined to bundled dialects and the supplied schema identifiers
     */
    public static SchemaRegistry registry(Value externalSchemas) {
        val schemas = new HashMap<String, String>();
        if (externalSchemas instanceof ArrayValue array) {
            for (val entry : array) {
                if (entry instanceof ObjectValue schema && schema.get("$id") instanceof TextValue(String id)) {
                    schemas.put(id, toJsonNode(schema).toString());
                }
            }
        }
        return schemas.isEmpty() ? BUILT_INS : confinedRegistry(schemas);
    }

    /**
     * Resolves references and validates each schema against its dialect.
     *
     * @param registry registry used to resolve dialects and references
     * @param source the schema to apply to SAPL values
     * @return the initialized validator
     * @throws SchemaException if the schema or a resolved reference is invalid
     */
    public static Schema prepare(SchemaRegistry registry, ObjectValue source) {
        val root = registry.getSchema(INLINE, toJsonNode(source));
        root.initializeValidators();
        val resources = resolvedSchemas(root);
        // Embedded resources with different dialects are validated separately (JSON Schema Core 9.3.3).
        val embedded = new IdentityHashMap<JsonNode, JsonNode>();
        for (val resource : resources) {
            val parent = resource.getParentSchema();
            if (parent != null && resource.getId() != null && !parent.getSchemaContext().getDialect().getId()
                    .equals(resource.getSchemaContext().getDialect().getId())) {
                embedded.put(resource.getSchemaNode(), MAPPER.createObjectNode().put("$ref", resource.getId()));
            }
        }
        for (val schema : resources) {
            validateAgainstDialect(schema, embedded);
        }
        return root;
    }

    private static Set<Schema> resolvedSchemas(Schema root) {
        val pending  = new ArrayDeque<Schema>();
        val schemas  = new LinkedHashSet<Schema>();
        val contexts = new HashSet<SchemaContext>();
        pending.add(root);
        while (!pending.isEmpty()) {
            val schema = pending.remove();
            schemas.add(schema);
            val context = schema.getSchemaContext();
            if (contexts.add(context)) {
                pending.addAll(context.getSchemaResources().values());
                pending.addAll(context.getSchemaReferences().values());
                if (Specification.getDialect(context.getDialect().getId()) == null) {
                    val localMetaSchema = metaSchema(context);
                    localMetaSchema.initializeValidators();
                    pending.add(localMetaSchema);
                }
            }
        }
        return schemas;
    }

    private static Schema metaSchema(SchemaContext context) {
        val id       = context.getDialect().getId();
        val registry = Specification.getDialect(id) == null ? context.getSchemaRegistry() : BUILT_INS;
        return registry.getSchema(SchemaLocation.of(id));
    }

    private static void validateAgainstDialect(Schema schema, Map<JsonNode, JsonNode> embedded) {
        val dialect    = schema.getSchemaContext().getDialect();
        val metaSchema = metaSchema(schema.getSchemaContext());
        val source     = schema.getSchemaNode();
        val node       = embedded.isEmpty() ? source : unbundle(source, source, embedded);
        // Draft 4 distinguishes integer tokens from decimals, while SAPL stores both as BigDecimal.
        val errors = dialect.getSpecificationVersion() == SpecificationVersion.DRAFT_4
                ? metaSchema.validate(node.toString(), InputFormat.JSON)
                : metaSchema.validate(node);
        if (!errors.isEmpty()) {
            throw new SchemaException(schema.getSchemaLocation().toString() + errors.getFirst());
        }
    }

    private static JsonNode unbundle(JsonNode node, JsonNode root, Map<JsonNode, JsonNode> embedded) {
        val reference = embedded.get(node);
        if (node != root && reference != null) {
            return reference;
        }
        if (node instanceof ObjectNode object) {
            val copy = MAPPER.createObjectNode();
            object.forEachEntry((key, value) -> copy.set(key, unbundle(value, root, embedded)));
            return copy;
        }
        if (node instanceof ArrayNode array) {
            val copy = MAPPER.createArrayNode();
            array.forEach(value -> copy.add(unbundle(value, root, embedded)));
            return copy;
        }
        return node.deepCopy();
    }

    private static SchemaRegistry confinedRegistry(Map<String, String> schemas) {
        return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12, builder -> {
            builder.nodeReader(reader -> reader.jsonMapper(MAPPER));
            builder.schemaLoader(new ConfinedSchemaLoader(schemas));
            LinearRegularExpressionFactory.applyTo(builder);
        });
    }

}
