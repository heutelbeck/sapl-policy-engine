package io.sapl.springdatamongoreactive.sapl.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.bson.types.ObjectId;

import java.io.IOException;

/**
 * Used in conjunction with OidObjectMapper.
 */
public class OidSerializer extends JsonSerializer<ObjectId> {

    /**
     * When converting to a known object and the occurrence of an {@link ObjectId},
     * it is automatically transformed to a string.
     */
    @Override
    public void serialize(ObjectId objectId, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        jsonGenerator.writeString(objectId.toString());
    }
}
