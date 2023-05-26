/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static com.spotify.hamcrest.jackson.JsonMatchers.jsonArray;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonObject;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonText;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;

import java.io.IOException;
import java.io.Serializable;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.security.util.MethodInvocationUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.util.TokenBuffer;

class MethodInvocationSerializerTests {

	private final ObjectMapper mapper = new ObjectMapper();

	private JsonNode serialize(MethodInvocation invocation) throws IOException {
		TokenBuffer        jsonGenerator      = new TokenBuffer(mapper, false);
		SerializerProvider serializerProvider = mapper.getSerializerProvider();
		new MethodInvocationSerializer().serialize(invocation, jsonGenerator, serializerProvider);
		jsonGenerator.flush();
		return (JsonNode) new ObjectMapper().readTree(jsonGenerator.asParser());
	}

	@Test
	void whenMethod_thenClassIsDescribedInJson() throws IOException {
		var invocation = MethodInvocationUtils.createFromClass(new TestClass(), TestClass.class, "publicVoid", null,
				null);
		var result     = serialize(invocation);
		assertThat(
				result, is(
						jsonObject()
								.where(MethodInvocationSerializer.DECLARING_TYPE_NAME,
										is(jsonText(TestClass.class.getName())))
								.where(MethodInvocationSerializer.INSTANCEOF, is(jsonArray(containsInAnyOrder(
										jsonObject().where(MethodInvocationSerializer.NAME,
												is(jsonText(TestClass.class.getName())))
												.where(MethodInvocationSerializer.SIMPLE_NAME,
														is(jsonText(TestClass.class.getSimpleName()))),
										jsonObject().where(MethodInvocationSerializer.NAME,
												is(jsonText(AbstractTestClass.class.getName())))
												.where(MethodInvocationSerializer.SIMPLE_NAME,
														is(jsonText(AbstractTestClass.class.getSimpleName()))),
										jsonObject().where(MethodInvocationSerializer.NAME,
												is(jsonText(Serializable.class.getName())))
												.where(MethodInvocationSerializer.SIMPLE_NAME,
														is(jsonText(Serializable.class.getSimpleName()))),
										jsonObject()
												.where(MethodInvocationSerializer.NAME,
														is(jsonText(Object.class.getName())))
												.where(MethodInvocationSerializer.SIMPLE_NAME,
														is(jsonText(Object.class.getSimpleName())))))))));
	}

	@Test
	void whenPublicVoid_thenMethodAndModifiersAreDescribedInJson() throws IOException {
		var invocation = MethodInvocationUtils.createFromClass(new TestClass(), TestClass.class, "publicVoid", null,
				null);
		var result     = serialize(invocation);
		assertThat(result,
				is(jsonObject().where(MethodInvocationSerializer.NAME, is(jsonText("publicVoid")))
						.where(MethodInvocationSerializer.DECLARING_TYPE_NAME, is(jsonText(TestClass.class.getName())))
						.where(MethodInvocationSerializer.MODIFIERS,
								is(jsonArray(containsInAnyOrder(jsonText(MethodInvocationSerializer.PUBLIC)))))));
	}

	@Test
	void whenStaticVoid_thenMethodAndModifiersAreDescribedInJson() throws IOException {
		var invocation = MethodInvocationUtils.createFromClass(TestClass.class, "staticVoid");
		var result     = serialize(invocation);
		assertThat(result,
				is(jsonObject().where(MethodInvocationSerializer.NAME, is(jsonText("staticVoid")))
						.where(MethodInvocationSerializer.DECLARING_TYPE_NAME, is(jsonText(TestClass.class.getName())))
						.where(MethodInvocationSerializer.MODIFIERS,
								is(jsonArray(containsInAnyOrder(jsonText(MethodInvocationSerializer.STATIC)))))));
	}

	@Test
	void whenProtectedVoid_thenMethodAndModifiersAreDescribedInJson() throws IOException {
		var invocation = MethodInvocationUtils.createFromClass(TestClass.class, "protectedVoid");
		var result     = serialize(invocation);
		assertThat(result,
				is(jsonObject().where(MethodInvocationSerializer.NAME, is(jsonText("protectedVoid")))
						.where(MethodInvocationSerializer.DECLARING_TYPE_NAME, is(jsonText(TestClass.class.getName())))
						.where(MethodInvocationSerializer.MODIFIERS,
								is(jsonArray(containsInAnyOrder(jsonText(MethodInvocationSerializer.PROTECTED)))))));
	}

	@Test
	void whenProtectedStaticVoid_thenMethodAndModifiersAreDescribedInJson() throws IOException {
		var invocation = MethodInvocationUtils.createFromClass(TestClass.class, "protectedStaticVoid");
		var result     = serialize(invocation);
		assertThat(result,
				is(jsonObject().where(MethodInvocationSerializer.NAME, is(jsonText("protectedStaticVoid")))
						.where(MethodInvocationSerializer.DECLARING_TYPE_NAME, is(jsonText(TestClass.class.getName())))
						.where(MethodInvocationSerializer.MODIFIERS,
								is(jsonArray(containsInAnyOrder(jsonText(MethodInvocationSerializer.PROTECTED),
										jsonText(MethodInvocationSerializer.STATIC)))))));
	}

	@Test
	void whenPrivateVoid_thenMethodAndModifiersAreDescribedInJson() throws IOException {
		var invocation = MethodInvocationUtils.createFromClass(TestClass.class, "privateVoid");

		var result = serialize(invocation);
		assertThat(result,
				is(jsonObject().where(MethodInvocationSerializer.NAME, is(jsonText("privateVoid")))
						.where(MethodInvocationSerializer.DECLARING_TYPE_NAME, is(jsonText(TestClass.class.getName())))
						.where(MethodInvocationSerializer.MODIFIERS,
								is(jsonArray(containsInAnyOrder(jsonText(MethodInvocationSerializer.PRIVATE)))))));
	}

	@Test
	void whenSynchronizedVoid_thenMethodAndModifiersAreDescribedInJson() throws IOException {
		var invocation = MethodInvocationUtils.createFromClass(TestClass.class, "synchronizedVoid");
		var result     = serialize(invocation);
		assertThat(result,
				is(jsonObject().where(MethodInvocationSerializer.NAME, is(jsonText("synchronizedVoid")))
						.where(MethodInvocationSerializer.DECLARING_TYPE_NAME, is(jsonText(TestClass.class.getName())))
						.where(MethodInvocationSerializer.MODIFIERS,
								is(jsonArray(containsInAnyOrder(jsonText(MethodInvocationSerializer.SYNCHRONIZED)))))));
	}

	@Test
	void whenFinalVoid_thenMethodAndModifiersAreDescribedInJson() throws IOException {
		var invocation = MethodInvocationUtils.createFromClass(TestClass.class, "finalVoid");
		var result     = serialize(invocation);
		assertThat(result,
				is(jsonObject().where(MethodInvocationSerializer.NAME, is(jsonText("finalVoid")))
						.where(MethodInvocationSerializer.DECLARING_TYPE_NAME, is(jsonText(TestClass.class.getName())))
						.where(MethodInvocationSerializer.MODIFIERS,
								is(jsonArray(containsInAnyOrder(jsonText(MethodInvocationSerializer.FINAL)))))));
	}

	@Test
	void whenNoModifiersVoid_thenMethodAndModifiersAreDescribedInJson() throws IOException {
		var invocation = MethodInvocationUtils.createFromClass(TestClass.class, "noModVoid");
		var result     = serialize(invocation);
		assertThat(result,
				is(jsonObject().where(MethodInvocationSerializer.NAME, is(jsonText("noModVoid")))
						.where(MethodInvocationSerializer.DECLARING_TYPE_NAME, is(jsonText(TestClass.class.getName())))
						.where(MethodInvocationSerializer.MODIFIERS, is(jsonArray(empty())))));
	}

	public static abstract class AbstractTestClass {

	}

	public static class TestClass extends AbstractTestClass implements Serializable {

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
