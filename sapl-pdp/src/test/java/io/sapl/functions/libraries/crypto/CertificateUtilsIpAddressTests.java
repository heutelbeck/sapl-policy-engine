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
package io.sapl.functions.libraries.crypto;

import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("IP Subject Alternative Name text")
class CertificateUtilsIpAddressTests {

    @MethodSource("ipAddresses")
    @ParameterizedTest(name = "{1}")
    @DisplayName("IP records use canonical address text")
    void whenIpSubjectAlternativeNameIsListedThenItsTextIsCanonical(byte[] bytes, String canonical)
            throws UnknownHostException {
        val raw = InetAddress.getByAddress(bytes).getHostAddress();
        assertThat(CertificateUtils.toSubjectAlternativeName(List.of(7, raw)))
                .isEqualTo(new SubjectAlternativeName(7, canonical));
    }

    static Stream<Arguments> ipAddresses() {
        return Stream.of(arguments(new byte[] { (byte) 192, 0, 2, 7 }, "192.0.2.7"),
                arguments(ipv6(0x2001, 0xdb8, 0, 0, 0, 0, 0, 1), "2001:db8::1"),
                arguments(ipv6(0, 0, 0, 0, 0, 0, 0, 0), "::"), arguments(ipv6(0, 0, 0, 0, 0, 0, 0, 1), "::1"),
                arguments(ipv6(0x2001, 0, 0, 1, 0, 0, 1, 1), "2001::1:0:0:1:1"),
                arguments(ipv6(0x2001, 0xdb8, 0, 1, 2, 3, 4, 5), "2001:db8:0:1:2:3:4:5"),
                arguments(ipv6(0, 0, 0, 0, 0, 0xffff, 0xc000, 0x207), "192.0.2.7"),
                arguments(ipv6(0, 0, 0, 0, 0, 0, 0xc000, 0x207), "::c000:207"),
                arguments(ipv6(0x2001, 0xdb8, 0, 1, 0, 0, 0, 1), "2001:db8:0:1::1"));
    }

    @Test
    @DisplayName("Other SAN text retains its value")
    void whenAnotherSubjectAlternativeNameIsListedThenItsTextIsUnchanged() {
        assertThat(CertificateUtils.toSubjectAlternativeName(List.of(2, "west.example")))
                .isEqualTo(new SubjectAlternativeName(2, "west.example"));
    }

    private static byte[] ipv6(int... groups) {
        val bytes = ByteBuffer.allocate(16);
        for (val group : groups) {
            bytes.putShort((short) group);
        }
        return bytes.array();
    }

}
