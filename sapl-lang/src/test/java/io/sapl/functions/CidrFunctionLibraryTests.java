/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.functions;

import io.sapl.api.interpreter.Val;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Comprehensive tests for CidrFunctionLibrary verifying RFC compliance and
 * security properties.
 *
 * Test Design Principles:
 * - Every test case is mathematically verified
 * - Address family mixing returns false (not error) for semantic correctness
 * - Edge cases (boundary conditions, alignment) are explicitly tested
 * - IPv4 and IPv6 are tested symmetrically where applicable
 * - IPv6 output is in RFC 5952 canonical form (compressed, lowercase)
 * - Semantic equivalence helpers test functional correctness independent of
 * formatting
 */
class CidrFunctionLibraryTests {

    /**
     * Helper to check if two CIDR strings are semantically equivalent.
     * Parses both and compares network address and prefix length.
     */
    private static boolean cidrsSemanticallyEquivalent(String cidr1, String cidr2) {
        try {
            val range1 = parseCidrForTest(cidr1);
            val range2 = parseCidrForTest(cidr2);

            if (range1.prefixLength() != range2.prefixLength()) {
                return false;
            }

            val network1 = getNetworkAddressForTest(range1);
            val network2 = getNetworkAddressForTest(range2);

            return network1.equals(network2);
        } catch (Exception exception) {
            return false;
        }
    }

    private static TestCidrRange parseCidrForTest(String cidr) throws UnknownHostException {
        val parts        = cidr.split("/");
        val address      = InetAddress.getByName(parts[0]);
        val prefixLength = parts.length == 2 ? Integer.parseInt(parts[1]) : (address.getAddress().length * 8);
        return new TestCidrRange(address, prefixLength);
    }

    private static InetAddress getNetworkAddressForTest(TestCidrRange range) throws UnknownHostException {
        val addressBytes = range.address().getAddress();
        val mask         = createMaskForTest(range.prefixLength(), addressBytes.length);
        val resultBytes  = new byte[addressBytes.length];

        for (int i = 0; i < addressBytes.length; i++) {
            resultBytes[i] = (byte) (addressBytes[i] & mask[i]);
        }

        return InetAddress.getByAddress(resultBytes);
    }

    private static byte[] createMaskForTest(int prefixLength, int addressLength) {
        val mask          = new byte[addressLength];
        val fullBytes     = prefixLength / 8;
        val remainingBits = prefixLength % 8;

        java.util.Arrays.fill(mask, 0, fullBytes, (byte) 0xFF);

        if (fullBytes < addressLength && remainingBits > 0) {
            mask[fullBytes] = (byte) (0xFF << (8 - remainingBits));
        }

        return mask;
    }

    record TestCidrRange(InetAddress address, int prefixLength) {}

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
    void testCidrContains(String cidr, String cidrOrIp, boolean expected) {
        val result = CidrFunctionLibrary.contains(Val.of(cidr), Val.of(cidrOrIp));

        assertThat(result.isBoolean()).isTrue();
        assertThat(result.getBoolean()).isEqualTo(expected);
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
    void testCidrContainsInvalidInput(String cidr, String cidrOrIp) {
        val result = CidrFunctionLibrary.contains(Val.of(cidr), Val.of(cidrOrIp));

        assertThat(result.isError()).isTrue();
    }

    @ParameterizedTest(name = "Mixed families: {0} contains {1} = false")
    @CsvSource(delimiterString = "|", textBlock = """
            192.168.1.0/24     | 2001:db8::1
            2001:db8::/32      | 192.168.1.1
            10.0.0.0/8         | fe80::1
            ::1/128            | 127.0.0.1
            """)
    void testCidrContainsMixedAddressFamilies(String cidr, String cidrOrIp) {
        val result = CidrFunctionLibrary.contains(Val.of(cidr), Val.of(cidrOrIp));

        assertThat(result.isBoolean()).isTrue();
        assertThat(result.getBoolean()).isFalse();
    }

    @Test
    void testContainsMatchesBasicScenario() {
        val cidrs = Val.JSON.arrayNode();
        cidrs.add("10.0.0.0/8");
        cidrs.add("192.168.0.0/16");
        cidrs.add("172.16.0.0/12");

        val ips = Val.JSON.arrayNode();
        ips.add("10.1.2.3");
        ips.add("8.8.8.8");
        ips.add("192.168.5.10");

        val result = CidrFunctionLibrary.containsMatches(Val.of(cidrs), Val.of(ips));

        assertThat(result.isArray()).isTrue();

        val matches = result.get();
        assertThat(matches).hasSize(2);

        assertThat(matches.get(0).get(0).asInt()).isZero();
        assertThat(matches.get(0).get(1).asInt()).isZero();

        assertThat(matches.get(1).get(0).asInt()).isEqualTo(1);
        assertThat(matches.get(1).get(1).asInt()).isEqualTo(2);
    }

    @Test
    void testContainsMatchesNoMatches() {
        val cidrs = Val.JSON.arrayNode();
        cidrs.add("10.0.0.0/8");
        cidrs.add("192.168.0.0/16");

        val ips = Val.JSON.arrayNode();
        ips.add("8.8.8.8");
        ips.add("1.1.1.1");

        val result = CidrFunctionLibrary.containsMatches(Val.of(cidrs), Val.of(ips));

        assertThat(result.isArray()).isTrue();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void testContainsMatchesAllMatch() {
        val cidrs = Val.JSON.arrayNode();
        cidrs.add("10.0.0.0/8");

        val ips = Val.JSON.arrayNode();
        ips.add("10.1.1.1");
        ips.add("10.2.2.2");
        ips.add("10.3.3.3");

        val result = CidrFunctionLibrary.containsMatches(Val.of(cidrs), Val.of(ips));

        assertThat(result.isArray()).isTrue();
        assertThat(result.get()).hasSize(3);
    }

    @Test
    void testContainsMatchesInvalidInput() {
        val cidrs = Val.JSON.arrayNode();
        cidrs.add("10.0.0.0/8");
        cidrs.add("invalid");

        val ips = Val.JSON.arrayNode();
        ips.add("10.1.1.1");

        val result = CidrFunctionLibrary.containsMatches(Val.of(cidrs), Val.of(ips));

        assertThat(result.isError()).isTrue();
    }

    @ParameterizedTest(name = "Expand {0} into {1} addresses")
    @MethodSource("expandTestCases")
    void testCidrExpand(String cidr, String[] expectedAddresses) {
        val result = CidrFunctionLibrary.expand(Val.of(cidr));

        assertThat(result.isArray()).isTrue();

        val addresses = new java.util.ArrayList<String>();
        result.get().forEach(node -> addresses.add(node.textValue()));

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
    void testCidrExpandTooLarge(String cidr) {
        val result = CidrFunctionLibrary.expand(Val.of(cidr));

        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).contains("maximum is 65535");
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
    void testCidrIntersects(String cidr1, String cidr2, boolean expected) {
        val result = CidrFunctionLibrary.intersects(Val.of(cidr1), Val.of(cidr2));

        assertThat(result.isBoolean()).isTrue();
        assertThat(result.getBoolean()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "Valid CIDR: {0}")
    @ValueSource(strings = { "192.168.1.0/24", "10.0.0.0/8", "172.16.0.0/12", "0.0.0.0/0", "255.255.255.255/32",
            "2001:db8::/32", "fe80::/10", "::/0", "::1/128" })
    void testCidrIsValidValidInput(String cidr) {
        val result = CidrFunctionLibrary.isValid(Val.of(cidr));

        assertThat(result.isBoolean()).isTrue();
        assertThat(result.getBoolean()).isTrue();
    }

    @ParameterizedTest(name = "Invalid CIDR: {0}")
    @ValueSource(strings = { "999.999.999.999/24", "not-an-ip/24", "192.168.1.0/33", "192.168.1.0/-1", "2001:db8::/129",
            "192.168.1.0//24" })
    void testCidrIsValidInvalidInput(String cidr) {
        val result = CidrFunctionLibrary.isValid(Val.of(cidr));

        assertThat(result.isBoolean()).isTrue();
        assertThat(result.getBoolean()).isFalse();
    }

    @ParameterizedTest(name = "Merge: {0}")
    @MethodSource("mergeTestCases")
    void testCidrMerge(String[] inputCidrs, String[] expectedCidrs) {
        val inputArray = Val.JSON.arrayNode();
        for (String cidr : inputCidrs) {
            inputArray.add(cidr);
        }

        val result = CidrFunctionLibrary.merge(Val.of(inputArray));

        assertThat(result.isArray()).isTrue();

        val mergedCidrs = new java.util.ArrayList<String>();
        result.get().forEach(node -> mergedCidrs.add(node.textValue()));

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

    @Test
    void testCidrMergeSemanticEquivalence() {
        val inputArray = Val.JSON.arrayNode();
        inputArray.add("2001:db8:0:0::/64");
        inputArray.add("2001:db8:0:1::/64");

        val result = CidrFunctionLibrary.merge(Val.of(inputArray));

        assertThat(result.isArray()).isTrue();
        val mergedCidrs = new java.util.ArrayList<String>();
        result.get().forEach(node -> mergedCidrs.add(node.textValue()));

        assertThat(mergedCidrs).hasSize(1);
        assertThat(cidrsSemanticallyEquivalent(mergedCidrs.get(0), "2001:db8::/63")).isTrue();
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
    void testIsPrivateIpv4(String ipAddress, boolean expected) {
        val result = CidrFunctionLibrary.isPrivateIpv4(Val.of(ipAddress));

        assertThat(result.isBoolean()).isTrue();
        assertThat(result.getBoolean()).isEqualTo(expected);
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
    void testIsLoopback(String ipAddress, boolean expected) {
        val result = CidrFunctionLibrary.isLoopback(Val.of(ipAddress));

        assertThat(result.isBoolean()).isTrue();
        assertThat(result.getBoolean()).isEqualTo(expected);
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
    void testIsLinkLocal(String ipAddress, boolean expected) {
        val result = CidrFunctionLibrary.isLinkLocal(Val.of(ipAddress));

        assertThat(result.isBoolean()).isTrue();
        assertThat(result.getBoolean()).isEqualTo(expected);
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
    void testIsMulticast(String ipAddress, boolean expected) {
        val result = CidrFunctionLibrary.isMulticast(Val.of(ipAddress));

        assertThat(result.isBoolean()).isTrue();
        assertThat(result.getBoolean()).isEqualTo(expected);
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
    void testIsDocumentation(String ipAddress, boolean expected) {
        val result = CidrFunctionLibrary.isDocumentation(Val.of(ipAddress));

        assertThat(result.isBoolean()).isTrue();
        assertThat(result.getBoolean()).isEqualTo(expected);
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
    void testIsCgnat(String ipAddress, boolean expected) {
        val result = CidrFunctionLibrary.isCgnat(Val.of(ipAddress));

        assertThat(result.isBoolean()).isTrue();
        assertThat(result.getBoolean()).isEqualTo(expected);
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
    void testIsBenchmark(String ipAddress, boolean expected) {
        val result = CidrFunctionLibrary.isBenchmark(Val.of(ipAddress));

        assertThat(result.isBoolean()).isTrue();
        assertThat(result.getBoolean()).isEqualTo(expected);
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
    void testIsReserved(String ipAddress, boolean expected) {
        val result = CidrFunctionLibrary.isReserved(Val.of(ipAddress));

        assertThat(result.isBoolean()).isTrue();
        assertThat(result.getBoolean()).isEqualTo(expected);
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
    void testIsBroadcast(String ipAddress, boolean expected) {
        val result = CidrFunctionLibrary.isBroadcast(Val.of(ipAddress));

        assertThat(result.isBoolean()).isTrue();
        assertThat(result.getBoolean()).isEqualTo(expected);
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
    void testIsPublicRoutable(String ipAddress, boolean expected) {
        val result = CidrFunctionLibrary.isPublicRoutable(Val.of(ipAddress));

        assertThat(result.isBoolean()).isTrue();
        assertThat(result.getBoolean()).isEqualTo(expected);
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
    void testAnonymizeIp(String ipAddress, int prefixLength, String expected) {
        val result = CidrFunctionLibrary.anonymizeIp(Val.of(ipAddress), Val.of(prefixLength));

        assertThat(result.isTextual()).isTrue();
        assertThat(result.getText()).isEqualTo(expected);
    }

    @Test
    void testAnonymizeIpInvalidPrefix() {
        val result = CidrFunctionLibrary.anonymizeIp(Val.of("192.168.1.1"), Val.of(33));

        assertThat(result.isError()).isTrue();
    }

    @Test
    void testHashIpPrefixConsistency() {
        val hash1 = CidrFunctionLibrary.hashIpPrefix(Val.of("192.168.1.100"), Val.of(24), Val.of("salt"));
        val hash2 = CidrFunctionLibrary.hashIpPrefix(Val.of("192.168.1.200"), Val.of(24), Val.of("salt"));
        val hash3 = CidrFunctionLibrary.hashIpPrefix(Val.of("192.168.2.100"), Val.of(24), Val.of("salt"));

        assertThat(hash1.getText()).isEqualTo(hash2.getText());
        assertThat(hash1.getText()).isNotEqualTo(hash3.getText());
    }

    @Test
    void testHashIpPrefixDifferentSalts() {
        val hash1 = CidrFunctionLibrary.hashIpPrefix(Val.of("192.168.1.100"), Val.of(24), Val.of("salt1"));
        val hash2 = CidrFunctionLibrary.hashIpPrefix(Val.of("192.168.1.100"), Val.of(24), Val.of("salt2"));

        assertThat(hash1.getText()).isNotEqualTo(hash2.getText());
    }

    @ParameterizedTest(name = "getNetworkAddress({0}) = {1}")
    @CsvSource(delimiterString = "|", textBlock = """
            192.168.1.64/26    | 192.168.1.64
            192.168.1.100/24   | 192.168.1.0
            10.20.30.40/16     | 10.20.0.0
            2001:db8:abcd::/48 | 2001:db8:abcd::
            """)
    void testGetNetworkAddress(String cidr, String expected) {
        val result = CidrFunctionLibrary.getNetworkAddress(Val.of(cidr));

        assertThat(result.isTextual()).isTrue();
        assertThat(result.getText()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "getBroadcastAddress({0}) = {1}")
    @CsvSource(delimiterString = "|", textBlock = """
            192.168.1.0/24     | 192.168.1.255
            192.168.1.64/26    | 192.168.1.127
            10.0.0.0/8         | 10.255.255.255
            2001:db8::/32      | 2001:db8:ffff:ffff:ffff:ffff:ffff:ffff
            """)
    void testGetBroadcastAddress(String cidr, String expected) {
        val result = CidrFunctionLibrary.getBroadcastAddress(Val.of(cidr));

        assertThat(result.isTextual()).isTrue();
        assertThat(result.getText()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "getAddressCount({0}) = {1}")
    @CsvSource(delimiterString = "|", textBlock = """
            192.168.1.0/24     | 256
            192.168.1.0/30     | 4
            10.0.0.0/8         | 16777216
            192.168.1.0/32     | 1
            2001:db8::/64      | 18446744073709551616
            """)
    void testGetAddressCount(String cidr, String expected) {
        val result = CidrFunctionLibrary.getAddressCount(Val.of(cidr));

        assertThat(result.isTextual()).isTrue();
        assertThat(result.getText()).isEqualTo(expected);
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
    void testGetUsableHostCount(String cidr, String expected) {
        val result = CidrFunctionLibrary.getUsableHostCount(Val.of(cidr));

        assertThat(result.isTextual()).isTrue();
        assertThat(result.getText()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "getFirstUsableAddress({0}) = {1}")
    @CsvSource(delimiterString = "|", textBlock = """
            192.168.1.0/24     | 192.168.1.1
            10.0.0.0/8         | 10.0.0.1
            192.168.1.64/26    | 192.168.1.65
            2001:db8::/64      | 2001:db8::1
            """)
    void testGetFirstUsableAddress(String cidr, String expected) {
        val result = CidrFunctionLibrary.getFirstUsableAddress(Val.of(cidr));

        assertThat(result.isTextual()).isTrue();
        assertThat(result.getText()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "getLastUsableAddress({0}) = {1}")
    @CsvSource(delimiterString = "|", textBlock = """
            192.168.1.0/24     | 192.168.1.254
            10.0.0.0/8         | 10.255.255.254
            192.168.1.64/26    | 192.168.1.126
            2001:db8::/64      | 2001:db8::ffff:ffff:ffff:ffff
            """)
    void testGetLastUsableAddress(String cidr, String expected) {
        val result = CidrFunctionLibrary.getLastUsableAddress(Val.of(cidr));

        assertThat(result.isTextual()).isTrue();
        assertThat(result.getText()).isEqualTo(expected);
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
    void testSameSubnet(String ip1, String ip2, int prefixLength, boolean expected) {
        val result = CidrFunctionLibrary.sameSubnet(Val.of(ip1), Val.of(ip2), Val.of(prefixLength));

        assertThat(result.isBoolean()).isTrue();
        assertThat(result.getBoolean()).isEqualTo(expected);
    }

    @Test
    void testSameSubnetDifferentAddressFamilies() {
        val result = CidrFunctionLibrary.sameSubnet(Val.of("192.168.1.1"), Val.of("2001:db8::1"), Val.of(24));

        assertThat(result.isBoolean()).isTrue();
        assertThat(result.getBoolean()).isFalse();
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
    void testGetCommonPrefixLength(String ip1, String ip2, int expected) {
        val result = CidrFunctionLibrary.getCommonPrefixLength(Val.of(ip1), Val.of(ip2));

        assertThat(result.isNumber()).isTrue();
        assertThat(result.get().asInt()).isEqualTo(expected);
    }

    @Test
    void testGetCommonPrefixLengthDifferentFamilies() {
        val result = CidrFunctionLibrary.getCommonPrefixLength(Val.of("192.168.1.1"), Val.of("2001:db8::1"));

        assertThat(result.isNumber()).isTrue();
        assertThat(result.get().asInt()).isZero();
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
    void testCanSubdivide(String cidr, int targetPrefix, boolean expected) {
        val result = CidrFunctionLibrary.canSubdivide(Val.of(cidr), Val.of(targetPrefix));

        assertThat(result.isBoolean()).isTrue();
        assertThat(result.getBoolean()).isEqualTo(expected);
    }

    @Test
    void testCanSubdivideInvalidTargetPrefix() {
        val result = CidrFunctionLibrary.canSubdivide(Val.of("192.168.0.0/24"), Val.of(33));

        assertThat(result.isError()).isTrue();
    }
}
