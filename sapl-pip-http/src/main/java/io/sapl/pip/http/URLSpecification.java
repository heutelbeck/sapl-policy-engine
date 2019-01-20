package io.sapl.pip.http;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class URLSpecification {

	private static final String COLON = ":";
	private static final String DOUBLE_SLASH = "//";
	private static final String AT = "@";
	private static final String SLASH = "/";
	private static final String QUESTIONMARK = "?";
	private static final String EQUALS = "=";
	private static final String AMPERSAND = "&";
	private static final String HASH = "#";

	private String scheme;
	private String user;
	private String password;
	private String host;
	private Integer port;
	private String path;
	private String rawQuery;
	private Map<String, String> queryParameters;
	private String fragment;

	static URLSpecification from(String urlStr) throws MalformedURLException {
		final URL url = new URL(urlStr);
		return new URLSpecification(url.getProtocol(), getUser(url), getPassword(url), url.getHost(), getPort(url), url.getPath(), url.getQuery(), getQueryParameters(url), url.getRef());
	}

	private static String getUser(URL url) {
		final String userInfo = url.getUserInfo();
		if (userInfo != null) {
			final String[] userPassword = userInfo.split(COLON);
			if (userPassword.length > 0) {
				return userPassword[0];
			}
		}
		return null;
	}

	private static String getPassword(URL url) {
		final String userInfo = url.getUserInfo();
		if (userInfo != null) {
			final String[] userPassword = userInfo.split(COLON);
			if (userPassword.length > 1) {
				return userPassword[1];
			}
		}
		return null;
	}

	private static Integer getPort(URL url) {
		return url.getPort() > 0 ? url.getPort() : null;
	}

	private static Map<String, String> getQueryParameters(URL url) {
		final String query = url.getQuery();
		if (query != null) {
			final Map<String, String> queryParams = new HashMap<>();
			final String[] nameValuePairs = query.split(AMPERSAND);
			for (String nameValuePair : nameValuePairs) {
				final String[] nameValue = nameValuePair.split(EQUALS);
				queryParams.put(nameValue[0].startsWith(QUESTIONMARK) ? nameValue[0].substring(1) : nameValue[0], nameValue.length > 1 ? nameValue[1] : "");
			}
			return queryParams;
		}
		return null;
	}

	String asString() {
		return baseUrl() + pathAndQueryString() + fragment();
	}

	String baseUrl() {
		final StringBuilder sb = new StringBuilder();
		if (scheme != null) {
			sb.append(scheme).append(COLON);
		}
		if (host != null) {
			sb.append(DOUBLE_SLASH);
			if (user != null) {
				sb.append(user);
				if (password != null) {
					sb.append(COLON).append(password);
				}
				sb.append(AT);
			}
			sb.append(host);
			if (port != null) {
				sb.append(COLON).append(port);
			}
		}
		return sb.toString();
	}

	String pathAndQueryString() {
		final StringBuilder sb = new StringBuilder();
		if (path != null) {
			if (!path.startsWith(SLASH)) {
				sb.append(SLASH);
			}
			sb.append(path);
		}
		if (rawQuery != null) {
			if (!rawQuery.startsWith(QUESTIONMARK)) {
				sb.append(QUESTIONMARK);
			}
			sb.append(rawQuery);
		} else if (queryParameters != null) {
			sb.append(QUESTIONMARK);
			boolean first = true;
			for (Entry<String, String> entry : queryParameters.entrySet()) {
			    if (! first) {
			        sb.append(AMPERSAND);
                }
				sb.append(entry.getKey()).append(EQUALS).append(entry.getValue());
				first = false;
			}
		}
		return sb.toString();
	}

	String fragment() {
		final StringBuilder sb = new StringBuilder();
		if (fragment != null) {
			if (!fragment.startsWith(HASH)) {
				sb.append(HASH);
			}
			sb.append(fragment);
		}
		return sb.toString();
	}
}
