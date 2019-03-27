package io.sapl.spring.serialization;

import static java.lang.reflect.Modifier.isAbstract;
import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isInterface;
import static java.lang.reflect.Modifier.isNative;
import static java.lang.reflect.Modifier.isPrivate;
import static java.lang.reflect.Modifier.isProtected;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;
import static java.lang.reflect.Modifier.isStrict;
import static java.lang.reflect.Modifier.isSynchronized;
import static java.lang.reflect.Modifier.isTransient;
import static java.lang.reflect.Modifier.isVolatile;

import java.io.IOException;
import java.lang.reflect.Method;

import org.springframework.boot.jackson.JsonComponent;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

@JsonComponent
public class MethodSerializer extends JsonSerializer<Method> {

	@Override
	public void serialize(Method value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
		gen.writeStartObject();
		gen.writeStringField("name", value.getName());
		gen.writeStringField("shortSig", value.getDeclaringClass().getSimpleName() + "." + value.getName() + "(..)");
		gen.writeStringField("declaringTypeName", value.getDeclaringClass().getTypeName());
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
