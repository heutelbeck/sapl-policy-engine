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

import inet.ipaddr.AddressStringException;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.*;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * CIDR network operations for authorization policies.
 * <p>
 * Uses the IPAddress library for RFC-compliant IPv4 and IPv6 operations. All
 * IPv6 addresses use RFC 5952 canonical form
 * (compressed, lowercase). Address family mixing returns false rather than
 * errors for cleaner policy logic.
 */
@UtilityClass
@FunctionLibrary(name = CidrFunctionLibrary.NAME, description = CidrFunctionLibrary.DESCRIPTION, libraryDocumentation = CidrFunctionLibrary.DOCUMENTATION)
public class CidrFunctionLibrary {

    public static final String NAME          = "cidr";
    public static final String DESCRIPTION   = "CIDR network operations and IP address validation for authorization policies.";
    public static final String DOCUMENTATION = """
            # CIDR Network Functions

            Network-based access control using IP addresses and CIDR notation. Test membership,
            validate configurations, anonymize addresses for privacy compliance, and calculate
            subnet properties.

            ## Access Control Patterns

            Restrict access based on network location. Check if client IPs fall within trusted
            corporate networks or geographic regions.

            ```sapl
            policy "corporate_only"
            permit action == "access_api";
                cidr.contains("10.0.0.0/8", subject.ipAddress);
            ```

            Prevent Server-Side Request Forgery by blocking requests to internal networks.

            ```sapl
            policy "block_ssrf"
            deny action == "fetch_url";
                var ip = resource.url.resolvedIp;
                cidr.isPrivateIpv4(ip) || cidr.isLoopback(ip);
            ```

            Anonymize client IPs before logging to comply with GDPR while maintaining geographic
            or organizational context for analytics.

            ```sapl
            policy "log_access"
            permit
            obligation
                {
                    "type": "log",
                    "subnet": cidr.anonymizeIp(subject.ipAddress, 24)
                }
            ```

            Validate network configurations to prevent overlapping address assignments or
            security zone violations.

            ```sapl
            policy "no_dmz_overlap"
            permit action == "create_subnet";
                !cidr.intersects(resource.cidr, "203.0.113.0/24");
            ```
            """;

    private static final int MAX_EXPANSION = 65535;

    private static final String ADDRESS_FAMILY_IPV4 = "IPv4";
    private static final String ADDRESS_FAMILY_IPV6 = "IPv6";
    private static final String ALGORITHM_SHA256    = "SHA-256";

    private static final String ERROR_ADDRESSES_MAXIMUM       = " addresses, maximum is ";
    private static final String ERROR_ALGORITHM_NOT_AVAILABLE = " not available: ";
    private static final String ERROR_ARRAY_MUST_BE_STRINGS   = "Array must contain only strings.";
    private static final String ERROR_CIDR_CONTAINS           = "CIDR contains ";
    private static final String ERROR_CIDR_MISSING_PREFIX     = "CIDR missing prefix length: %s.";
    private static final String ERROR_INVALID_ADDRESS         = "Invalid address: %s.";
    private static final String ERROR_INVALID_CIDR            = "Invalid CIDR: %s.";
    private static final String ERROR_INVALID_FIRST_CIDR      = "Invalid first CIDR: %s.";
    private static final String ERROR_INVALID_FIRST_IP        = "Invalid first IP: %s.";
    private static final String ERROR_INVALID_IP_ADDRESS      = "Invalid IP address: %s.";
    private static final String ERROR_INVALID_SECOND_CIDR     = "Invalid second CIDR: %s.";
    private static final String ERROR_INVALID_SECOND_IP       = "Invalid second IP: %s.";
    private static final String ERROR_PREFIX                  = "Prefix ";
    private static final String ERROR_PREFIX_OUT_OF_RANGE     = " out of range for ";

    private static final String RANGE_IPV4 = " (0-32).";
    private static final String RANGE_IPV6 = " (0-128).";

    private static final List<String> IPV4_BENCHMARK_RANGES     = List.of("198.18.0.0/15");
    private static final List<String> IPV4_BROADCAST_RANGES     = List.of("255.255.255.255/32");
    private static final List<String> IPV4_CGNAT_RANGES         = List.of("100.64.0.0/10");
    private static final List<String> IPV4_DOCUMENTATION_RANGES = List.of("192.0.2.0/24", "198.51.100.0/24",
            "203.0.113.0/24");
    private static final List<String> IPV4_LINK_LOCAL_RANGES    = List.of("169.254.0.0/16");
    private static final List<String> IPV4_LOOPBACK_RANGES      = List.of("127.0.0.0/8");
    private static final List<String> IPV4_MULTICAST_RANGES     = List.of("224.0.0.0/4");
    private static final List<String> IPV4_RESERVED_RANGES      = List.of("240.0.0.0/4");
    private static final List<String> IPV6_DOCUMENTATION_RANGES = List.of("2001:db8::/32");
    private static final List<String> IPV6_LINK_LOCAL_RANGES    = List.of("fe80::/10");
    private static final List<String> IPV6_LOOPBACK_RANGES      = List.of("::1/128");
    private static final List<String> IPV6_MULTICAST_RANGES     = List.of("ff00::/8");
    private static final List<String> IPV6_RESERVED_RANGES      = List.of("::/128", "::ffff:0:0/96", "100::/64",
            "2001::/23");
    private static final List<String> RFC1918_PRIVATE_RANGES    = List.of("10.0.0.0/8", "172.16.0.0/12",
            "192.168.0.0/16");

    /**
     * Tests if an IP address or CIDR range falls within another CIDR.
     *
     * @param cidr
     * the containing CIDR range
     * @param cidrOrIp
     * IP address or CIDR to test
     *
     * @return Value.TRUE if contained, Value.FALSE otherwise, or ErrorValue on
     * invalid input
     */
    @Function(docs = """
            ```cidr.contains(STRING cidr, STRING cidrOrIp)```

            Tests if an IP address or CIDR range falls within another CIDR. Works with both
            IPv4 and IPv6 addresses. Mixing address families returns false.

            Parameters:
            - cidr: The containing CIDR range (e.g., "10.0.0.0/8")
            - cidrOrIp: IP address or CIDR to test (e.g., "10.0.1.5" or "10.0.1.0/24")

            Returns: Boolean indicating containment

            Example - restrict API access to corporate network:

            ```sapl
            policy "corporate_api"
            permit action == "call_api";
                cidr.contains("198.51.100.0/24", subject.ipAddress);
            ```
            """)
    public static Value contains(TextValue cidr, TextValue cidrOrIp) {
        val containingRange = parseAddress(cidr.value());
        if (containingRange == null) {
            return Value.error(ERROR_INVALID_CIDR, cidr.value());
        }

        val testAddress = parseAddress(cidrOrIp.value());
        if (testAddress == null) {
            return Value.error(ERROR_INVALID_IP_ADDRESS, cidrOrIp.value());
        }

        return Value.of(containingRange.toPrefixBlock().contains(testAddress));
    }

    /**
     * Batch containment checking across multiple CIDRs and IPs.
     *
     * @param cidrs
     * array of CIDR strings to check against
     * @param cidrsOrIps
     * array of IP addresses or CIDRs to test
     *
     * @return ArrayValue of [cidrIndex, ipIndex] tuples for each match, or
     * ErrorValue on invalid input
     */
    @Function(docs = """
            ```cidr.containsMatches(ARRAY cidrs, ARRAY cidrsOrIps)```

            Batch containment checking across multiple CIDRs and IPs. Returns index pairs
            identifying all matches as [cidrIndex, ipIndex]. More efficient than individual
            contains() calls when checking many addresses against many ranges.

            Parameters:
            - cidrs: Array of CIDR strings to check against
            - cidrsOrIps: Array of IP addresses or CIDRs to test

            Returns: Array of [cidrIndex, ipIndex] tuples for each match

            Example - verify any user IP matches trusted networks:

            ```sapl
            policy "multi_location"
            permit
                var trusted = ["10.0.0.0/8", "172.16.0.0/12"];
                var matches = cidr.containsMatches(trusted, subject.recentIps);
                matches != [];
            ```
            """)
    public static Value containsMatches(ArrayValue cidrs, ArrayValue cidrsOrIps) {
        try {
            val cidrAddresses = parseAddressArray(cidrs);
            val testAddresses = parseAddressArray(cidrsOrIps);
            val resultBuilder = ArrayValue.builder();

            for (int i = 0; i < cidrAddresses.size(); i++) {
                val cidrBlock = cidrAddresses.get(i).toPrefixBlock();
                for (int j = 0; j < testAddresses.size(); j++) {
                    if (cidrBlock.contains(testAddresses.get(j))) {
                        val tupleBuilder = ArrayValue.builder();
                        tupleBuilder.add(Value.of(i));
                        tupleBuilder.add(Value.of(j));
                        resultBuilder.add(tupleBuilder.build());
                    }
                }
            }

            return resultBuilder.build();
        } catch (IllegalArgumentException exception) {
            return Value.error(exception.getMessage());
        }
    }

    /**
     * Enumerates all IP addresses in a CIDR range.
     *
     * @param cidr
     * CIDR range to expand
     *
     * @return ArrayValue of IP address strings, or ErrorValue on invalid input or
     * if range is too large
     */
    @Function(docs = """
            ```cidr.expand(STRING cidr)```

            Enumerates all IP addresses in a CIDR range. Limited to 65535 addresses to
            prevent memory exhaustion. IPv6 addresses use RFC 5952 canonical form.

            Parameters:
            - cidr: CIDR range to expand (e.g., "192.168.0.0/30")

            Returns: Array of IP address strings
            """)
    public static Value expand(TextValue cidr) {
        val address = parseAddress(cidr.value());
        if (address == null) {
            return Value.error(ERROR_INVALID_CIDR, cidr.value());
        }

        val prefixBlock = address.toPrefixBlock();
        val count       = prefixBlock.getCount();

        if (count.compareTo(BigInteger.valueOf(MAX_EXPANSION)) > 0) {
            return Value.error(ERROR_CIDR_CONTAINS + count + ERROR_ADDRESSES_MAXIMUM + MAX_EXPANSION + ".");
        }

        val resultBuilder = ArrayValue.builder();
        prefixBlock.iterator().forEachRemaining(
                ipAddress -> resultBuilder.add(Value.of(ipAddress.withoutPrefixLength().toCanonicalString())));

        return resultBuilder.build();
    }

    /**
     * Tests whether two CIDR ranges share any addresses.
     *
     * @param cidr1
     * first CIDR range
     * @param cidr2
     * second CIDR range
     *
     * @return Value.TRUE if overlapping, Value.FALSE otherwise, or ErrorValue on
     * invalid input
     */
    @Function(docs = """
            ```cidr.intersects(STRING cidr1, STRING cidr2)```

            Tests whether two CIDR ranges share any addresses. Use this to validate network
            allocations don't conflict or to detect overlapping security zones.

            Parameters:
            - cidr1: First CIDR range
            - cidr2: Second CIDR range

            Returns: Boolean indicating overlap
            """)
    public static Value intersects(TextValue cidr1, TextValue cidr2) {
        val address1 = parseAddress(cidr1.value());
        if (address1 == null) {
            return Value.error(ERROR_INVALID_FIRST_CIDR, cidr1.value());
        }

        val address2 = parseAddress(cidr2.value());
        if (address2 == null) {
            return Value.error(ERROR_INVALID_SECOND_CIDR, cidr2.value());
        }

        val block1 = address1.toPrefixBlock();
        val block2 = address2.toPrefixBlock();

        return Value.of(block1.intersect(block2) != null);
    }

    /**
     * Validates CIDR notation or IP address syntax.
     *
     * @param cidr
     * string to validate
     *
     * @return Value.TRUE if valid, Value.FALSE otherwise
     */
    @Function(docs = """
            ```cidr.isValid(STRING cidr)```

            Validates CIDR notation or IP address syntax for both IPv4 and IPv6.

            Parameters:
            - cidr: String to validate

            Returns: Boolean indicating validity
            """)
    public static Value isValid(TextValue cidr) {
        return Value.of(parseAddress(cidr.value()) != null);
    }

    /**
     * Consolidates IP addresses and subnets into the minimal set of non-overlapping
     * CIDRs.
     *
     * @param addresses
     * array of IP addresses and CIDR strings
     *
     * @return ArrayValue of minimal CIDR strings, or ErrorValue on invalid input
     */
    @Function(docs = """
            ```cidr.merge(ARRAY addresses)```

            Consolidates IP addresses and subnets into the minimal set of non-overlapping CIDRs.
            Eliminates duplicates, removes contained ranges, and combines adjacent blocks. IPv6
            addresses use RFC 5952 canonical form.

            Parameters:
            - addresses: Array of IP addresses and CIDR strings

            Returns: Array of minimal CIDR strings
            """)
    public static Value merge(ArrayValue addresses) {
        try {
            val ipAddresses = parseAddressArray(addresses);
            if (ipAddresses.isEmpty()) {
                return Value.EMPTY_ARRAY;
            }

            val ipv4Addresses = new ArrayList<IPAddress>();
            val ipv6Addresses = new ArrayList<IPAddress>();

            for (IPAddress address : ipAddresses) {
                if (address.isIPv4()) {
                    ipv4Addresses.add(address);
                } else {
                    ipv6Addresses.add(address);
                }
            }

            val resultBuilder = ArrayValue.builder();
            addMergedAddresses(resultBuilder, ipv4Addresses);
            addMergedAddresses(resultBuilder, ipv6Addresses);

            return resultBuilder.build();
        } catch (IllegalArgumentException exception) {
            return Value.error(exception.getMessage());
        }
    }

    /**
     * Tests if an IPv4 address falls in RFC 1918 private ranges.
     *
     * @param ipAddress
     * IP address to test
     *
     * @return Value.TRUE if private, Value.FALSE otherwise, or ErrorValue on
     * invalid input
     */
    @Function(docs = """
            ```cidr.isPrivateIpv4(STRING ipAddress)```

            Tests if an IPv4 address falls in RFC 1918 private ranges: 10.0.0.0/8,
            172.16.0.0/12, or 192.168.0.0/16. Returns false for IPv6 addresses.

            Parameters:
            - ipAddress: IP address to test

            Returns: Boolean indicating private address
            """)
    public static Value isPrivateIpv4(TextValue ipAddress) {
        return checkInRanges(ipAddress.value(), RFC1918_PRIVATE_RANGES);
    }

    /**
     * Tests for loopback addresses.
     *
     * @param ipAddress
     * IP address to test
     *
     * @return Value.TRUE if loopback, Value.FALSE otherwise, or ErrorValue on
     * invalid input
     */
    @Function(docs = """
            ```cidr.isLoopback(STRING ipAddress)```

            Tests for loopback addresses: 127.0.0.0/8 (IPv4) or ::1/128 (IPv6).

            Parameters:
            - ipAddress: IP address to test

            Returns: Boolean indicating loopback address
            """)
    public static Value isLoopback(TextValue ipAddress) {
        return checkInRangesByFamily(ipAddress.value(), IPV4_LOOPBACK_RANGES, IPV6_LOOPBACK_RANGES);
    }

    /**
     * Tests for link-local addresses.
     *
     * @param ipAddress
     * IP address to test
     *
     * @return Value.TRUE if link-local, Value.FALSE otherwise, or ErrorValue on
     * invalid input
     */
    @Function(docs = """
            ```cidr.isLinkLocal(STRING ipAddress)```

            Tests for link-local addresses: 169.254.0.0/16 (IPv4) or fe80::/10 (IPv6).
            These addresses are only valid on the local network segment.

            Parameters:
            - ipAddress: IP address to test

            Returns: Boolean indicating link-local address
            """)
    public static Value isLinkLocal(TextValue ipAddress) {
        return checkInRangesByFamily(ipAddress.value(), IPV4_LINK_LOCAL_RANGES, IPV6_LINK_LOCAL_RANGES);
    }

    /**
     * Tests for multicast addresses.
     *
     * @param ipAddress
     * IP address to test
     *
     * @return Value.TRUE if multicast, Value.FALSE otherwise, or ErrorValue on
     * invalid input
     */
    @Function(docs = """
            ```cidr.isMulticast(STRING ipAddress)```

            Tests for multicast addresses: 224.0.0.0/4 (IPv4) or ff00::/8 (IPv6).

            Parameters:
            - ipAddress: IP address to test

            Returns: Boolean indicating multicast address
            """)
    public static Value isMulticast(TextValue ipAddress) {
        return checkInRangesByFamily(ipAddress.value(), IPV4_MULTICAST_RANGES, IPV6_MULTICAST_RANGES);
    }

    /**
     * Tests if an address is in documentation ranges.
     *
     * @param ipAddress
     * IP address to test
     *
     * @return Value.TRUE if documentation range, Value.FALSE otherwise, or
     * ErrorValue on invalid input
     */
    @Function(docs = """
            ```cidr.isDocumentation(STRING ipAddress)```

            Tests if an address is in ranges reserved for documentation and examples.
            IPv4: 192.0.2.0/24, 198.51.100.0/24, 203.0.113.0/24
            IPv6: 2001:db8::/32

            Parameters:
            - ipAddress: IP address to test

            Returns: Boolean indicating documentation range
            """)
    public static Value isDocumentation(TextValue ipAddress) {
        return checkInRangesByFamily(ipAddress.value(), IPV4_DOCUMENTATION_RANGES, IPV6_DOCUMENTATION_RANGES);
    }

    /**
     * Tests if an IPv4 address is in the Carrier-Grade NAT range.
     *
     * @param ipAddress
     * IP address to test
     *
     * @return Value.TRUE if CGNAT range, Value.FALSE otherwise, or ErrorValue on
     * invalid input
     */
    @Function(docs = """
            ```cidr.isCgnat(STRING ipAddress)```

            Tests if an IPv4 address is in the Carrier-Grade NAT range (100.64.0.0/10).
            ISPs use this range for shared address space. Returns false for IPv6.

            Parameters:
            - ipAddress: IP address to test

            Returns: Boolean indicating CGNAT range
            """)
    public static Value isCgnat(TextValue ipAddress) {
        return checkInRanges(ipAddress.value(), IPV4_CGNAT_RANGES);
    }

    /**
     * Tests if an IPv4 address is in the benchmarking range.
     *
     * @param ipAddress
     * IP address to test
     *
     * @return Value.TRUE if benchmark range, Value.FALSE otherwise, or ErrorValue
     * on invalid input
     */
    @Function(docs = """
            ```cidr.isBenchmark(STRING ipAddress)```

            Tests if an IPv4 address is in the benchmarking range (198.18.0.0/15).
            Reserved for network testing. Returns false for IPv6.

            Parameters:
            - ipAddress: IP address to test

            Returns: Boolean indicating benchmark range
            """)
    public static Value isBenchmark(TextValue ipAddress) {
        return checkInRanges(ipAddress.value(), IPV4_BENCHMARK_RANGES);
    }

    /**
     * Tests if an address is in reserved ranges.
     *
     * @param ipAddress
     * IP address to test
     *
     * @return Value.TRUE if reserved range, Value.FALSE otherwise, or ErrorValue on
     * invalid input
     */
    @Function(docs = """
            ```cidr.isReserved(STRING ipAddress)```

            Tests if an address is in ranges reserved for future use or special purposes.
            IPv4: 240.0.0.0/4
            IPv6: ::/128, ::ffff:0:0/96, 100::/64, 2001::/23, 2001:db8::/32

            Parameters:
            - ipAddress: IP address to test

            Returns: Boolean indicating reserved range
            """)
    public static Value isReserved(TextValue ipAddress) {
        val combined = new ArrayList<String>();
        combined.addAll(IPV6_RESERVED_RANGES);
        combined.addAll(IPV6_DOCUMENTATION_RANGES);
        return checkInRangesByFamily(ipAddress.value(), IPV4_RESERVED_RANGES, combined);
    }

    /**
     * Tests if an IPv4 address is the broadcast address.
     *
     * @param ipAddress
     * IP address to test
     *
     * @return Value.TRUE if broadcast, Value.FALSE otherwise, or ErrorValue on
     * invalid input
     */
    @Function(docs = """
            ```cidr.isBroadcast(STRING ipAddress)```

            Tests if an IPv4 address is the broadcast address (255.255.255.255).
            Returns false for IPv6.

            Parameters:
            - ipAddress: IP address to test

            Returns: Boolean indicating broadcast address
            """)
    public static Value isBroadcast(TextValue ipAddress) {
        return checkInRanges(ipAddress.value(), IPV4_BROADCAST_RANGES);
    }

    /**
     * Tests if an address is publicly routable.
     *
     * @param ipAddress
     * IP address to test
     *
     * @return Value.TRUE if publicly routable, Value.FALSE otherwise, or ErrorValue
     * on invalid input
     */
    @Function(docs = """
            ```cidr.isPublicRoutable(STRING ipAddress)```

            Tests if an address is publicly routable. Returns true only if the address
            is not private, loopback, link-local, multicast, documentation, CGNAT,
            benchmark, reserved, or broadcast.

            Parameters:
            - ipAddress: IP address to test

            Returns: Boolean indicating publicly routable address
            """)
    public static Value isPublicRoutable(TextValue ipAddress) {
        if (parseAddress(ipAddress.value()) == null) {
            return Value.error(ERROR_INVALID_IP_ADDRESS, ipAddress.value());
        }

        val checks = List.of(isPrivateIpv4(ipAddress), isLoopback(ipAddress), isLinkLocal(ipAddress),
                isMulticast(ipAddress), isDocumentation(ipAddress), isCgnat(ipAddress), isBenchmark(ipAddress),
                isReserved(ipAddress), isBroadcast(ipAddress));

        val isSpecial = checks.stream().anyMatch(Value.TRUE::equals);
        return Value.of(!isSpecial);
    }

    /**
     * Anonymizes an IP by zeroing host bits beyond the prefix length.
     *
     * @param ipAddress
     * IP address to anonymize
     * @param prefixLength
     * network bits to preserve
     *
     * @return TextValue with anonymized IP address, or ErrorValue on invalid input
     */
    @Function(docs = """
            ```cidr.anonymizeIp(STRING ipAddress, INT prefixLength)```

            Anonymizes an IP by zeroing host bits beyond the prefix length. All addresses
            in the same subnet produce identical results. Enables GDPR-compliant logging
            while maintaining geographic or organizational context.

            The prefix determines granularity. For IPv4, /24 preserves organization-level
            context (254 hosts), while /16 preserves city-level (65534 hosts). For IPv6,
            /48 represents a site and /64 represents a subnet.

            Parameters:
            - ipAddress: IP address to anonymize
            - prefixLength: Network bits to preserve

            Returns: Anonymized IP address string

            Example - log client access with privacy protection:

            ```sapl
            policy "privacy_log"
            permit
            obligation
                {
                    "type": "log",
                    "subnet": cidr.anonymizeIp(subject.ipAddress, 24)
                }
            ```
            """)
    public static Value anonymizeIp(TextValue ipAddress, NumberValue prefixLength) {
        val address = parseAddress(ipAddress.value());
        if (address == null) {
            return Value.error(ERROR_INVALID_IP_ADDRESS, ipAddress.value());
        }

        val prefix    = prefixLength.value().intValue();
        val maxPrefix = address.getBitCount();

        if (prefix < 0 || prefix > maxPrefix) {
            return Value.error(buildPrefixRangeError(prefix, address.isIPv4()));
        }

        val masked = address.toZeroHost(prefix);
        return Value.of(masked.withoutPrefixLength().toCanonicalString());
    }

    /**
     * Hashes an IP prefix with a salt for privacy-preserving analytics.
     *
     * @param ipAddress
     * IP address to hash
     * @param prefixLength
     * network bits to include
     * @param salt
     * secret salt string
     *
     * @return TextValue with hexadecimal SHA-256 hash, or ErrorValue on failure
     */
    @Function(docs = """
            ```cidr.hashIpPrefix(STRING ipAddress, INT prefixLength, STRING salt)```

            Hashes an IP prefix with a salt for privacy-preserving analytics and rate limiting.
            First anonymizes to subnet level, then applies SHA-256 to produce a pseudonymous
            identifier. Same subnet and salt always produce the same hash.

            The salt must be secret and unique per application. Without it, attackers can
            pre-compute hashes of all possible subnets. Store salts like cryptographic keys
            in environment variables or key vaults. Different salts for different purposes
            prevent correlation across systems.

            Parameters:
            - ipAddress: IP address to hash
            - prefixLength: Network bits to include
            - salt: Secret salt string

            Returns: Hexadecimal SHA-256 hash (64 characters)

            Example - rate limit by subnet without storing IPs:

            ```sapl
            policy "rate_limit"
            deny
                var hash = cidr.hashIpPrefix(subject.ipAddress, 24, environment.salt);
                var count = cache.get(hash);
                count > 100;
            advice
                {
                    "type": "increment",
                    "key": hash
                }
            ```
            """)
    public static Value hashIpPrefix(TextValue ipAddress, NumberValue prefixLength, TextValue salt) {
        val anonymized = anonymizeIp(ipAddress, prefixLength);
        if (anonymized instanceof ErrorValue) {
            return anonymized;
        }

        try {
            val toHash = ((TextValue) anonymized).value() + salt.value();
            val digest = MessageDigest.getInstance(ALGORITHM_SHA256);
            val hash   = digest.digest(toHash.getBytes(StandardCharsets.UTF_8));
            return Value.of(bytesToHex(hash));
        } catch (NoSuchAlgorithmException exception) {
            return Value.error(ALGORITHM_SHA256 + ERROR_ALGORITHM_NOT_AVAILABLE + exception.getMessage() + ".");
        }
    }

    /**
     * Returns the first address in a CIDR range (network address).
     *
     * @param cidr
     * CIDR range
     *
     * @return TextValue with network address, or ErrorValue on invalid input
     */
    @Function(docs = """
            ```cidr.getNetworkAddress(STRING cidr)```

            Returns the first address in a CIDR range (network address).

            Parameters:
            - cidr: CIDR range

            Returns: Network address string
            """)
    public static Value getNetworkAddress(TextValue cidr) {
        return extractPrefixBlockAddress(cidr.value(), IPAddress::getLower);
    }

    /**
     * Returns the last address in a CIDR range (broadcast address for IPv4).
     *
     * @param cidr
     * CIDR range
     *
     * @return TextValue with broadcast address, or ErrorValue on invalid input
     */
    @Function(docs = """
            ```cidr.getBroadcastAddress(STRING cidr)```

            Returns the last address in a CIDR range (broadcast address for IPv4).

            Parameters:
            - cidr: CIDR range

            Returns: Broadcast address string
            """)
    public static Value getBroadcastAddress(TextValue cidr) {
        return extractPrefixBlockAddress(cidr.value(), IPAddress::getUpper);
    }

    /**
     * Returns total addresses in a CIDR range as a string.
     *
     * @param cidr
     * CIDR range
     *
     * @return TextValue with address count, or ErrorValue on invalid input
     */
    @Function(docs = """
            ```cidr.getAddressCount(STRING cidr)```

            Returns total addresses in a CIDR range as a string to handle large IPv6 ranges.

            Parameters:
            - cidr: CIDR range

            Returns: Address count as string
            """)
    public static Value getAddressCount(TextValue cidr) {
        val address = parseAddress(cidr.value());
        if (address == null) {
            return Value.error(ERROR_INVALID_CIDR, cidr.value());
        }

        val prefixBlock = address.toPrefixBlock();
        return Value.of(prefixBlock.getCount().toString());
    }

    /**
     * Returns usable host addresses in a CIDR range.
     *
     * @param cidr
     * CIDR range
     *
     * @return TextValue with usable host count, or ErrorValue on invalid input
     */
    @Function(docs = """
            ```cidr.getUsableHostCount(STRING cidr)```

            Returns usable host addresses in a CIDR range. For IPv4, excludes network and
            broadcast addresses. For /31 and /32, returns 0. Returns string to handle
            large IPv6 ranges.

            Parameters:
            - cidr: CIDR range

            Returns: Usable host count as string
            """)
    public static Value getUsableHostCount(TextValue cidr) {
        val address = parseAddress(cidr.value());
        if (address == null) {
            return Value.error(ERROR_INVALID_CIDR, cidr.value());
        }

        val prefixBlock = address.toPrefixBlock();
        var count       = prefixBlock.getCount();

        if (address.isIPv4()) {
            val prefixLengthValue = prefixBlock.getNetworkPrefixLength();
            if (prefixLengthValue != null && prefixLengthValue >= 31) {
                return Value.of("0");
            }
            count = count.subtract(BigInteger.TWO);
        } else {
            count = count.subtract(BigInteger.ONE);
        }

        return Value.of(count.toString());
    }

    /**
     * Returns the first usable host address.
     *
     * @param cidr
     * CIDR range
     *
     * @return TextValue with first usable address, or ErrorValue on invalid input
     */
    @Function(docs = """
            ```cidr.getFirstUsableAddress(STRING cidr)```

            Returns the first usable host address (network address + 1).

            Parameters:
            - cidr: CIDR range

            Returns: First usable address string
            """)
    public static Value getFirstUsableAddress(TextValue cidr) {
        val address = parseAddress(cidr.value());
        if (address == null) {
            return Value.error(ERROR_INVALID_CIDR, cidr.value());
        }

        val prefixBlock = address.toPrefixBlock();
        val firstUsable = prefixBlock.getLower().increment(1);
        return Value.of(firstUsable.withoutPrefixLength().toCanonicalString());
    }

    /**
     * Returns the last usable host address.
     *
     * @param cidr
     * CIDR range
     *
     * @return TextValue with last usable address, or ErrorValue on invalid input
     */
    @Function(docs = """
            ```cidr.getLastUsableAddress(STRING cidr)```

            Returns the last usable host address. For IPv4, returns broadcast address - 1.
            For IPv6, returns the last address in the range.

            Parameters:
            - cidr: CIDR range

            Returns: Last usable address string
            """)
    public static Value getLastUsableAddress(TextValue cidr) {
        val address = parseAddress(cidr.value());
        if (address == null) {
            return Value.error(ERROR_INVALID_CIDR, cidr.value());
        }

        val prefixBlock = address.toPrefixBlock();

        if (address.isIPv4()) {
            val lastUsable = prefixBlock.getUpper().increment(-1);
            return Value.of(lastUsable.withoutPrefixLength().toCanonicalString());
        }

        return Value.of(prefixBlock.getUpper().withoutPrefixLength().toCanonicalString());
    }

    /**
     * Tests whether two IP addresses belong to the same subnet.
     *
     * @param ip1
     * first IP address
     * @param ip2
     * second IP address
     * @param prefixLength
     * subnet mask length
     *
     * @return Value.TRUE if same subnet, Value.FALSE otherwise, or ErrorValue on
     * invalid input
     */
    @Function(docs = """
            ```cidr.sameSubnet(STRING ip1, STRING ip2, INT prefixLength)```

            Tests whether two IP addresses belong to the same subnet. Mixing address
            families returns false.

            Parameters:
            - ip1: First IP address
            - ip2: Second IP address
            - prefixLength: Subnet mask length

            Returns: Boolean indicating same subnet
            """)
    public static Value sameSubnet(TextValue ip1, TextValue ip2, NumberValue prefixLength) {
        val address1 = parseAddress(ip1.value());
        if (address1 == null) {
            return Value.error(ERROR_INVALID_FIRST_IP, ip1.value());
        }

        val address2 = parseAddress(ip2.value());
        if (address2 == null) {
            return Value.error(ERROR_INVALID_SECOND_IP, ip2.value());
        }

        if (address1.isIPv4() != address2.isIPv4()) {
            return Value.of(false);
        }

        val prefix    = prefixLength.value().intValue();
        val maxPrefix = address1.getBitCount();

        if (prefix < 0 || prefix > maxPrefix) {
            return Value.error(buildPrefixRangeError(prefix, address1.isIPv4()));
        }

        val network1 = address1.toZeroHost(prefix);
        val network2 = address2.toZeroHost(prefix);

        return Value.of(network1.equals(network2));
    }

    /**
     * Calculates how many leading bits are identical between two IP addresses.
     *
     * @param ip1
     * first IP address
     * @param ip2
     * second IP address
     *
     * @return NumberValue with common prefix bits, or ErrorValue on invalid input
     */
    @Function(docs = """
            ```cidr.getCommonPrefixLength(STRING ip1, STRING ip2)```

            Calculates how many leading bits are identical between two IP addresses.
            Returns 0 for different address families.

            Parameters:
            - ip1: First IP address
            - ip2: Second IP address

            Returns: Number of common prefix bits
            """)
    public static Value getCommonPrefixLength(TextValue ip1, TextValue ip2) {
        val address1 = parseAddress(ip1.value());
        if (address1 == null) {
            return Value.error(ERROR_INVALID_FIRST_IP, ip1.value());
        }

        val address2 = parseAddress(ip2.value());
        if (address2 == null) {
            return Value.error(ERROR_INVALID_SECOND_IP, ip2.value());
        }

        if (address1.isIPv4() != address2.isIPv4()) {
            return Value.of(0);
        }

        val bytes1 = address1.getBytes();
        val bytes2 = address2.getBytes();

        var commonBits = 0;
        for (int i = 0; i < bytes1.length; i++) {
            val xor = (bytes1[i] ^ bytes2[i]) & 0xFF;
            if (xor == 0) {
                commonBits += 8;
            } else {
                var mask = 0x80;
                for (int bit = 0; bit < 8; bit++) {
                    if ((xor & mask) == 0) {
                        commonBits++;
                        mask >>= 1;
                    } else {
                        return Value.of(commonBits);
                    }
                }
                break;
            }
        }

        return Value.of(commonBits);
    }

    /**
     * Tests whether a CIDR can be evenly subdivided into smaller subnets.
     *
     * @param cidr
     * CIDR range to subdivide
     * @param targetPrefixLength
     * desired subdivision prefix
     *
     * @return Value.TRUE if can subdivide, Value.FALSE otherwise, or ErrorValue on
     * invalid input
     */
    @Function(docs = """
            ```cidr.canSubdivide(STRING cidr, INT targetPrefixLength)```

            Tests whether a CIDR can be evenly subdivided into smaller subnets. Returns true
            only if the target prefix is larger (more specific) and the CIDR is aligned on
            its network boundary.

            Parameters:
            - cidr: CIDR range to subdivide
            - targetPrefixLength: Desired subdivision prefix

            Returns: Boolean indicating subdivision possibility
            """)
    public static Value canSubdivide(TextValue cidr, NumberValue targetPrefixLength) {
        val address = parseAddress(cidr.value());
        if (address == null) {
            return Value.error(ERROR_INVALID_CIDR, cidr.value());
        }

        val prefixBlock   = address.toPrefixBlock();
        val currentPrefix = prefixBlock.getNetworkPrefixLength();

        if (currentPrefix == null) {
            return Value.error(ERROR_CIDR_MISSING_PREFIX, cidr.value());
        }

        val targetPrefix = targetPrefixLength.value().intValue();
        val maxPrefix    = address.getBitCount();

        if (targetPrefix < 0 || targetPrefix > maxPrefix) {
            return Value.error(buildPrefixRangeError(targetPrefix, address.isIPv4()));
        }

        if (targetPrefix <= currentPrefix) {
            return Value.of(false);
        }

        val isAligned = address.getLower().equals(prefixBlock.getLower());
        return Value.of(isAligned);
    }

    /**
     * Parses an IP address or CIDR string.
     *
     * @param addressString
     * string to parse
     *
     * @return parsed IPAddress or null if invalid
     */
    private static IPAddress parseAddress(String addressString) {
        try {
            return new IPAddressString(addressString).toAddress();
        } catch (AddressStringException exception) {
            return null;
        }
    }

    /**
     * Parses an array of IP address or CIDR strings.
     *
     * @param addresses
     * ArrayValue containing address strings
     *
     * @return list of parsed IPAddress objects
     *
     * @throws IllegalArgumentException
     * if input is invalid
     */
    private static List<IPAddress> parseAddressArray(ArrayValue addresses) {
        val result = new ArrayList<IPAddress>();
        for (val element : addresses) {
            if (!(element instanceof TextValue textValue)) {
                throw new IllegalArgumentException(ERROR_ARRAY_MUST_BE_STRINGS);
            }

            val address = parseAddress(textValue.value());
            if (address == null) {
                throw new IllegalArgumentException(ERROR_INVALID_ADDRESS.formatted(textValue.value()));
            }
            result.add(address);
        }

        return result;
    }

    /**
     * Extracts an address from a prefix block using the provided function.
     *
     * @param cidrString
     * CIDR string
     * @param extractor
     * function to extract address from prefix block
     *
     * @return Value containing address string or error
     */
    private static Value extractPrefixBlockAddress(String cidrString, UnaryOperator<IPAddress> extractor) {
        val address = parseAddress(cidrString);
        if (address == null) {
            return Value.error(ERROR_INVALID_CIDR, cidrString);
        }

        val prefixBlock = address.toPrefixBlock();
        val extracted   = extractor.apply(prefixBlock);
        return Value.of(extracted.withoutPrefixLength().toCanonicalString());
    }

    /**
     * Adds merged addresses to result builder.
     *
     * @param resultBuilder
     * builder to add to
     * @param addresses
     * addresses to merge
     */
    private static void addMergedAddresses(ArrayValue.Builder resultBuilder, List<IPAddress> addresses) {
        if (!addresses.isEmpty()) {
            val merged = mergeAddressList(addresses);
            for (IPAddress address : merged) {
                resultBuilder.add(Value.of(address.toCanonicalString()));
            }
        }
    }

    /**
     * Merges address list into minimal prefix blocks.
     *
     * @param addresses
     * list of addresses (same family)
     *
     * @return list of merged prefix blocks
     */
    private static List<IPAddress> mergeAddressList(List<IPAddress> addresses) {
        if (addresses.isEmpty()) {
            return List.of();
        }

        val prefixBlocks = addresses.stream().map(IPAddress::toPrefixBlock).toList();

        val filtered = removeContainedAddresses(prefixBlocks);
        if (filtered.isEmpty()) {
            return List.of();
        }

        val sorted = new ArrayList<>(filtered);
        sorted.sort(IPAddress::compareTo);

        val result       = new ArrayList<IPAddress>();
        var currentLower = sorted.getFirst().getLower();
        var currentUpper = sorted.getFirst().getUpper();

        for (int i = 1; i < sorted.size(); i++) {
            val nextAddress = sorted.get(i);
            val nextLower   = nextAddress.getLower();
            val nextUpper   = nextAddress.getUpper();

            if (nextLower.compareTo(currentUpper.increment(1)) <= 0) {
                if (nextUpper.compareTo(currentUpper) > 0) {
                    currentUpper = nextUpper;
                }
            } else {
                addSpannedBlocks(result, currentLower, currentUpper);
                currentLower = nextLower;
                currentUpper = nextUpper;
            }
        }

        addSpannedBlocks(result, currentLower, currentUpper);
        return result;
    }

    /**
     * Adds prefix blocks spanning the range to result list.
     *
     * @param result
     * list to add to
     * @param lower
     * lower bound
     * @param upper
     * upper bound
     */
    private static void addSpannedBlocks(List<IPAddress> result, IPAddress lower, IPAddress upper) {
        val range = lower.spanWithRange(upper);
        result.addAll(Arrays.asList(range.spanWithPrefixBlocks()));
    }

    /**
     * Removes addresses contained within other addresses.
     *
     * @param addresses
     * list of addresses
     *
     * @return list with contained addresses removed
     */
    private static List<IPAddress> removeContainedAddresses(List<IPAddress> addresses) {
        val result = new ArrayList<IPAddress>();

        for (IPAddress address : addresses) {
            val isContained = addresses.stream().filter(other -> !address.equals(other))
                    .anyMatch(other -> other.contains(address));

            if (!isContained && result.stream().noneMatch(existing -> existing.equals(address))) {
                result.add(address);
            }
        }

        return result;
    }

    /**
     * Checks if an IP is in specified ranges.
     *
     * @param ipAddressString
     * IP to check
     * @param ranges
     * CIDR ranges to check against
     *
     * @return Value with boolean result or error
     */
    private static Value checkInRanges(String ipAddressString, List<String> ranges) {
        val address = parseAddress(ipAddressString);
        if (address == null) {
            return Value.error(ERROR_INVALID_ADDRESS, ipAddressString);
        }

        if (!address.isIPv4()) {
            return Value.of(false);
        }

        for (String rangeStr : ranges) {
            val range = parseAddress(rangeStr);
            if (range != null && range.toPrefixBlock().contains(address)) {
                return Value.of(true);
            }
        }

        return Value.of(false);
    }

    /**
     * Checks if an IP is in family-specific ranges.
     *
     * @param ipAddressString
     * IP to check
     * @param ipv4Ranges
     * IPv4 ranges
     * @param ipv6Ranges
     * IPv6 ranges
     *
     * @return Value with boolean result or error
     */
    private static Value checkInRangesByFamily(String ipAddressString, List<String> ipv4Ranges,
            List<String> ipv6Ranges) {
        val address = parseAddress(ipAddressString);
        if (address == null) {
            return Value.error(ERROR_INVALID_ADDRESS, ipAddressString);
        }

        val ranges = address.isIPv4() ? ipv4Ranges : ipv6Ranges;

        for (String rangeStr : ranges) {
            val range = parseAddress(rangeStr);
            if (range != null && range.toPrefixBlock().contains(address)) {
                return Value.of(true);
            }
        }

        return Value.of(false);
    }

    /**
     * Builds an error message for prefix out of range.
     *
     * @param prefix
     * the invalid prefix value
     * @param isIpv4
     * true if IPv4, false if IPv6
     *
     * @return error message string
     */
    private static String buildPrefixRangeError(int prefix, boolean isIpv4) {
        return ERROR_PREFIX + prefix + ERROR_PREFIX_OUT_OF_RANGE + (isIpv4 ? ADDRESS_FAMILY_IPV4 : ADDRESS_FAMILY_IPV6)
                + (isIpv4 ? RANGE_IPV4 : RANGE_IPV6);
    }

    /**
     * Converts byte array to hexadecimal string.
     *
     * @param bytes
     * bytes to convert
     *
     * @return hexadecimal string
     */
    private static String bytesToHex(byte[] bytes) {
        val hexString = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hexString.append("%02x".formatted(b));
        }
        return hexString.toString();
    }
}
