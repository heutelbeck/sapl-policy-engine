/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.attributes.libraries.util;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.val;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

public class JWTTestUtility {

    static final String UNSUPPORTED_KEY_ERROR = "The type of the provided key is not supported!";

    static final long TIME_UNIT             = 2000L; // two seconds in millis
    static final long SYNCHRONOUS_TIME_UNIT = 50L; // fifty milliseconds

    static final String EC  = "EC";
    static final String RSA = "RSA";

    /**
     * @return timestamp one unit ago as Date object
     */
    public static Date timeOneUnitBeforeNow() {
        return Date.from(Instant.now().minusMillis(TIME_UNIT));
    }

    /**
     * @return timestamp one unit in the future as Date object
     */
    public static Date timeOneUnitAfterNow() {
        return Date.from(Instant.now().plusMillis(TIME_UNIT));
    }

    /**
     * @return timestamp three units in the future as Date object
     */
    static Date timeThreeUnitsAfterNow() {
        return Date.from(Instant.now().plusMillis(3 * TIME_UNIT));
    }

    /**
     * @return time interval of two units as Duration object
     */
    public static Duration twoUnitDuration() {
        return Duration.ofMillis(2 * TIME_UNIT);
    }

    /**
     * @return time interval of two synchronous units as Duration object
     */
    static Duration twoSynchronousUnitDuration() {
        return Duration.ofMillis(2 * SYNCHRONOUS_TIME_UNIT);
    }

    public static TextValue buildAndSignJwt(JWSHeader header, JWTClaimsSet claims, KeyPair keyPair)
            throws JOSEException {
        JWSSigner signer;
        if (EC.equalsIgnoreCase(keyPair.getPrivate().getAlgorithm())) {
            signer = new ECDSASigner((ECPrivateKey) keyPair.getPrivate());
        } else if (RSA.equalsIgnoreCase(keyPair.getPrivate().getAlgorithm())) {
            signer = new RSASSASigner(keyPair.getPrivate());
        } else
            throw new UnsupportedOperationException(UNSUPPORTED_KEY_ERROR);

        SignedJWT signedJwt = new SignedJWT(header, claims);
        signedJwt.sign(signer);
        return (TextValue) Value.of(signedJwt.serialize());
    }

    public static TextValue buildAndSignHmacJwt(JWSHeader header, JWTClaimsSet claims, SecretKey secretKey)
            throws JOSEException {
        val signer    = new MACSigner(secretKey);
        val signedJwt = new SignedJWT(header, claims);
        signedJwt.sign(signer);
        return (TextValue) Value.of(signedJwt.serialize());
    }

    public static String encodeSecretKey(SecretKey secretKey) {
        return java.util.Base64.getUrlEncoder().encodeToString(secretKey.getEncoded());
    }

    public static TextValue replacePayload(TextValue signedJWT, JWTClaimsSet tamperedPayload) {
        val parts       = signedJWT.value().split("\\.");
        val tamperedJWT = parts[0] + "." + tamperedPayload.toPayload().toBase64URL().toString() + "." + parts[2];
        return (TextValue) Value.of(tamperedJWT);
    }

}
