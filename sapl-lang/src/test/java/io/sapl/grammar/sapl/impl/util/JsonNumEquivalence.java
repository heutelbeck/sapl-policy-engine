/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.sapl.impl.util;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.NodeType;
import com.google.common.base.Equivalence;
import com.google.common.collect.Sets;

// https://stackoverflow.com/questions/23425454/compare-two-json-objects-with-tolerance-for-numbers
public class JsonNumEquivalence extends Equivalence<JsonNode> {

	protected boolean doNumEquivalent(final JsonNode a, final JsonNode b) {
		return a.decimalValue().compareTo(b.decimalValue()) == 0;
	}

	protected int doNumHash(final JsonNode t) {
		// https://stackoverflow.com/a/65658325
		return t == null ? 0 : t.decimalValue().stripTrailingZeros().hashCode();
	}

	@Override
	protected final boolean doEquivalent(final JsonNode a, final JsonNode b) {
		/*
		 * If both are numbers, delegate to the helper method
		 */
		if (a.isNumber() && b.isNumber())
			return doNumEquivalent(a, b);

		final NodeType typeA = NodeType.getNodeType(a);
		final NodeType typeB = NodeType.getNodeType(b);

		/*
		 * If they are of different types, no dice
		 */
		if (typeA != typeB)
			return false;

		/*
		 * For all other primitive types than numbers, trust JsonNode
		 */
		if (!a.isContainerNode())
			return a.equals(b);

		/*
		 * OK, so they are containers (either both arrays or objects due to the test on
		 * types above). They are obviously not equal if they do not have the same
		 * number of elements/members.
		 */
		if (a.size() != b.size())
			return false;

		/*
		 * Delegate to the appropriate method according to their type.
		 */
		return typeA == NodeType.ARRAY ? arrayEquals(a, b) : objectEquals(a, b);
	}

	@Override
	protected final int doHash(final JsonNode t) {
		/*
		 * If this is a numeric node, delegate to the helper method
		 */
		if (t.isNumber())
			return doNumHash(t);

		/*
		 * If this is a primitive type (other than numbers, handled above), delegate to
		 * JsonNode.
		 */
		if (!t.isContainerNode())
			return t.hashCode();

		/*
		 * The following hash calculations works, yes, but they are poor at best. And
		 * probably slow, too.
		 */
		int ret = 0;

		/*
		 * If the container is empty, just return
		 */
		if (t.size() == 0)
			return ret;

		/*
		 * Array
		 */
		if (t.isArray()) {
			for (final JsonNode element : t)
				ret = 31 * ret + doHash(element);
			return ret;
		}

		/*
		 * Not an array? An object.
		 */
		final Iterator<Map.Entry<String, JsonNode>> iterator = t.fields();

		Map.Entry<String, JsonNode> entry;

		while (iterator.hasNext()) {
			entry = iterator.next();
			ret = 31 * ret + (entry.getKey().hashCode() ^ doHash(entry.getValue()));
		}

		return ret;
	}

	private boolean arrayEquals(final JsonNode a, final JsonNode b) {
		/*
		 * We are guaranteed here that arrays are the same size.
		 */
		final int size = a.size();

		for (int i = 0; i < size; i++)
			if (!doEquivalent(a.get(i), b.get(i)))
				return false;

		return true;
	}

	private boolean objectEquals(final JsonNode a, final JsonNode b) {
		/*
		 * Grab the key set from the first node
		 */
		final Set<String> keys = Sets.newHashSet(a.fieldNames());

		/*
		 * Grab the key set from the second node, and see if both sets are the same. If
		 * not, objects are not equal, no need to check for children.
		 */
		final Set<String> set = Sets.newHashSet(b.fieldNames());
		if (!set.equals(keys))
			return false;

		/*
		 * Test each member individually.
		 */
		for (final String key : keys)
			if (!doEquivalent(a.get(key), b.get(key)))
				return false;

		return true;
	}
}