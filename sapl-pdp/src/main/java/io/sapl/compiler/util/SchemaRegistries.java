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

import com.networknt.schema.AbsoluteIri;
import com.networknt.schema.Error;
import com.networknt.schema.InvalidSchemaException;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.resource.InputStreamSource;
import com.networknt.schema.resource.SchemaLoader;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates the networknt {@link SchemaRegistry} instances used for JSON Schema
 * validation in the PDP.
 * <p>
 * Schemas reach the PDP from policies, from the PDP's {@code schemas} variable
 * and as arguments of the schema validation functions, which can be fed from
 * subscriptions or attribute data. A {@code $schema} or {@code $ref} in such a
 * schema must never make the PDP open a network connection or read a file or
 * class path resource. The registries created here therefore resolve only the
 * meta-schemas of the supported JSON Schema drafts, which ship with the
 * validator, and the schemas explicitly supplied by their {@code $id}. Every
 * other reference fails with an {@link InvalidSchemaException} before any
 * resource is touched.
 * <p>
 * All registries evaluate {@code pattern} keywords under the match budget of
 * {@link BoundedRegularExpressionFactory}.
 */
@UtilityClass
public class SchemaRegistries {

    private static final String ERROR_SCHEMA_REFERENCE_NOT_RESOLVABLE = "The schema reference '%s' cannot be resolved. Only the meta-schemas of the supported JSON Schema drafts and schemas supplied with a matching $id can be referenced.";

    private static final String FRAGMENT_SEPARATOR = "#";

    /**
     * Creates a registry for JSON Schema 2020-12 by default that resolves
     * references only to the built-in meta-schemas and to the given schemas.
     *
     * @param schemasById the serialized schemas that may be referenced, keyed by
     * their {@code $id}
     * @return a registry that refuses every other reference
     */
    public static SchemaRegistry create(Map<String, String> schemasById) {
        val schemaLoader = new SuppliedSchemasOnlyLoader(schemasById);
        return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12, builder -> {
            builder.schemaLoader(schemaLoader);
            BoundedRegularExpressionFactory.applyTo(builder);
        });
    }

    /** The IRIs of the meta-schemas that ship with the validator, under http and https. */
    private static Set<String> metaSchemaIris() {
        val paths = List.of("draft-04/schema", "draft-06/schema", "draft-07/schema", "draft/2019-09/schema",
                "draft/2019-09/meta/applicator", "draft/2019-09/meta/content", "draft/2019-09/meta/core",
                "draft/2019-09/meta/format", "draft/2019-09/meta/meta-data", "draft/2019-09/meta/validation",
                "draft/2020-12/schema", "draft/2020-12/meta/applicator", "draft/2020-12/meta/content",
                "draft/2020-12/meta/core", "draft/2020-12/meta/format-annotation",
                "draft/2020-12/meta/format-assertion", "draft/2020-12/meta/meta-data", "draft/2020-12/meta/unevaluated",
                "draft/2020-12/meta/validation");
        val iris  = new HashSet<String>();
        for (val origin : List.of("http://json-schema.org/", "https://json-schema.org/")) {
            for (val path : paths) {
                iris.add(origin + path);
            }
        }
        return Set.copyOf(iris);
    }

    /**
     * Replaces the default networknt resource resolution, which reads class path
     * resources by {@code classpath:} and {@code resource:} IRIs and, depending on
     * configuration, any URL, with a lookup in a closed set of schemas.
     */
    private static final class SuppliedSchemasOnlyLoader extends SchemaLoader {

        private static final Set<String> META_SCHEMA_IRIS = metaSchemaIris();

        private final Map<String, String> schemasById;

        SuppliedSchemasOnlyLoader(Map<String, String> schemasById) {
            super(List.of(), List.of());
            this.schemasById = Map.copyOf(schemasById);
        }

        @Override
        public InputStreamSource getSchemaResource(AbsoluteIri absoluteIri) {
            val iri           = String.valueOf(absoluteIri);
            val metaSchemaIri = withoutEmptyFragment(iri);
            if (META_SCHEMA_IRIS.contains(metaSchemaIri)) {
                return super.getSchemaResource(AbsoluteIri.of(metaSchemaIri));
            }
            val schema = schemasById.get(iri);
            if (schema != null) {
                return () -> new ByteArrayInputStream(schema.getBytes(StandardCharsets.UTF_8));
            }
            throw new InvalidSchemaException(Error.builder()
                    .messageSupplier(() -> ERROR_SCHEMA_REFERENCE_NOT_RESOLVABLE.formatted(iri)).build());
        }

        private static String withoutEmptyFragment(String iri) {
            return iri.endsWith(FRAGMENT_SEPARATOR) ? iri.substring(0, iri.length() - 1) : iri;
        }
    }
}
