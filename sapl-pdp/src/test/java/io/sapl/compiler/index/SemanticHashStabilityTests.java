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
package io.sapl.compiler.index;

import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.IndexPredicate;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.Value;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.document.DocumentCompiler;
import io.sapl.compiler.index.canonical.CanonicalPolicyIndex;
import io.sapl.compiler.index.smtdd.SmtddPolicyIndex;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.sapl.util.SaplTesting.compilationContext;
import static io.sapl.util.SaplTesting.compileExpression;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The canonical order of commutative operands and the policy index rely on
 * semantic hashes that are the same in every run. A hash that reaches an
 * identity hash, such as the hash of an enum constant, a lambda or a class,
 * differs from one JVM to the next. This test compiles a corpus in two
 * separate JVMs and compares every semantic hash and the structure of the
 * built indexes.
 */
@DisplayName("Semantic hashes and the policy index are the same in every run")
class SemanticHashStabilityTests {

    private static final long TIMEOUT_SECONDS = 120;

    private static final List<String> EXPRESSIONS = List.of("subject.a + 1", "subject.a - 1", "subject.a * 2",
            "subject.a / 2", "subject.a % 2", "subject.a == \"x\"", "subject.a != \"x\"", "subject.a < 1",
            "subject.a <= 1", "subject.a > 1", "subject.a >= 1", "subject.a in [\"x\", \"y\"]", "subject.a =~ \"^a\"",
            "subject.a =~ subject.pattern", "subject.a && subject.b", "subject.a || subject.b", "subject.a & subject.b",
            "subject.a | subject.b", "subject.a ^ subject.b", "!subject.a", "-subject.a",
            "subject.a + subject.b + subject.c", "subject.a * subject.b * 2", "subject.a == null",
            "subject.a == undefined", "subject.a.b", "subject[\"a\"]", "subject[0]", "subject.*", "subject[1:3]",
            "subject[0,2]", "subject[\"a\",\"b\"]", "subject..a", "subject..[0]", "subject..*", "subject[(subject.i)]",
            "subject[(undefined)]", "subject[(null)]", "subject[?(@ > 1)]", "subject[?(subject.ok)]",
            "subject |- filter.blacken", "subject |- { @.a : filter.remove }",
            "subject |- { @[(subject.i)] : filter.remove }", "subject |- { @[?(@.x == subject.y)] : filter.remove }",
            "subject |- { @..a : filter.blacken, @[1:2] : filter.remove, @[0,1] : filter.remove }",
            "subject |- { each @.list : filter.replace(subject.x) }", "subject.list :: { \"n\": @.name }",
            "[subject.a, 1, \"x\", null]", "{\"a\": subject.a, \"b\": 2, \"c\": undefined}",
            "standard.length(subject.list)", "standard.length([subject.a, null])", "[subject.a, <test.attr>]");

    private static final List<String> DOCUMENTS = List.of("""
            subject enforced schema { "type": "object" }
            action enforced schema { "type": "string" }
            resource enforced schema { "type": "object" }
            resource enforced schema { "type": "array" }
            policy "schemas" permit subject.role == "admin";
            """, """
            policy "null role" permit subject.role == null;
            """, """
            policy "user role" permit subject.role == "user"; subject.age > 18;
            """, """
            policy "not guest" deny subject.role != "guest"; action == "read";
            """, """
            policy "error role" deny subject.role == (1 / 0); resource.type =~ "^doc";
            """, """
            policy "filtered" permit (subject |- { @[?(@.x == resource.y)] : filter.remove }) == {};
            """, """
            policy "position" permit subject.position == [1, subject.x]; !(subject.blocked || subject.locked);
            """, """
            set "set" first or deny
            for subject.role == "user" || subject.role == null
            policy "inner" permit subject.age > 21;
            policy "other" deny subject.age < 16;
            """);

    @Test
    @DisplayName("two separate JVMs compile the corpus to the same semantic hashes and build the same indexes")
    void whenCorpusIsCompiledInTwoJvmsThenSemanticHashesAndIndexesAreTheSame(@TempDir Path directory)
            throws IOException, InterruptedException {
        val firstRun  = reportOfSeparateJvm(directory.resolve("first.txt"), 0);
        val secondRun = reportOfSeparateJvm(directory.resolve("second.txt"), 1);
        assertThat(secondRun).hasSize(EXPRESSIONS.size() + DOCUMENTS.size() + 2).containsExactlyElementsOf(firstRun);
    }

    private static List<String> reportOfSeparateJvm(Path report, int identityHashesToDraw)
            throws IOException, InterruptedException {
        val java    = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        val process = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), Report.class.getName(),
                report.toString(), String.valueOf(identityHashesToDraw)).redirectOutput(Redirect.DISCARD)
                .redirectError(Redirect.DISCARD).start();
        try {
            assertThat(process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)).as("report finished in time").isTrue();
            assertThat(process.exitValue()).as("exit code of the report").isZero();
        } finally {
            process.destroyForcibly();
        }
        return Files.readAllLines(report);
    }

    /**
     * Writes the semantic hashes of the corpus and a description of the indexes
     * built from its documents to the file named by the first argument.
     */
    static final class Report {

        private Report() {
        }

        /**
         * @param args the report file and the number of identity hashes to draw
         * before compiling
         * @throws IOException if the report cannot be written
         */
        public static void main(String[] args) throws IOException {
            drawIdentityHashes(Integer.parseInt(args[1]));
            val lines = new ArrayList<String>();
            for (val expression : EXPRESSIONS) {
                lines.add("expression " + expression + " -> " + describe(compileExpression(expression)));
            }
            val documents = new ArrayList<CompiledDocument>();
            for (val source : DOCUMENTS) {
                val document = DocumentCompiler.compileDocument(source, compilationContext());
                documents.add(document);
                lines.add("document " + document.metadata().name() + " -> " + describe(document.isApplicable()));
            }
            lines.add("canonical index " + render(CanonicalPolicyIndex.create(documents), new IdentityHashMap<>()));
            lines.add("smtdd index " + render(SmtddPolicyIndex.create(documents, 0), new IdentityHashMap<>()));
            Files.write(Path.of(args[0]), lines);
        }

        /**
         * Identity hashes come from a sequence per thread. Drawing a different
         * number of them first shifts every later identity hash, so any identity
         * hash in the report differs between the two runs as it does between two
         * real runs.
         */
        private static void drawIdentityHashes(int count) {
            for (var i = 0; i < count; i++) {
                System.identityHashCode(new Object());
            }
        }

        private static String describe(CompiledExpression compiled) {
            val kind = compiled.getClass().getSimpleName();
            return switch (compiled) {
            case Value value            -> kind + " " + value + " " + SemanticHashing.valueHash(value);
            case PureOperator operator  -> kind + " " + operator.semanticHash();
            case StreamOperator ignored -> kind;
            };
        }

        private static String render(Object object, Map<Object, Integer> visited) {
            return switch (object) {
            case null                      -> "null";
            case PureOperator operator     -> "op" + operator.semanticHash();
            case IndexPredicate predicate  -> "p" + predicate.semanticHash();
            case CompiledDocument document -> document.metadata().name();
            case Value value               -> value.toString();
            case Map<?, ?> map             -> map.entrySet().stream()
                    .map(entry -> render(entry.getKey(), visited) + "=" + render(entry.getValue(), visited))
                    .collect(joining(", ", "{", "}"));
            case Collection<?> collection  ->
                collection.stream().map(element -> render(element, visited)).collect(joining(", ", "[", "]"));
            case CharSequence ignored      -> object.toString();
            case Number ignored            -> object.toString();
            case Boolean ignored           -> object.toString();
            case Enum<?> ignored           -> object.toString();
            case BitSet ignored            -> object.toString();
            default                        ->
                object.getClass().isArray() ? renderArray(object, visited) : renderStructure(object, visited);
            };
        }

        private static String renderArray(Object array, Map<Object, Integer> visited) {
            val elements = new ArrayList<String>();
            for (var i = 0; i < Array.getLength(array); i++) {
                elements.add(render(Array.get(array, i), visited));
            }
            return elements.stream().collect(joining(", ", "[", "]"));
        }

        /**
         * Renders the fields of an index structure. A node shared in the
         * decision diagram is rendered once and referenced by its number after.
         */
        private static String renderStructure(Object object, Map<Object, Integer> visited) {
            val type = object.getClass();
            if (!type.getName().startsWith("io.sapl.")) {
                throw new IllegalArgumentException("No stable description of " + type.getName() + ".");
            }
            val known = visited.get(object);
            if (known != null) {
                return "#" + known;
            }
            val number = visited.size();
            visited.put(object, number);
            try {
                val parts = new ArrayList<String>();
                if (type.isRecord()) {
                    for (RecordComponent component : type.getRecordComponents()) {
                        val accessor = component.getAccessor();
                        accessor.setAccessible(true);
                        parts.add(component.getName() + "=" + render(accessor.invoke(object), visited));
                    }
                } else {
                    for (val field : type.getDeclaredFields()) {
                        if (!Modifier.isStatic(field.getModifiers())) {
                            field.setAccessible(true);
                            parts.add(field.getName() + "=" + render(field.get(object), visited));
                        }
                    }
                }
                return "#" + number + " " + type.getSimpleName() + parts.stream().collect(joining(", ", "(", ")"));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot read " + type.getName() + ".", e);
            }
        }
    }
}
