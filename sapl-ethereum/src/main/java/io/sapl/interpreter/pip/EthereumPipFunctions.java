/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.interpreter.pip.EthereumBasicFunctions.getJsonList;
import static io.sapl.interpreter.pip.EthereumBasicFunctions.getStringFrom;
import static io.sapl.interpreter.pip.EthereumBasicFunctions.getStringListFrom;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
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

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EthereumPipFunctions {

	private static final String TO_BLOCK = "toBlock";

	private static final String FROM_BLOCK = "fromBlock";

	private static final String VALUE = "value";

	private static final String TYPE = "type";

	private static final String DEFAULT_BLOCK_PARAMETER = "defaultBlockParameter";

	private static final String LATEST = "latest";

	private static final String EARLIEST = "earliest";

	private static final String PENDING = "pending";

	private static final String ADDRESS = "address";

	private static final String NO_DBP_INFO = "The DefaultBlockParameter was not correctly provided. By default the latest Block is used.";

	private static final Bool DEFAULT_RETURN_TYPE = new Bool(false);

	private static final String DEFAULT_RETURN_WARNING = "By default a bool with value false is being returned.";

	private EthereumPipFunctions() {

	}

	/**
	 * Determines the DefaultBlockParameter needed for some Ethereum API calls. This Parameter can be a BigInteger value
	 * or one of the Strings "latest", "earliest" or "pending". If the DefaultBlockParameter is not provided in the
	 * policy, the latest Block is used by default. In this case there is also a log message.
	 *
	 * @param saplObject should hold the following values: <br>
	 * "defaultBlockParameter": BigInteger value of the desired block number <b>or</b> one of the strings "latest",
	 * "earliest", or "pending". <br>
	 * @return The DefaultBlockParameter corresponding to the input.
	 */
	protected static DefaultBlockParameter getDefaultBlockParameter(JsonNode saplObject) {
		return createDefaultBlockParameter(saplObject, DEFAULT_BLOCK_PARAMETER);
	}

	protected static org.web3j.protocol.core.methods.request.Transaction getTransactionFromJson(
			JsonNode jsonTransaction) {
		return org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
				getStringFrom(jsonTransaction, "from"), getStringFrom(jsonTransaction, "to"),
				getStringFrom(jsonTransaction, "data"));
	}

	protected static EthFilter getEthFilterFrom(JsonNode saplObject) {
		if (saplObject.has(FROM_BLOCK) && saplObject.has(TO_BLOCK) && saplObject.has(ADDRESS)) {
			String fromBlock = saplObject.get(FROM_BLOCK).textValue();
			String toBlock = saplObject.get(TO_BLOCK).textValue();
			return new EthFilter(DefaultBlockParameter.valueOf(bigIntFromHex(fromBlock)),
					DefaultBlockParameter.valueOf(bigIntFromHex(toBlock)), getStringListFrom(saplObject, ADDRESS));
		}
		log.warn(
				"The EthFilter was not correctly provided. Please make sure that you used the correct input parameters.");
		return new EthFilter();
	}

	protected static String createEncodedFunction(Function function) {
		return FunctionEncoder.encode(function);
	}

	protected static Function createFunction(String functionName, JsonNode inputParams, JsonNode outputParams)
			throws ClassNotFoundException {
		return new Function(functionName,
				getJsonList(inputParams).stream().map(EthereumPipFunctions::convertToType).collect(Collectors.toList()),
				getOutputParameters(outputParams));

	}

	private static List<TypeReference<?>> getOutputParameters(JsonNode outputNode) throws ClassNotFoundException {
		List<TypeReference<?>> outputParameters = new ArrayList<>();
		if (outputNode.isArray()) {
			for (JsonNode solidityType : outputNode) {
				outputParameters.add(TypeReference.makeTypeReference(solidityType.textValue()));
			}
			return outputParameters;
		}
		log.warn("The JsonNode containing the ouput parameters wasn't an array as expected. "
				+ "An empty list is being returned.");
		return outputParameters;
	}

	private static Type<?> convertToType(JsonNode inputParam) {

		if (inputParam != null && inputParam.has(TYPE) && inputParam.has(VALUE)) {
			String solidityType = inputParam.get(TYPE).textValue();
			JsonNode value = inputParam.get(VALUE);
			String textValue = value.textValue();
			BigInteger bigIntegerValue = value.bigIntegerValue();
			byte[] binaryValue = new byte[0];

			try {
				if (value.isBinary()) {
					binaryValue = value.binaryValue();
				}

				switch (solidityType) {
				case ADDRESS:
					return new Address(textValue);
				case "bool":
				case "boolean":
					return new Bool(value.asBoolean());
				case "string":
					return new Utf8String(textValue);
				case "bytes":
					return new DynamicBytes(binaryValue);
				case "byte":
					return new org.web3j.abi.datatypes.primitive.Byte((byte) value.asInt());
				case "char":
					return new Char(textValue.charAt(0));
				case "double":
					log.warn("You tried to use a double type but this is not supported as function input. "
							+ DEFAULT_RETURN_WARNING);
					return DEFAULT_RETURN_TYPE;
				case "float":
					log.warn("You tried to use a float type but this is not supported as function input. "
							+ DEFAULT_RETURN_WARNING);
					return DEFAULT_RETURN_TYPE;
				case "uint":
					return new Uint(bigIntegerValue);
				case "int":
					return new org.web3j.abi.datatypes.Int(bigIntegerValue);
				case "long":
					return new org.web3j.abi.datatypes.primitive.Long(value.asLong());
				case "short":
					return new org.web3j.abi.datatypes.primitive.Short(value.shortValue());
				case "uint8":
					return new Uint8(bigIntegerValue);
				case "int8":
					return new Int8(bigIntegerValue);
				case "uint16":
					return new Uint16(bigIntegerValue);
				case "int16":
					return new Int16(bigIntegerValue);
				case "uint24":
					return new Uint24(bigIntegerValue);
				case "int24":
					return new Int24(bigIntegerValue);
				case "uint32":
					return new Uint32(bigIntegerValue);
				case "int32":
					return new Int32(bigIntegerValue);
				case "uint40":
					return new Uint40(bigIntegerValue);
				case "int40":
					return new Int40(bigIntegerValue);
				case "uint48":
					return new Uint48(bigIntegerValue);
				case "int48":
					return new Int48(bigIntegerValue);
				case "uint56":
					return new Uint56(bigIntegerValue);
				case "int56":
					return new Int56(bigIntegerValue);
				case "uint64":
					return new Uint64(bigIntegerValue);
				case "int64":
					return new Int64(bigIntegerValue);
				case "uint72":
					return new Uint72(bigIntegerValue);
				case "int72":
					return new Int72(bigIntegerValue);
				case "uint80":
					return new Uint80(bigIntegerValue);
				case "int80":
					return new Int80(bigIntegerValue);
				case "uint88":
					return new Uint88(bigIntegerValue);
				case "int88":
					return new Int88(bigIntegerValue);
				case "uint96":
					return new Uint96(bigIntegerValue);
				case "int96":
					return new Int96(bigIntegerValue);
				case "uint104":
					return new Uint104(bigIntegerValue);
				case "int104":
					return new Int104(bigIntegerValue);
				case "uint112":
					return new Uint112(bigIntegerValue);
				case "int112":
					return new Int112(bigIntegerValue);
				case "uint120":
					return new Uint120(bigIntegerValue);
				case "int120":
					return new Int120(bigIntegerValue);
				case "uint128":
					return new Uint128(bigIntegerValue);
				case "int128":
					return new Int128(bigIntegerValue);
				case "uint136":
					return new Uint136(bigIntegerValue);
				case "int136":
					return new Int136(bigIntegerValue);
				case "uint144":
					return new Uint144(bigIntegerValue);
				case "int144":
					return new Int144(bigIntegerValue);
				case "uint152":
					return new Uint152(bigIntegerValue);
				case "int152":
					return new Int152(bigIntegerValue);
				case "uint160":
					return new Uint160(bigIntegerValue);
				case "int160":
					return new Int160(bigIntegerValue);
				case "uint168":
					return new Uint168(bigIntegerValue);
				case "int168":
					return new Int168(bigIntegerValue);
				case "uint176":
					return new Uint176(bigIntegerValue);
				case "int176":
					return new Int176(bigIntegerValue);
				case "uint184":
					return new Uint184(bigIntegerValue);
				case "int184":
					return new Int184(bigIntegerValue);
				case "uint192":
					return new Uint192(bigIntegerValue);
				case "int192":
					return new Int192(bigIntegerValue);
				case "uint200":
					return new Uint200(bigIntegerValue);
				case "int200":
					return new Int200(bigIntegerValue);
				case "uint208":
					return new Uint208(bigIntegerValue);
				case "int208":
					return new Int208(bigIntegerValue);
				case "uint216":
					return new Uint216(bigIntegerValue);
				case "int216":
					return new Int216(bigIntegerValue);
				case "uint224":
					return new Uint224(bigIntegerValue);
				case "int224":
					return new Int224(bigIntegerValue);
				case "uint232":
					return new Uint232(bigIntegerValue);
				case "int232":
					return new Int232(bigIntegerValue);
				case "uint240":
					return new Uint240(bigIntegerValue);
				case "int240":
					return new Int240(bigIntegerValue);
				case "uint248":
					return new Uint248(bigIntegerValue);
				case "int248":
					return new Int248(bigIntegerValue);
				case "uint256":
					return new Uint256(bigIntegerValue);
				case "int256":
					return new Int256(bigIntegerValue);
				case "bytes1":
					return new Bytes1(binaryValue);
				case "bytes2":
					return new Bytes2(binaryValue);
				case "bytes3":
					return new Bytes3(binaryValue);
				case "bytes4":
					return new Bytes4(binaryValue);
				case "bytes5":
					return new Bytes5(binaryValue);
				case "bytes6":
					return new Bytes6(binaryValue);
				case "bytes7":
					return new Bytes7(binaryValue);
				case "bytes8":
					return new Bytes8(binaryValue);
				case "bytes9":
					return new Bytes9(binaryValue);
				case "bytes10":
					return new Bytes10(binaryValue);
				case "bytes11":
					return new Bytes11(binaryValue);
				case "bytes12":
					return new Bytes12(binaryValue);
				case "bytes13":
					return new Bytes13(binaryValue);
				case "bytes14":
					return new Bytes14(binaryValue);
				case "bytes15":
					return new Bytes15(binaryValue);
				case "bytes16":
					return new Bytes16(binaryValue);
				case "bytes17":
					return new Bytes17(binaryValue);
				case "bytes18":
					return new Bytes18(binaryValue);
				case "bytes19":
					return new Bytes19(binaryValue);
				case "bytes20":
					return new Bytes20(binaryValue);
				case "bytes21":
					return new Bytes21(binaryValue);
				case "bytes22":
					return new Bytes22(binaryValue);
				case "bytes23":
					return new Bytes23(binaryValue);
				case "bytes24":
					return new Bytes24(binaryValue);
				case "bytes25":
					return new Bytes25(binaryValue);
				case "bytes26":
					return new Bytes26(binaryValue);
				case "bytes27":
					return new Bytes27(binaryValue);
				case "bytes28":
					return new Bytes28(binaryValue);
				case "bytes29":
					return new Bytes29(binaryValue);
				case "bytes30":
					return new Bytes30(binaryValue);
				case "bytes31":
					return new Bytes31(binaryValue);
				case "bytes32":
					return new Bytes32(binaryValue);
				default:
					log.warn("The type with the name {} couldn't be found. " + DEFAULT_RETURN_WARNING, solidityType);
					return DEFAULT_RETURN_TYPE;

				}
			}
			catch (IOException | StringIndexOutOfBoundsException e) {
				log.warn(
						"The type {} with value {} coudn't be generated. Please make sure that you used correct spelling and the "
								+ "value is correctly provided for this type. " + DEFAULT_RETURN_WARNING,
						solidityType, value);
				return DEFAULT_RETURN_TYPE;
			}

		}
		log.warn("There has been a request to convertToType, but the input didn't have both fields type "
				+ "and value or was null. " + DEFAULT_RETURN_WARNING);
		return DEFAULT_RETURN_TYPE;
	}

	private static BigInteger bigIntFromHex(String s) {
		return new BigInteger(s.substring(2), 16);
	}

	private static DefaultBlockParameter createDefaultBlockParameter(JsonNode saplObject, String inputName) {
		if (saplObject != null && saplObject.has(inputName)) {
			JsonNode dbp = saplObject.get(inputName);
			if (dbp.isTextual()) {
				String dbpName = dbp.textValue();
				if (EARLIEST.equals(dbpName) || LATEST.equals(dbpName) || PENDING.equals(dbpName))
					return DefaultBlockParameter.valueOf(dbpName);
			}
			if (dbp.isBigInteger())
				return DefaultBlockParameter.valueOf(dbp.bigIntegerValue());
		}
		log.info(NO_DBP_INFO);
		return DefaultBlockParameter.valueOf(LATEST);
	}

}
