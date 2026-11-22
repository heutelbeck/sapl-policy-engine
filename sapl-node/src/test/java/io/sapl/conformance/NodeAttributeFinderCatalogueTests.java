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
package io.sapl.conformance;

import io.sapl.api.documentation.EntryDocumentation;
import io.sapl.api.documentation.EntryType;
import io.sapl.attributes.broker.pip.StreamAttributeMethodSignatureProcessor;
import io.sapl.attributes.libraries.HttpPolicyInformationPoint;
import io.sapl.attributes.libraries.JWTPolicyInformationPoint;
import io.sapl.attributes.libraries.TimePolicyInformationPoint;
import io.sapl.attributes.libraries.X509PolicyInformationPoint;
import io.sapl.documentation.LibraryDocumentationExtractor;
import io.sapl.extensions.mqtt.MqttPolicyInformationPoint;
import io.sapl.pip.geo.traccar.TraccarPolicyInformationPoint;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("The standalone node attribute finder catalogue")
class NodeAttributeFinderCatalogueTests {

    private static final String HEADER = "# Qualified name, receiver, fixed arguments, and arity. Columns use tabs.";

    @Test
    @Tag("conformance")
    @DisplayName("the stated catalogue includes every default and optional finder shape of the standalone node")
    void whenTheNodeExposesItsFindersThenTheyMatchTheSharedCatalogue() throws IOException {
        assertThat(Files.readAllLines(path())).isEqualTo(catalogue());
    }

    @Test
    @Tag("conformance")
    @DisplayName("the documented receiver and arity agree with the finders the broker admits")
    void whenTheBrokerInspectsEachProviderThenTheDocumentationDescribesItsServingRoutes() {
        val serving = new ArrayList<Finder>();
        for (val provider : providers()) {
            val namespace = LibraryDocumentationExtractor.extractPolicyInformationPoint(provider).name();
            val instance  = mock(provider);
            for (val method : provider.getMethods()) {
                val spec = StreamAttributeMethodSignatureProcessor.processAttributeMethod(instance, namespace, method);
                if (spec != null) {
                    serving.add(new Finder(spec.fullyQualifiedName(), !spec.isEnvironmentAttribute(),
                            spec.parameterTypes().size(), spec.hasVariableNumberOfArguments()));
                }
            }
        }
        assertThat(lines(serving.stream())).isEqualTo(catalogue());
    }

    @Test
    @Tag("conformance-dump")
    @DisplayName("the engine writes the standalone finders into the supplied corpus")
    void whenTheFinderCatalogueIsGeneratedThenItHoldsTheActualInvocationShapes() throws IOException {
        Files.write(path(), catalogue());
    }

    private static Path path() {
        return Path.of(System.getProperty("sapl.conformance.directory", "../../sapl-rust/conformance"), "attributes",
                "finders.tsv");
    }

    private static List<String> catalogue() {
        val finders = providers().stream().map(LibraryDocumentationExtractor::extractPolicyInformationPoint)
                .flatMap(library -> library.entries().stream().map(entry -> Finder.of(library.name(), entry)));
        return lines(finders);
    }

    private static List<Class<?>> providers() {
        return List.of(TimePolicyInformationPoint.class, HttpPolicyInformationPoint.class,
                JWTPolicyInformationPoint.class, X509PolicyInformationPoint.class, MqttPolicyInformationPoint.class,
                TraccarPolicyInformationPoint.class);
    }

    private static List<String> lines(Stream<Finder> finders) {
        val sorted = finders
                .sorted(Comparator.comparing(Finder::name).thenComparing(Finder::entity).thenComparing(Finder::fixed))
                .map(Finder::line);
        return Stream.concat(Stream.of(HEADER), sorted).toList();
    }

    private record Finder(String name, boolean entity, int fixed, boolean variadic) {
        static Finder of(String namespace, EntryDocumentation entry) {
            val parameters = entry.parameters();
            val variadic   = !parameters.isEmpty() && parameters.getLast().varArgs();
            val fixed      = variadic ? parameters.size() - 1 : parameters.size();
            return new Finder(namespace + "." + entry.name(), entry.type() == EntryType.ATTRIBUTE, fixed, variadic);
        }

        String line() {
            val receiver = entity ? "entity" : "environment";
            val arity    = variadic ? "variadic" : "fixed";
            return String.join("\t", name, receiver, Integer.toString(fixed), arity);
        }
    }
}
