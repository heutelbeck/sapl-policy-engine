package io.sapl.spring.marshall.action;

import javax.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.RequestMethod;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.sapl.spring.marshall.Action;
import lombok.Value;

@Value
public class HttpAction implements Action {

	public static final String TYPE_VALUE = "HTTPAction";

	String method;

	public HttpAction(HttpServletRequest request) {
		method = request.getMethod().toUpperCase();
	}

	public HttpAction(RequestMethod method) {
		this.method = method.name().toUpperCase();
	}

	@JsonIgnore
	public String getType() {
		return TYPE_VALUE;
	}

}
