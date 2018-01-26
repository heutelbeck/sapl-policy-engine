package io.sapl.spring.marshall.mapper;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

public class HttpServletRequestSerializer extends StdSerializer<HttpServletRequest> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public HttpServletRequestSerializer() {
		this(null);
	}

	public HttpServletRequestSerializer(Class<HttpServletRequest> t) {
		super(t);
	}

	@Override
	public void serialize(HttpServletRequest request, JsonGenerator jsonGenerator, SerializerProvider serializer)
			throws IOException {

		jsonGenerator.writeStartObject();
		jsonGenerator.writeStringField("uri", request.getRequestURI().toLowerCase());
		jsonGenerator.writeStringField("method", request.getMethod().toUpperCase());
		jsonGenerator.writeEndObject();

	}

}
