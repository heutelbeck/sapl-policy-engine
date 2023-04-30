package io.sapl.extension.jwt;

public enum DispatchMode {

	/**
	 * Dispatcher returns the true Base64Url-encoded key, matching the kid
	 */
	True,
	/**
	 * Dispatcher returns the true Base64-encoded key, matching the kid
	 */
	Basic,
	/**
	 * Dispatcher returns a wrong Base64Url-encoded key, not matching the kid
	 */
	Wrong,
	/**
	 * Dispatcher returns the key with Base64(Url) encoding errors
	 */
	Invalid,
	/**
	 * Dispatcher returns bogus data, not resembling an encoded key
	 */
	Bogus,
	/**
	 * Dispatcher always returns 404 - unknown
	 */
	Unknown

}