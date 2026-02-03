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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serial;
import java.io.Serializable;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.util.MethodInvocationUtils;

import io.sapl.api.SaplVersion;
import lombok.val;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

class MethodInvocationSerializerTests {

    private static JsonMapper mapper;

    @BeforeAll
    static void setup() {
        val module = new SimpleModule();
        module.addSerializer(MethodInvocation.class, new MethodInvocationSerializer());
        mapper = JsonMapper.builder().addModule(module).build();
    }

    private JsonNode serialize(MethodInvocation invocation) throws JacksonException {
        return mapper.valueToTree(invocation);
    }

    @Test
    void whenMethod_thenClassIsDescribedInJson() throws JacksonException {
        val invocation = MethodInvocationUtils.createFromClass(new TestClass(), TestClass.class, "publicVoid", null,
                null);
        val result     = serialize(invocation);
        assertThat(result.get(MethodInvocationSerializer.DECLARING_TYPE_NAME).asString())
                .isEqualTo(TestClass.class.getName());
        assertThat(result.get(MethodInvocationSerializer.INSTANCEOF)).isNotNull();
    }

    @Test
    void whenPublicVoid_thenMethodAndModifiersAreDescribedInJson() throws JacksonException {
        val invocation = MethodInvocationUtils.createFromClass(new TestClass(), TestClass.class, "publicVoid", null,
                null);
        val result     = serialize(invocation);
        assertThat(result.get(MethodInvocationSerializer.NAME).asString()).isEqualTo("publicVoid");
        assertThat(result.get(MethodInvocationSerializer.DECLARING_TYPE_NAME).asString())
                .isEqualTo(TestClass.class.getName());
        assertThat(result.get(MethodInvocationSerializer.MODIFIERS).toString()).contains("public");
    }

    @Test
    void whenStaticVoid_thenMethodAndModifiersAreDescribedInJson() throws JacksonException {
        val invocation = MethodInvocationUtils.createFromClass(TestClass.class, "staticVoid");
        val result     = serialize(invocation);
        assertThat(result.get(MethodInvocationSerializer.NAME).asString()).isEqualTo("staticVoid");
        assertThat(result.get(MethodInvocationSerializer.MODIFIERS).toString()).contains("static");
    }

    @Test
    void whenProtectedVoid_thenMethodAndModifiersAreDescribedInJson() throws JacksonException {
        val invocation = MethodInvocationUtils.createFromClass(TestClass.class, "protectedVoid");
        val result     = serialize(invocation);
        assertThat(result.get(MethodInvocationSerializer.NAME).asString()).isEqualTo("protectedVoid");
        assertThat(result.get(MethodInvocationSerializer.MODIFIERS).toString()).contains("protected");
    }

    @Test
    void whenProtectedStaticVoid_thenMethodAndModifiersAreDescribedInJson() throws JacksonException {
        val invocation = MethodInvocationUtils.createFromClass(TestClass.class, "protectedStaticVoid");
        val result     = serialize(invocation);
        assertThat(result.get(MethodInvocationSerializer.NAME).asString()).isEqualTo("protectedStaticVoid");
        val modifiers = result.get(MethodInvocationSerializer.MODIFIERS).toString();
        assertThat(modifiers).contains("protected").contains("static");
    }

    @Test
    void whenPrivateVoid_thenMethodAndModifiersAreDescribedInJson() throws JacksonException {
        val invocation = MethodInvocationUtils.createFromClass(TestClass.class, "privateVoid");
        val result     = serialize(invocation);
        assertThat(result.get(MethodInvocationSerializer.NAME).asString()).isEqualTo("privateVoid");
        assertThat(result.get(MethodInvocationSerializer.MODIFIERS).toString()).contains("private");
    }

    @Test
    void whenSynchronizedVoid_thenMethodAndModifiersAreDescribedInJson() throws JacksonException {
        val invocation = MethodInvocationUtils.createFromClass(TestClass.class, "synchronizedVoid");
        val result     = serialize(invocation);
        assertThat(result.get(MethodInvocationSerializer.NAME).asString()).isEqualTo("synchronizedVoid");
        assertThat(result.get(MethodInvocationSerializer.MODIFIERS).toString()).contains("synchronized");
    }

    @Test
    void whenFinalVoid_thenMethodAndModifiersAreDescribedInJson() throws JacksonException {
        val invocation = MethodInvocationUtils.createFromClass(TestClass.class, "finalVoid");
        val result     = serialize(invocation);
        assertThat(result.get(MethodInvocationSerializer.NAME).asString()).isEqualTo("finalVoid");
        assertThat(result.get(MethodInvocationSerializer.MODIFIERS).toString()).contains("final");
    }

    @Test
    void whenNoModifiersVoid_thenMethodAndModifiersAreDescribedInJson() throws JacksonException {
        val invocation = MethodInvocationUtils.createFromClass(TestClass.class, "noModVoid");
        val result     = serialize(invocation);
        assertThat(result.get(MethodInvocationSerializer.NAME).asString()).isEqualTo("noModVoid");
        assertThat(result.get(MethodInvocationSerializer.MODIFIERS)).isEmpty();
    }

    public abstract static class AbstractTestClass {
    }

    public static class TestClass extends AbstractTestClass implements Serializable {

        @Serial
        private static final long serialVersionUID = SaplVersion.VERSION_UID;

        public void publicVoid() {
        }

        static void staticVoid() {
        }

        protected static void protectedStaticVoid() {
        }

        protected void protectedVoid() {
        }

        @SuppressWarnings("unused")
        private void privateVoid() {
        }

        synchronized void synchronizedVoid() {
        }

        final void finalVoid() {
        }

        void noModVoid() {
        }

    }

}
