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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Transfer;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Async;
import org.web3j.utils.Convert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.interpreter.pip.contracts.Authorization;
import io.sapl.pdp.embedded.EmbeddedPolicyDecisionPoint;
import io.sapl.pdp.embedded.EmbeddedPolicyDecisionPoint.Builder.IndexType;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * Testing the EthereumPIP with an ethereum blockchain node running in a Docker container. The node is the hyperledger
 * besu implemenation (https://besu.hyperledger.org/en/stable/)
 *
 * This test requires running Docker with version at least 1.6.0 and Docker environment should have more than 2GB free
 * disk space.
 *
 * The accounts used for testing are publicly known from the besu website
 * (https://besu.hyperledger.org/en/stable/Reference/Accounts-for-Testing/) <br>
 * DO NOT USE THESES ACCOUNTS IN THE MAIN NET. ANY ETHER SENT TO THESE ACCOUNTS WILL BE LOST.
 *
 * For these tests to be applied automatically, the maven profile integration-tests has to be activated.
 */
public class EthereumIntegrationTest {

	private static final String TRANSACTION = "transaction";

	private static final String HTTP_LOCALHOST = "http://localhost:";

	private static final String WRONG_NAME = "wrongName";

	private static final String ACCESS = "access";

	private static final String ETHEREUM = "ethereum";

	private static final String OUTPUT_PARAMS = "outputParams";

	private static final String BOOL = "bool";

	private static final String INPUT_PARAMS = "inputParams";

	private static final String VALUE = "value";

	private static final String ADDRESS = "address";

	private static final String TYPE = "type";

	private static final String TO_ACCOUNT = "toAccount";

	private static final String FROM_ACCOUNT = "fromAccount";

	private static final String CONTRACT_ADDRESS = "contractAddress";

	private static final String TRANSACTION_HASH = "transactionHash";

	private static final String TRANSACTION_VALUE = "transactionValue";

	private static final String FUNCTION_NAME = "functionName";

	private static final String IS_AUTHORIZED = "isAuthorized";

	private static final String USER1_ADDRESS = "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73";

	private static final String USER1_PRIVATE_KEY = "0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63";

	private static final String USER2_ADDRESS = "0x627306090abaB3A6e1400e9345bC60c78a8BEf57";

	private static final String USER3_ADDRESS = "0xf17f52151EbEF6C7334FAD080c5704D77216b732";

	private static final String USER3_PRIVATE_KEY = "0xae6ae8e5ccbfb04590405997ee2d52d2b330726137b875053c36d94e974d162f";

	private static final BigInteger TRANSACTION1_VALUE = new BigInteger("2000000000000000000");

	private static final String DEFAULT_BLOCK_PARAMETER = "defaultBlockParameter";

	private static final String LATEST = "latest";

	private static final String POSITION = "position";

	private static final String BLOCK_HASH = "blockHash";

	private static final String RETURN_FULL_TRANSACTION_OBJECTS = "returnFullTransactionObjects";

	private static final String TRANSACTION_INDEX = "transactionIndex";

	private static final String FILTER_ID = "filterId";

	private static final String UNCLE_INDEX = "uncleIndex";

	private static final ObjectMapper mapper = new ObjectMapper();

	private static Web3j web3j;

	private static EthereumPolicyInformationPoint ethPip;

	private static EmbeddedPolicyDecisionPoint pdp;

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static String authContractAddress;

	private static TransactionReceipt transactionReceiptUser2;

	private static TransactionReceipt transactionReceiptUser3;

	@ClassRule
	public static final GenericContainer<? extends GenericContainer<?>> besuContainer = new GenericContainer<>(
			"hyperledger/besu:latest").withExposedPorts(8545, 8546)
					.withCommand("--miner-enabled", "--miner-coinbase=" + USER1_ADDRESS, "--rpc-http-enabled",
							"--network=dev")
					.waitingFor(Wait.forHttp("/liveness").forStatusCode(200).forPort(8545));

	@BeforeClass
	public static void init() throws InterruptedException, TransactionException, Exception {
		final Integer port = besuContainer.getMappedPort(8545);
		web3j = Web3j.build(new HttpService(HTTP_LOCALHOST + port), 500, Async.defaultExecutorService());
		ethPip = new EthereumPolicyInformationPoint(web3j);

		String path = "src/test/resources";
		File file = new File(path);
		String absolutePath = file.getAbsolutePath();

		pdp = EmbeddedPolicyDecisionPoint.builder().withFilesystemPDPConfigurationProvider(absolutePath + "/policies")
				.withFilesystemPolicyRetrievalPoint(absolutePath + "/policies", IndexType.SIMPLE)
				.withPolicyInformationPoint(ethPip).build();

		Credentials credentials = Credentials.create(USER1_PRIVATE_KEY);
		transactionReceiptUser2 = Transfer.sendFunds(web3j, credentials, USER2_ADDRESS,
				BigDecimal.valueOf(TRANSACTION1_VALUE.doubleValue()), Convert.Unit.WEI).send();
		transactionReceiptUser3 = Transfer
				.sendFunds(web3j, credentials, USER3_ADDRESS, BigDecimal.valueOf(3.3), Convert.Unit.ETHER).send();

		Authorization authContract = Authorization.deploy(web3j, credentials, new DefaultGasProvider()).send();
		authContractAddress = authContract.getContractAddress();
		authContract.authorize(USER2_ADDRESS).send();

	}

	// Test with Policy

	@Test
	public void loadContractInformationShouldWorkInAuthorizationPolicy() {
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(CONTRACT_ADDRESS, authContractAddress);
		saplObject.put(FUNCTION_NAME, IS_AUTHORIZED);
		ArrayNode inputParams = JSON.arrayNode();
		ObjectNode input1 = JSON.objectNode();
		input1.put(TYPE, ADDRESS);
		input1.put(VALUE, USER2_ADDRESS.substring(2));
		inputParams.add(input1);
		saplObject.set(INPUT_PARAMS, inputParams);
		ArrayNode outputParams = JSON.arrayNode();
		outputParams.add(BOOL);
		saplObject.set(OUTPUT_PARAMS, outputParams);
		AuthorizationSubscription authzSubscription = new AuthorizationSubscription(saplObject, JSON.textNode(ACCESS),
				JSON.textNode(ETHEREUM), null);
		final Flux<AuthorizationDecision> decision = pdp.decide(authzSubscription);
		StepVerifier.create(decision).expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.PERMIT)
				.thenCancel().verify();
	}

	// loadContractInformation

	@Test
	public void loadContractInformationWithAuthorizationShouldReturnCorrectValue() {
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(CONTRACT_ADDRESS, authContractAddress);
		saplObject.put(FUNCTION_NAME, IS_AUTHORIZED);
		ArrayNode inputParams = JSON.arrayNode();
		ObjectNode input1 = JSON.objectNode();
		input1.put(TYPE, ADDRESS);
		input1.put(VALUE, USER2_ADDRESS.substring(2));
		inputParams.add(input1);
		saplObject.set(INPUT_PARAMS, inputParams);
		ArrayNode outputParams = JSON.arrayNode();
		outputParams.add(BOOL);
		saplObject.set(OUTPUT_PARAMS, outputParams);
		JsonNode result = ethPip.loadContractInformation(saplObject, null).blockFirst();

		assertTrue("False was returned although user2 was authorized and result should have been true.",
				result.get(0).get(VALUE).asBoolean());

	}

	// verifyTransaction

	@Test
	public void verifyTransactionShouldReturnTrueWithCorrectTransaction() {
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(TRANSACTION_HASH, transactionReceiptUser2.getTransactionHash());
		saplObject.put(FROM_ACCOUNT, USER1_ADDRESS);
		saplObject.put(TO_ACCOUNT, USER2_ADDRESS);
		saplObject.put(TRANSACTION_VALUE, TRANSACTION1_VALUE);
		boolean result = ethPip.verifyTransaction(saplObject, null).blockFirst().asBoolean();
		assertTrue("Transaction was not validated as true although it is correct.", result);

	}

	@Test
	public void verifyTransactionShouldReturnFalseWithFalseValue() {
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(TRANSACTION_HASH, transactionReceiptUser2.getTransactionHash());
		saplObject.put(FROM_ACCOUNT, USER1_ADDRESS);
		saplObject.put(TO_ACCOUNT, USER2_ADDRESS);
		saplObject.put(TRANSACTION_VALUE, new BigInteger("25"));
		boolean result = ethPip.verifyTransaction(saplObject, null).blockFirst().asBoolean();
		assertFalse("Transaction was not validated as false although the value was false.", result);

	}

	@Test
	public void verifyTransactionShouldReturnFalseWithFalseSender() {
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(TRANSACTION_HASH, transactionReceiptUser2.getTransactionHash());
		saplObject.put(FROM_ACCOUNT, USER3_ADDRESS);
		saplObject.put(TO_ACCOUNT, USER2_ADDRESS);
		saplObject.put(TRANSACTION_VALUE, new BigInteger("2000000000000000000"));
		boolean result = ethPip.verifyTransaction(saplObject, null).blockFirst().asBoolean();
		assertFalse("Transaction was not validated as false although the sender was false.", result);

	}

	@Test
	public void verifyTransactionShouldReturnFalseWithFalseRecipient() {
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(TRANSACTION_HASH, transactionReceiptUser2.getTransactionHash());
		saplObject.put(FROM_ACCOUNT, USER1_ADDRESS);
		saplObject.put(TO_ACCOUNT, USER3_ADDRESS);
		saplObject.put(TRANSACTION_VALUE, new BigInteger("2000000000000000000"));
		boolean result = ethPip.verifyTransaction(saplObject, null).blockFirst().asBoolean();
		assertFalse("Transaction was not validated as false although the recipient was false.", result);

	}

	@Test
	public void verifyTransactionShouldReturnFalseWithFalseTransactionHash() {
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(TRANSACTION_HASH, transactionReceiptUser3.getTransactionHash());
		saplObject.put(FROM_ACCOUNT, USER1_ADDRESS);
		saplObject.put(TO_ACCOUNT, USER2_ADDRESS);
		saplObject.put(TRANSACTION_VALUE, new BigInteger("2000000000000000000"));
		boolean result = ethPip.verifyTransaction(saplObject, null).blockFirst().asBoolean();
		assertFalse("Transaction was not validated as false although the TransactionHash was false.", result);

	}

	@Test
	public void verifyTransactionShouldReturnFalseWithNullInput() {
		boolean result = ethPip.verifyTransaction(null, null).blockFirst().asBoolean();
		assertFalse("Transaction was not validated as false although the input was null.", result);

	}

	@Test
	public void verifyTransactionShouldReturnFalseWithWrongInput1() {
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(WRONG_NAME, transactionReceiptUser2.getTransactionHash());
		saplObject.put(FROM_ACCOUNT, USER1_ADDRESS);
		saplObject.put(TO_ACCOUNT, USER2_ADDRESS);
		saplObject.put(TRANSACTION_VALUE, new BigInteger("2000000000000000000"));
		boolean result = ethPip.verifyTransaction(saplObject, null).blockFirst().asBoolean();
		assertFalse("Transaction was not validated as false although the input was erroneous.", result);

	}

	@Test
	public void verifyTransactionShouldReturnFalseWithWrongInput2() {
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(TRANSACTION_HASH, transactionReceiptUser2.getTransactionHash());
		saplObject.put(WRONG_NAME, USER1_ADDRESS);
		saplObject.put(TO_ACCOUNT, USER2_ADDRESS);
		saplObject.put(TRANSACTION_VALUE, new BigInteger("2000000000000000000"));
		boolean result = ethPip.verifyTransaction(saplObject, null).blockFirst().asBoolean();
		assertFalse("Transaction was not validated as false although the input was erroneous.", result);

	}

	@Test
	public void verifyTransactionShouldReturnFalseWithWrongInput3() {
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(TRANSACTION_HASH, transactionReceiptUser2.getTransactionHash());
		saplObject.put(FROM_ACCOUNT, USER1_ADDRESS);
		saplObject.put(WRONG_NAME, USER2_ADDRESS);
		saplObject.put(TRANSACTION_VALUE, new BigInteger("2000000000000000000"));
		boolean result = ethPip.verifyTransaction(saplObject, null).blockFirst().asBoolean();
		assertFalse("Transaction was not validated as false although the input was erroneous.", result);

	}

	@Test
	public void verifyTransactionShouldReturnFalseWithWrongInput4() {
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(TRANSACTION_HASH, transactionReceiptUser2.getTransactionHash());
		saplObject.put(FROM_ACCOUNT, USER1_ADDRESS);
		saplObject.put(TO_ACCOUNT, USER2_ADDRESS);
		saplObject.put(WRONG_NAME, new BigInteger("2000000000000000000"));
		boolean result = ethPip.verifyTransaction(saplObject, null).blockFirst().asBoolean();
		assertFalse("Transaction was not validated as false although the input was erroneous.", result);

	}

	// clientVersion

	@Test
	public void web3ClientVersionShouldReturnTheClientVersion() throws IOException {
		String pipResult = ethPip.web3ClientVersion(null, null).blockFirst().asText();
		String web3jResult = web3j.web3ClientVersion().send().getWeb3ClientVersion();
		assertEquals("The web3ClientVersion from the PIP was not loaded correctly.", web3jResult, pipResult);
	}

	// sha3
	@Test
	public void web3Sha3ShouldReturnCorrectValuer() throws IOException {
		JsonNode saplObject = JSON.textNode(USER3_PRIVATE_KEY);
		String pipResult = ethPip.web3Sha3(saplObject, null).blockFirst().textValue();
		String web3jResult = web3j.web3Sha3(USER3_PRIVATE_KEY).send().getResult();
		assertEquals("The web3Sha3 method did not work correctly.", web3jResult, pipResult);
	}

	// netVersion
	@Test
	public void netVersionShouldReturnCorrectValue() throws IOException {
		String pipResult = ethPip.netVersion(null, null).blockFirst().textValue();
		String web3jResult = web3j.netVersion().send().getNetVersion();
		assertEquals("The netVersion method did not work correctly.", web3jResult, pipResult);

	}

	// listening
	@Test
	public void netListeningShouldReturnTrueWhenListeningToNetworkConnections() throws IOException {
		assertTrue("The netListening method did not return true although the Client by default is listening.",
				ethPip.netListening(null, null).blockFirst().asBoolean());
	}

	// peerCount
	@Test
	public void netPeerCountShouldReturnTheCorrectNumber() throws IOException {
		BigInteger pipResult = ethPip.netPeerCount(null, null).blockFirst().bigIntegerValue();
		BigInteger web3jResult = web3j.netPeerCount().send().getQuantity();
		assertEquals("The netPeerCount method did not return the correct number.", web3jResult, pipResult);
	}

	// protocolVersion
	@Test
	public void protocolVersionShouldReturnTheCorrectValue() throws IOException {
		String pipResult = ethPip.ethProtocolVersion(null, null).blockFirst().textValue();
		String web3jResult = web3j.ethProtocolVersion().send().getProtocolVersion();
		assertEquals("The ethProtocolVersion method did not return the correct value.", web3jResult, pipResult);
	}

	// syncing
	@Test
	public void ethSyncingShouldReturnTheCorrectValue() throws IOException {
		boolean pipResult = ethPip.ethSyncing(null, null).blockFirst().asBoolean();
		boolean web3jResult = web3j.ethSyncing().send().getResult().isSyncing();
		assertEquals("The ethSyncing method did not return the correct value.", web3jResult, pipResult);
	}

	// coinbase
	@Test
	public void ethCoinbaseShouldReturnTheCorrectValue() throws IOException {
		String pipResult = ethPip.ethCoinbase(null, null).blockFirst().textValue();
		String web3jResult = web3j.ethCoinbase().send().getResult();
		assertEquals("The ethCoinbase method did not return the correct value.", web3jResult, pipResult);
	}

	// mining
	@Test
	public void ethMiningShouldReturnTheCorrectValue() throws IOException {
		boolean pipResult = ethPip.ethMining(null, null).blockFirst().asBoolean();
		boolean web3jResult = web3j.ethMining().send().getResult();
		assertEquals("The ethMining method did not return the correct value.", web3jResult, pipResult);
	}

	// hashrate
	@Test
	public void ethHashrateShouldReturnTheCorrectValue() throws IOException {
		BigInteger pipResult = ethPip.ethHashrate(null, null).blockFirst().bigIntegerValue();
		assertTrue("The ethHashrate should be greater than 0.", pipResult.intValue() > 0);
	}

	// gasPrice
	@Test
	public void ethGasPriceShouldReturnTheCorrectValue() throws IOException {
		BigInteger pipResult = ethPip.ethGasPrice(null, null).blockFirst().bigIntegerValue();
		BigInteger web3jResult = web3j.ethGasPrice().send().getGasPrice();
		assertEquals("The ethGasPrice method did not return the correct number.", web3jResult, pipResult);
	}

	// accounts
	@Test
	public void ethAccountsShouldReturnTheCorrectValue() throws IOException {

		List<JsonNode> result = new ArrayList<JsonNode>();
		ethPip.ethAccounts(null, null).blockFirst().elements().forEachRemaining(result::add);
		List<String> pipResult = result.stream().map(s -> s.toString()).collect(Collectors.toList());
		List<String> web3jResult = web3j.ethAccounts().send().getAccounts();
		assertEquals("The accounts method did not return the correct accounts.", web3jResult, pipResult);
	}

	// blockNumber
	@Test
	public void ethBlockNumberShouldReturnTheCorrectValue() throws IOException {
		BigInteger pipResult = ethPip.ethBlockNumber(null, null).blockFirst().bigIntegerValue();
		BigInteger web3jResult = web3j.ethBlockNumber().send().getBlockNumber();
		assertEquals("The ethBlockNumber method did not return the correct value.", web3jResult, pipResult);
	}

	// balance
	@Test
	public void ethGetBalanceShouldReturnTheCorrectValue() throws IOException {
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(ADDRESS, USER1_ADDRESS);
		saplObject.put(DEFAULT_BLOCK_PARAMETER, LATEST);
		BigInteger pipResult = ethPip.ethGetBalance(saplObject, null).blockFirst().bigIntegerValue();
		BigInteger web3jResult = web3j.ethGetBalance(USER1_ADDRESS, DefaultBlockParameter.valueOf(LATEST)).send()
				.getBalance();
		assertEquals("The ethGetBalance method did not return the correct value.", web3jResult, pipResult);
	}

	// storage
	@Test
	public void ethGetStorageAtShouldReturnTheCorrectValue() throws IOException {
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(ADDRESS, authContractAddress);
		saplObject.put(POSITION, BigInteger.ZERO);
		saplObject.put(DEFAULT_BLOCK_PARAMETER, LATEST);
		String pipResult = ethPip.ethGetStorageAt(saplObject, null).blockFirst().textValue();
		String web3jResult = web3j
				.ethGetStorageAt(authContractAddress, BigInteger.ZERO, DefaultBlockParameter.valueOf(LATEST)).send()
				.getData();
		assertEquals("The ethGetStorageAt method did not return the correct value.", web3jResult, pipResult);
	}

	// transactionCount
	@Test
	public void ethGetTransactionCountShouldReturnTheCorrectValue() throws IOException {
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(ADDRESS, USER1_ADDRESS);
		saplObject.put(DEFAULT_BLOCK_PARAMETER, LATEST);
		BigInteger pipResult = ethPip.ethGetTransactionCount(saplObject, null).blockFirst().bigIntegerValue();
		BigInteger web3jResult = web3j.ethGetTransactionCount(USER1_ADDRESS, DefaultBlockParameter.valueOf(LATEST))
				.send().getTransactionCount();
		assertEquals("The ethGetTransactionCount method did not return the correct value.", web3jResult, pipResult);
	}

	// blockTransactionCountByHash
	@Test
	public void ethGetBlockTransactionCountByHashShouldReturnTheCorrectValue()
			throws IOException, InterruptedException, ExecutionException {
		String blockhash = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(LATEST), false).send().getBlock()
				.getHash();
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(BLOCK_HASH, blockhash);
		BigInteger pipResult = ethPip.ethGetBlockTransactionCountByHash(saplObject, null).blockFirst()
				.bigIntegerValue();
		BigInteger web3jResult = web3j.ethGetBlockTransactionCountByHash(blockhash).send().getTransactionCount();
		assertEquals("The ethGetBlockTransactionCountByHash method did not return the correct value.", web3jResult,
				pipResult);
	}

	// blockTransactionCountByNumber
	@Test
	public void ethGetBlockTransactionCountByNumberShouldReturnTheCorrectValue() throws IOException {
		BigInteger blocknumber = web3j.ethBlockNumber().send().getBlockNumber();
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(DEFAULT_BLOCK_PARAMETER, blocknumber);
		BigInteger pipResult = ethPip.ethGetBlockTransactionCountByNumber(saplObject, null).blockFirst()
				.bigIntegerValue();
		BigInteger web3jResult = web3j.ethGetBlockTransactionCountByNumber(DefaultBlockParameter.valueOf(blocknumber))
				.send().getTransactionCount();
		assertEquals("The ethGetBlockTransactionCountByNumber method did not return the correct value.", web3jResult,
				pipResult);
	}

	// uncleCountByBlockHash
	@Test
	public void ethGetUncleCountByBlockHashShouldReturnTheCorrectValue() throws IOException {
		String blockhash = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(LATEST), false).send().getBlock()
				.getHash();
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(BLOCK_HASH, blockhash);
		BigInteger pipResult = ethPip.ethGetUncleCountByBlockHash(saplObject, null).blockFirst().bigIntegerValue();
		BigInteger web3jResult = web3j.ethGetUncleCountByBlockHash(blockhash).send().getUncleCount();
		assertEquals("The ethGetUncleCountByBlockHash method did not return the correct value.", web3jResult,
				pipResult);
	}

	// uncleCountByBlockNumber
	@Test
	public void uncleCountByBlockNumberShouldReturnTheCorrectValue() throws IOException {
		BigInteger blocknumber = web3j.ethBlockNumber().send().getBlockNumber();
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(DEFAULT_BLOCK_PARAMETER, blocknumber);
		BigInteger pipResult = ethPip.ethGetUncleCountByBlockNumber(saplObject, null).blockFirst().bigIntegerValue();
		BigInteger web3jResult = web3j.ethGetBlockTransactionCountByNumber(DefaultBlockParameter.valueOf(blocknumber))
				.send().getTransactionCount();
		assertEquals("The ethGetUncleCountByBlockNumber method did not return the correct value.", web3jResult,
				pipResult);

	}

	// code
	@Test
	public void ethGetCodeShouldReturnTheCorrectValue() throws IOException {
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(ADDRESS, authContractAddress);
		String pipResult = ethPip.ethGetCode(saplObject, null).blockFirst().textValue();
		String web3jResult = web3j.ethGetCode(authContractAddress, DefaultBlockParameter.valueOf(LATEST)).send()
				.getCode();
		assertEquals("The ethGetCode method did not return the correct value.", web3jResult, pipResult);
	}

	// call
	@Test
	public void ethCallShouldReturnTheCorrectValue() throws IOException, ClassNotFoundException {
		List<TypeReference<?>> outputParameters = new ArrayList<>();
		outputParameters.add(TypeReference.makeTypeReference(BOOL));
		List<Type<?>> inputList = new ArrayList<>();
		inputList.add(new Address(USER2_ADDRESS.substring(2)));
		Function function = new Function(IS_AUTHORIZED, inputList.stream().collect(Collectors.toList()),
				outputParameters);
		String encodedFunction = FunctionEncoder.encode(function);
		Transaction transaction = Transaction.createEthCallTransaction(USER1_ADDRESS, authContractAddress,
				encodedFunction);

		ObjectNode saplObject = JSON.objectNode();
		saplObject.set(TRANSACTION, mapper.convertValue(transaction, JsonNode.class));

		String pipResult = ethPip.ethCall(saplObject, null).blockFirst().textValue();
		String web3jResult = web3j.ethCall(transaction, DefaultBlockParameter.valueOf(LATEST)).send().getValue();
		assertEquals("The ethCall method did not return the correct value.", web3jResult, pipResult);
	}

	// estimateGas
	@Test
	public void ethEstimateGasCodeShouldReturnTheCorrectValue() throws IOException, ClassNotFoundException {
		List<TypeReference<?>> outputParameters = new ArrayList<>();
		outputParameters.add(TypeReference.makeTypeReference(BOOL));
		List<Type<?>> inputList = new ArrayList<>();
		inputList.add(new Address(USER2_ADDRESS.substring(2)));
		Function function = new Function(IS_AUTHORIZED, inputList.stream().collect(Collectors.toList()),
				outputParameters);
		String encodedFunction = FunctionEncoder.encode(function);
		Transaction transaction = Transaction.createEthCallTransaction(USER1_ADDRESS, authContractAddress,
				encodedFunction);

		ObjectNode saplObject = JSON.objectNode();
		saplObject.set(TRANSACTION, mapper.convertValue(transaction, JsonNode.class));

		BigInteger pipResult = ethPip.ethEstimateGas(saplObject, null).blockFirst().bigIntegerValue();
		BigInteger web3jResult = web3j.ethEstimateGas(transaction).send().getAmountUsed();
		assertEquals("The ethEstimateGas method did not return the correct value.", web3jResult, pipResult);
	}

	// blockByHash
	@Test
	public void ethGetBlockByHashShouldReturnTheCorrectValue() throws IOException {
		String blockHash = web3j.ethBlockHashFlowable().blockingFirst();

		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(BLOCK_HASH, blockHash);
		saplObject.put(RETURN_FULL_TRANSACTION_OBJECTS, false);
		String pipResult = ethPip.ethGetBlockByHash(saplObject, null).blockFirst().toString();
		String web3jResult = mapper
				.convertValue(web3j.ethGetBlockByHash(blockHash, false).send().getBlock(), JsonNode.class).toString();
		assertEquals("The ethGetBlockByHash method did not return the correct value.", web3jResult, pipResult);
	}

	// blockByNumber
	@Test
	public void ethGetBlockByNumberShouldReturnTheCorrectValue() throws IOException {
		BigInteger blockNumber = web3j.ethBlockNumber().send().getBlockNumber();

		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(DEFAULT_BLOCK_PARAMETER, blockNumber);
		saplObject.put(RETURN_FULL_TRANSACTION_OBJECTS, false);
		String pipResult = ethPip.ethGetBlockByNumber(saplObject, null).blockFirst().toString();
		String web3jResult = mapper.convertValue(
				web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(blockNumber), false).send().getBlock(),
				JsonNode.class).toString();

		assertEquals("The ethGetBlockByNumber method did not return the correct value.", web3jResult, pipResult);
	}

	// transactionByHash
	@Test
	public void ethGetTransactionByHashShouldReturnTheCorrectValue() throws IOException {

		String transactionHash = transactionReceiptUser2.getTransactionHash();
		JsonNode saplObject = JSON.textNode(transactionHash);
		String pipResult = ethPip.ethGetTransactionByHash(saplObject, null).blockFirst().toString();
		String web3jResult = mapper
				.convertValue(web3j.ethGetTransactionByHash(transactionHash).send().getResult(), JsonNode.class)
				.toString();

		assertEquals("The ethGetTransactionByHash method did not return the correct value.", web3jResult, pipResult);
	}

	// transactionByBlockHashAndIndex
	@Test
	public void ethGetTransactionByBlockHashAndIndexShouldReturnTheCorrectValue() throws IOException {

		String blockHash = transactionReceiptUser2.getBlockHash();
		BigInteger index = transactionReceiptUser2.getTransactionIndex();

		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(BLOCK_HASH, blockHash);
		saplObject.put(TRANSACTION_INDEX, index);

		String pipResult = ethPip.ethGetTransactionByBlockHashAndIndex(saplObject, null).blockFirst().toString();
		String web3jResult = mapper
				.convertValue(web3j.ethGetTransactionByBlockHashAndIndex(blockHash, index).send().getResult(),
						JsonNode.class)
				.toString();

		assertEquals("The ethGetTransactionByBlockHashAndIndex method did not return the correct value.", web3jResult,
				pipResult);
	}

	// transactionByBlockNumberAndIndex
	@Test
	public void ethGetTransactionByBlockNumberAndIndexShouldReturnTheCorrectValue() throws IOException {

		BigInteger blockNumber = transactionReceiptUser2.getBlockNumber();
		BigInteger index = transactionReceiptUser2.getTransactionIndex();

		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(DEFAULT_BLOCK_PARAMETER, blockNumber);
		saplObject.put(TRANSACTION_INDEX, index);

		String pipResult = ethPip.ethGetTransactionByBlockNumberAndIndex(saplObject, null).blockFirst().toString();
		String web3jResult = mapper.convertValue(
				web3j.ethGetTransactionByBlockNumberAndIndex(DefaultBlockParameter.valueOf(blockNumber), index).send()
						.getResult(),
				JsonNode.class).toString();

		assertEquals("The ethGetTransactionByBlockNumberAndIndex method did not return the correct value.", web3jResult,
				pipResult);
	}

	// transactionReceipt
	@Test
	public void ethGetTransactionReceiptShouldReturnTheCorrectValue() throws IOException {

		String transactionHash = transactionReceiptUser2.getTransactionHash();
		JsonNode saplObject = JSON.textNode(transactionHash);

		String pipResult = ethPip.ethGetTransactionReceipt(saplObject, null).blockFirst().toString();
		String web3jResult = mapper
				.convertValue(web3j.ethGetTransactionReceipt(transactionHash).send().getResult(), JsonNode.class)
				.toString();

		assertEquals("The ethGetTransactionReceipt method did not return the correct value.", web3jResult, pipResult);
	}

	// uncleByBlockHashAndIndex
	@Test
	public void ethGetUncleByBlockHashAndIndexShouldThrowAttributeExceptionWithNoUncle() throws IOException {

		String blockHash = transactionReceiptUser2.getBlockHash();
		BigInteger index = BigInteger.ZERO;

		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(BLOCK_HASH, blockHash);
		saplObject.put(UNCLE_INDEX, index);

		String pipResult = ethPip.ethGetUncleByBlockHashAndIndex(saplObject, null).blockFirst().toString();
		String web3jResult = mapper
				.convertValue(web3j.ethGetUncleByBlockHashAndIndex(blockHash, index).send().getResult(), JsonNode.class)
				.toString();

		assertEquals("The ethGetUncleByBlockHashAndIndex method did not return the correct value.", web3jResult,
				pipResult);
	}

	// uncleByBlockNumberAndIndex
	@Test
	public void ethGetUncleByBlockNumberAndIndexShouldThrowAttributeExceptionWithNoUncle() throws IOException {

		BigInteger blockNumber = transactionReceiptUser2.getBlockNumber();
		BigInteger index = BigInteger.ZERO;

		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(DEFAULT_BLOCK_PARAMETER, blockNumber);
		saplObject.put(UNCLE_INDEX, index);

		String pipResult = ethPip.ethGetUncleByBlockNumberAndIndex(saplObject, null).blockFirst().toString();
		String web3jResult = mapper
				.convertValue(web3j.ethGetUncleByBlockNumberAndIndex(DefaultBlockParameter.valueOf(blockNumber), index)
						.send().getResult(), JsonNode.class)
				.toString();

		assertEquals("The ethGetUncleByBlockNumberAndIndex method did not return the correct value.", web3jResult,
				pipResult);
	}

	// ethFilterChanges
	@Test
	public void ethGetFilterChangesShouldReturnTheCorrectValue() throws IOException {

		BigInteger filterId = web3j.ethNewBlockFilter().send().getFilterId();

		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(FILTER_ID, filterId);

		String pipResult = ethPip.ethGetFilterChanges(saplObject, null).blockFirst().toString();
		String web3jResult = web3j.ethGetFilterChanges(filterId).send().getResult().toString();

		assertEquals("The ethGetFilterChanges method did not return the correct value.", web3jResult, pipResult);
	}

}

