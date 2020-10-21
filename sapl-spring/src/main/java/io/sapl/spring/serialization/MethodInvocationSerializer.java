/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.boot.jackson.JsonComponent;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

@JsonComponent
public class MethodInvocationSerializer extends JsonSerializer<MethodInvocation> {

	@Override
	public void serialize(MethodInvocation value, JsonGenerator gen, SerializerProvider serializers)
			throws IOException {
		gen.writeStartObject();
		gen.writeStringField("name", value.getMethod().getName());
		gen.writeStringField("shortSig",
				value.getMethod().getDeclaringClass().getSimpleName() + "." + value.getMethod().getName() + "(..)");
		gen.writeStringField("declaringTypeName", value.getMethod().getDeclaringClass().getTypeName());
		gen.writeArrayFieldStart("modifiers");
		int mod = value.getMethod().getModifiers();
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
		gen.writeArrayFieldStart("instanceof");
		writeClassHierarchy(gen, value.getThis().getClass());
		gen.writeEndArray();

		gen.writeEndObject();
	}

	private void writeInterfaces(JsonGenerator gen, Class<?> clazz) throws IOException {
		for (Class<?> i : clazz.getInterfaces()) {
			writeClassHierarchy(gen, i);
		}
	}

	private void writeClassHierarchy(JsonGenerator gen, Class<?> clazz) throws IOException {
		do {
			writeClass(gen, clazz);
			writeInterfaces(gen, clazz);
			clazz = clazz.getSuperclass();
		} while (clazz != null);
	}

	private void writeClass(JsonGenerator gen, Class<?> clazz) throws IOException {
		gen.writeStartObject();
		gen.writeStringField("name", clazz.getName());
		gen.writeStringField("simpleName", clazz.getSimpleName());
		gen.writeStringField("canonicalName", clazz.getCanonicalName());
		gen.writeEndObject();
	}

}
