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
import com.networknt.schema.SchemaException;
import com.networknt.schema.resource.InputStreamSource;
import com.networknt.schema.resource.SchemaLoader;
import lombok.val;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Resolves the schemas a JSON Schema names by {@code $schema} or {@code $ref}
 * from two sources only: the meta-schemas of the supported drafts that ship
 * with networknt, and the schemas provided by their {@code $id}.
 * <p>
 * Unconfined, networknt reads any resource of the class path a schema names
 * with the schemes {@code classpath:} and {@code resource:}, or with a path
 * below {@code https://json-schema.org/}, and logs a missing one at ERROR with
 * the name the schema gave. Schemas may come from authorization subscriptions
 * and policies, so any other reference fails the schema with a
 * {@link SchemaException} instead, and the PDP neither fetches a URL nor reads
 * a file or a resource for a schema.
 */
public final class ConfinedSchemaLoader extends SchemaLoader {

    private static final String ERROR_UNRESOLVABLE_REFERENCE = "the reference '%s' names neither a built-in meta-schema nor a provided schema";

    private static final Set<String> BUILT_IN_META_SCHEMAS = builtInMetaSchemas();

    private final Map<String, String> schemas;

    /**
     * @param schemas the provided schemas, the text of each by its {@code $id}
     */
    public ConfinedSchemaLoader(Map<String, String> schemas) {
        super(List.of(), List.of());
        this.schemas = Map.copyOf(schemas);
    }

    @Override
    public InputStreamSource getSchemaResource(AbsoluteIri absoluteIri) {
        val iri = absoluteIri.toString();
        if (BUILT_IN_META_SCHEMAS.contains(iri)) {
            return super.getSchemaResource(absoluteIri);
        }
        val schema = schemas.get(iri);
        if (schema == null) {
            throw new InvalidSchemaException(
                    Error.builder().messageSupplier(() -> ERROR_UNRESOLVABLE_REFERENCE.formatted(iri)).build());
        }
        return () -> new ByteArrayInputStream(schema.getBytes(StandardCharsets.UTF_8));
    }

    private static Set<String> builtInMetaSchemas() {
        val draft201909 = Stream.of("schema", "meta/applicator", "meta/content", "meta/core", "meta/format",
                "meta/meta-data", "meta/validation").map("draft/2019-09/"::concat);
        val draft202012 = Stream
                .of("schema", "meta/applicator", "meta/content", "meta/core", "meta/format-annotation",
                        "meta/format-assertion", "meta/meta-data", "meta/unevaluated", "meta/validation")
                .map("draft/2020-12/"::concat);
        val olderDrafts = Stream.of("draft-04/schema", "draft-06/schema", "draft-07/schema");
        return Stream.of(draft201909, draft202012, olderDrafts).flatMap(Function.identity())
                .flatMap(path -> Stream.of("http://json-schema.org/" + path, "https://json-schema.org/" + path))
                .collect(Collectors.toUnmodifiableSet());
    }
}
