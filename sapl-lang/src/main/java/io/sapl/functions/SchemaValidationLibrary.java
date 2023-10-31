package io.sapl.functions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaException;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Text;

@FunctionLibrary(name = SchemaValidationLibrary.NAME, description = SchemaValidationLibrary.DESCRIPTION)
public class SchemaValidationLibrary {

    public static final String NAME = "schemaValidation";

    public static final String DESCRIPTION = "This library contains the mandatory functions for testing the compliance of a JSON object with a JSON schema.";

    private static final String ISCOMPLIANTWITHSCHEMA_VAL_DOC = "isCompliantWithSchema(jsonObject, schema):" +
            "Assumes that schema is a String that can be converted into a valid JSON schema, and jsonObject is a valid JSON Object or a String that can be converted to a JSON node." +
            "If schema cannot be converted to a JSON schema, or jsonObject cannot be converted to a JSON node, returns an error." +
            "If jsonObject is compliant with schema, returns TRUE, else returns FALSE.";

    private static final SpecVersion.VersionFlag SPEC_VERSION_JSON_SCHEMA = SpecVersion.VersionFlag.V7;

    private static final String BOOL_SCHEMA = "{ \"type\": \"boolean\" }";


    @Function(docs = ISCOMPLIANTWITHSCHEMA_VAL_DOC, schema = BOOL_SCHEMA)
    public static Val isCompliantWithSchema(@JsonObject @Text Val jsonObject, @Text Val schema) {
        JsonNode node;
        Val isCompliant;
        String schemaAsString = schema.get().asText();
        if(jsonObject.isTextual()){
            var jsonAsText = jsonObject.get().asText();
            try {
                node = jsonNodeFromString(jsonAsText);
            } catch (JsonProcessingException e){
                node = JsonNodeFactory.instance.textNode(jsonAsText);
            }
        } else {
            node = jsonObject.getJsonNode();
        }

        try {
            isCompliant = isCompliantWithSchema(node, schemaAsString);
        } catch (JsonSchemaException e){
            return Val.error(e);
        }
        return isCompliant;
    }

    //@Function(docs = ISCOMPLIANTWITHSCHEMA_DOC, schema = BOOL_SCHEMA)
    private static Val isCompliantWithSchema(JsonNode node, String schema) throws JsonSchemaException {
        JsonSchema jsonSchema = jsonSchemaFromString(schema);
        return Val.of(jsonSchema.validate(node).isEmpty());
    }

    private static JsonNode jsonNodeFromString(String node) throws JsonProcessingException {
        return new ObjectMapper().readTree(node);
    }

    private static JsonSchema jsonSchemaFromString(String schema) throws JsonSchemaException {
        JsonSchema jsonSchema;
        var jsonSchemaFactory = JsonSchemaFactory.getInstance(SPEC_VERSION_JSON_SCHEMA);
        jsonSchema = jsonSchemaFactory.getSchema(schema);
        return jsonSchema;
    }

}
