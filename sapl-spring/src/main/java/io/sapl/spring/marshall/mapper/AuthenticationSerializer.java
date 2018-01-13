package io.sapl.spring.marshall.mapper;

import java.io.IOException;
import java.util.Collection;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

public class AuthenticationSerializer extends StdSerializer<Authentication> {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public AuthenticationSerializer() {
		this(null);
	}
	
	public AuthenticationSerializer (Class<Authentication> t) {
        super(t);
    }
	
	@Override
    public void serialize(Authentication authentication, JsonGenerator jsonGenerator, SerializerProvider serializer) throws IOException {

    	jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField("name", authentication.getName());
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        jsonGenerator.writeArrayFieldStart("authorities");
        for ( GrantedAuthority authority : authorities) {
        	jsonGenerator.writeStringField("authority", authority.toString());
        }
        jsonGenerator.writeEndArray();
        jsonGenerator.writeEndObject();

    }

}
