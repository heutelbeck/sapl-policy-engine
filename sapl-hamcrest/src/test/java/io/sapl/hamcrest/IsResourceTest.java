/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.hamcrest;

import static com.spotify.hamcrest.jackson.JsonMatchers.jsonText;
import static io.sapl.hamcrest.Matchers.anyResource;
import static io.sapl.hamcrest.Matchers.isResource;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.hamcrest.StringDescription;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;

class IsResourceTest {

	@Test
	public void test() {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode resource = mapper.createObjectNode();
		resource.put("foo", "bar");
		AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, Optional.of(resource), null, null);

		var sut = isResource(resource);

		assertThat(dec, is(sut));
	}

	@Test
	public void test_neg() {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode expectedResource = mapper.createObjectNode();
		expectedResource.put("foo", "bar");
		ObjectNode actualResource = mapper.createObjectNode();
		actualResource.put("xxx", "yyy");
		AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, Optional.of(actualResource), null, null);

		var sut = isResource(expectedResource);

		assertThat(dec, not(is(sut)));
	}

	@Test
	public void test_nullDecision() {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode resource = mapper.createObjectNode();
		resource.put("foo", "bar");

		var sut = isResource(resource);

		assertThat(null, not(is(sut)));
	}

	@Test
	public void test_emptyResource() {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode resource = mapper.createObjectNode();
		resource.put("foo", "bar");
		AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), null, null);

		var sut = isResource(resource);

		assertThat(dec, not(is(sut)));
	}

	@Test
	public void test_emptyMatcher() {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode resource = mapper.createObjectNode();
		resource.put("foo", "bar");
		AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, Optional.of(resource), null, null);

		var sut = anyResource();

		assertThat(dec, is(sut));
	}

	@Test
	public void test_nullJsonNode() {
		assertThrows(NullPointerException.class, () -> isResource((ObjectNode) null));
	}

	@Test
	void testDescriptionEmptyMatcher() {
		var sut = isResource();
		final StringDescription description = new StringDescription();
		sut.describeTo(description);
		assertThat(description.toString(), is("a resource with any JsonNode"));
	}

	@Test
	void testDescriptionMatcher() {
		var sut = isResource(jsonText("value"));
		final StringDescription description = new StringDescription();
		sut.describeTo(description);
		assertThat(description.toString(), is("a resource with a text node with value that is \"value\""));
	}

}
