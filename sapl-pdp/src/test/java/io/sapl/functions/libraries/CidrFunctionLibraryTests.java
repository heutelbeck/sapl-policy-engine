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
package io.sapl.functions.libraries;

import io.sapl.api.model.*;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class CidrFunctionLibraryTests {

    @Test
    void when_loadedIntoBroker_then_noError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.loadStaticFunctionLibrary(CidrFunctionLibrary.class))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} contains {1} = {2}")
    @CsvSource(delimiterString = "|", textBlock = """
            # IPv4 Basic Containment
            192.168.1.0/24     | 192.168.1.128      | true
            192.168.1.0/24     | 192.168.1.0/25     | true
            192.168.1.0/24     | 192.168.2.1        | false
            10.0.0.0/8         | 10.255.255.255     | true
            10.0.0.0/8         | 11.0.0.1           | false
            # IPv4 Edge Cases
            192.168.1.0/32     | 192.168.1.0        | true
            192.168.1.0/32     | 192.168.1.1        | false
            0.0.0.0/0          | 192.168.1.1        | true
            127.0.0.0/8        | 127.0.0.1          | true
            # IPv4 Subnet Containment
            10.20.30.0/24      | 10.20.30.64/26     | true
            10.20.30.0/24      | 10.20.31.0/24      | false
            172.16.0.0/12      | 172.31.255.255     | true
            172.16.0.0/12      | 172.32.0.0         | false
            # IPv6 Basic Containment
            2001:db8::/32      | 2001:db8::1        | true
            2001:db8::/32      | 2001:db9::1        | false
            2001:db8::/32      | 2001:db8::/64      | true
            fe80::/10          | fe80::1            | true
            # IPv6 Edge Cases
            ::/0               | 2001:db8::1        | true
            ::1/128            | ::1                | true
            ::1/128            | ::2                | false
            """)
    void contains_whenValidInputs_thenReturnsExpectedResult(String cidr, String cidrOrIp, boolean expected) {
        val result = CidrFunctionLibrary.contains(Value.of(cidr), Value.of(cidrOrIp));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest(name = "Invalid: {0} contains {1}")
    @CsvSource(delimiterString = "|", textBlock = """
            192.168.1.0/24     | 999.999.999.999
            192.168.1.0/24     | not-an-ip
            invalid-cidr       | 192.168.1.1
            192.168.1.0/33     | 192.168.1.1
            192.168.1.0/-1     | 192.168.1.1
            192.168.1.0/abc    | 192.168.1.1
            """)
    void contains_whenInvalidInput_thenReturnsError(String cidr, String cidrOrIp) {
        val result = CidrFunctionLibrary.contains(Value.of(cidr), Value.of(cidrOrIp));

        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @ParameterizedTest(name = "Mixed families: {0} contains {1} = false")
    @CsvSource(delimiterString = "|", textBlock = """
            192.168.1.0/24     | 2001:db8::1
            2001:db8::/32      | 192.168.1.1
            10.0.0.0/8         | fe80::1
            ::1/128            | 127.0.0.1
            """)
    void contains_whenMixedAddressFamilies_thenReturnsFalse(String cidr, String cidrOrIp) {
        val result = CidrFunctionLibrary.contains(Value.of(cidr), Value.of(cidrOrIp));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.FALSE);
    }

    @ParameterizedTest(name = "{3}")
    @MethodSource("containsMatchesScenarios")
    void containsMatches_whenValidInputs_thenReturnsExpectedMatches(String[] cidrs, String[] ips,
            int[][] expectedMatches, String description) {
        val cidrArray = buildStringArray(cidrs);
        val ipArray   = buildStringArray(ips);

        val result = CidrFunctionLibrary.containsMatches(cidrArray, ipArray);

        assertThat(result).isInstanceOf(ArrayValue.class);
        val matches = (ArrayValue) result;
        assertThat(matches).hasSize(expectedMatches.length);

        for (int i = 0; i < expectedMatches.length; i++) {
            val tuple = (ArrayValue) matches.get(i);
            assertThat(((NumberValue) tuple.get(0)).value().intValue()).isEqualTo(expectedMatches[i][0]);
            assertThat(((NumberValue) tuple.get(1)).value().intValue()).isEqualTo(expectedMatches[i][1]);
        }
    }

    static Stream<Arguments> containsMatchesScenarios() {
        return Stream.of(
                arguments(new String[] { "10.0.0.0/8", "192.168.0.0/16", "172.16.0.0/12" },
                        new String[] { "10.1.2.3", "8.8.8.8", "192.168.5.10" }, new int[][] { { 0, 0 }, { 1, 2 } },
                        "basic scenario with partial matches"),
                arguments(new String[] { "10.0.0.0/8", "192.168.0.0/16" }, new String[] { "8.8.8.8", "1.1.1.1" },
                        new int[][] {}, "no matches"),
                arguments(new String[] { "10.0.0.0/8" }, new String[] { "10.1.1.1", "10.2.2.2", "10.3.3.3" },
                        new int[][] { { 0, 0 }, { 0, 1 }, { 0, 2 } }, "all match"));
    }

    @Test
    void containsMatches_whenInvalidInput_thenReturnsError() {
        val cidrs = buildStringArray("10.0.0.0/8", "invalid");
        val ips   = buildStringArray("10.1.1.1");

        val result = CidrFunctionLibrary.containsMatches(cidrs, ips);

        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @ParameterizedTest(name = "Expand {0} into expected addresses")
    @MethodSource("expandTestCases")
    void expand_whenValidCidr_thenReturnsExpectedAddresses(String cidr, String[] expectedAddresses) {
        val result = CidrFunctionLibrary.expand(Value.of(cidr));

        assertThat(result).isInstanceOf(ArrayValue.class);

        val addresses = new ArrayList<String>();
        for (val element : (ArrayValue) result) {
            addresses.add(((TextValue) element).value());
        }

        assertThat(addresses).containsExactlyInAnyOrder(expectedAddresses);
    }

    static Stream<Arguments> expandTestCases() {
        return Stream.of(
                arguments("192.168.1.0/30",
                        new String[] { "192.168.1.0", "192.168.1.1", "192.168.1.2", "192.168.1.3" }),
                arguments("10.0.0.0/30", new String[] { "10.0.0.0", "10.0.0.1", "10.0.0.2", "10.0.0.3" }),
                arguments("192.168.1.255/32", new String[] { "192.168.1.255" }),
                arguments("192.168.1.0/31", new String[] { "192.168.1.0", "192.168.1.1" }), arguments("2001:db8::0/126",
                        new String[] { "2001:db8::", "2001:db8::1", "2001:db8::2", "2001:db8::3" }));
    }

    @ParameterizedTest(name = "Large expansion {0} should fail")
    @ValueSource(strings = { "10.0.0.0/8", "192.168.0.0/15", "2001:db8::/64" })
    void expand_whenTooLarge_thenReturnsError(String cidr) {
        val result = CidrFunctionLibrary.expand(Value.of(cidr));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("maximum is 65535");
    }

    @ParameterizedTest(name = "{0} intersects {1} = {2}")
    @CsvSource(delimiterString = "|", textBlock = """
            # IPv4 Overlapping
            192.168.0.0/16     | 192.168.1.0/24     | true
            10.0.0.0/8         | 10.20.30.0/24      | true
            172.16.0.0/12      | 172.31.0.0/16      | true
            # IPv4 Non-Overlapping
            192.168.1.0/24     | 192.168.2.0/24     | false
            10.0.0.0/24        | 10.1.0.0/24        | false
            192.168.0.0/24     | 10.0.0.0/8         | false
            # IPv4 Adjacent
            192.168.1.0/25     | 192.168.1.128/25   | false
            10.0.0.0/24        | 10.0.1.0/24        | false
            # IPv4 Identical
            192.168.1.0/24     | 192.168.1.0/24     | true
            # IPv6 Overlapping
            2001:db8::/32      | 2001:db8:1::/48    | true
            fe80::/10          | fe80::/64          | true
            # IPv6 Non-Overlapping
            2001:db8::/32      | 2001:db9::/32      | false
            """)
    void intersects_whenValidInputs_thenReturnsExpectedResult(String cidr1, String cidr2, boolean expected) {
        val result = CidrFunctionLibrary.intersects(Value.of(cidr1), Value.of(cidr2));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest(name = "Valid CIDR: {0}")
    @ValueSource(strings = { "192.168.1.0/24", "10.0.0.0/8", "172.16.0.0/12", "0.0.0.0/0", "255.255.255.255/32",
            "2001:db8::/32", "fe80::/10", "::/0", "::1/128" })
    void isValid_whenValidCidr_thenReturnsTrue(String cidr) {
        val result = CidrFunctionLibrary.isValid(Value.of(cidr));

        assertThat(result).isEqualTo(Value.TRUE);
    }

    @ParameterizedTest(name = "Invalid CIDR: {0}")
    @ValueSource(strings = { "999.999.999.999/24", "not-an-ip/24", "192.168.1.0/33", "192.168.1.0/-1", "2001:db8::/129",
            "192.168.1.0//24" })
    void isValid_whenInvalidCidr_thenReturnsFalse(String cidr) {
        val result = CidrFunctionLibrary.isValid(Value.of(cidr));

        assertThat(result).isEqualTo(Value.FALSE);
    }

    @ParameterizedTest(name = "Merge: {0}")
    @MethodSource("mergeTestCases")
    void merge_whenValidInputs_thenReturnsExpectedCidrs(String[] inputCidrs, String[] expectedCidrs) {
        val inputArray = buildStringArray(inputCidrs);

        val result = CidrFunctionLibrary.merge(inputArray);

        assertThat(result).isInstanceOf(ArrayValue.class);

        val mergedCidrs = new ArrayList<String>();
        for (val element : (ArrayValue) result) {
            mergedCidrs.add(((TextValue) element).value());
        }

        assertThat(mergedCidrs).containsExactlyInAnyOrder(expectedCidrs);
    }

    static Stream<Arguments> mergeTestCases() {
        return Stream.of(
                arguments(new String[] { "192.168.1.0/25", "192.168.1.128/25" }, new String[] { "192.168.1.0/24" }),
                arguments(new String[] { "10.0.0.0/16", "10.0.1.0/24" }, new String[] { "10.0.0.0/16" }),
                arguments(new String[] { "192.168.1.0/24", "192.168.1.0/24" }, new String[] { "192.168.1.0/24" }),
                arguments(new String[] { "192.0.128.0/24", "192.0.129.0/24" }, new String[] { "192.0.128.0/23" }),
                arguments(new String[] { "192.168.1.0/24", "192.168.3.0/24" },
                        new String[] { "192.168.1.0/24", "192.168.3.0/24" }),
                arguments(new String[] { "2001:db8:0:0::/64", "2001:db8:0:1::/64" }, new String[] { "2001:db8::/63" }),
                arguments(new String[] { "2001:db8::/64", "2001:db8:1::/64" },
                        new String[] { "2001:db8::/64", "2001:db8:1::/64" }),
                arguments(new String[] {}, new String[] {}));
    }

    @ParameterizedTest(name = "isPrivateIpv4({0}) = {1}")
    @CsvSource(delimiterString = "|", textBlock = """
            # RFC 1918 Private Addresses
            10.0.0.0           | true
            10.255.255.255     | true
            10.123.45.67       | true
            172.16.0.0         | true
            172.31.255.255     | true
            172.20.1.1         | true
            192.168.0.0        | true
            192.168.255.255    | true
            192.168.1.100      | true
            # Public Addresses
            8.8.8.8            | false
            1.1.1.1            | false
            11.0.0.0           | false
            172.15.255.255     | false
            172.32.0.0         | false
            192.167.1.1        | false
            192.169.1.1        | false
            # IPv6 Should Be False
            2001:db8::1        | false
            ::1                | false
            """)
    void isPrivateIpv4_whenValidInput_thenReturnsExpectedResult(String ipAddress, boolean expected) {
        val result = CidrFunctionLibrary.isPrivateIpv4(Value.of(ipAddress));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest(name = "isLoopback({0}) = {1}")
    @CsvSource(delimiterString = "|", textBlock = """
            # IPv4 Loopback
            127.0.0.1          | true
            127.0.0.0          | true
            127.255.255.255    | true
            127.123.45.67      | true
            # IPv6 Loopback
            ::1                | true
            # Not Loopback
            128.0.0.1          | false
            126.255.255.255    | false
            192.168.1.1        | false
            2001:db8::1        | false
            ::2                | false
            """)
    void isLoopback_whenValidInput_thenReturnsExpectedResult(String ipAddress, boolean expected) {
        val result = CidrFunctionLibrary.isLoopback(Value.of(ipAddress));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest(name = "isLinkLocal({0}) = {1}")
    @CsvSource(delimiterString = "|", textBlock = """
            # IPv4 Link-Local
            169.254.0.0        | true
            169.254.255.255    | true
            169.254.1.1        | true
            # IPv6 Link-Local
            fe80::1            | true
            fe80::             | true
            febf:ffff:ffff:ffff:ffff:ffff:ffff:ffff | true
            # Not Link-Local
            169.253.255.255    | false
            169.255.0.0        | false
            192.168.1.1        | false
            fc00::1            | false
            fec0::1            | false
            """)
    void isLinkLocal_whenValidInput_thenReturnsExpectedResult(String ipAddress, boolean expected) {
        val result = CidrFunctionLibrary.isLinkLocal(Value.of(ipAddress));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest(name = "isMulticast({0}) = {1}")
    @CsvSource(delimiterString = "|", textBlock = """
            # IPv4 Multicast
            224.0.0.0          | true
            239.255.255.255    | true
            224.0.0.1          | true
            # IPv6 Multicast
            ff00::1            | true
            ff02::1            | true
            ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff | true
            # Not Multicast
            223.255.255.255    | false
            240.0.0.0          | false
            192.168.1.1        | false
            fe80::1            | false
            """)
    void isMulticast_whenValidInput_thenReturnsExpectedResult(String ipAddress, boolean expected) {
        val result = CidrFunctionLibrary.isMulticast(Value.of(ipAddress));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest(name = "isDocumentation({0}) = {1}")
    @CsvSource(delimiterString = "|", textBlock = """
            # IPv4 Documentation
            192.0.2.0          | true
            192.0.2.255        | true
            198.51.100.0       | true
            198.51.100.255     | true
            203.0.113.0        | true
            203.0.113.255      | true
            # IPv6 Documentation
            2001:db8::1        | true
            2001:db8:ffff:ffff:ffff:ffff:ffff:ffff | true
            # Not Documentation
            192.0.1.255        | false
            192.0.3.0          | false
            198.51.99.255      | false
            198.51.101.0       | false
            2001:db7:ffff:ffff:ffff:ffff:ffff:ffff | false
            2001:db9::1        | false
            """)
    void isDocumentation_whenValidInput_thenReturnsExpectedResult(String ipAddress, boolean expected) {
        val result = CidrFunctionLibrary.isDocumentation(Value.of(ipAddress));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest(name = "isCgnat({0}) = {1}")
    @CsvSource(delimiterString = "|", textBlock = """
            # CGNAT Range
            100.64.0.0         | true
            100.127.255.255    | true
            100.100.100.100    | true
            # Not CGNAT
            100.63.255.255     | false
            100.128.0.0        | false
            10.0.0.0           | false
            192.168.1.1        | false
            """)
    void isCgnat_whenValidInput_thenReturnsExpectedResult(String ipAddress, boolean expected) {
        val result = CidrFunctionLibrary.isCgnat(Value.of(ipAddress));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest(name = "isBenchmark({0}) = {1}")
    @CsvSource(delimiterString = "|", textBlock = """
            # Benchmark Range
            198.18.0.0         | true
            198.19.255.255     | true
            198.18.100.100     | true
            # Not Benchmark
            198.17.255.255     | false
            198.20.0.0         | false
            192.168.1.1        | false
            """)
    void isBenchmark_whenValidInput_thenReturnsExpectedResult(String ipAddress, boolean expected) {
        val result = CidrFunctionLibrary.isBenchmark(Value.of(ipAddress));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest(name = "isReserved({0}) = {1}")
    @CsvSource(delimiterString = "|", textBlock = """
            # IPv4 Reserved (Class E)
            240.0.0.0          | true
            255.255.255.254    | true
            250.123.45.67      | true
            # IPv6 Reserved and Documentation (treated as reserved)
            ::                 | true
            ::ffff:0:0         | true
            100::1             | true
            100::ffff          | true
            2001::1            | true
            2001:1::1          | true
            2001:db8::1        | true
            2001:db8:ffff::    | true
            # Not Reserved
            239.255.255.255    | false
            192.168.1.1        | false
            10.0.0.1           | false
            8.8.8.8            | false
            2606:4700::1111    | false
            2a00:1450:4001::   | false
            """)
    void isReserved_whenValidInput_thenReturnsExpectedResult(String ipAddress, boolean expected) {
        val result = CidrFunctionLibrary.isReserved(Value.of(ipAddress));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest(name = "isBroadcast({0}) = {1}")
    @CsvSource(delimiterString = "|", textBlock = """
            # Broadcast
            255.255.255.255    | true
            # Not Broadcast
            255.255.255.254    | false
            0.0.0.0            | false
            192.168.1.255      | false
            """)
    void isBroadcast_whenValidInput_thenReturnsExpectedResult(String ipAddress, boolean expected) {
        val result = CidrFunctionLibrary.isBroadcast(Value.of(ipAddress));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest(name = "isPublicRoutable({0}) = {1}")
    @CsvSource(delimiterString = "|", textBlock = """
            # Public Addresses
            8.8.8.8            | true
            1.1.1.1            | true
            151.101.1.69       | true
            2606:4700:4700::1111 | true
            # Private/Special Addresses
            10.0.0.0           | false
            172.16.0.0         | false
            192.168.1.1        | false
            127.0.0.1          | false
            169.254.1.1        | false
            224.0.0.1          | false
            255.255.255.255    | false
            100.64.0.0         | false
            ::1                | false
            fe80::1            | false
            2001:db8::1        | false
            """)
    void isPublicRoutable_whenValidInput_thenReturnsExpectedResult(String ipAddress, boolean expected) {
        val result = CidrFunctionLibrary.isPublicRoutable(Value.of(ipAddress));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest(name = "anonymizeIp({0}, {1}) = {2}")
    @CsvSource(delimiterString = "|", textBlock = """
            # IPv4 Anonymization
            192.168.1.123      | 24 | 192.168.1.0
            10.20.30.40        | 16 | 10.20.0.0
            172.16.25.50       | 12 | 172.16.0.0
            8.8.8.8            | 8  | 8.0.0.0
            # IPv6 Anonymization (RFC 5952 canonical form)
            2001:db8:abcd:1234:5678:90ab:cdef:1234 | 64 | 2001:db8:abcd:1234::
            2001:db8::1        | 32 | 2001:db8::
            fe80::1234:5678    | 10 | fe80::
            """)
    void anonymizeIp_whenValidInputs_thenReturnsExpectedResult(String ipAddress, int prefixLength, String expected) {
        val result = CidrFunctionLibrary.anonymizeIp(Value.of(ipAddress), Value.of(prefixLength));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest(name = "{4}")
    @MethodSource("hashIpPrefixTestCases")
    void hashIpPrefix_whenValidInputs_thenReturnsExpectedHashComparison(String ip1, String ip2, String salt1,
            String salt2, boolean shouldMatch, String description) {
        val hash1 = CidrFunctionLibrary.hashIpPrefix(Value.of(ip1), Value.of(24), Value.of(salt1));
        val hash2 = CidrFunctionLibrary.hashIpPrefix(Value.of(ip2), Value.of(24), Value.of(salt2));

        assertThat(hash1).isInstanceOf(TextValue.class);
        assertThat(hash2).isInstanceOf(TextValue.class);

        if (shouldMatch) {
            assertThat(hash1).isEqualTo(hash2);
        } else {
            assertThat(hash1).isNotEqualTo(hash2);
        }
    }

    static Stream<Arguments> hashIpPrefixTestCases() {
        return Stream.of(arguments("192.168.1.100", "192.168.1.200", "salt", "salt", true, "same subnet, same salt"),
                arguments("192.168.1.100", "192.168.2.100", "salt", "salt", false, "different subnet, same salt"),
                arguments("192.168.1.100", "192.168.1.100", "salt1", "salt2", false, "same IP, different salts"));
    }

    @ParameterizedTest(name = "getNetworkAddress({0}) = {1}")
    @CsvSource(delimiterString = "|", textBlock = """
            192.168.1.64/26    | 192.168.1.64
            192.168.1.100/24   | 192.168.1.0
            10.20.30.40/16     | 10.20.0.0
            2001:db8:abcd::/48 | 2001:db8:abcd::
            """)
    void getNetworkAddress_whenValidCidr_thenReturnsExpectedAddress(String cidr, String expected) {
        val result = CidrFunctionLibrary.getNetworkAddress(Value.of(cidr));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest(name = "getBroadcastAddress({0}) = {1}")
    @CsvSource(delimiterString = "|", textBlock = """
            192.168.1.0/24     | 192.168.1.255
            192.168.1.64/26    | 192.168.1.127
            10.0.0.0/8         | 10.255.255.255
            2001:db8::/32      | 2001:db8:ffff:ffff:ffff:ffff:ffff:ffff
            """)
    void getBroadcastAddress_whenValidCidr_thenReturnsExpectedAddress(String cidr, String expected) {
        val result = CidrFunctionLibrary.getBroadcastAddress(Value.of(cidr));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest(name = "getAddressCount({0}) = {1}")
    @CsvSource(delimiterString = "|", textBlock = """
            192.168.1.0/24     | 256
            192.168.1.0/30     | 4
            10.0.0.0/8         | 16777216
            192.168.1.0/32     | 1
            2001:db8::/64      | 18446744073709551616
            """)
    void getAddressCount_whenValidCidr_thenReturnsExpectedCount(String cidr, String expected) {
        val result = CidrFunctionLibrary.getAddressCount(Value.of(cidr));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest(name = "getUsableHostCount({0}) = {1}")
    @CsvSource(delimiterString = "|", textBlock = """
            192.168.1.0/24     | 254
            192.168.1.0/30     | 2
            10.0.0.0/8         | 16777214
            192.168.1.0/31     | 0
            192.168.1.0/32     | 0
            2001:db8::/64      | 18446744073709551615
            """)
    void getUsableHostCount_whenValidCidr_thenReturnsExpectedCount(String cidr, String expected) {
        val result = CidrFunctionLibrary.getUsableHostCount(Value.of(cidr));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest(name = "getFirstUsableAddress({0}) = {1}")
    @CsvSource(delimiterString = "|", textBlock = """
            192.168.1.0/24     | 192.168.1.1
            10.0.0.0/8         | 10.0.0.1
            192.168.1.64/26    | 192.168.1.65
            2001:db8::/64      | 2001:db8::1
            """)
    void getFirstUsableAddress_whenValidCidr_thenReturnsExpectedAddress(String cidr, String expected) {
        val result = CidrFunctionLibrary.getFirstUsableAddress(Value.of(cidr));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest(name = "getLastUsableAddress({0}) = {1}")
    @CsvSource(delimiterString = "|", textBlock = """
            192.168.1.0/24     | 192.168.1.254
            10.0.0.0/8         | 10.255.255.254
            192.168.1.64/26    | 192.168.1.126
            2001:db8::/64      | 2001:db8::ffff:ffff:ffff:ffff
            """)
    void getLastUsableAddress_whenValidCidr_thenReturnsExpectedAddress(String cidr, String expected) {
        val result = CidrFunctionLibrary.getLastUsableAddress(Value.of(cidr));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest(name = "sameSubnet({0}, {1}, {2}) = {3}")
    @CsvSource(delimiterString = "|", textBlock = """
            # Same /24 Subnet
            192.168.1.10       | 192.168.1.20       | 24 | true
            192.168.1.1        | 192.168.1.254      | 24 | true
            # Different /24 Subnets
            192.168.1.1        | 192.168.2.1        | 24 | false
            10.0.1.1           | 10.0.2.1           | 24 | false
            # Same /16 Subnet
            10.20.1.1          | 10.20.2.1          | 16 | true
            # Different /16 Subnets
            10.20.1.1          | 10.21.1.1          | 16 | false
            # IPv6 Same Subnet
            2001:db8::1        | 2001:db8::2        | 64 | true
            # IPv6 Different Subnet
            2001:db8::1        | 2001:db9::1        | 32 | false
            """)
    void sameSubnet_whenValidInputs_thenReturnsExpectedResult(String ip1, String ip2, int prefixLength,
            boolean expected) {
        val result = CidrFunctionLibrary.sameSubnet(Value.of(ip1), Value.of(ip2), Value.of(prefixLength));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest(name = "getCommonPrefixLength({0}, {1}) = {2}")
    @CsvSource(delimiterString = "|", textBlock = """
            # Identical Addresses
            192.168.1.1        | 192.168.1.1        | 32
            # Same /24
            192.168.1.1        | 192.168.1.254      | 24
            # Same /16
            192.168.1.1        | 192.168.200.1      | 16
            # Same /8 plus 1 bit
            192.168.1.1        | 192.200.1.1        | 9
            # Different First Octet
            192.168.1.1        | 193.168.1.1        | 7
            # IPv6
            2001:db8::1        | 2001:db8::2        | 126
            2001:db8::1        | 2001:db8:1::1      | 47
            """)
    void getCommonPrefixLength_whenValidInputs_thenReturnsExpectedResult(String ip1, String ip2, int expected) {
        val result = CidrFunctionLibrary.getCommonPrefixLength(Value.of(ip1), Value.of(ip2));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest(name = "canSubdivide({0}, {1}) = {2}")
    @CsvSource(delimiterString = "|", textBlock = """
            # Valid Subdivisions
            192.168.0.0/22     | 24 | true
            10.0.0.0/16        | 24 | true
            192.168.0.0/24     | 25 | true
            192.168.0.0/24     | 26 | true
            # Invalid - Target Too Large
            192.168.0.0/24     | 23 | false
            192.168.0.0/24     | 16 | false
            # Invalid - Not On Boundary
            192.168.1.0/23     | 24 | false
            192.168.3.0/22     | 24 | false
            # IPv6
            2001:db8::/32      | 48 | true
            2001:db8::/48      | 64 | true
            """)
    void canSubdivide_whenValidInputs_thenReturnsExpectedResult(String cidr, int targetPrefix, boolean expected) {
        val result = CidrFunctionLibrary.canSubdivide(Value.of(cidr), Value.of(targetPrefix));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @Test
    void anonymizeIp_whenInvalidPrefix_thenReturnsError() {
        val result = CidrFunctionLibrary.anonymizeIp(Value.of("192.168.1.1"), Value.of(33));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("out of range");
    }

    @Test
    void canSubdivide_whenInvalidTargetPrefix_thenReturnsError() {
        val result = CidrFunctionLibrary.canSubdivide(Value.of("192.168.0.0/24"), Value.of(33));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("out of range");
    }

    @Test
    void isPublicRoutable_whenInvalidIp_thenReturnsError() {
        val result = CidrFunctionLibrary.isPublicRoutable(Value.of("invalid-ip"));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Invalid IP address");
    }

    @Test
    void sameSubnet_whenDifferentAddressFamilies_thenReturnsFalse() {
        val result = CidrFunctionLibrary.sameSubnet(Value.of("192.168.1.1"), Value.of("2001:db8::1"), Value.of(24));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.FALSE);
    }

    @Test
    void getCommonPrefixLength_whenDifferentFamilies_thenReturnsZero() {
        val result = CidrFunctionLibrary.getCommonPrefixLength(Value.of("192.168.1.1"), Value.of("2001:db8::1"));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(0));
    }

    private static ArrayValue buildStringArray(String... values) {
        return Value.ofArray(Stream.of(values).map(Value::of).toArray(Value[]::new));
    }
}
