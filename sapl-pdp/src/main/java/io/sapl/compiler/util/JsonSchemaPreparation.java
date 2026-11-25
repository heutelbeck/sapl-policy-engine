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

import com.heutelbeck.rustregex4j.RegexLimits;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Error;
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

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;

import static io.sapl.api.model.ValueJsonMarshaller.JSON_FACTORY;
import static io.sapl.api.model.ValueJsonMarshaller.toJsonNode;

/** Prepares a schema and its resolved references before validating SAPL values. */
@UtilityClass
public class JsonSchemaPreparation {

    private static final String ERROR_PROPERTY_NAME_TOO_LONG = "property name too long";

    private static final SchemaLocation INLINE    = SchemaLocation.of("mem://inline");
    private static final JsonMapper     MAPPER    = JsonMapper.builder(JSON_FACTORY)
            .enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    private static final SchemaRegistry BUILT_INS = confinedRegistry(Map.of(), LinearRegex.LIMITS);

    /**
     * Creates the registry for a set of locally supplied schemas.
     *
     * @param externalSchemas schemas supplied by the configuration or the function caller
     * @return a registry confined to bundled dialects and the supplied schema identifiers
     */
    public static SchemaRegistry registry(Value externalSchemas) {
        return registry(externalSchemas, LinearRegex.LIMITS);
    }

    /**
     * Creates the registry for a set of locally supplied schemas.
     *
     * @param externalSchemas schemas supplied by the configuration or the function caller
     * @param limits the limits for the patterns of the schemas
     * @return a registry confined to bundled dialects and the supplied schema identifiers
     */
    public static SchemaRegistry registry(Value externalSchemas, RegexLimits limits) {
        val schemas = new HashMap<String, String>();
        if (externalSchemas instanceof ArrayValue array) {
            for (val entry : array) {
                if (entry instanceof ObjectValue schema && schema.get("$id") instanceof TextValue(String id)) {
                    schemas.put(id, toJsonNode(schema).toString());
                }
            }
        }
        return schemas.isEmpty() && LinearRegex.LIMITS.equals(limits) ? BUILT_INS : confinedRegistry(schemas, limits);
    }

    /**
     * Resolves references and validates each schema against its dialect.
     *
     * @param registry registry used to resolve dialects and references
     * @param source the schema to apply to SAPL values
     * @return the initialized validator, with undeclared external dialects inherited from the root
     * @throws SchemaException if the schema or a resolved reference is invalid
     */
    public static Schema prepare(SchemaRegistry registry, ObjectValue source) {
        return prepareValidation(registry, source).schema();
    }

    /**
     * Prepares validation and records whether it can activate external dynamic resources.
     *
     * @param registry registry confined to bundled dialects and supplied schemas
     * @param source the schema to apply to SAPL values
     * @return validation with checks for references in resources loaded during evaluation
     * @throws SchemaException if the schema or a resolved reference is invalid
     */
    public static PreparedValidation prepareValidation(SchemaRegistry registry, ObjectValue source) {
        val scoped    = source.get("$schema") instanceof TextValue(String dialect)
                ? SchemaRegistry.builder(registry).defaultDialectId(dialect).build()
                : registry;
        val root      = scoped.getSchema(INLINE, toJsonNode(source));
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
        boolean dynamicResources = false;
        for (val schema : resources) {
            validateAgainstDialect(schema, embedded);
            if (!dynamicResources) {
                dynamicResources = mayLoadDynamicResources(schema);
            }
        }
        return new PreparedValidation(root, dynamicResources);
    }

    /**
     * Validation prepared for repeated use with immutable supplied schemas.
     *
     * @param schema the native initialized schema
     * @param dynamicResources whether a dynamic reference can load a resource during validation
     */
    public record PreparedValidation(Schema schema, boolean dynamicResources) {
        /**
         * Validates one value and checks references in dynamically activated resources.
         *
         * @param subject the value to validate
         * @return the native validation errors
         * @throws SchemaException if a loaded schema has an invalid reference
         */
        public List<Error> validate(JsonNode subject) {
            val errors = schema.validate(subject);
            if (dynamicResources) {
                synchronized (schema.getSchemaContext().getSchemaRegistry()) {
                    resolvedSchemas(schema);
                }
            }
            return errors;
        }
    }

    private static boolean mayLoadDynamicResources(Schema schema) {
        val source            = schema.getSchemaNode();
        val externalReference = source.findValues("$dynamicRef").stream().anyMatch(reference -> reference.isString()
                && !reference.asString().isEmpty() && !reference.asString().startsWith("#"));
        if (!externalReference) {
            return false;
        }
        val version = schema.getSchemaContext().getDialect().getSpecificationVersion();
        if (version == null || version.getOrder() >= SpecificationVersion.DRAFT_2020_12.getOrder()) {
            return true;
        }
        // A legacy resource can contain a subschema declaring a newer or custom dialect.
        return source.findValues("$schema").stream().filter(JsonNode::isString).anyMatch(declaration -> {
            val dialect = Specification.getDialect(declaration.asString());
            return dialect == null
                    || dialect.getSpecificationVersion().getOrder() >= SpecificationVersion.DRAFT_2020_12.getOrder();
        });
    }

    /**
     * Refuses a value with a property name longer than the text limit. A
     * pattern of a schema could not search such a name, and whether a schema
     * applies a pattern to a name depends on the course of the validation. So
     * no value with such a name is validated at all.
     *
     * @param subject the value to validate
     * @param maxTextBytes the longest text a pattern searches, in bytes of UTF-8
     * @throws SchemaException if a property name anywhere in the value is longer
     */
    public static void requireSearchablePropertyNames(JsonNode subject, int maxTextBytes) {
        if (subject instanceof ObjectNode object) {
            for (val property : object.properties()) {
                if (isLongerThan(property.getKey(), maxTextBytes)) {
                    throw new SchemaException(ERROR_PROPERTY_NAME_TOO_LONG);
                }
                requireSearchablePropertyNames(property.getValue(), maxTextBytes);
            }
        } else if (subject instanceof ArrayNode array) {
            for (val element : array) {
                requireSearchablePropertyNames(element, maxTextBytes);
            }
        }
    }

    private static boolean isLongerThan(String text, int maxBytes) {
        // A character takes at most three bytes of UTF-8.
        return text.length() > maxBytes / 3 && text.getBytes(StandardCharsets.UTF_8).length > maxBytes;
    }

    private static Set<Schema> resolvedSchemas(Schema root) {
        val pending     = new ArrayDeque<Schema>();
        val schemas     = new LinkedHashSet<Schema>();
        val contexts    = new HashSet<SchemaContext>();
        val initialized = new HashSet<Schema>();
        root.initializeValidators();
        initialized.add(root);
        pending.add(root);
        while (!pending.isEmpty()) {
            while (!pending.isEmpty()) {
                val schema = pending.remove();
                if (schemas.add(schema)) {
                    val context = schema.getSchemaContext();
                    if (contexts.add(context) && Specification.getDialect(context.getDialect().getId()) == null) {
                        pending.add(metaSchema(context));
                    }
                }
            }
            // Initializing unused definitions may register further referenced resources.
            for (val context : contexts) {
                context.getSchemaResources().values().stream().filter(schema -> !schemas.contains(schema))
                        .forEach(pending::add);
                for (val reference : context.getSchemaReferences().values()) {
                    if (initialized.add(reference)) {
                        reference.initializeValidators();
                        pending.add(reference);
                    }
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

    private static SchemaRegistry confinedRegistry(Map<String, String> schemas, RegexLimits limits) {
        return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12, builder -> {
            builder.nodeReader(reader -> reader.jsonMapper(MAPPER));
            builder.schemaLoader(new ConfinedSchemaLoader(schemas));
            LinearRegularExpressionFactory.applyTo(builder, limits);
        });
    }

}
