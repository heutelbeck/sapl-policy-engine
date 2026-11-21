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

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import lombok.val;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static java.util.Comparator.comparingInt;

/**
 * Global reachability classification from pinned IANA address allocations.
 */
final class CidrPublicAddresses {

    // IANA IPv4 Special-Purpose Address Space, 2025-10-09.
    // https://www.iana.org/assignments/iana-ipv4-special-registry/
    // Ordinary IPv4 defaults to public. Multicast is outside the unicast space.
    private static final List<ReachabilityRule> IPV4_RULES = compileRules(true, new Allocation("0.0.0.0/0", true),
            new Allocation("224.0.0.0/4", false), new Allocation("0.0.0.0/8", false),
            new Allocation("0.0.0.0/32", false), new Allocation("10.0.0.0/8", false),
            new Allocation("100.64.0.0/10", false), new Allocation("127.0.0.0/8", false),
            new Allocation("169.254.0.0/16", false), new Allocation("172.16.0.0/12", false),
            new Allocation("192.0.0.0/24", false), new Allocation("192.0.0.0/29", false),
            new Allocation("192.0.0.8/32", false), new Allocation("192.0.0.9/32", true),
            new Allocation("192.0.0.10/32", true), new Allocation("192.0.0.170/32", false),
            new Allocation("192.0.0.171/32", false), new Allocation("192.0.2.0/24", false),
            new Allocation("192.31.196.0/24", true), new Allocation("192.52.193.0/24", true),
            new Allocation("192.88.99.0/24", false), // Blank.
            new Allocation("192.88.99.2/32", false), new Allocation("192.168.0.0/16", false),
            new Allocation("192.175.48.0/24", true), new Allocation("198.18.0.0/15", false),
            new Allocation("198.51.100.0/24", false), new Allocation("203.0.113.0/24", false),
            new Allocation("240.0.0.0/4", false), new Allocation("255.255.255.255/32", false));

    // IANA IPv6 Special-Purpose Address Space, 2025-10-09.
    // https://www.iana.org/assignments/iana-ipv6-special-registry/
    // Ordinary IPv6 is public within 2000::/3 per the IANA IPv6 Address Space, 2025-10-23.
    // https://www.iana.org/assignments/ipv6-address-space/
    private static final List<ReachabilityRule> IPV6_RULES = compileRules(false, new Allocation("2000::/3", true),
            new Allocation("::1/128", false), new Allocation("::/128", false), new Allocation("::ffff:0:0/96", false),
            new Allocation("64:ff9b::/96", true), new Allocation("64:ff9b:1::/48", false),
            new Allocation("100::/64", false), new Allocation("100:0:0:1::/64", false),
            new Allocation("2001::/23", false), new Allocation("2001::/32", false), // N/A.
            new Allocation("2001:1::1/128", true), new Allocation("2001:1::2/128", true),
            new Allocation("2001:1::3/128", true), new Allocation("2001:2::/48", false),
            new Allocation("2001:3::/32", true), new Allocation("2001:4:112::/48", true),
            new Allocation("2001:10::/28", false), // Blank.
            new Allocation("2001:20::/28", true), new Allocation("2001:30::/28", true),
            new Allocation("2001:db8::/32", false), new Allocation("2002::/16", false), // N/A.
            new Allocation("2620:4f:8000::/48", true), new Allocation("3fff::/20", false),
            new Allocation("5f00::/16", false), new Allocation("fc00::/7", false), new Allocation("fe80::/10", false));

    private CidrPublicAddresses() {
    }

    static boolean isPublic(IPAddress address) {
        final List<ReachabilityRule> rules;
        if (address.isIPv4()) {
            rules = IPV4_RULES;
        } else {
            rules = IPV6_RULES;
        }
        val lower            = address.getValue();
        val upper            = address.getUpperValue();
        val firstIsReachable = globallyReachableAt(lower, rules);
        if (!firstIsReachable || lower.equals(upper)) {
            return firstIsReachable;
        }

        // The matching allocation can change only where a rule starts or ends.
        for (val rule : rules) {
            if (lower.compareTo(rule.lower()) < 0 && rule.lower().compareTo(upper) <= 0
                    && !globallyReachableAt(rule.lower(), rules)) {
                return false;
            }
            if (lower.compareTo(rule.upper()) <= 0 && rule.upper().compareTo(upper) < 0
                    && !globallyReachableAt(rule.upper().add(BigInteger.ONE), rules)) {
                return false;
            }
        }
        return true;
    }

    private static boolean globallyReachableAt(BigInteger address, List<ReachabilityRule> rules) {
        for (val rule : rules) {
            if (rule.lower().compareTo(address) <= 0 && address.compareTo(rule.upper()) <= 0) {
                return rule.globallyReachable();
            }
        }
        return false;
    }

    private static List<ReachabilityRule> compileRules(boolean ipv4, Allocation... allocations) {
        val rules = new ArrayList<ReachabilityRule>(allocations.length);
        for (val allocation : allocations) {
            val address = new IPAddressString(allocation.prefix()).getAddress();
            // A malformed allocation invalidates the whole table.
            if (address == null || address.isIPv4() != ipv4) {
                return List.of();
            }
            val prefixLength = address.getPrefixLength();
            if (prefixLength == null) {
                return List.of();
            }
            val block = address.toPrefixBlock();
            rules.add(new ReachabilityRule(block.getValue(), block.getUpperValue(), prefixLength,
                    allocation.globallyReachable()));
        }
        rules.sort(comparingInt(ReachabilityRule::prefixLength).reversed());
        return List.copyOf(rules);
    }

    private record Allocation(String prefix, boolean globallyReachable) {}

    private record ReachabilityRule(BigInteger lower, BigInteger upper, int prefixLength, boolean globallyReachable) {}
}
