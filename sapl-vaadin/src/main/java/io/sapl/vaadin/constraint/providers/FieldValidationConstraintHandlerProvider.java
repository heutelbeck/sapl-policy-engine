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
import java.util.function.Consumer;

import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.SpecVersionDetector;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.data.binder.Validator;

import io.sapl.spring.constraints.api.ConsumerConstraintHandlerProvider;
import io.sapl.vaadin.VaadinConstraintEnforcementService;

/**
 * This Constraint Handler Provider can be used to apply validations on a field
 * based on SAPL Obligations. Use the method
 * "{addConsumerConstraintHandlerProviders()}" on your builder to register a new
 * instance of this class. Fields can be registered with the method
 * {@link #bindField(HasValue)}.
 *
 * This provider manages constrains of type "saplVaadin" with id "validation",
 * here an example: ... obligation { "type": "saplVaadin", "id" : "validation",
 * "fields": { "fieldA": { "$schema": "http://json-schema.org/draft-07/schema#",
 * "type": "number", "maximum": 20, "message": "This message is displayed as
 * validator message" } } } ...
 *
 * Each field defined in the constraint "fields" dict should be bound using
 * {@link #bindField(HasValue)}. We use a slightly modified version of the
 * JsonSchema define validations in the policy. Each field entry is transformed
 * to a single "properties" entry following the same syntax. The additional
 * "$schema" keyword can be used to define the json schema version for this
 * validation. The additional "message" keyword can be used to define a custom
 * validator error message.
 */
public class FieldValidationConstraintHandlerProvider implements ConsumerConstraintHandlerProvider<UI> {
	private final Binder<?>               binder;
	private final Object                  objectWithMemberFields;
	private final JsonNodeFactory         JSON                              = JsonNodeFactory.instance;
	final ObjectMapper                    objectMapper;
	private final Map<String, JsonSchema> jsonValidationSchemaFromFieldName = new HashMap<>();
	private final Map<String, String>     validationMessageFromFieldName    = new HashMap<>();
	private final Map<String, Boolean>    isBoundFromFieldName              = new HashMap<>();
	final SpecVersion.VersionFlag         DEFAULT_JSON_SCHEMA_VERSION       = SpecVersion.VersionFlag.V7;

	public FieldValidationConstraintHandlerProvider(Binder<?> binder, Object objectWithMemberFields) {
		this.binder                 = binder;
		this.objectWithMemberFields = objectWithMemberFields;
		this.objectMapper           = new ObjectMapper();
		objectMapper.findAndRegisterModules();
	}

	public FieldValidationConstraintHandlerProvider(Binder<?> binder, Object objectWithMemberFields,
			ObjectMapper objectMapper) {
		this.binder                 = binder;
		this.objectWithMemberFields = objectWithMemberFields;
		this.objectMapper           = objectMapper;
	}

	/**
	 * This method is used by {@link VaadinConstraintEnforcementService} to get a
	 * handler for each constraint in the decision. The handler is a consumer
	 * functional interface consuming the ui related to the component specified in
	 * the builder.
	 * 
	 * @param constraint Constraint from the actual decision
	 * @return Constraint handler instance
	 */
	@Override
	public Consumer<UI> getHandler(JsonNode constraint) {
		if (constraint == null) {
			return null;
		} else {
			updateValidationSchemes(constraint);
			return (ui) -> ui.access(binder::validate);
		}
	}

	@Override
	public boolean isResponsible(JsonNode constraint) {
		if (constraint == null) {
			return false;
		}
		return constraint.has("type") && "saplVaadin".equals(constraint.get("type").asText()) &&
				constraint.has("id") && "validation".equals(constraint.get("id").asText());
	}

	@Override
	public Class<UI> getSupportedType() {
		return null;
	}

	/**
	 * Update the internal structures {@link #jsonValidationSchemaFromFieldName} and
	 * {@link #validationMessageFromFieldName} that are used by the validator if
	 * field is bound (see {@link #isBoundFromFieldName})
	 * 
	 * @param constraint from the actual decision
	 */
	private void updateValidationSchemes(JsonNode constraint) {
		if (constraint.has("fields")) {
			constraint.get("fields").fields().forEachRemaining(
					fieldPolicy -> {
						String fieldName = fieldPolicy.getKey();
						if (isBoundFromFieldName.containsKey(fieldName)) {
							var validationJson = JSON.objectNode();
							validationJson.set("properties", JSON.objectNode()
									.set(fieldName, fieldPolicy.getValue()));

							var jsonSchemaFactory = fieldPolicy.getValue().has("$schema")
									? JsonSchemaFactory.getInstance(SpecVersionDetector.detect(fieldPolicy.getValue()))
									: JsonSchemaFactory.getInstance(DEFAULT_JSON_SCHEMA_VERSION);

							jsonValidationSchemaFromFieldName.put(fieldName,
									jsonSchemaFactory.getSchema(validationJson));

							if (fieldPolicy.getValue().has("message")) {
								validationMessageFromFieldName.put(fieldName,
										fieldPolicy.getValue().get("message").asText());
							}
						} else {
							throw new AccessDeniedException(
									"Failed to validate field \"" + fieldName + "\". It has not been bound");
						}
					});
		}
	}

	/**
	 * This function adds a sapl schema based field validator to the binder.
	 *
	 * @param <FIELDVALUE> type of the field value
	 * @param field        requested to bind
	 * @return this FieldValidationConstraintHandlerProvider instance
	 */
	public <FIELDVALUE> FieldValidationConstraintHandlerProvider bindField(HasValue<?, FIELDVALUE> field) {
		getFieldsInDeclareOrder(objectWithMemberFields.getClass())
				.stream()
				.filter(Objects::nonNull)
				.filter(memberField -> memberField.getType().isAssignableFrom(field.getClass()))
				.filter(memberField -> isFieldBound(memberField, field, objectWithMemberFields))
				.forEach(memberField -> {
					binder.forMemberField(field)
							.withValidator(
									getSchemaBasedFieldValidator(memberField));
					isBoundFromFieldName.put(memberField.getName(), Boolean.TRUE);
				});
		return this;
	}

	private <FIELDVALUE> Validator<FIELDVALUE> getSchemaBasedFieldValidator(Field memberField) {
		return (value, context) -> {
			var fieldName        = memberField.getName();
			var validationSchema = jsonValidationSchemaFromFieldName.get(fieldName);
			if (validationSchema != null) {
				var convertedValue = objectMapper.convertValue(value, JsonNode.class);
				if (value instanceof LocalTime) {
					convertedValue = objectMapper.convertValue(((LocalTime) value).format(DateTimeFormatter.ISO_TIME),
							JsonNode.class);
				} else if (value instanceof LocalDateTime) {
					convertedValue = objectMapper.convertValue(
							((LocalDateTime) value).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), JsonNode.class);
				}
				var dataJsonToValidate = JSON.objectNode()
						.set(fieldName, convertedValue);
				var result             = validationSchema.validate(dataJsonToValidate);
				if (!result.isEmpty()) {
					var errorMessage = validationMessageFromFieldName.get(fieldName);
					if (errorMessage != null) {
						return ValidationResult.error(errorMessage);
					} else {
						return ValidationResult.error(result.stream().findFirst().get().getMessage());
					}
				}
			}
			return ValidationResult.ok();
		};
	}

	/**
	 * Checks that the field is bound correctly
	 * 
	 * @param memberField            from the class
	 * @param field                  from the instance
	 * @param objectWithMemberFields instance holding field
	 * @param <FIELDVALUE>           the value type of the field
	 * @return returns true of the field is bound
	 */
	<FIELDVALUE> boolean isFieldBound(
			Field memberField,
			HasValue<?, FIELDVALUE> field,
			Object objectWithMemberFields) {
		try {
			HasValue<?, ?> boundField = (HasValue<?, ?>) getMemberFieldValue(
					memberField, objectWithMemberFields);
			return boundField.equals(field);
		} catch (Exception e) {
			return false;
		}
	}

	private Object getMemberFieldValue(Field memberField, Object objectWithMemberFields) {
		memberField.setAccessible(true);
		try {
			return memberField.get(objectWithMemberFields);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new RuntimeException(e);
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
			memberFieldInOrder
					.addAll(Arrays.asList(searchClass.getDeclaredFields()));
			searchClass = searchClass.getSuperclass();
		}
		return memberFieldInOrder;
	}
}
