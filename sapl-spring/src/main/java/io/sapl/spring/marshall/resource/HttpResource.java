package io.sapl.spring.marshall.resource;

import java.util.Locale;

import javax.servlet.http.HttpServletRequest;

import io.sapl.api.pdp.marshall.Resource;
import lombok.Value;

@Value
public class HttpResource implements Resource {

	private static final Locale LOWER_CASING_LOCALE = Locale.GERMAN;

	String uri;
	String uriLowerCased;

	public HttpResource(HttpServletRequest request) {
		uri = request.getRequestURI();
		uriLowerCased = uri.toLowerCase(LOWER_CASING_LOCALE);
	}

	public HttpResource(String uri) {
		this.uri = uri;
		uriLowerCased = uri.toLowerCase(LOWER_CASING_LOCALE);
	}

}
