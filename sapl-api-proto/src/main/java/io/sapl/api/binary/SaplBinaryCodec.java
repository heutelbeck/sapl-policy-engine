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
package io.sapl.api.binary;

import static io.sapl.api.binary.SaplBinaryFormatException.Reason.BLANK_SUBSCRIPTION_ID;
import static io.sapl.api.binary.SaplBinaryFormatException.Reason.DUPLICATE_SUBSCRIPTION_ID;
import static io.sapl.api.binary.SaplBinaryFormatException.Reason.TOO_MANY_SUBSCRIPTIONS;

import java.util.HashSet;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * The SAPL binary format, version 1: a compact encoding of the wire objects of
 * the RSocket API, selected by the data MIME type {@link #MIME_TYPE} of the
 * connection. The format is specified in {@code BINARY_FORMAT.md} of the Rust
 * implementation {@code sapl-rust}, whose test vectors this codec passes byte
 * for byte.
 * <p>
 * A payload starts with the lead byte {@code 0x00}, the version of the format,
 * then all its texts in one region, which is checked to be UTF-8 once, then the
 * structure: one tag byte per value, with short texts, arrays, objects and
 * small integers in the tag itself, and numbers in binary. No protobuf message
 * starts with the lead byte, so a node that reads protobuf whatever the data
 * MIME type refuses such a payload instead of misreading it.
 * <p>
 * A reader refuses every payload that is cut off, holds anything after its end,
 * names more elements than its bytes can hold, or nests arrays and objects
 * deeper than 500.
 */
@UtilityClass
public class SaplBinaryCodec {

    /** The data MIME type of an RSocket connection whose payloads are in this format. */
    public static final String MIME_TYPE = "application/vnd.sapl.binary";

    /** The first byte of every payload: the version of the format. */
    static final int LEAD_BYTE = 0x00;

    /** The deepest nesting of arrays and objects in a value. */
    static final int MAX_DEPTH = 500;

    /**
     * Reads a subscription.
     *
     * @param payload the payload
     * @return the subscription
     * @throws SaplBinaryFormatException if the payload is not a subscription
     */
    public static AuthorizationSubscription readAuthorizationSubscription(byte[] payload)
            throws SaplBinaryFormatException {
        val reader       = new BinaryReader(payload);
        val subscription = reader.subscription();
        reader.end();
        return subscription;
    }

    /**
     * Reads a multi-subscription with at most {@code maxSubscriptions}
     * subscriptions. Too many subscriptions are refused before any is read.
     *
     * @param payload the payload
     * @param maxSubscriptions the most subscriptions allowed
     * @return the multi-subscription
     * @throws SaplBinaryFormatException if the payload is not a
     * multi-subscription, holds too many subscriptions, or an id is blank or used
     * twice
     */
    public static MultiAuthorizationSubscription readMultiAuthorizationSubscription(byte[] payload,
            int maxSubscriptions) throws SaplBinaryFormatException {
        val reader = new BinaryReader(payload);
        val count  = reader.length();
        if (count > maxSubscriptions) {
            throw new SaplBinaryFormatException(TOO_MANY_SUBSCRIPTIONS);
        }
        val subscriptions = new MultiAuthorizationSubscription();
        for (var i = 0; i < count; i++) {
            val id           = reader.text();
            val subscription = reader.subscription();
            if (id.isBlank()) {
                throw new SaplBinaryFormatException(BLANK_SUBSCRIPTION_ID);
            }
            if (subscriptions.getSubscription(id) != null) {
                throw new SaplBinaryFormatException(DUPLICATE_SUBSCRIPTION_ID);
            }
            subscriptions.addSubscription(id, subscription);
        }
        reader.end();
        return subscriptions;
    }

    /**
     * Reads a decision. A verdict the format does not know is indeterminate, so
     * that a newer node cannot make an older client grant access.
     *
     * @param payload the payload
     * @return the decision
     * @throws SaplBinaryFormatException if the payload is not a decision
     */
    public static AuthorizationDecision readAuthorizationDecision(byte[] payload) throws SaplBinaryFormatException {
        val reader   = new BinaryReader(payload);
        val decision = reader.decision();
        reader.end();
        return decision;
    }

    /**
     * Reads a decision under the id of its subscription.
     *
     * @param payload the payload
     * @return the decision under its id
     * @throws SaplBinaryFormatException if the payload is not a decision under an
     * id
     */
    public static IdentifiableAuthorizationDecision readIdentifiableAuthorizationDecision(byte[] payload)
            throws SaplBinaryFormatException {
        val reader   = new BinaryReader(payload);
        val id       = reader.text();
        val decision = reader.decision();
        reader.end();
        return new IdentifiableAuthorizationDecision(id, decision);
    }

    /**
     * Reads the decisions of a multi-subscription.
     *
     * @param payload the payload
     * @return the decisions by subscription id
     * @throws SaplBinaryFormatException if the payload is not a multi-decision or
     * an id is used twice
     */
    public static MultiAuthorizationDecision readMultiAuthorizationDecision(byte[] payload)
            throws SaplBinaryFormatException {
        val reader    = new BinaryReader(payload);
        val count     = reader.length();
        val decisions = new MultiAuthorizationDecision();
        val ids       = new HashSet<String>();
        for (var i = 0; i < count; i++) {
            val id       = reader.text();
            val decision = reader.decision();
            if (!ids.add(id)) {
                throw new SaplBinaryFormatException(DUPLICATE_SUBSCRIPTION_ID);
            }
            decisions.setDecision(id, decision);
        }
        reader.end();
        return decisions;
    }

    /**
     * Writes a subscription: the four parts, an undefined environment included,
     * and the secrets.
     *
     * @param subscription the subscription
     * @return the payload
     * @throws SaplBinaryFormatException if a part is nested too deeply or holds a
     * text that UTF-8 cannot carry
     */
    public static byte[] writeAuthorizationSubscription(AuthorizationSubscription subscription)
            throws SaplBinaryFormatException {
        val writer = new BinaryWriter();
        writer.subscription(subscription);
        return writer.toBytes();
    }

    /**
     * Writes a multi-subscription.
     *
     * @param subscriptions the multi-subscription
     * @return the payload
     * @throws SaplBinaryFormatException if a part of a subscription is nested too
     * deeply or holds a text that UTF-8 cannot carry
     */
    public static byte[] writeMultiAuthorizationSubscription(MultiAuthorizationSubscription subscriptions)
            throws SaplBinaryFormatException {
        val writer = new BinaryWriter();
        writer.count(subscriptions.size());
        for (val subscription : subscriptions) {
            writer.text(subscription.subscriptionId());
            writer.subscription(subscription.subscription());
        }
        return writer.toBytes();
    }

    /**
     * Writes a decision.
     *
     * @param decision the decision
     * @return the payload
     * @throws SaplBinaryFormatException if a value is nested too deeply or holds a
     * text that UTF-8 cannot carry
     */
    public static byte[] writeAuthorizationDecision(AuthorizationDecision decision) throws SaplBinaryFormatException {
        val writer = new BinaryWriter();
        writer.decision(decision);
        return writer.toBytes();
    }

    /**
     * Writes a decision under the id of its subscription.
     *
     * @param decision the decision under its id
     * @return the payload
     * @throws SaplBinaryFormatException if a value is nested too deeply or holds a
     * text that UTF-8 cannot carry
     */
    public static byte[] writeIdentifiableAuthorizationDecision(IdentifiableAuthorizationDecision decision)
            throws SaplBinaryFormatException {
        val writer = new BinaryWriter();
        writer.text(decision.subscriptionId());
        writer.decision(decision.decision());
        return writer.toBytes();
    }

    /**
     * Writes the decisions of a multi-subscription.
     *
     * @param decisions the decisions by subscription id
     * @return the payload
     * @throws SaplBinaryFormatException if a value is nested too deeply or holds a
     * text that UTF-8 cannot carry
     */
    public static byte[] writeMultiAuthorizationDecision(MultiAuthorizationDecision decisions)
            throws SaplBinaryFormatException {
        val writer = new BinaryWriter();
        writer.count(decisions.size());
        for (val decision : decisions) {
            writer.text(decision.subscriptionId());
            writer.decision(decision.decision());
        }
        return writer.toBytes();
    }
}
