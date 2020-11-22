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
package io.sapl.interpreter.pip;

import static io.sapl.interpreter.pip.EthereumPipFunctions.createEncodedFunction;
import static io.sapl.interpreter.pip.EthereumPipFunctions.getEthFilterFrom;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Int;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Uint;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes1;
import org.web3j.abi.datatypes.generated.Bytes10;
import org.web3j.abi.datatypes.generated.Bytes11;
import org.web3j.abi.datatypes.generated.Bytes12;
import org.web3j.abi.datatypes.generated.Bytes13;
import org.web3j.abi.datatypes.generated.Bytes14;
import org.web3j.abi.datatypes.generated.Bytes15;
import org.web3j.abi.datatypes.generated.Bytes16;
import org.web3j.abi.datatypes.generated.Bytes17;
import org.web3j.abi.datatypes.generated.Bytes18;
import org.web3j.abi.datatypes.generated.Bytes19;
import org.web3j.abi.datatypes.generated.Bytes2;
import org.web3j.abi.datatypes.generated.Bytes20;
import org.web3j.abi.datatypes.generated.Bytes21;
import org.web3j.abi.datatypes.generated.Bytes22;
import org.web3j.abi.datatypes.generated.Bytes23;
import org.web3j.abi.datatypes.generated.Bytes24;
import org.web3j.abi.datatypes.generated.Bytes25;
import org.web3j.abi.datatypes.generated.Bytes26;
import org.web3j.abi.datatypes.generated.Bytes27;
import org.web3j.abi.datatypes.generated.Bytes28;
import org.web3j.abi.datatypes.generated.Bytes29;
import org.web3j.abi.datatypes.generated.Bytes3;
import org.web3j.abi.datatypes.generated.Bytes30;
import org.web3j.abi.datatypes.generated.Bytes31;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Bytes4;
import org.web3j.abi.datatypes.generated.Bytes5;
import org.web3j.abi.datatypes.generated.Bytes6;
import org.web3j.abi.datatypes.generated.Bytes7;
import org.web3j.abi.datatypes.generated.Bytes8;
import org.web3j.abi.datatypes.generated.Bytes9;
import org.web3j.abi.datatypes.generated.Int104;
import org.web3j.abi.datatypes.generated.Int112;
import org.web3j.abi.datatypes.generated.Int120;
import org.web3j.abi.datatypes.generated.Int128;
import org.web3j.abi.datatypes.generated.Int136;
import org.web3j.abi.datatypes.generated.Int144;
import org.web3j.abi.datatypes.generated.Int152;
import org.web3j.abi.datatypes.generated.Int16;
import org.web3j.abi.datatypes.generated.Int160;
import org.web3j.abi.datatypes.generated.Int168;
import org.web3j.abi.datatypes.generated.Int176;
import org.web3j.abi.datatypes.generated.Int184;
import org.web3j.abi.datatypes.generated.Int192;
import org.web3j.abi.datatypes.generated.Int200;
import org.web3j.abi.datatypes.generated.Int208;
import org.web3j.abi.datatypes.generated.Int216;
import org.web3j.abi.datatypes.generated.Int224;
import org.web3j.abi.datatypes.generated.Int232;
import org.web3j.abi.datatypes.generated.Int24;
import org.web3j.abi.datatypes.generated.Int240;
import org.web3j.abi.datatypes.generated.Int248;
import org.web3j.abi.datatypes.generated.Int256;
import org.web3j.abi.datatypes.generated.Int32;
import org.web3j.abi.datatypes.generated.Int40;
import org.web3j.abi.datatypes.generated.Int48;
import org.web3j.abi.datatypes.generated.Int56;
import org.web3j.abi.datatypes.generated.Int64;
import org.web3j.abi.datatypes.generated.Int72;
import org.web3j.abi.datatypes.generated.Int8;
import org.web3j.abi.datatypes.generated.Int80;
import org.web3j.abi.datatypes.generated.Int88;
import org.web3j.abi.datatypes.generated.Int96;
import org.web3j.abi.datatypes.generated.Uint104;
import org.web3j.abi.datatypes.generated.Uint112;
import org.web3j.abi.datatypes.generated.Uint120;
import org.web3j.abi.datatypes.generated.Uint128;
import org.web3j.abi.datatypes.generated.Uint136;
import org.web3j.abi.datatypes.generated.Uint144;
import org.web3j.abi.datatypes.generated.Uint152;
import org.web3j.abi.datatypes.generated.Uint16;
import org.web3j.abi.datatypes.generated.Uint160;
import org.web3j.abi.datatypes.generated.Uint168;
import org.web3j.abi.datatypes.generated.Uint176;
import org.web3j.abi.datatypes.generated.Uint184;
import org.web3j.abi.datatypes.generated.Uint192;
import org.web3j.abi.datatypes.generated.Uint200;
import org.web3j.abi.datatypes.generated.Uint208;
import org.web3j.abi.datatypes.generated.Uint216;
import org.web3j.abi.datatypes.generated.Uint224;
import org.web3j.abi.datatypes.generated.Uint232;
import org.web3j.abi.datatypes.generated.Uint24;
import org.web3j.abi.datatypes.generated.Uint240;
import org.web3j.abi.datatypes.generated.Uint248;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint32;
import org.web3j.abi.datatypes.generated.Uint40;
import org.web3j.abi.datatypes.generated.Uint48;
import org.web3j.abi.datatypes.generated.Uint56;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.abi.datatypes.generated.Uint72;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.abi.datatypes.generated.Uint80;
import org.web3j.abi.datatypes.generated.Uint88;
import org.web3j.abi.datatypes.generated.Uint96;
import org.web3j.abi.datatypes.primitive.Char;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class EthereumPipFunctionsTest {

	private static final String TEST_TO_BLOCK = "0x1abcdef";

	private static final String TEST_FROM_BLOCK = "0x12356";

	private static final String TO_BLOCK = "toBlock";

	private static final String FROM_BLOCK = "fromBlock";

	private static final int INT_TEST_VALUE = 123;

	private static final int UINT_TEST_VALUE = 222;

	private static final String SOME_STRING = "someString";

	private static final String STRING = "string";

	private static final String BOOL = "bool";

	private static final String ADDRESS = "address";

	private static final String TEST_ADDRESS = "0x3f2cbea2185089ea5bbabbcd7616b215b724885c";

	private static final String TYPE = "type";

	private static final String VALUE = "value";

	private static final byte[] BYTE_ARRAY = hexStringToByteArray(TEST_ADDRESS);

	private static final BigInteger TEST_BIG_INT = BigInteger.valueOf(1364961235);

	private static final String TEST_FUNCTION_NAME = "testFunctionName";

	private static byte[] bytesArray;

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static final JsonNode TEST_OUTPUT_PARAM = JSON.arrayNode().add(BOOL);

	private static final ObjectMapper mapper = new ObjectMapper();

	// convertToType

	@Test
	public void createFunctionShouldUseBoolFalseIfTypeIsNotPresent() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(VALUE, 25);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bool(false));
		assertEquals("ConvertToType didn't return null when type field was not present in input.", encodedTestFunction,
				encodedFunction);

	}

	@Test
	public void createFunctionShouldUseBoolFalseIfValueIsNotPresent() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "aString");
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bool(false));
		assertEquals("ConvertToType didn't return null when field value was not present in input.", encodedTestFunction,
				encodedFunction);

	}

	@Test
	public void createFunctionShouldWorkWithAddressTypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, ADDRESS);
		inputParam.put(VALUE, TEST_ADDRESS);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Address(TEST_ADDRESS));
		assertEquals("ConvertToType didn't return the Address correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBoolTypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, BOOL);
		inputParam.put(VALUE, true);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bool(true));
		assertEquals("ConvertToType didn't return the Bool correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithStringTypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, STRING);
		inputParam.put(VALUE, SOME_STRING);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Utf8String(SOME_STRING));
		assertEquals("ConvertToType didn't return the String correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytesTypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "bytes");
		inputParam.put(VALUE, BYTE_ARRAY);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new DynamicBytes(BYTE_ARRAY));
		assertEquals("ConvertToType didn't return the DynamicBytes correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithByteTypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		byte testByte = 125;
		inputParam.put(TYPE, "byte");
		inputParam.put(VALUE, testByte);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new org.web3j.abi.datatypes.primitive.Byte(testByte));
		assertEquals("ConvertToType didn't return the Byte correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithCharTypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "char");
		inputParam.put(VALUE, "a");
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Char('a'));
		assertEquals("ConvertToType didn't return the Char correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void creatFunctionShouldUseBoolFalseWhenEmptyCharProvided() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "char");
		inputParam.put(VALUE, "");
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bool(false));
		assertEquals("ConvertToType didn't return the Char correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldUseBoolFalseWhenDoubleProvided() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "double");
		inputParam.put(VALUE, Double.valueOf(1.789));
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bool(false));
		assertEquals("ConvertToType didn't return the Double correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldUseBoolFalseWhenFloatProvided()
			throws IOException, NumberFormatException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "float");
		inputParam.put(VALUE, Float.valueOf("7.654321"));
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bool(false));
		assertEquals("ConvertToType didn't return the Float correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUintTypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint");
		inputParam.put(VALUE, TEST_BIG_INT);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint(TEST_BIG_INT));
		assertEquals("ConvertToType didn't return the Uint correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithIntTypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int");
		inputParam.put(VALUE, TEST_BIG_INT);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int(TEST_BIG_INT));
		assertEquals("ConvertToType didn't return the Int correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithLongTypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "long");
		inputParam.put(VALUE, Long.valueOf(9786135));
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(
				new org.web3j.abi.datatypes.primitive.Long(Long.valueOf(9786135)));
		assertEquals("ConvertToType didn't return the Long correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithShortTypeCorrectly()
			throws IOException, NumberFormatException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "short");
		inputParam.put(VALUE, Short.valueOf("111"));
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(
				new org.web3j.abi.datatypes.primitive.Short(Short.valueOf("111")));
		assertEquals("ConvertToType didn't return the Short correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint8TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint8");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint8(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint8 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt8TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int8");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int8(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int8 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint16TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint16");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint16(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint16 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt16TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int16");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int16(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int16 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint24TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint24");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint24(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint24 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt24TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int24");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int24(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int24 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint32TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint32");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint32(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint32 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt32TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int32");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int32(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int32 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint40TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint40");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint40(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint40 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt40TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int40");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int40(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int40 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint48TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint48");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint48(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint48 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt48TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int48");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int48(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int48 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint56TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint56");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint56(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint56 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt56TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int56");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int56(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int56 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint64TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint64");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint64(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint64 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt64TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int64");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int64(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int64 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint72TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint72");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint72(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint72 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt72TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int72");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int72(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int72 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint80TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint80");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint80(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint80 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt80TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int80");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int80(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int80 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint88TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint88");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint88(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint88 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt88TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int88");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int88(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int88 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint96TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint96");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint96(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint96 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt96TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int96");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int96(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int96 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint104TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint104");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint104(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint104 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt104TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int104");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int104(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int104 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint112TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint112");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint112(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint112 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt112TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int112");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int112(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int112 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint120TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint120");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint120(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint120 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt120TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int120");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int120(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int120 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint128TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint128");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint128(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint128 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt128TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int128");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int128(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int128 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint136TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint136");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint136(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint136 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt136TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int136");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int136(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int136 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint144TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint144");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint144(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint144 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt144TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int144");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int144(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int144 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint152TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint152");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint152(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint152 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt152TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int152");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int152(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int152 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint160TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint160");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint160(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint160 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt160TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int160");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int160(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int160 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint168TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint168");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint168(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint168 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt168TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int168");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int168(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int168 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint176TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint176");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint176(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint176 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt176TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int176");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int176(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int176 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint184TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint184");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint184(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint184 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt184TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int184");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int184(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int184 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint192TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint192");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint192(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint192 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt192TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int192");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int192(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int192 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint200TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint200");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint200(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint200 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt200TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int200");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int200(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int200 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint208TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint208");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint208(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint208 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt208TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int208");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int208(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int208 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint216TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint216");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint216(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint216 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt216TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int216");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int216(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int216 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint224TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint224");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint224(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint224 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt224TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int224");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int224(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int224 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint232TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint232");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint232(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint232 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt232TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int232");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int232(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int232 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint240TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint240");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint240(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint240 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt240TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int240");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int240(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int240 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint248TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint248");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint248(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint248 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt248TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int248");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int248(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int248 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithUint256TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "uint256");
		inputParam.put(VALUE, UINT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Uint256(UINT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Uint256 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithInt256TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "int256");
		inputParam.put(VALUE, INT_TEST_VALUE);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Int256(INT_TEST_VALUE));
		assertEquals("ConvertToType didn't return the Int256 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes1TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[1];
		bytesArray[0] = 25;
		inputParam.put(TYPE, "bytes1");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes1(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes1 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes2TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[2];
		bytesArray[1] = 33;
		inputParam.put(TYPE, "bytes2");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes2(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes2 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes3TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[3];
		inputParam.put(TYPE, "bytes3");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes3(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes3 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes4TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[4];
		inputParam.put(TYPE, "bytes4");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes4(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes4 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes5TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[5];
		inputParam.put(TYPE, "bytes5");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes5(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes5 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes6TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[6];
		inputParam.put(TYPE, "bytes6");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes6(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes6 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes7TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[7];
		inputParam.put(TYPE, "bytes7");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes7(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes7 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes8TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[8];
		inputParam.put(TYPE, "bytes8");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes8(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes8 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes9TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[9];
		inputParam.put(TYPE, "bytes9");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes9(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes9 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes10TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[10];
		inputParam.put(TYPE, "bytes10");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes10(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes10 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithByte11TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[11];
		inputParam.put(TYPE, "bytes11");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes11(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes11 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes12TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[12];
		inputParam.put(TYPE, "bytes12");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes12(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes12 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes13TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[13];
		inputParam.put(TYPE, "bytes13");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes13(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes13 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes14TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[14];
		inputParam.put(TYPE, "bytes14");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes14(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes14 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes15TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[15];
		inputParam.put(TYPE, "bytes15");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes15(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes15 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes16TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[16];
		inputParam.put(TYPE, "bytes16");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes16(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes16 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes17TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[17];
		inputParam.put(TYPE, "bytes17");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes17(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes17 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes18TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[18];
		inputParam.put(TYPE, "bytes18");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes18(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes18 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes19TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[19];
		inputParam.put(TYPE, "bytes19");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes19(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes19 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes20TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[20];
		inputParam.put(TYPE, "bytes20");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes20(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes20 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes21TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[21];
		inputParam.put(TYPE, "bytes21");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes21(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes21 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithByte22TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[22];
		inputParam.put(TYPE, "bytes22");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes22(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes22 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes23TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[23];
		inputParam.put(TYPE, "bytes23");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes23(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes23 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes24TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[24];
		inputParam.put(TYPE, "bytes24");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes24(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes24 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes25TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[25];
		inputParam.put(TYPE, "bytes25");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes25(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes25 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes26TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[26];
		inputParam.put(TYPE, "bytes26");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes26(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes26 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes27TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[27];
		inputParam.put(TYPE, "bytes27");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes27(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes27 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes28TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[28];
		inputParam.put(TYPE, "bytes28");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes28(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes28 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes29TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[29];
		inputParam.put(TYPE, "bytes29");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes29(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes29 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes30TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[30];
		inputParam.put(TYPE, "bytes30");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes30(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes30 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes31TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[31];
		inputParam.put(TYPE, "bytes31");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes31(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes31 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionShouldWorkWithBytes32TypeCorrectly() throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		bytesArray = new byte[32];
		inputParam.put(TYPE, "bytes32");
		inputParam.put(VALUE, bytesArray);
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bytes32(bytesArray));
		assertEquals("ConvertToType didn't return the Bytes32 correctly.", encodedTestFunction, encodedFunction);
	}

	@Test
	public void createFunctionWithFalseSolidityTypeShouldWorkAsWithBoolFalse()
			throws IOException, ClassNotFoundException {
		ObjectNode inputParam = JSON.objectNode();
		inputParam.put(TYPE, "wrongType");
		inputParam.put(VALUE, "anyValue");
		String encodedFunction = createFunctionFromApi(inputParam);
		String encodedTestFunction = createEncodedTestFunction(new Bool(false));
		assertEquals("ConvertToType didn't return null when non-existing solidity type was provided.",
				encodedTestFunction, encodedFunction);
	}

	// getEthFilterFrom
	@Test
	public void getEthFilterFromShouldReturnCorrectFilter() {
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(FROM_BLOCK, TEST_FROM_BLOCK);
		saplObject.put(TO_BLOCK, TEST_TO_BLOCK);
		saplObject.set(ADDRESS, JSON.arrayNode().add(TEST_ADDRESS));
		EthFilter filter = getEthFilterFrom(saplObject);
		EthFilter testFilter = getTestEthFilter(TEST_FROM_BLOCK, TEST_TO_BLOCK, Arrays.asList(TEST_ADDRESS));
		assertTrue("The getEthFilterFrom method didn't return the correct filter.",
				filtersAreEqual(testFilter, filter));
	}

	@Test
	public void getEthFilterFromCanBeUsedWithMappedFilter() {
		EthFilter testFilter = getTestEthFilter(TEST_FROM_BLOCK, TEST_TO_BLOCK, Arrays.asList(TEST_ADDRESS));
		JsonNode saplObject = mapper.convertValue(testFilter, JsonNode.class);
		EthFilter filter = getEthFilterFrom(saplObject);
		assertTrue("The getEthFilterFrom method didn't return the correct filter.",
				filtersAreEqual(testFilter, filter));
	}

	@Test
	public void getEthFilterFromShouldReturnEmptyFilterWithNoFromBlock() {
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(TO_BLOCK, TEST_TO_BLOCK);
		saplObject.set(ADDRESS, JSON.arrayNode().add(TEST_ADDRESS));
		EthFilter filter = getEthFilterFrom(saplObject);
		EthFilter testFilter = new EthFilter();
		assertTrue("The getEthFilterFrom method didn't return the correct filter.",
				filtersAreEqual(testFilter, filter));
	}

	@Test
	public void getEthFilterFromShouldReturnEmptyFilterWithNoToBlock() {
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(FROM_BLOCK, TEST_FROM_BLOCK);
		saplObject.set(ADDRESS, JSON.arrayNode().add(TEST_ADDRESS));
		EthFilter filter = getEthFilterFrom(saplObject);
		EthFilter testFilter = new EthFilter();
		assertTrue("The getEthFilterFrom method didn't return the correct filter.",
				filtersAreEqual(testFilter, filter));
	}

	@Test
	public void getEthFilterFromShouldReturnEmptyFilterWithNoAddress() {
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(FROM_BLOCK, TEST_FROM_BLOCK);
		saplObject.put(TO_BLOCK, TEST_TO_BLOCK);
		EthFilter filter = getEthFilterFrom(saplObject);
		EthFilter testFilter = new EthFilter();
		assertTrue("The getEthFilterFrom method didn't return the correct filter.",
				filtersAreEqual(testFilter, filter));
	}

	private static byte[] hexStringToByteArray(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
		}
		return data;
	}

	private static String createFunctionFromApi(JsonNode inputParam) throws ClassNotFoundException {
		ArrayNode inputParams = JSON.arrayNode().add(inputParam);
		Function function = EthereumPipFunctions.createFunction(TEST_FUNCTION_NAME, inputParams, TEST_OUTPUT_PARAM);
		return createEncodedFunction(function);
	}

	private static String createEncodedTestFunction(Type<?> inputParam) throws ClassNotFoundException {
		Function testFunction = new Function(TEST_FUNCTION_NAME, Arrays.asList(inputParam),
				Arrays.asList(TypeReference.makeTypeReference(BOOL)));
		return createEncodedFunction(testFunction);
	}

	private static BigInteger bigIntFromHex(String s) {
		return new BigInteger(s.substring(2), 16);
	}

	private static boolean filtersAreEqual(EthFilter filter1, EthFilter filter2) {
		return compareDbp(filter1.getFromBlock(), filter2.getFromBlock())
				&& compareDbp(filter1.getToBlock(), filter2.getToBlock())
				&& compareAddress(filter1.getAddress(), filter2.getAddress());
	}

	private static boolean compareAddress(List<String> address1, List<String> address2) {
		if (address1 == null | address2 == null) {
			if (address1 == null && address2 == null)
				return true;
			return false;
		}
		return address1.equals(address2);
	}

	private static boolean compareDbp(DefaultBlockParameter dbp1, DefaultBlockParameter dbp2) {
		if (dbp1 == null | dbp2 == null) {
			if (dbp1 == null && dbp2 == null)
				return true;
			return false;
		}
		return dbp1.getValue().equals(dbp2.getValue());
	}

	private static EthFilter getTestEthFilter(String from, String to, List<String> address) {
		return new EthFilter(DefaultBlockParameter.valueOf(bigIntFromHex(from)),
				DefaultBlockParameter.valueOf(bigIntFromHex(to)), address);
	}

}
