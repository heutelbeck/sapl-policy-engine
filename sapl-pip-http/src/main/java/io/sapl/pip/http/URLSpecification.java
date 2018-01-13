package io.sapl.pip.http;

import java.util.HashMap;
import java.util.Map.Entry;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class URLSpecification {
	private static final String HASH = "#";
	private static final String AMPERSAND = "&";
	private static final char EQUALS = '=';
	private static final String QUESTIONMARK = "?";
	private static final String SLASH = "/";
	private static final String DOUBLE_SLASH = "//";
	private static final char COLON = ':';

	String scheme;
	String user;
	String password;
	String host;
	Integer port;
	String path;
	String rawQuery;
	HashMap<String, String> queryParameters;
	String fragment;

	@Override
	public String toString() {
		StringBuilder url = new StringBuilder();
		if (getScheme() != null) {
			url.append(getScheme()).append(COLON);
		}
		if (getHost() != null) {
			url.append(DOUBLE_SLASH);
			if (getUser() != null) {
				url.append(getUser());
				if (getPassword() != null) {
					url.append(COLON).append(getPassword());
				}
			}
			if (getPort() != null) {
				url.append(COLON).append(getPort());
			}
		}
		if (getPath() != null) {
			if (!getPath().startsWith(SLASH)) {
				url.append(SLASH);
			}
			url.append(getPath());
		}
		if (getRawQuery() != null) {
			if (!getRawQuery().startsWith(QUESTIONMARK)) {
				url.append(QUESTIONMARK);
			}
			url.append(getRawQuery());
		} else if (getQueryParameters() != null) {
			url.append(QUESTIONMARK);
			String delim = "";
			for (Entry<String, String> entry : getQueryParameters().entrySet()) {
				url.append(entry.getKey()).append(EQUALS).append(entry.getValue()).append(delim);
				delim = AMPERSAND;
			}
		}
		if (getFragment() != null) {
			if (!getFragment().startsWith(HASH)) {
				url.append(HASH);
			}
			url.append(getFragment());
		}
		return url.toString();
	}
}
