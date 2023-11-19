package io.sapl.springdatamongoreactive.sapl.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.bson.types.ObjectId;

public class OidObjectMapper extends ObjectMapper {

    /**
     * A modified ObjectMapper to handle {@link ObjectId}s.
     */
    public OidObjectMapper() {
        var module = new SimpleModule("OidModule");
        module.addSerializer(ObjectId.class, new OidSerializer());
        this.registerModule(module);
    }

}
