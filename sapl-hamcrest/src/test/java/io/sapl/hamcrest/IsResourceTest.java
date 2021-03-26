package io.sapl.hamcrest;

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
		assertThrows(NullPointerException.class, () -> isResource(null));
	}

	@Test
	void testDescriptionForMatcher() {
		var sut = isResource();
		final StringDescription description = new StringDescription();
		sut.describeTo(description);
		assertThat(description.toString(), is("a resource with any JsonNode"));
	}	
}
