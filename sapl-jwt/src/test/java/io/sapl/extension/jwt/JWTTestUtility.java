package io.sapl.extension.jwt;

import java.io.UnsupportedEncodingException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.Date;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.net.HttpHeaders;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.PlainHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Text;

class JWTTestUtility {

	static final long tokenMaturity = 30000; // in 0.5 minutes
	static final long tokenValidity = 80000; // in 1.3 minutes

	static final String trustedIssuer = "https://www.ftk.de/";

	static final String subject = "subject1";

	static final String[] authorities = { "ROLE_1", "ROLE_2", "ROLE_3" };

	/**
	 * @return complete and proper JWT
	 */
	static Val jwt(KeyPair keyPair) {
		return sign(header(keyPair), claims(), keyPair);
	}

	/**
	 * @return complete but unsigned (plain) JWT
	 */
	static Val unsignedJwt() {
		PlainJWT plainJwt = new PlainJWT(headerWithoutAlgorithm(), claims());
		return Val.of(plainJwt.serialize());
	}

	/**
	 * @return bogus jwt
	 */
	static Val bogusJwt() {
		return Val.of("not.a.jwt");
	}

	/**
	 * @return JWT without issuer
	 */
	static Val jwtWithoutIssuer(KeyPair keyPair) {
		return sign(header(keyPair), claimsWithoutIssuer(), keyPair);
	}

	/**
	 * @return JWT without subject
	 */
	static Val jwtWithoutSubject(KeyPair keyPair) {
		return sign(header(keyPair), claimsWithoutSubject(), keyPair);
	}

	/**
	 * @return JWT without authorities
	 */
	static Val jwtWithoutAuthorities(KeyPair keyPair) {
		return sign(header(keyPair), claimsWithoutAuthorities(), keyPair);
	}

	/**
	 * @return JWT without type
	 */
	static Val jwtWithoutType(KeyPair keyPair) {
		return sign(headerWithoutType(keyPair), claims(), keyPair);
	}

	/**
	 * @return JWT with wrong type
	 */
	static Val jwtWithWrongType(KeyPair keyPair) {
		return sign(headerWithWrongType(keyPair), claims(), keyPair);
	}

	/**
	 * @return JWT without key ID
	 */
	static Val jwtWithoutKid(KeyPair keyPair) {
		return sign(headerWithoutKid(), claims(), keyPair);
	}

	/**
	 * @return JWT with empty key ID
	 */
	static Val jwtWithEmptyKid(KeyPair keyPair) {
		return sign(headerWithEmptyKid(), claims(), keyPair);
	}

	/**
	 * @return JWT without token ID
	 */
	static Val jwtWithoutId(KeyPair keyPair) {
		return sign(header(keyPair), claimsWithoutId(), keyPair);
	}

	/**
	 * @return JWT with empty token ID
	 */
	static Val jwtWithEmptyId(KeyPair keyPair) {
		return sign(header(keyPair), claimsWithEmptyId(), keyPair);
	}

	/**
	 * @return JWT signed with wrong algorithm
	 */
	static Val jwtWithWrongAlgorithm(KeyPair keyPair) {

		try {
			String message = "shared secret";
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] bytes = md.digest(message.getBytes("UTF-8"));
			JWSSigner signer = new MACSigner(bytes);
			SignedJWT signedJwt = new SignedJWT(headerWithWrongAlgorithm(keyPair), claims());
			signedJwt.sign(signer);
			return Val.of(signedJwt.serialize());
		}
		catch (NoSuchAlgorithmException | UnsupportedEncodingException | JOSEException e1) {
			e1.printStackTrace();
		}

		return null;
	}

	/**
	 * @return JWT whose authorities are not an array
	 */
	static Val jwtWithWrongAuthorities(KeyPair keyPair) {
		return sign(header(keyPair), claimsWithWrongAuthorities(), keyPair);
	}

	/**
	 * @return JWT whose maturity is after its expiration (never valid)
	 */
	static Val jwtWithNbfAfterExp(KeyPair keyPair) {
		return sign(header(keyPair), claimsWithNbfAfterExp(), keyPair);
	}

	/**
	 * @return JWT without maturity and which has already expired
	 */
	static Val jwtExpired(KeyPair keyPair) {
		return sign(header(keyPair), claimsExpBeforeNow(), keyPair);
	}

	/**
	 * @return JWT without maturity and validity (eternal)
	 */
	static Val jwtEternal(KeyPair keyPair) {
		return sign(header(keyPair), claimsWithoutNbfAndExp(), keyPair);
	}

	/**
	 * @return JWT without validity which is mature
	 */
	static Val jwtWithNbfBeforeNow(KeyPair keyPair) {
		return sign(header(keyPair), claimsWithNbfBeforeNow(), keyPair);
	}

	/**
	 * @return JWT without maturity which is valid
	 */
	static Val jwtWithExpAfterNow(KeyPair keyPair) {
		return sign(header(keyPair), claimsWithExpAfterNow(), keyPair);
	}

	/**
	 * @return JWT which will mature in future
	 */
	static Val jwtWithNbfAfterNow(KeyPair keyPair) {
		return sign(header(keyPair), claimsWithNbfAfterNow(), keyPair);
	}

	/**
	 * @return JWT with tampered claims
	 */
	static Val jwtWithTamperedPayload(KeyPair keyPair) {

		String normalBase64 = sign(header(keyPair), claims(), keyPair).getText();
		String superBase64 = sign(header(keyPair), tamperedClaims(), keyPair).getText();
		String tamperedJwt = normalBase64.split("\\.")[0] + "." + superBase64.split("\\.")[1] + "."
				+ normalBase64.split("\\.")[2];

		return Val.of(tamperedJwt);
	}

	/**
	 * @return complete and proper JWT inside a properly prefixed HTTP header scheme field
	 */
	static Val properJwtInProperScheme(KeyPair keyPair) {
		return Val.of(JWTLibraryService.HTTP_HEADER_SCHEME + " " + jwt(keyPair).getText());
	}

	/**
	 * @return JWT inside a properly prefixed HTTP header scheme field
	 */
	static Val jwtInProperScheme(String jwt) {
		return Val.of(JWTLibraryService.HTTP_HEADER_SCHEME + " " + jwt);
	}

	/**
	 * @return complete and proper JWT inside a wrongly prefixed HTTP header scheme field
	 */
	static Val properJwtInImproperScheme(KeyPair keyPair) {
		return Val.of("Not" + JWTLibraryService.HTTP_HEADER_SCHEME + " " + jwt(keyPair).getText());
	}

	/**
	 * @return complete and proper JWT inside a proper HTTP header
	 */
	static Val properJwtInProperHeader(KeyPair keyPair) {
		return Val.of(
				JsonTestUtility.getPepResource(HttpHeaders.AUTHORIZATION, properJwtInProperScheme(keyPair).getText()));
	}

	/**
	 * @return JWT inside a proper HTTP header
	 */
	static Val jwtInProperHeader(String jwt) {
		return Val.of(JsonTestUtility.getPepResource(HttpHeaders.AUTHORIZATION, jwtInProperScheme(jwt).getText()));
	}

	/**
	 * @return JWT as proper credentials value
	 */
	static Val jwtInProperCredentials(String jwt) {
		return Val.of(JsonTestUtility.getPepSubject(JWTLibraryService.AUTHENTICATION_CREDENTIALS_KEY, jwt));
	}

	/**
	 * @return complete and proper JWT inside a proper HTTP header identified by
	 * lower-case key
	 */
	static Val properJwtInLowerCaseHeader(KeyPair keyPair) {
		return Val.of(JsonTestUtility.getPepResource(HttpHeaders.AUTHORIZATION.toLowerCase(),
				properJwtInProperScheme(keyPair).getText()));
	}

	/**
	 * @return JWT inside a proper HTTP header identified by lower-case key
	 */
	static Val jwtInLowerCaseHeader(String jwt) {
		return Val.of(JsonTestUtility.getPepResource(HttpHeaders.AUTHORIZATION.toLowerCase(),
				jwtInProperScheme(jwt).getText()));
	}

	/**
	 * @return bogus JWT inside a properly prefixed HTTP header scheme field
	 */
	static Val improperJwtInProperScheme() {
		return Val.of(JWTLibraryService.HTTP_HEADER_SCHEME + " " + bogusJwt().getText());
	}

	/**
	 * @return bogus JWT inside a proper HTTP header
	 */
	static Val improperJwtInProperHeader() {
		return Val.of(JsonTestUtility.getPepResource(HttpHeaders.AUTHORIZATION, improperJwtInProperScheme().getText()));
	}

	/**
	 * @return complete and proper JWT inside an HTTP header with a wrongly prefixed
	 * scheme field
	 */
	static Val properJwtInProperHeaderWithImproperScheme(KeyPair keyPair) {
		return Val.of(JsonTestUtility.getPepResource(HttpHeaders.AUTHORIZATION,
				properJwtInImproperScheme(keyPair).getText()));
	}

	/**
	 * @return complete and proper JWT inside an HTTP header without prefix in scheme
	 * field
	 */
	static Val properJwtInProperHeaderWithEmptyScheme(KeyPair keyPair) {
		return Val.of(JsonTestUtility.getPepResource(HttpHeaders.AUTHORIZATION, jwt(keyPair).getText()));
	}

	/**
	 * @return complete and proper JWT inside a wrong HTTP header
	 */
	static Val properJwtInImproperHeader(KeyPair keyPair) {
		return Val.of(JsonTestUtility.getPepResource("Not" + HttpHeaders.AUTHORIZATION,
				properJwtInProperScheme(keyPair).getText()));
	}

	/**
	 * @return signed jwt
	 */
	private static Val sign(JWSHeader header, JWTClaimsSet claims, KeyPair keyPair) {
		JWSSigner signer = new RSASSASigner(KeyTestUtility.privateKey(keyPair));
		SignedJWT signedJwt = new SignedJWT(header, claims);
		try {
			signedJwt.sign(signer);
		}
		catch (JOSEException e) {
			e.printStackTrace();
		}

		return Val.of(signedJwt.serialize());
	}

	/**
	 * @return complete header
	 */
	private static JWSHeader header(KeyPair keyPair) {
		return new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(KeyTestUtility.kid(keyPair))
				.build();
	}

	/**
	 * @return header without algorithm
	 */
	private static PlainHeader headerWithoutAlgorithm() {
		return new PlainHeader.Builder().type(JOSEObjectType.JWT).build();
	}

	/**
	 * @return header with wrong algorithm
	 */
	private static JWSHeader headerWithWrongAlgorithm(KeyPair keyPair) {
		return new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).keyID(KeyTestUtility.kid(keyPair))
				.build();
	}

	/**
	 * @return header without type
	 */
	private static JWSHeader headerWithoutType(KeyPair keyPair) {
		return new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KeyTestUtility.kid(keyPair)).build();
	}

	/**
	 * @return header with wrong type
	 */
	private static JWSHeader headerWithWrongType(KeyPair keyPair) {
		return new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JOSE_JSON)
				.keyID(KeyTestUtility.kid(keyPair)).build();
	}

	/**
	 * @return header without KID
	 */
	private static JWSHeader headerWithoutKid() {
		return new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build();
	}

	/**
	 * @return header with empty KID
	 */
	private static JWSHeader headerWithEmptyKid() {
		return new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID("").build();
	}

	/**
	 * @return complete claims set
	 */
	private static JWTClaimsSet claims() {
		return new JWTClaimsSet.Builder().jwtID(UUID.randomUUID().toString()).issuer(trustedIssuer).issueTime(now())
				.notBeforeTime(maturity()).expirationTime(validity()).subject(subject)
				.claim(JWTLibraryService.AUTHORITIES_KEY, authorities).build();
	}

	/**
	 * @return claims set without issuer
	 */
	private static JWTClaimsSet claimsWithoutIssuer() {
		return new JWTClaimsSet.Builder().jwtID(UUID.randomUUID().toString()).issueTime(now()).notBeforeTime(maturity())
				.expirationTime(validity()).subject(subject).claim(JWTLibraryService.AUTHORITIES_KEY, authorities)
				.build();
	}

	/**
	 * @return claims set without subject
	 */
	private static JWTClaimsSet claimsWithoutSubject() {
		return new JWTClaimsSet.Builder().jwtID(UUID.randomUUID().toString()).issuer(trustedIssuer).issueTime(now())
				.notBeforeTime(maturity()).expirationTime(validity())
				.claim(JWTLibraryService.AUTHORITIES_KEY, authorities).build();
	}

	/**
	 * @return claims set without authorities
	 */
	private static JWTClaimsSet claimsWithoutAuthorities() {
		return new JWTClaimsSet.Builder().jwtID(UUID.randomUUID().toString()).issuer(trustedIssuer).issueTime(now())
				.notBeforeTime(maturity()).expirationTime(validity()).subject(subject).build();
	}

	/**
	 * @return claims set without token ID
	 */
	private static JWTClaimsSet claimsWithoutId() {
		return new JWTClaimsSet.Builder().issuer(trustedIssuer).issueTime(now()).notBeforeTime(maturity())
				.expirationTime(validity()).subject(subject).claim(JWTLibraryService.AUTHORITIES_KEY, authorities)
				.build();
	}

	/**
	 * @return claims set with empty token ID
	 */
	private static JWTClaimsSet claimsWithEmptyId() {
		return new JWTClaimsSet.Builder().jwtID("").issuer(trustedIssuer).issueTime(now()).notBeforeTime(maturity())
				.expirationTime(validity()).subject(subject).claim(JWTLibraryService.AUTHORITIES_KEY, authorities)
				.build();
	}

	/**
	 * @return claims set with wrong authorities format
	 */
	private static JWTClaimsSet claimsWithWrongAuthorities() {
		return new JWTClaimsSet.Builder().jwtID(UUID.randomUUID().toString()).issuer(trustedIssuer).issueTime(now())
				.notBeforeTime(maturity()).expirationTime(validity()).subject(subject)
				.claim(JWTLibraryService.AUTHORITIES_KEY, authorities.toString()).build();
	}

	/**
	 * @return claims set whose maturity is after its expiration (never valid)
	 */
	private static JWTClaimsSet claimsWithNbfAfterExp() {
		return new JWTClaimsSet.Builder().jwtID(UUID.randomUUID().toString()).issuer(trustedIssuer).issueTime(now())
				.notBeforeTime(validity()) // switched maturity and validity
				.expirationTime(maturity()).subject(subject).claim(JWTLibraryService.AUTHORITIES_KEY, authorities)
				.build();
	}

	/**
	 * @return claims set without maturity and which has already expired
	 */
	private static JWTClaimsSet claimsExpBeforeNow() {
		return new JWTClaimsSet.Builder().jwtID(UUID.randomUUID().toString()).issuer(trustedIssuer).issueTime(now())
				.expirationTime(new Date(now().getTime() - 1000)).subject(subject)
				.claim(JWTLibraryService.AUTHORITIES_KEY, authorities).build();
	}

	/**
	 * @return claims set without maturity and validity (eternal)
	 */
	private static JWTClaimsSet claimsWithoutNbfAndExp() {
		return new JWTClaimsSet.Builder().jwtID(UUID.randomUUID().toString()).issuer(trustedIssuer).issueTime(now())
				.subject(subject).claim(JWTLibraryService.AUTHORITIES_KEY, authorities).build();
	}

	/**
	 * @return claims set which is mature
	 */
	private static JWTClaimsSet claimsWithNbfBeforeNow() {
		return new JWTClaimsSet.Builder().jwtID(UUID.randomUUID().toString()).issuer(trustedIssuer).issueTime(now())
				.notBeforeTime(new Date(now().getTime() - 1000)).subject(subject)
				.claim(JWTLibraryService.AUTHORITIES_KEY, authorities).build();
	}

	/**
	 * @return claims set without maturity which is valid
	 */
	private static JWTClaimsSet claimsWithExpAfterNow() {
		return new JWTClaimsSet.Builder().jwtID(UUID.randomUUID().toString()).issuer(trustedIssuer).issueTime(now())
				.expirationTime(validity()).subject(subject).claim(JWTLibraryService.AUTHORITIES_KEY, authorities)
				.build();
	}

	/**
	 * @return claims set which will mature in future
	 */
	private static JWTClaimsSet claimsWithNbfAfterNow() {
		return new JWTClaimsSet.Builder().jwtID(UUID.randomUUID().toString()).issuer(trustedIssuer).issueTime(now())
				.notBeforeTime(maturity()).subject(subject).claim(JWTLibraryService.AUTHORITIES_KEY, authorities)
				.build();
	}

	/**
	 * @return claims set with modified authorities
	 */
	private static JWTClaimsSet tamperedClaims() {

		String[] superAuthorities = { "ROLE_ADMIN", "ROLE_SYSTEM", "ROLE_SECURITY", "ROLE_WHEEL" };

		return new JWTClaimsSet.Builder().jwtID(UUID.randomUUID().toString()).issuer(trustedIssuer).issueTime(now())
				.notBeforeTime(maturity()).expirationTime(validity()).subject(subject)
				.claim(JWTLibraryService.AUTHORITIES_KEY, superAuthorities).build();
	}

	/**
	 * @return current time as epoch
	 */
	private static Date now() {
		return new Date();
	}

	/**
	 * @return maturity time as epoch
	 */
	private static Date maturity() {
		return new Date(new Date().getTime() + tokenMaturity);
	}

	/**
	 * @return expiration time as epoch
	 */
	private static Date validity() {
		return new Date(new Date().getTime() + tokenValidity);
	}

	/**
	 * @return the jwt's claims
	 */
	static JWTClaimsSet claims(Val jwt) {
		JWTClaimsSet claims = null;
		try {
			claims = SignedJWT.parse(jwt.getText()).getJWTClaimsSet();
		}
		catch (ParseException e) {
		}
		return claims;
	}

	/**
	 * @return the jwt's header
	 */
	static JWSHeader header(Val jwt) {
		JWSHeader header = null;
		try {
			header = SignedJWT.parse(jwt.getText()).getHeader();
		}
		catch (ParseException e) {
		}
		return header;
	}

	/**
	 * @return base64 converted to nimbus signed jwt
	 */
	static SignedJWT signedJwt(Val jwt) {
		SignedJWT sJwt = null;
		try {
			sJwt = SignedJWT.parse(jwt.getText());
		}
		catch (ParseException | NullPointerException e) {
		}
		return sJwt;
	}

	/**
	 * @return the jwt's payload
	 */
	static Val payload(@Text @JsonObject Val jwt) {
		try {
			Payload payload = SignedJWT.parse(jwt.getText()).getPayload();
			JsonNode jsonNode = JsonTestUtility.jsonNode(payload.toString());
			return Val.of(jsonNode);
		}
		catch (ParseException | NullPointerException e) {
			return Val.ofEmptyObject();
		}
	}

}
