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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import inet.ipaddr.AddressStringException;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Array;
import io.sapl.api.validation.Int;
import io.sapl.api.validation.Text;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
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
 * <p/>
 * Uses the IPAddress library for RFC-compliant IPv4 and IPv6 operations.
 * All IPv6 addresses use RFC 5952 canonical form (compressed, lowercase).
 * Address family mixing returns false rather than error for cleaner policy
 * logic.
 */
@Slf4j
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
            permit action == "access_api"
            where
                cidr.contains("10.0.0.0/8", subject.ipAddress);
            ```

            Prevent Server-Side Request Forgery by blocking requests to internal networks.

            ```sapl
            policy "block_ssrf"
            deny action == "fetch_url"
            where
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
            permit action == "create_subnet"
            where
                !cidr.intersects(resource.cidr, "203.0.113.0/24");
            ```
            """;

    private static final int MAX_EXPANSION = 65535;

    private static final String ADDRESS_FAMILY_IPV4 = "IPv4";
    private static final String ADDRESS_FAMILY_IPV6 = "IPv6";
    private static final String ALGORITHM_SHA256    = "SHA-256";

    private static final String ERROR_ADDRESSES_MAXIMUM       = " addresses, maximum is ";
    private static final String ERROR_ALGORITHM_NOT_AVAILABLE = " not available: ";
    private static final String ERROR_CIDR_CONTAINS           = "CIDR contains ";
    private static final String ERROR_CIDR_MISSING_PREFIX     = "CIDR missing prefix length: ";
    private static final String ERROR_INVALID_ADDRESS         = "Invalid address: ";
    private static final String ERROR_INVALID_CIDR            = "Invalid CIDR: ";
    private static final String ERROR_INVALID_FIRST_CIDR      = "Invalid first CIDR: ";
    private static final String ERROR_INVALID_FIRST_IP        = "Invalid first IP: ";
    private static final String ERROR_INVALID_IP_ADDRESS      = "Invalid IP address: ";
    private static final String ERROR_INVALID_SECOND_CIDR     = "Invalid second CIDR: ";
    private static final String ERROR_INVALID_SECOND_IP       = "Invalid second IP: ";
    private static final String ERROR_PREFIX                  = "Prefix ";
    private static final String ERROR_PREFIX_OUT_OF_RANGE     = " out of range for ";

    private static final String RANGE_IPV4 = " (0-32)";
    private static final String RANGE_IPV6 = " (0-128)";

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
            permit action == "call_api"
            where
                cidr.contains("198.51.100.0/24", subject.ipAddress);
            ```
            """)
    public static Val contains(@Text Val cidr, @Text Val cidrOrIp) {
        val containingRange = parseAddress(cidr.getText());
        if (containingRange == null) {
            return Val.error(ERROR_INVALID_CIDR + cidr.getText());
        }

        val testAddress = parseAddress(cidrOrIp.getText());
        if (testAddress == null) {
            return Val.error(ERROR_INVALID_IP_ADDRESS + cidrOrIp.getText());
        }

        return Val.of(containingRange.toPrefixBlock().contains(testAddress));
    }

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
            where
                var trusted = ["10.0.0.0/8", "172.16.0.0/12"];
                var matches = cidr.containsMatches(trusted, subject.recentIps);
                matches != [];
            ```
            """)
    public static Val containsMatches(@Array Val cidrs, @Array Val cidrsOrIps) {
        try {
            val cidrAddresses = parseAddressArray(cidrs);
            val testAddresses = parseAddressArray(cidrsOrIps);
            val result        = Val.JSON.arrayNode();

            for (int i = 0; i < cidrAddresses.size(); i++) {
                val cidrBlock = cidrAddresses.get(i).toPrefixBlock();
                for (int j = 0; j < testAddresses.size(); j++) {
                    if (cidrBlock.contains(testAddresses.get(j))) {
                        val tuple = Val.JSON.arrayNode();
                        tuple.add(i);
                        tuple.add(j);
                        result.add(tuple);
                    }
                }
            }

            return Val.of(result);
        } catch (IllegalArgumentException exception) {
            return Val.error(exception.getMessage());
        }
    }

    @Function(docs = """
            ```cidr.expand(STRING cidr)```

            Enumerates all IP addresses in a CIDR range. Limited to 65535 addresses to
            prevent memory exhaustion. IPv6 addresses use RFC 5952 canonical form.

            Parameters:
            - cidr: CIDR range to expand (e.g., "192.168.0.0/30")

            Returns: Array of IP address strings
            """)
    public static Val expand(@Text Val cidr) {
        val address = parseAddress(cidr.getText());
        if (address == null) {
            return Val.error(ERROR_INVALID_CIDR + cidr.getText());
        }

        val prefixBlock = address.toPrefixBlock();
        val count       = prefixBlock.getCount();

        if (count.compareTo(BigInteger.valueOf(MAX_EXPANSION)) > 0) {
            return Val.error(ERROR_CIDR_CONTAINS + count + ERROR_ADDRESSES_MAXIMUM + MAX_EXPANSION);
        }

        val result = Val.JSON.arrayNode();
        prefixBlock.iterator().forEachRemaining(addr -> result.add(addr.withoutPrefixLength().toCanonicalString()));

        return Val.of(result);
    }

    @Function(docs = """
            ```cidr.intersects(STRING cidr1, STRING cidr2)```

            Tests whether two CIDR ranges share any addresses. Use this to validate network
            allocations don't conflict or to detect overlapping security zones.

            Parameters:
            - cidr1: First CIDR range
            - cidr2: Second CIDR range

            Returns: Boolean indicating overlap
            """)
    public static Val intersects(@Text Val cidr1, @Text Val cidr2) {
        val address1 = parseAddress(cidr1.getText());
        if (address1 == null) {
            return Val.error(ERROR_INVALID_FIRST_CIDR + cidr1.getText());
        }

        val address2 = parseAddress(cidr2.getText());
        if (address2 == null) {
            return Val.error(ERROR_INVALID_SECOND_CIDR + cidr2.getText());
        }

        val block1 = address1.toPrefixBlock();
        val block2 = address2.toPrefixBlock();

        return Val.of(block1.intersect(block2) != null);
    }

    @Function(docs = """
            ```cidr.isValid(STRING cidr)```

            Validates CIDR notation or IP address syntax for both IPv4 and IPv6.

            Parameters:
            - cidr: String to validate

            Returns: Boolean indicating validity
            """)
    public static Val isValid(@Text Val cidr) {
        return Val.of(parseAddress(cidr.getText()) != null);
    }

    @Function(docs = """
            ```cidr.merge(ARRAY addresses)```

            Consolidates IP addresses and subnets into the minimal set of non-overlapping CIDRs.
            Eliminates duplicates, removes contained ranges, and combines adjacent blocks. IPv6
            addresses use RFC 5952 canonical form.

            Parameters:
            - addresses: Array of IP addresses and CIDR strings

            Returns: Array of minimal CIDR strings
            """)
    public static Val merge(@Array Val addresses) {
        try {
            val ipAddresses = parseAddressArray(addresses);
            if (ipAddresses.isEmpty()) {
                return Val.of(Val.JSON.arrayNode());
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

            val result = Val.JSON.arrayNode();
            addMergedAddresses(result, ipv4Addresses);
            addMergedAddresses(result, ipv6Addresses);

            return Val.of(result);
        } catch (IllegalArgumentException exception) {
            return Val.error(exception.getMessage());
        }
    }

    @Function(docs = """
            ```cidr.isPrivateIpv4(STRING ipAddress)```

            Tests if an IPv4 address falls in RFC 1918 private ranges: 10.0.0.0/8,
            172.16.0.0/12, or 192.168.0.0/16. Returns false for IPv6 addresses.

            Parameters:
            - ipAddress: IP address to test

            Returns: Boolean indicating private address
            """)
    public static Val isPrivateIpv4(@Text Val ipAddress) {
        return checkInRanges(ipAddress, RFC1918_PRIVATE_RANGES);
    }

    @Function(docs = """
            ```cidr.isLoopback(STRING ipAddress)```

            Tests for loopback addresses: 127.0.0.0/8 (IPv4) or ::1/128 (IPv6).

            Parameters:
            - ipAddress: IP address to test

            Returns: Boolean indicating loopback address
            """)
    public static Val isLoopback(@Text Val ipAddress) {
        return checkInRangesByFamily(ipAddress, IPV4_LOOPBACK_RANGES, IPV6_LOOPBACK_RANGES);
    }

    @Function(docs = """
            ```cidr.isLinkLocal(STRING ipAddress)```

            Tests for link-local addresses: 169.254.0.0/16 (IPv4) or fe80::/10 (IPv6).
            These addresses are only valid on the local network segment.

            Parameters:
            - ipAddress: IP address to test

            Returns: Boolean indicating link-local address
            """)
    public static Val isLinkLocal(@Text Val ipAddress) {
        return checkInRangesByFamily(ipAddress, IPV4_LINK_LOCAL_RANGES, IPV6_LINK_LOCAL_RANGES);
    }

    @Function(docs = """
            ```cidr.isMulticast(STRING ipAddress)```

            Tests for multicast addresses: 224.0.0.0/4 (IPv4) or ff00::/8 (IPv6).

            Parameters:
            - ipAddress: IP address to test

            Returns: Boolean indicating multicast address
            """)
    public static Val isMulticast(@Text Val ipAddress) {
        return checkInRangesByFamily(ipAddress, IPV4_MULTICAST_RANGES, IPV6_MULTICAST_RANGES);
    }

    @Function(docs = """
            ```cidr.isDocumentation(STRING ipAddress)```

            Tests if an address is in ranges reserved for documentation and examples.
            IPv4: 192.0.2.0/24, 198.51.100.0/24, 203.0.113.0/24
            IPv6: 2001:db8::/32

            Parameters:
            - ipAddress: IP address to test

            Returns: Boolean indicating documentation range
            """)
    public static Val isDocumentation(@Text Val ipAddress) {
        return checkInRangesByFamily(ipAddress, IPV4_DOCUMENTATION_RANGES, IPV6_DOCUMENTATION_RANGES);
    }

    @Function(docs = """
            ```cidr.isCgnat(STRING ipAddress)```

            Tests if an IPv4 address is in the Carrier-Grade NAT range (100.64.0.0/10).
            ISPs use this range for shared address space. Returns false for IPv6.

            Parameters:
            - ipAddress: IP address to test

            Returns: Boolean indicating CGNAT range
            """)
    public static Val isCgnat(@Text Val ipAddress) {
        return checkInRanges(ipAddress, IPV4_CGNAT_RANGES);
    }

    @Function(docs = """
            ```cidr.isBenchmark(STRING ipAddress)```

            Tests if an IPv4 address is in the benchmarking range (198.18.0.0/15).
            Reserved for network testing. Returns false for IPv6.

            Parameters:
            - ipAddress: IP address to test

            Returns: Boolean indicating benchmark range
            """)
    public static Val isBenchmark(@Text Val ipAddress) {
        return checkInRanges(ipAddress, IPV4_BENCHMARK_RANGES);
    }

    @Function(docs = """
            ```cidr.isReserved(STRING ipAddress)```

            Tests if an address is in ranges reserved for future use or special purposes.
            IPv4: 240.0.0.0/4
            IPv6: ::/128, ::ffff:0:0/96, 100::/64, 2001::/23, 2001:db8::/32

            Parameters:
            - ipAddress: IP address to test

            Returns: Boolean indicating reserved range
            """)
    public static Val isReserved(@Text Val ipAddress) {
        val combined = new ArrayList<String>();
        combined.addAll(IPV6_RESERVED_RANGES);
        combined.addAll(IPV6_DOCUMENTATION_RANGES);
        return checkInRangesByFamily(ipAddress, IPV4_RESERVED_RANGES, combined);
    }

    @Function(docs = """
            ```cidr.isBroadcast(STRING ipAddress)```

            Tests if an IPv4 address is the broadcast address (255.255.255.255).
            Returns false for IPv6.

            Parameters:
            - ipAddress: IP address to test

            Returns: Boolean indicating broadcast address
            """)
    public static Val isBroadcast(@Text Val ipAddress) {
        return checkInRanges(ipAddress, IPV4_BROADCAST_RANGES);
    }

    @Function(docs = """
            ```cidr.isPublicRoutable(STRING ipAddress)```

            Tests if an address is publicly routable. Returns true only if the address
            is not private, loopback, link-local, multicast, documentation, CGNAT,
            benchmark, reserved, or broadcast.

            Parameters:
            - ipAddress: IP address to test

            Returns: Boolean indicating publicly routable address
            """)
    public static Val isPublicRoutable(@Text Val ipAddress) {
        if (parseAddress(ipAddress.getText()) == null) {
            return Val.error(ERROR_INVALID_IP_ADDRESS + ipAddress.getText());
        }

        val checks = List.of(isPrivateIpv4(ipAddress), isLoopback(ipAddress), isLinkLocal(ipAddress),
                isMulticast(ipAddress), isDocumentation(ipAddress), isCgnat(ipAddress), isBenchmark(ipAddress),
                isReserved(ipAddress), isBroadcast(ipAddress));

        val isSpecial = checks.stream().anyMatch(v -> v.isBoolean() && v.getBoolean());
        return Val.of(!isSpecial);
    }

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
    public static Val anonymizeIp(@Text Val ipAddress, @Int Val prefixLength) {
        val address = parseAddress(ipAddress.getText());
        if (address == null) {
            return Val.error(ERROR_INVALID_IP_ADDRESS + ipAddress.getText());
        }

        val prefix    = prefixLength.get().asInt();
        val maxPrefix = address.getBitCount();

        if (prefix < 0 || prefix > maxPrefix) {
            return Val.error(buildPrefixRangeError(prefix, address.isIPv4()));
        }

        val masked = address.toZeroHost(prefix);
        return Val.of(masked.withoutPrefixLength().toCanonicalString());
    }

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
            where
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
    public static Val hashIpPrefix(@Text Val ipAddress, @Int Val prefixLength, @Text Val salt) {
        val anonymized = anonymizeIp(ipAddress, prefixLength);
        if (anonymized.isError()) {
            return anonymized;
        }

        try {
            val toHash = anonymized.getText() + salt.getText();
            val digest = MessageDigest.getInstance(ALGORITHM_SHA256);
            val hash   = digest.digest(toHash.getBytes(StandardCharsets.UTF_8));
            return Val.of(bytesToHex(hash));
        } catch (NoSuchAlgorithmException exception) {
            return Val.error(ALGORITHM_SHA256 + ERROR_ALGORITHM_NOT_AVAILABLE + exception.getMessage());
        }
    }

    @Function(docs = """
            ```cidr.getNetworkAddress(STRING cidr)```

            Returns the first address in a CIDR range (network address).

            Parameters:
            - cidr: CIDR range

            Returns: Network address string
            """)
    public static Val getNetworkAddress(@Text Val cidr) {
        return extractPrefixBlockAddress(cidr, IPAddress::getLower);
    }

    @Function(docs = """
            ```cidr.getBroadcastAddress(STRING cidr)```

            Returns the last address in a CIDR range (broadcast address for IPv4).

            Parameters:
            - cidr: CIDR range

            Returns: Broadcast address string
            """)
    public static Val getBroadcastAddress(@Text Val cidr) {
        return extractPrefixBlockAddress(cidr, IPAddress::getUpper);
    }

    @Function(docs = """
            ```cidr.getAddressCount(STRING cidr)```

            Returns total addresses in a CIDR range as a string to handle large IPv6 ranges.

            Parameters:
            - cidr: CIDR range

            Returns: Address count as string
            """)
    public static Val getAddressCount(@Text Val cidr) {
        val address = parseAddress(cidr.getText());
        if (address == null) {
            return Val.error(ERROR_INVALID_CIDR + cidr.getText());
        }

        val prefixBlock = address.toPrefixBlock();
        return Val.of(prefixBlock.getCount().toString());
    }

    @Function(docs = """
            ```cidr.getUsableHostCount(STRING cidr)```

            Returns usable host addresses in a CIDR range. For IPv4, excludes network and
            broadcast addresses. For /31 and /32, returns 0. Returns string to handle
            large IPv6 ranges.

            Parameters:
            - cidr: CIDR range

            Returns: Usable host count as string
            """)
    public static Val getUsableHostCount(@Text Val cidr) {
        val address = parseAddress(cidr.getText());
        if (address == null) {
            return Val.error(ERROR_INVALID_CIDR + cidr.getText());
        }

        val prefixBlock = address.toPrefixBlock();
        var count       = prefixBlock.getCount();

        if (address.isIPv4()) {
            val prefixLengthValue = prefixBlock.getNetworkPrefixLength();
            if (prefixLengthValue != null && prefixLengthValue >= 31) {
                return Val.of("0");
            }
            count = count.subtract(BigInteger.TWO);
        } else {
            count = count.subtract(BigInteger.ONE);
        }

        return Val.of(count.toString());
    }

    @Function(docs = """
            ```cidr.getFirstUsableAddress(STRING cidr)```

            Returns the first usable host address (network address + 1).

            Parameters:
            - cidr: CIDR range

            Returns: First usable address string
            """)
    public static Val getFirstUsableAddress(@Text Val cidr) {
        val address = parseAddress(cidr.getText());
        if (address == null) {
            return Val.error(ERROR_INVALID_CIDR + cidr.getText());
        }

        val prefixBlock = address.toPrefixBlock();
        val firstUsable = prefixBlock.getLower().increment(1);
        return Val.of(firstUsable.withoutPrefixLength().toCanonicalString());
    }

    @Function(docs = """
            ```cidr.getLastUsableAddress(STRING cidr)```

            Returns the last usable host address. For IPv4, returns broadcast address - 1.
            For IPv6, returns the last address in the range.

            Parameters:
            - cidr: CIDR range

            Returns: Last usable address string
            """)
    public static Val getLastUsableAddress(@Text Val cidr) {
        val address = parseAddress(cidr.getText());
        if (address == null) {
            return Val.error(ERROR_INVALID_CIDR + cidr.getText());
        }

        val prefixBlock = address.toPrefixBlock();

        if (address.isIPv4()) {
            val lastUsable = prefixBlock.getUpper().increment(-1);
            return Val.of(lastUsable.withoutPrefixLength().toCanonicalString());
        }

        return Val.of(prefixBlock.getUpper().withoutPrefixLength().toCanonicalString());
    }

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
    public static Val sameSubnet(@Text Val ip1, @Text Val ip2, @Int Val prefixLength) {
        val address1 = parseAddress(ip1.getText());
        if (address1 == null) {
            return Val.error(ERROR_INVALID_FIRST_IP + ip1.getText());
        }

        val address2 = parseAddress(ip2.getText());
        if (address2 == null) {
            return Val.error(ERROR_INVALID_SECOND_IP + ip2.getText());
        }

        if (address1.isIPv4() != address2.isIPv4()) {
            return Val.of(false);
        }

        val prefix    = prefixLength.get().asInt();
        val maxPrefix = address1.getBitCount();

        if (prefix < 0 || prefix > maxPrefix) {
            return Val.error(buildPrefixRangeError(prefix, address1.isIPv4()));
        }

        val network1 = address1.toZeroHost(prefix);
        val network2 = address2.toZeroHost(prefix);

        return Val.of(network1.equals(network2));
    }

    @Function(docs = """
            ```cidr.getCommonPrefixLength(STRING ip1, STRING ip2)```

            Calculates how many leading bits are identical between two IP addresses.
            Returns 0 for different address families.

            Parameters:
            - ip1: First IP address
            - ip2: Second IP address

            Returns: Number of common prefix bits
            """)
    public static Val getCommonPrefixLength(@Text Val ip1, @Text Val ip2) {
        val address1 = parseAddress(ip1.getText());
        if (address1 == null) {
            return Val.error(ERROR_INVALID_FIRST_IP + ip1.getText());
        }

        val address2 = parseAddress(ip2.getText());
        if (address2 == null) {
            return Val.error(ERROR_INVALID_SECOND_IP + ip2.getText());
        }

        if (address1.isIPv4() != address2.isIPv4()) {
            return Val.of(0);
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
                        return Val.of(commonBits);
                    }
                }
                break;
            }
        }

        return Val.of(commonBits);
    }

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
    public static Val canSubdivide(@Text Val cidr, @Int Val targetPrefixLength) {
        val address = parseAddress(cidr.getText());
        if (address == null) {
            return Val.error(ERROR_INVALID_CIDR + cidr.getText());
        }

        val prefixBlock   = address.toPrefixBlock();
        val currentPrefix = prefixBlock.getNetworkPrefixLength();

        if (currentPrefix == null) {
            return Val.error(ERROR_CIDR_MISSING_PREFIX + cidr.getText());
        }

        val targetPrefix = targetPrefixLength.get().asInt();
        val maxPrefix    = address.getBitCount();

        if (targetPrefix < 0 || targetPrefix > maxPrefix) {
            return Val.error(buildPrefixRangeError(targetPrefix, address.isIPv4()));
        }

        if (targetPrefix <= currentPrefix) {
            return Val.of(false);
        }

        val isAligned = address.getLower().equals(prefixBlock.getLower());
        return Val.of(isAligned);
    }

    /**
     * Parses an IP address or CIDR string.
     *
     * @param addressString string to parse
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
     * @param addresses Val containing array of address strings
     * @return list of parsed IPAddress objects
     * @throws IllegalArgumentException if input is invalid
     */
    private static List<IPAddress> parseAddressArray(Val addresses) {
        if (!addresses.isArray()) {
            throw new IllegalArgumentException("Expected array of address strings");
        }

        val result = new ArrayList<IPAddress>();
        for (JsonNode element : addresses.get()) {
            if (!element.isTextual()) {
                throw new IllegalArgumentException("Array must contain only strings");
            }

            val address = parseAddress(element.textValue());
            if (address == null) {
                throw new IllegalArgumentException(ERROR_INVALID_ADDRESS + element.textValue());
            }
            result.add(address);
        }

        return result;
    }

    /**
     * Extracts an address from a prefix block using the provided function.
     *
     * @param cidr CIDR string
     * @param extractor function to extract address from prefix block
     * @return Val containing address string or error
     */
    private static Val extractPrefixBlockAddress(Val cidr, UnaryOperator<IPAddress> extractor) {
        val address = parseAddress(cidr.getText());
        if (address == null) {
            return Val.error(ERROR_INVALID_CIDR + cidr.getText());
        }

        val prefixBlock = address.toPrefixBlock();
        val extracted   = extractor.apply(prefixBlock);
        return Val.of(extracted.withoutPrefixLength().toCanonicalString());
    }

    /**
     * Adds merged addresses to result array.
     *
     * @param result JSON array to add to
     * @param addresses addresses to merge
     */
    private static void addMergedAddresses(ArrayNode result, List<IPAddress> addresses) {
        if (!addresses.isEmpty()) {
            val merged = mergeAddressList(addresses);
            for (IPAddress address : merged) {
                result.add(address.toCanonicalString());
            }
        }
    }

    /**
     * Merges address list into minimal prefix blocks.
     *
     * @param addresses list of addresses (same family)
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
     * @param result list to add to
     * @param lower lower bound
     * @param upper upper bound
     */
    private static void addSpannedBlocks(List<IPAddress> result, IPAddress lower, IPAddress upper) {
        val range = lower.spanWithRange(upper);
        result.addAll(Arrays.asList(range.spanWithPrefixBlocks()));
    }

    /**
     * Removes addresses contained within other addresses.
     *
     * @param addresses list of addresses
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
     * @param ipAddress IP to check
     * @param ranges CIDR ranges to check against
     * @return Val with boolean result or error
     */
    private static Val checkInRanges(Val ipAddress, List<String> ranges) {
        val address = parseAddress(ipAddress.getText());
        if (address == null) {
            return Val.error(ERROR_INVALID_ADDRESS + ipAddress.getText());
        }

        if (!address.isIPv4()) {
            return Val.of(false);
        }

        for (String rangeStr : ranges) {
            val range = parseAddress(rangeStr);
            if (range != null && range.toPrefixBlock().contains(address)) {
                return Val.of(true);
            }
        }

        return Val.of(false);
    }

    /**
     * Checks if an IP is in family-specific ranges.
     *
     * @param ipAddress IP to check
     * @param ipv4Ranges IPv4 ranges
     * @param ipv6Ranges IPv6 ranges
     * @return Val with boolean result or error
     */
    private static Val checkInRangesByFamily(Val ipAddress, List<String> ipv4Ranges, List<String> ipv6Ranges) {
        val address = parseAddress(ipAddress.getText());
        if (address == null) {
            return Val.error(ERROR_INVALID_ADDRESS + ipAddress.getText());
        }

        val ranges = address.isIPv4() ? ipv4Ranges : ipv6Ranges;

        for (String rangeStr : ranges) {
            val range = parseAddress(rangeStr);
            if (range != null && range.toPrefixBlock().contains(address)) {
                return Val.of(true);
            }
        }

        return Val.of(false);
    }

    /**
     * Builds an error message for prefix out of range.
     *
     * @param prefix the invalid prefix value
     * @param isIpv4 true if IPv4, false if IPv6
     * @return error message string
     */
    private static String buildPrefixRangeError(int prefix, boolean isIpv4) {
        return ERROR_PREFIX + prefix + ERROR_PREFIX_OUT_OF_RANGE + (isIpv4 ? ADDRESS_FAMILY_IPV4 : ADDRESS_FAMILY_IPV6)
                + (isIpv4 ? RANGE_IPV4 : RANGE_IPV6);
    }

    /**
     * Converts byte array to hexadecimal string.
     *
     * @param bytes bytes to convert
     * @return hexadecimal string
     */
    private static String bytesToHex(byte[] bytes) {
        val hexString = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }
}
