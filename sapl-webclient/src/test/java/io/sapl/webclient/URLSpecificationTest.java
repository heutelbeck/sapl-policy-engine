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
package io.sapl.webclient;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

public class URLSpecificationTest {

	private static final String FULL_URL = "https://admin:secret@localhost:8081/rest/api/data?name=foo&value=bar#anchor";

	private static final String SIMPLE_URL = "http://localhost/rest/api/data";

	private URLSpecification fullSpec;

	private URLSpecification simpleSpec;

	@Before
	public void setUp() throws MalformedURLException {
		fullSpec = URLSpecification.from(FULL_URL);
		simpleSpec = URLSpecification.from(SIMPLE_URL);
	}

	@Test
	public void from_fullUrl_shouldCreateCorrectSpec() {
		final Map<String, String> expectedQueryParams = new HashMap<>();
		expectedQueryParams.put("name", "foo");
		expectedQueryParams.put("value", "bar");

		assertThat(fullSpec.getScheme(), is("https"));
		assertThat(fullSpec.getUser(), is("admin"));
		assertThat(fullSpec.getPassword(), is("secret"));
		assertThat(fullSpec.getHost(), is("localhost"));
		assertThat(fullSpec.getPort(), is(8081));
		assertThat(fullSpec.getPath(), is("/rest/api/data"));
		assertThat(fullSpec.getRawQuery(), is("name=foo&value=bar"));
		assertThat(fullSpec.getQueryParameters(), is(expectedQueryParams));
		assertThat(fullSpec.getFragment(), is("anchor"));
	}

	@Test
	public void from_simpleUrl_shouldCreateCorrectSpec() {
		assertThat(simpleSpec.getScheme(), is("http"));
		assertNull(simpleSpec.getUser());
		assertNull(simpleSpec.getPassword());
		assertThat(simpleSpec.getHost(), is("localhost"));
		assertNull(simpleSpec.getPort());
		assertThat(simpleSpec.getPath(), is("/rest/api/data"));
		assertNull(simpleSpec.getRawQuery());
		assertNull(simpleSpec.getQueryParameters());
		assertNull(simpleSpec.getFragment());
	}

	@Test
	public void asString_shouldReturnCorrectUrl() {
		assertThat(fullSpec.asString(), is(FULL_URL));
		assertThat(simpleSpec.asString(), is(SIMPLE_URL));
	}

	@Test
	public void baseUrl_shouldReturnCorrectBaseUrl() {
		assertThat(fullSpec.baseUrl(), is("https://admin:secret@localhost:8081"));
		assertThat(simpleSpec.baseUrl(), is("http://localhost"));
	}

	@Test
	public void pathAndQueryString_onFullSpec_shouldReturnCorrectPathWithQueryString() {
		assertThat(fullSpec.pathAndQueryString(), is("/rest/api/data?name=foo&value=bar"));
	}

	@Test
	public void pathAndQueryString_onSimpleSpec_shouldReturnCorrectPathWithoutQueryString() {
		assertThat(simpleSpec.pathAndQueryString(), is("/rest/api/data"));
	}

	@Test
	public void pathAndQueryString_onSpecWithQueryParams_shouldReturnCorrectQueryString() {
		final URLSpecification spec = new URLSpecification();
		final Map<String, String> queryParams = new HashMap<>();
		queryParams.put("name", "foo");
		queryParams.put("value", "bar");
		spec.setQueryParameters(queryParams);

		assertThat(spec.pathAndQueryString(), is("?name=foo&value=bar"));
	}

	@Test
	public void fragment_onFullSpec_shouldReturnHashAndFragment() {
		assertThat(fullSpec.fragment(), is("#anchor"));
	}

	@Test
	public void fragment_onSimpleSpec_shouldReturnEmptyString() {
		assertThat(simpleSpec.fragment(), is(""));
	}

}
