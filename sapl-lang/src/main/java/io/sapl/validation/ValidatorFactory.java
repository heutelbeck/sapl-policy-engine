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
package io.sapl.validation;

import java.lang.annotation.Annotation;
import java.util.function.Predicate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchemaFactory;

import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Array;
import io.sapl.api.validation.Bool;
import io.sapl.api.validation.Int;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Long;
import io.sapl.api.validation.Number;
import io.sapl.api.validation.Schema;
import io.sapl.api.validation.Text;
import io.sapl.attributes.broker.api.AttributeBrokerException;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ValidatorFactory {

    static final String INVALID_JSON_AS_SCHEMA_ERROR             = "Invalid JSON value declared as parameter validation schema.";
    static final String EXPECTED_AN_OBJECT_BUT_GOT_S             = "Expected an object, but got %s";
    static final String EXPECTED_A_LONG_INTEGER_VALUE_BUT_GOT_S  = "Expected a long integer value, but got %s";
    static final String EXPECTED_A_BOOLEAN_VALUE_BUT_GOT_S       = "Expected a Boolean value, but got %s";
    static final String EXPECTED_AN_INTEGER_BUT_GOT_S            = "Expected an integer, but got %s";
    static final String EXPECTED_A_NUMERIC_VALUE_BUT_GOT_S       = "Expected a numeric value, but got %s";
    static final String EXPECTED_AN_ARRAY_BUT_GOT_S              = "Expected an array, but got %s";
    static final String EXPECTED_A_TEXTUAL_VALUE_BUT_GOT_S       = "Expected a textual value, but got %s";
    static final String PARAMETER_NOT_COMPLY_WITH_JSON_SCHEMA    = "Parameter does not comply with supplied JSONSchema. ";
    static final String EXPECTED_COMPLYING_WITH_SCHEMA_BUT_GOT_S = "Expected a value complying with schema, but got %s.";

    private static final JsonSchemaFactory SCHEMA_FACTORY = JsonSchemaFactory.builder()
            .metaSchema(JsonMetaSchema.getV202012()).defaultMetaSchemaIri(JsonMetaSchema.getV202012().getIri()).build();

    private final ObjectMapper mapper;

    public Validator parameterValidatorFromAnnotations(Annotation[] annotations) {

        if (annotations == null || annotations.length == 0) {
            return Validator.NOOP;
        }

        Validator validator = null;
        for (var annotation : annotations) {
            Validator newValidator = null;
            if (annotation instanceof Text) {
                newValidator = validate(Val::isTextual, EXPECTED_A_TEXTUAL_VALUE_BUT_GOT_S);
            } else if (annotation instanceof Array) {
                newValidator = validate(Val::isArray, EXPECTED_AN_ARRAY_BUT_GOT_S);
            } else if (annotation instanceof Number) {
                newValidator = validate(Val::isNumber, EXPECTED_A_NUMERIC_VALUE_BUT_GOT_S);
            } else if (annotation instanceof Int) {
                newValidator = validate(Val::isInt, EXPECTED_AN_INTEGER_BUT_GOT_S);
            } else if (annotation instanceof Bool) {
                newValidator = validate(Val::isBoolean, EXPECTED_A_BOOLEAN_VALUE_BUT_GOT_S);
            } else if (annotation instanceof Long) {
                newValidator = validate(Val::isLong, EXPECTED_A_LONG_INTEGER_VALUE_BUT_GOT_S);
            } else if (annotation instanceof JsonObject) {
                newValidator = validate(Val::isObject, EXPECTED_AN_OBJECT_BUT_GOT_S);
            } else if (annotation instanceof Schema schemaAnnotation) {
                newValidator = schemaValidator(schemaAnnotation.value(), schemaAnnotation.errorText());
            }
            if (validator == null) {
                validator = newValidator;
            } else {
                validator = validator.or(newValidator);
            }
        }

        return validator;
    }

    private static Validator validate(Predicate<Val> validationPredicate, String errorMessage) {
        return v -> {
            if (!validationPredicate.test(v))
                throw new IllegalArgumentException(String.format(errorMessage, v));
        };
    }

    private Validator schemaValidator(String schema, String errorMessage) {
        JsonNode jsonSchema;
        try {
            jsonSchema = mapper.readValue(schema, JsonNode.class);
        } catch (JsonProcessingException e) {
            throw new AttributeBrokerException(INVALID_JSON_AS_SCHEMA_ERROR, e);
        }

        final var schemaValidator = SCHEMA_FACTORY.getSchema(jsonSchema);

        return validationSubject -> {
            if (validationSubject.isError() || validationSubject.isUndefined()) {
                throw new ValidationException(errorMessageOrDefault(validationSubject, errorMessage,
                        EXPECTED_COMPLYING_WITH_SCHEMA_BUT_GOT_S));
            }

            final var messages = schemaValidator.validate(validationSubject.get());

            if (messages.isEmpty()) {
                return;
            }

            if (!errorMessage.isBlank()) {
                throw new ValidationException(String.format(errorMessage, validationSubject));
            }

            final var validationErrorMessage = new StringBuilder();
            for (var message : messages) {
                validationErrorMessage.append(PARAMETER_NOT_COMPLY_WITH_JSON_SCHEMA);
                validationErrorMessage.append(message.getMessage()).append(' ');
            }
            throw new ValidationException(validationErrorMessage.toString());
        };
    }

    private String errorMessageOrDefault(Val validationSubject, String errorMessage, String defaultMessage) {
        if (errorMessage.isBlank()) {
            return String.format(defaultMessage, validationSubject);
        } else {
            return String.format(errorMessage, validationSubject);
        }
    }

}
