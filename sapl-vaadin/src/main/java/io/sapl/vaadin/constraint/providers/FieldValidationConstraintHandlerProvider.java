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
package io.sapl.vaadin.constraint.providers;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.data.binder.Validator;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.spring.pep.constraints.ConstraintHandler;
import io.sapl.spring.pep.constraints.ConstraintHandlerProvider;
import io.sapl.spring.pep.constraints.ScopedConstraintHandler;
import io.sapl.spring.pep.constraints.SignalType;
import io.sapl.vaadin.constraint.VaadinSignals;
import lombok.SneakyThrows;

/**
 * This Constraint Handler Provider can be used to apply validations on a field
 * based on SAPL Obligations. Use the method
 * "{addConstraintHandlerProvider(...)}" on your builder to register a new
 * instance of this class. Fields can be registered with the method
 * {@link #bindField(HasValue)}.
 * <p>
 * This provider manages constrains of type "saplVaadin" with id "validation",
 * here an example:
 *
 * <pre>
 * obligation {
 *              "type": "saplVaadin",
 *              "id" : "validation",
 *              "fields": {
 *                "fieldA": {
 *                  {@code "$schema": "http://json-schema.org/draft-07/schema#",}
 *                  "type": "number",
 *                  "maximum": 20,
 *                  "message": "This message is displayed as validator message"
 *                }
 *              }
 *            }
 * </pre>
 * <p>
 * Each field defined in the constraint "fields" dict should be bound using
 * {@link #bindField(HasValue)}. We use a slightly modified version of the
 * JsonSchema define validations in the policy. Each field entry is transformed
 * to a single "properties" entry following the same syntax. The additional
 * "$schema" keyword can be used to define the json schema version for this
 * validation. The additional "message" keyword can be used to define a custom
 * validator error message.
 */
public class FieldValidationConstraintHandlerProvider implements ConstraintHandlerProvider {
    private final Binder<?>                   binder;
    private final Object                      objectWithMemberFields;
    private static final JsonNodeFactory      JSON                              = JsonNodeFactory.instance;
    final JsonMapper                          objectMapper;
    private final Map<String, Schema>         jsonValidationSchemaFromFieldName = new HashMap<>();
    private final Map<String, String>         validationMessageFromFieldName    = new HashMap<>();
    private final Map<String, Boolean>        isBoundFromFieldName              = new HashMap<>();
    private static final SpecificationVersion DEFAULT_JSON_SCHEMA_VERSION       = SpecificationVersion.DRAFT_2020_12;

    public FieldValidationConstraintHandlerProvider(Binder<?> binder, Object objectWithMemberFields) {
        this.binder                 = binder;
        this.objectWithMemberFields = objectWithMemberFields;
        this.objectMapper           = JsonMapper.builder().addModule(new io.sapl.api.model.jackson.SaplJacksonModule())
                .build();
    }

    public FieldValidationConstraintHandlerProvider(Binder<?> binder,
            Object objectWithMemberFields,
            JsonMapper objectMapper) {
        this.binder                 = binder;
        this.objectWithMemberFields = objectWithMemberFields;
        this.objectMapper           = objectMapper;
    }

    /**
     * This method is used by
     * {@link io.sapl.vaadin.VaadinConstraintEnforcementService} to get the handlers
     * for each constraint in the decision. The handler is a consumer functional
     * interface consuming the ui related to the component specified in the builder.
     *
     * @param constraint Constraint from the actual decision
     * @param supportedSignals Signals fired by the enforcing PEP
     * @return Scoped constraint handlers for the constraint
     */
    @Override
    public List<ScopedConstraintHandler> getConstraintHandlers(Value constraint, Set<SignalType> supportedSignals) {
        if (!isResponsible(constraint) || !supportedSignals.contains(VaadinSignals.UI_SIGNAL)) {
            return List.of();
        }
        updateValidationSchemes((ObjectValue) constraint);
        ConstraintHandler.Consumer<UI> handler = ui -> ui.access(binder::validate);
        return List.of(new ScopedConstraintHandler(handler, VaadinSignals.UI_SIGNAL, 0));
    }

    public boolean isResponsible(Value constraint) {
        if (!(constraint instanceof ObjectValue obj)) {
            return false;
        }
        return obj.containsKey("type") && obj.get("type") instanceof TextValue(var type) && "saplVaadin".equals(type)
                && obj.containsKey("id") && obj.get("id") instanceof TextValue(var id) && "validation".equals(id);
    }

    /**
     * Update the internal structures {@link #jsonValidationSchemaFromFieldName} and
     * {@link #validationMessageFromFieldName} that are used by the validator if
     * field is bound (see {@link #isBoundFromFieldName})
     *
     * @param constraint from the actual decision
     */
    private void updateValidationSchemes(ObjectValue constraint) {
        if (constraint.containsKey("fields") && constraint.get("fields") instanceof ObjectValue fields) {
            fields.forEach(this::updateFieldValidation);
        }
    }

    private void updateFieldValidation(String fieldName, Value fieldValue) {
        if (isBoundFromFieldName.containsKey(fieldName) && fieldValue instanceof ObjectValue fieldPolicy) {
            var fieldPolicyJson = objectMapper.convertValue(fieldPolicy, JsonNode.class);
            var validationJson  = JSON.objectNode();
            validationJson.set("properties", JSON.objectNode().set(fieldName, fieldPolicyJson));

            var schemaVersion  = extractSchemaVersion(fieldPolicy);
            var schemaRegistry = SchemaRegistry.withDefaultDialect(schemaVersion);

            jsonValidationSchemaFromFieldName.put(fieldName, schemaRegistry.getSchema(validationJson));

            if (fieldPolicy.containsKey("message") && fieldPolicy.get("message") instanceof TextValue(var message)) {
                validationMessageFromFieldName.put(fieldName, message);
            }
        } else if (!isBoundFromFieldName.containsKey(fieldName)) {
            throw new AccessDeniedException("Failed to validate field \"" + fieldName + "\". It has not been bound");
        }
    }

    private static SpecificationVersion extractSchemaVersion(ObjectValue fieldPolicy) {
        if (fieldPolicy.containsKey("$schema") && fieldPolicy.get("$schema") instanceof TextValue(var schemaId)) {
            return SpecificationVersion.fromDialectId(schemaId).orElse(DEFAULT_JSON_SCHEMA_VERSION);
        }
        return DEFAULT_JSON_SCHEMA_VERSION;
    }

    /**
     * This function adds a sapl schema based field validator to the binder.
     *
     * @param <F> type of the field value
     * @param field requested to bind
     * @return this FieldValidationConstraintHandlerProvider instance
     */
    public <F> FieldValidationConstraintHandlerProvider bindField(HasValue<?, F> field) {
        getFieldsInDeclareOrder(objectWithMemberFields.getClass()).stream().filter(Objects::nonNull)
                .filter(memberField -> memberField.getType().isAssignableFrom(field.getClass()))
                .filter(memberField -> isFieldBound(memberField, field, objectWithMemberFields))
                .forEach(memberField -> {
                    binder.forMemberField(field).withValidator(getSchemaBasedFieldValidator(memberField));
                    isBoundFromFieldName.put(memberField.getName(), Boolean.TRUE);
                });
        return this;
    }

    private <F> Validator<F> getSchemaBasedFieldValidator(Field memberField) {
        return (value, context) -> {
            var fieldName        = memberField.getName();
            var validationSchema = jsonValidationSchemaFromFieldName.get(fieldName);
            if (validationSchema != null) {
                var convertedValue = objectMapper.convertValue(value, JsonNode.class);
                if (value instanceof LocalTime localTime) {
                    convertedValue = objectMapper.convertValue(localTime.format(DateTimeFormatter.ISO_TIME),
                            JsonNode.class);
                } else if (value instanceof LocalDateTime localDateTime) {
                    convertedValue = objectMapper
                            .convertValue(localDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), JsonNode.class);
                }
                var dataJsonToValidate = JSON.objectNode().set(fieldName, convertedValue);
                var result             = validationSchema.validate(dataJsonToValidate);
                if (!result.isEmpty()) {
                    var errorMessage = validationMessageFromFieldName.get(fieldName);
                    if (errorMessage != null) {
                        return ValidationResult.error(errorMessage);
                    } else {
                        return ValidationResult.error(result.getFirst().getMessage());
                    }
                }
            }
            return ValidationResult.ok();
        };
    }

    /**
     * Checks that the field is bound correctly
     *
     * @param memberField from the class
     * @param field from the instance
     * @param objectWithMemberFields instance holding field
     * @param <F> the value type of the field
     * @return returns true of the field is bound
     */
    <F> boolean isFieldBound(Field memberField, HasValue<?, F> field, Object objectWithMemberFields) {
        try {
            HasValue<?, ?> boundField = (HasValue<?, ?>) getMemberFieldValue(memberField, objectWithMemberFields);
            return boundField.equals(field);
        } catch (Exception e) {
            return false;
        }
    }

    @SneakyThrows
    private Object getMemberFieldValue(Field memberField, Object objectWithMemberFields) {
        memberField.setAccessible(true);
        try {
            return memberField.get(objectWithMemberFields);
        } finally {
            memberField.setAccessible(false);
        }
    }

    /**
     * Returns an array containing {@link Field} objects reflecting all the fields
     * of the class or interface represented by this Class object. The elements in
     * the array returned are sorted in declare order from subclass to super class.
     *
     * @param searchClass class to introspect
     * @return list of all fields in the class considering hierarchy
     */
    private List<Field> getFieldsInDeclareOrder(Class<?> searchClass) {
        List<Field> memberFieldInOrder = new ArrayList<>();

        while (searchClass != null) {
            memberFieldInOrder.addAll(Arrays.asList(searchClass.getDeclaredFields()));
            searchClass = searchClass.getSuperclass();
        }
        return memberFieldInOrder;
    }
}
