/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isPrivate;
import static java.lang.reflect.Modifier.isProtected;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;
import static java.lang.reflect.Modifier.isSynchronized;

import java.io.IOException;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.boot.jackson.JsonComponent;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

@JsonComponent
public class MethodInvocationSerializer extends JsonSerializer<MethodInvocation> {

	static final String SIMPLE_NAME = "simpleName";
	static final String INSTANCEOF = "instanceof";
	static final String SYNCHRONIZED = "synchronized";
	static final String STATIC = "static";
	static final String PUBLIC = "public";
	static final String PROTECTED = "protected";
	static final String PRIVATE = "private";
	static final String FINAL = "final";
	static final String MODIFIERS = "modifiers";
	static final String DECLARING_TYPE_NAME = "declaringTypeName";
	static final String NAME = "name";

	@Override
	public void serialize(MethodInvocation value, JsonGenerator gen, SerializerProvider serializers)
			throws IOException {
		gen.writeStartObject();
		gen.writeStringField(NAME, value.getMethod().getName());
		gen.writeStringField(DECLARING_TYPE_NAME, value.getMethod().getDeclaringClass().getTypeName());
		gen.writeArrayFieldStart(MODIFIERS);
		int mod = value.getMethod().getModifiers();

		if (isFinal(mod)) {
			gen.writeString(FINAL);
		}
		if (isPrivate(mod)) {
			gen.writeString(PRIVATE);
		}
		if (isProtected(mod)) {
			gen.writeString(PROTECTED);
		}
		if (isPublic(mod)) {
			gen.writeString(PUBLIC);
		}
		if (isStatic(mod)) {
			gen.writeString(STATIC);
		}
		if (isSynchronized(mod)) {
			gen.writeString(SYNCHRONIZED);
		}

		gen.writeEndArray();

		gen.writeArrayFieldStart(INSTANCEOF);
		if (value.getThis() != null)
			writeClassHierarchy(gen, value.getThis().getClass());
		else
			writeClassHierarchy(gen, value.getMethod().getDeclaringClass());
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
		}
		while (clazz != null);
	}

	private void writeClass(JsonGenerator gen, Class<?> clazz) throws IOException {
		gen.writeStartObject();
		gen.writeStringField(NAME, clazz.getName());
		gen.writeStringField(SIMPLE_NAME, clazz.getSimpleName());
		gen.writeEndObject();
	}

}
