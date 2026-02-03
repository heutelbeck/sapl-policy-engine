/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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

import lombok.val;
import org.aopalliance.intercept.MethodInvocation;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isPrivate;
import static java.lang.reflect.Modifier.isProtected;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;
import static java.lang.reflect.Modifier.isSynchronized;

/**
 * Jackson serializer for MethodInvocation that extracts method metadata
 * including name, declaring class, modifiers, and class hierarchy.
 */
public class MethodInvocationSerializer extends StdSerializer<MethodInvocation> {

    static final String DECLARING_TYPE_NAME = "declaringTypeName";
    static final String FINAL               = "final";
    static final String INSTANCEOF          = "instanceof";
    static final String MODIFIERS           = "modifiers";
    static final String NAME                = "name";
    static final String PRIVATE             = "private";
    static final String PROTECTED           = "protected";
    static final String PUBLIC              = "public";
    static final String SIMPLE_NAME         = "simpleName";
    static final String STATIC              = "static";
    static final String SYNCHRONIZED        = "synchronized";

    public MethodInvocationSerializer() {
        super(MethodInvocation.class);
    }

    @Override
    public void serialize(MethodInvocation value, JsonGenerator gen, SerializationContext serializers) {
        gen.writeStartObject();
        gen.writeStringProperty(NAME, value.getMethod().getName());
        gen.writeStringProperty(DECLARING_TYPE_NAME, value.getMethod().getDeclaringClass().getTypeName());
        gen.writeName(MODIFIERS);
        gen.writeStartArray();
        val mod = value.getMethod().getModifiers();

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

        gen.writeName(INSTANCEOF);
        gen.writeStartArray();
        val thiz = value.getThis();
        if (thiz != null)
            writeClassHierarchy(gen, thiz.getClass());
        else
            writeClassHierarchy(gen, value.getMethod().getDeclaringClass());
        gen.writeEndArray();

        gen.writeEndObject();
    }

    private void writeInterfaces(JsonGenerator gen, Class<?> clazz) {
        for (Class<?> i : clazz.getInterfaces()) {
            writeClassHierarchy(gen, i);
        }
    }

    private void writeClassHierarchy(JsonGenerator gen, Class<?> clazz) {
        do {
            writeClass(gen, clazz);
            writeInterfaces(gen, clazz);
            clazz = clazz.getSuperclass();
        } while (clazz != null);
    }

    private void writeClass(JsonGenerator gen, Class<?> clazz) {
        gen.writeStartObject();
        gen.writeStringProperty(NAME, clazz.getName());
        gen.writeStringProperty(SIMPLE_NAME, clazz.getSimpleName());
        gen.writeEndObject();
    }

}
