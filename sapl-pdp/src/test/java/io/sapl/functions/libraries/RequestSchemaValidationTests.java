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
package io.sapl.functions.libraries;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.sapl.util.SaplTesting.evaluate;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JSON schemas supplied by an authorization request")
class RequestSchemaValidationTests {

    private static final String COMPLIANT          = "jsonschema.isCompliant(subject.data, subject.guard)";
    private static final String COMPLIANT_EXTERNAL = "jsonschema.isCompliantWithExternalSchemas(subject.data, subject.guard, subject.schemas)";
    private static final String VALIDATE           = "jsonschema.validate(subject.data, subject.guard)";
    private static final String VALIDATE_EXTERNAL  = "jsonschema.validateWithExternalSchemas(subject.data, subject.guard, subject.schemas)";

    @ValueSource(strings = { COMPLIANT, COMPLIANT_EXTERNAL, VALIDATE, VALIDATE_EXTERNAL })
    @ParameterizedTest(name = "{0}")
    @DisplayName("A malformed request schema returns an error for both present and undefined data")
    void whenRequestSuppliesAnInvalidSchemaThenTheFunctionReturnsAnError(String call) {
        val withData  = run(call, """
                {"data":"ok","guard":{"type":123},"schemas":[]}
                """);
        val undefined = run(call, """
                {"guard":{"type":123},"schemas":[]}
                """);
        assertThat(withData).isInstanceOf(ErrorValue.class);
        assertThat(undefined).isInstanceOf(ErrorValue.class);
    }

    @ValueSource(strings = { COMPLIANT, COMPLIANT_EXTERNAL, VALIDATE + ".valid", VALIDATE_EXTERNAL + ".valid" })
    @ParameterizedTest(name = "{0}")
    @DisplayName("With a valid schema, mismatching and undefined data are ordinary validation failures")
    void whenRequestSuppliesAValidSchemaThenItChecksTheData(String expression) {
        assertThat(run(expression, """
                {"data":"ok","guard":{"type":"string"},"schemas":[]}
                """)).isEqualTo(Value.TRUE);
        assertThat(run(expression, """
                {"data":123,"guard":{"type":"string"},"schemas":[]}
                """)).isEqualTo(Value.FALSE);
        assertThat(run(expression, """
                {"guard":{"type":"string"},"schemas":[]}
                """)).isEqualTo(Value.FALSE);
    }

    @ValueSource(strings = { COMPLIANT_EXTERNAL, VALIDATE_EXTERNAL })
    @ParameterizedTest(name = "{0}")
    @DisplayName("A malformed referenced schema is an error even if the data does not reach the reference")
    void whenRequestReferencesAnInvalidSchemaThenTheFunctionReturnsAnError(String call) {
        val result = run(call, """
                {"data":{}, "guard":{"properties":{"optional":{"$ref":"urn:sapl:middle"}}},
                 "schemas":[{"$id":"urn:sapl:middle","$ref":"urn:sapl:leaf"},
                            {"$id":"urn:sapl:leaf","minLength":-1}]}
                """);
        assertThat(result).isInstanceOfSatisfying(ErrorValue.class,
                error -> assertThat(error.message()).contains("minLength"));
    }

    @ValueSource(strings = { COMPLIANT, COMPLIANT_EXTERNAL, VALIDATE, VALIDATE_EXTERNAL })
    @ParameterizedTest(name = "{0}")
    @DisplayName("A malformed nested required keyword is a schema error")
    void whenANestedKeywordHasTheWrongShapeThenTheFunctionReturnsAnError(String call) {
        assertThat(run(call, """
                {"data":{}, "guard":{"properties":{"optional":{"required":"field"}}}, "schemas":[]}
                """)).isInstanceOfSatisfying(ErrorValue.class,
                error -> assertThat(error.message()).contains("required"));
    }

    @ValueSource(strings = { COMPLIANT_EXTERNAL, VALIDATE_EXTERNAL })
    @ParameterizedTest(name = "{0}")
    @DisplayName("A request-supplied meta-schema has to satisfy its own declared dialect")
    void whenTheDeclaredMetaSchemaIsMalformedThenTheFunctionReturnsAnError(String call) {
        val result = run(call, """
                {"data":"ok","guard":{"$schema":"urn:sapl:custom","type":"string"},
                 "schemas":[{"$id":"urn:sapl:custom","$schema":"https://json-schema.org/draft/2020-12/schema",
                             "minLength":-1}]}
                """);
        assertThat(result).isInstanceOfSatisfying(ErrorValue.class,
                error -> assertThat(error.message()).contains("urn:sapl:custom", "minLength"));
    }

    private static Value run(String expression, String request) {
        val broker = new DefaultFunctionBroker();
        broker.load(new SchemaValidationLibrary());
        return evaluate(expression).withFunctionBroker(broker).withSubject(Value.ofJson(request)).step().result();
    }

}
