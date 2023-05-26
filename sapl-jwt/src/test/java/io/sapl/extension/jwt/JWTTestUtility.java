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
package io.sapl.extension.jwt;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import io.sapl.api.interpreter.Val;

class JWTTestUtility {

	static final String UNSUPPORTED_KEY_ERROR = "The type of the provided key is not supported!";

	static final long timeUnit            = 2000L; // two seconds in millis
	static final long synchronousTimeUnit = 50L;   // fifty milliseconds

	static final String EC  = "EC";
	static final String RSA = "RSA";

	/**
	 * @return timestamp one unit ago as Date object
	 */
	static Date timeOneUnitBeforeNow() {
		return Date.from(Instant.now().minusMillis(timeUnit));
	}

	/**
	 * @return timestamp one unit in the future as Date object
	 */
	static Date timeOneUnitAfterNow() {
		return Date.from(Instant.now().plusMillis(timeUnit));
	}

	/**
	 * @return timestamp three units in the future as Date object
	 */
	static Date timeThreeUnitsAfterNow() {
		return Date.from(Instant.now().plusMillis(3 * timeUnit));
	}

	/**
	 * @return time interval of two units as Duration object
	 */
	static Duration twoUnitDuration() {
		return Duration.ofMillis(2 * timeUnit);
	}

	/**
	 * @return time interval of two synchronous units as Duration object
	 */
	static Duration twoSynchronousUnitDuration() {
		return Duration.ofMillis(2 * synchronousTimeUnit);
	}

	/**
	 * @return signed jwt
	 * @throws JOSEException
	 */
	static Val buildAndSignJwt(JWSHeader header, JWTClaimsSet claims, KeyPair keyPair) throws JOSEException {
		JWSSigner signer;
		if (EC.equalsIgnoreCase(keyPair.getPrivate().getAlgorithm())) {
			signer = new ECDSASigner((ECPrivateKey) keyPair.getPrivate());
		} else if (RSA.equalsIgnoreCase(keyPair.getPrivate().getAlgorithm())) {
			signer = new RSASSASigner(keyPair.getPrivate());
		} else
			throw new UnsupportedOperationException(UNSUPPORTED_KEY_ERROR);

		SignedJWT signedJwt = new SignedJWT(header, claims);
		signedJwt.sign(signer);
		return Val.of(signedJwt.serialize());
	}

	/**
	 * @param signedJWT
	 * @param tamperedPayload
	 * @return replaces the encoded payload of a signed JWT with another payload,
	 *         without updating the signature.
	 */
	static Val replacePayload(Val signedJWT, JWTClaimsSet tamperedPayload) {
		String[] parts       = signedJWT.getText().split("\\.");
		String   tamperedJWT = parts[0] + "." + tamperedPayload.toPayload().toBase64URL().toString() + "." + parts[2];
		return Val.of(tamperedJWT);
	}

}
