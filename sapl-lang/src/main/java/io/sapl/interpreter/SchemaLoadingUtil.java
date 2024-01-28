package io.sapl.interpreter;

import java.io.IOException;
import java.lang.reflect.Method;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SchemaLoadingUtil {
    static final String ERROR_LOADING_SCHEMA_FROM_RESOURCES = "Error loading schema from resources.";
    static final String INVALID_SCHEMA_DEFINITION           = "Invalid schema definition for attribute found. This only validated JSON syntax, not compliance with JSONSchema specification";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static JsonNode loadSchemaFromString(String attributeSchema) throws InitializationException {
        try {
            return MAPPER.readValue(attributeSchema, JsonNode.class);
        } catch (JsonProcessingException e) {
            throw new InitializationException(INVALID_SCHEMA_DEFINITION, e);
        }
    }

    public static JsonNode loadSchemaFromResource(Method method, String attributePathToSchema)
            throws InitializationException {
        try (var is = method.getDeclaringClass().getClassLoader().getResourceAsStream(attributePathToSchema)) {
            if (is == null) {
                throw new IOException("Schema file not found " + attributePathToSchema);
            }
            return MAPPER.readValue(is, JsonNode.class);
        } catch (IOException e) {
            throw new InitializationException(ERROR_LOADING_SCHEMA_FROM_RESOURCES, e);
        }
    }

}
