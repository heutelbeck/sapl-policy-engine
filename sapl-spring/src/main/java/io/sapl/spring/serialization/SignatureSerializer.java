package io.sapl.spring.serialization;

import java.io.IOException;

import org.aspectj.lang.Signature;
import org.springframework.boot.jackson.JsonComponent;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import static java.lang.reflect.Modifier.*;

@JsonComponent
public class SignatureSerializer extends JsonSerializer<Signature> {

	@Override
	public void serialize(Signature value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
		gen.writeStartObject();
		gen.writeStringField("sig", value.toString());
		gen.writeStringField("shortSig", value.toShortString());
		gen.writeStringField("longSig", value.toLongString());
		gen.writeStringField("name", value.getName());
		gen.writeStringField("declaringTypeName", value.getDeclaringTypeName());
		gen.writeArrayFieldStart("modifiers");
		int mod = value.getModifiers();
		if (isAbstract(mod)) {
			gen.writeString("abstract");
		}
		if (isFinal(mod)) {
			gen.writeString("final");
		}
		if (isInterface(mod)) {
			gen.writeString("interface");
		}
		if (isNative(mod)) {
			gen.writeString("native");
		}
		if (isPrivate(mod)) {
			gen.writeString("private");
		}
		if (isProtected(mod)) {
			gen.writeString("protected");
		}
		if (isPublic(mod)) {
			gen.writeString("public");
		}
		if (isStatic(mod)) {
			gen.writeString("static");
		}
		if (isStrict(mod)) {
			gen.writeString("strict");
		}
		if (isSynchronized(mod)) {
			gen.writeString("synchronized");
		}
		if (isTransient(mod)) {
			gen.writeString("transient");
		}
		if (isVolatile(mod)) {
			gen.writeString("volatile");
		}
		gen.writeEndArray();
		gen.writeEndObject();
	}

}
