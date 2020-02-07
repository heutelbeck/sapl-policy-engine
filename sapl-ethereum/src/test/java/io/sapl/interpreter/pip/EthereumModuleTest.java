package io.sapl.interpreter.pip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import org.web3j.protocol.core.methods.response.EthBlock.TransactionObject;
import org.web3j.protocol.core.methods.response.EthLog.LogObject;
import org.web3j.protocol.core.methods.response.EthTransaction;
import org.web3j.protocol.core.methods.response.ShhMessages.SshMessage;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.reactivex.Flowable;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ Web3j.class, EthereumPipFunctions.class, org.web3j.protocol.core.methods.request.Transaction.class })
public class EthereumModuleTest {

	private static final String WRONG_NAME = "wrongName";

	private static final String TRANSACTION = "transaction";

	private static final String TRANSACTION_VALUE = "transactionValue";

	private static final String TO_ACCOUNT = "toAccount";

	private static final String FROM_ACCOUNT = "fromAccount";

	private static final String TRANSACTION_HASH = "transactionHash";

	private static final String TEST_TRANSACTION_HASH = "0xbeac927d1d256e9a21f8d81233cc83c03bf1a7a79a73a4664fa7ffba74101dac";

	private static final String TEST_FALSE_TRANSACTION_HASH = "0x777c927d1d256e9a21f8d81233cc83c03bf1a7a79a73a4664fa7ffba74101dac";

	private static final String TEST_FROM_ACCOUNT = "0x70b6613e37616045a80a97e08e930e1e4d800039";

	private static final String TEST_TO_ACCOUNT = "0x3f2cbea2185089ea5bbabbcd7616b215b724885c";

	private static final String TEST_FALSE_ACCOUNT = "0x555cbea2185089ea5bbabbcd7616b215b724885c";

	private static final BigInteger TEST_TRANSACTION_VALUE = new BigInteger("2000000000000000000");

	private static final String OUTPUT_PARAMS = "outputParams";

	private static final String BOOL = "bool";

	private static final String INPUT_PARAMS = "inputParams";

	private static final String VALUE = "value";

	private static final String ADDRESS = "address";

	private static final String TYPE = "type";

	private static final String CONTRACT_ADDRESS = "contractAddress";

	private static final String FUNCTION_NAME = "functionName";

	private static final String IS_AUTHORIZED = "isAuthorized";

	private static final String USER1_ADDRESS = "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73";

	private static final String USER2_ADDRESS = "0x627306090abaB3A6e1400e9345bC60c78a8BEf57";

	private static final String USER3_ADDRESS = "0xf17f52151EbEF6C7334FAD080c5704D77216b732";

	private static final String USER3_PRIVATE_KEY = "0xae6ae8e5ccbfb04590405997ee2d52d2b330726137b875053c36d94e974d162f";

	private static final String DEFAULT_BLOCK_PARAMETER = "defaultBlockParameter";

	private static final String RETURN_FULL_TRANSACTION_OBJECTS = "returnFullTransactionObjects";

	private static final String LATEST = "latest";

	private static final String EARLIEST = "earliest";

	private static final String PENDING = "pending";

	private static final String POSITION = "position";

	private static final String BLOCK_HASH = "blockHash";

	private static final String SHA3_HASH_OF_DATA_TO_SIGN = "sha3HashOfDataToSign";

	private static final String FILTER_ID = "filterId";

	private static final String TRANSACTION_INDEX = "transactionIndex";

	private static final String UNCLE_INDEX = "uncleIndex";

	private static final String ETH_POLLING_INTERVAL = "ethPollingInterval";

	private static final String TEST_DATA_CLIENT_VERSION = "besu/v1.3.5/linux-x86_64/oracle_openjdk-java-11";

	private static final String TEST_DATA_SIGN_ADDRESS = "0x9b2055d370f73ec7d8a03e965129118dc8f5bf83";

	private static final String TEST_DATA_SIGN_MESSAGE = "0xdeadbeaf";

	private static final String TEST_DATA_SIGN_RESULT = "0xa3f20717a250c2b0b729b7e5becbff67fdaef7e0699da4de7ca5895b02a170a12d887fd3b17bfdce3481f10bea41f45ba9f709d39ce8325427b57afcfc994cee1b";

	private static final String TEST_DATA_SHH_VERSION = "2";

	private static final String TEST_DATA_HAS_IDENTITY = "0x04f96a5e25610293e42a73908e93ccc8c4d4dc0edcfa9fa872f50cb214e08ebf61a03e245533f97284d442460f2998cd41858798ddfd4d661997d3940272b717b1";

	private static final String TEST_DATA_SHA3_RESULT = "0x1e8c821d4eb4d148eb679979820c7c3338148b8433b4ffb0b9d713c8f8e33228";

	private static final String TEST_DATA_BLOCKHASH = "0xc34757f3b3e5ee0d4533f2dedfa98925613acd16e1bb99ad1905bdabb37c897a";

	private static final String TEST_DATA_TRANSACTION_HASH_2 = "0x0d75b11b42df31d635730c6a1a26a0b849916cc5e9ceed4bc04a3348fe1f1db3";

	private static final String TEST_DATA_TRANSACTION_HASH = "0x610a8276014437089ff619136486474322444f0814f224fbbf9e925bb477e1e4";

	private static final String TEST_DATA_STORAGE_ADDRESS = "0x9a3dbca554e9f6b9257aaa24010da8377c57c17e";

	private static final String TEST_DATA_STORAGE_RETURN_VALUE = "0x000000000000000000000000fe3b557e8fb62b89f4916b721be55ceb828dbd73";

	private static final String TEST_DATA_CONTRACT_ADDRESS = "0x9a3dbca554e9f6b9257aaa24010da8377c57c17e";

	private static final String TEST_DATA_CALL_RETURN_VALUE = "0x0000000000000000000000000000000000000000000000000000000000000001";

	private static final String TEST_DATA_RECEIPT_TRANSACTION_HASH = "0x610a8276014437089ff619136486474322444f0814f224fbbf9e925bb477e1e4";

	private static final String TEST_DATA_CODE_RETURN = "0x608060405234801561001057600080fd5b50600436106100575760003560e01c8063a87430ba1461005c578063b6a5d7de14610096578063f0b37c04146100be578063f851a440146100e4578063fe9fbb8014610108575b600080fd5b6100826004803603602081101561007257600080fd5b50356001600160a01b031661012e565b604080519115158252519081900360200190f35b6100bc600480360360208110156100ac57600080fd5b50356001600160a01b0316610143565b005b6100bc600480360360208110156100d457600080fd5b50356001600160a01b03166101b3565b6100ec61021d565b604080516001600160a01b039092168252519081900360200190f35b6100826004803603602081101561011e57600080fd5b50356001600160a01b031661022c565b60016020526000908152604090205460ff1681565b6000546001600160a01b0316331461018c5760405162461bcd60e51b815260040180806020018281038252602381526020018061024b6023913960400191505060405180910390fd5b6001600160a01b03166000908152600160208190526040909120805460ff19169091179055565b6000546001600160a01b031633146101fc5760405162461bcd60e51b815260040180806020018281038252602581526020018061026e6025913960400191505060405180910390fd5b6001600160a01b03166000908152600160205260409020805460ff19169055565b6000546001600160a01b031681565b6001600160a01b031660009081526001602052604090205460ff169056fe4f6e6c79207468652061646d696e2063616e20617574686f72697a652075736572732e4f6e6c79207468652061646d696e2063616e20756e617574686f72697a652075736572732ea265627a7a723058205e648b3c949b765bf920a00b4306109e0fdb1a2204a85a0c9ed7cf171576562464736f6c63430005090032";

	private static final String TEST_DATA_LCI_CONTRACT_ADDRESS = "0x9a3dbca554e9f6b9257aaa24010da8377c57c17e";

	private static final String TEST_DATA_LCI_ENCODED_FUNCTION = "0xfe9fbb80000000000000000000000000627306090abab3a6e1400e9345bc60c78a8bef57";

	private static final String TEST_DATA_LCI_RESULT = "0x0000000000000000000000000000000000000000000000000000000000000001";

	private static final ObjectMapper mapper = new ObjectMapper();

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static EthereumPolicyInformationPoint ethPip;

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private Web3j web3j;

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private EthTransaction ethTransaction;

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private Transaction transactionFromChain;

	private Optional<Transaction> optionalTransactionFromChain;

	@Rule
	public MockitoRule mockitoRule = MockitoJUnit.rule();

	@Before
	public void init() throws IOException {
		mockStatic(Web3j.class);
		ethPip = new EthereumPolicyInformationPoint(web3j);
	}

	// verifyTransaction

	@Test
	public void verifyTransactionShouldReturnTrueWithCorrectTransaction() throws IOException {
		optionalTransactionFromChain = Optional.of(transactionFromChain);
		when(web3j.ethGetTransactionByHash(TEST_TRANSACTION_HASH).send()).thenReturn(ethTransaction);
		when(ethTransaction.getTransaction()).thenReturn(optionalTransactionFromChain);
		when(transactionFromChain.getFrom()).thenReturn(TEST_FROM_ACCOUNT);
		when(transactionFromChain.getTo()).thenReturn(TEST_TO_ACCOUNT);
		when(transactionFromChain.getValue()).thenReturn(TEST_TRANSACTION_VALUE);
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(TRANSACTION_HASH, TEST_TRANSACTION_HASH);
		saplObject.put(FROM_ACCOUNT, TEST_FROM_ACCOUNT);
		saplObject.put(TO_ACCOUNT, TEST_TO_ACCOUNT);
		saplObject.put(TRANSACTION_VALUE, TEST_TRANSACTION_VALUE);
		boolean result = ethPip.verifyTransaction(saplObject, null).blockFirst().asBoolean();
		assertTrue("Transaction was not validated as true although it is correct.", result);

	}

	@Test
	public void verifyTransactionShouldReturnFalseWithFalseValue() throws IOException {
		optionalTransactionFromChain = Optional.of(transactionFromChain);
		when(web3j.ethGetTransactionByHash(TEST_TRANSACTION_HASH).send()).thenReturn(ethTransaction);
		when(ethTransaction.getTransaction()).thenReturn(optionalTransactionFromChain);
		when(transactionFromChain.getFrom()).thenReturn(TEST_FROM_ACCOUNT);
		when(transactionFromChain.getTo()).thenReturn(TEST_TO_ACCOUNT);
		when(transactionFromChain.getValue()).thenReturn(TEST_TRANSACTION_VALUE);
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(TRANSACTION_HASH, TEST_TRANSACTION_HASH);
		saplObject.put(FROM_ACCOUNT, TEST_FROM_ACCOUNT);
		saplObject.put(TO_ACCOUNT, TEST_TO_ACCOUNT);
		saplObject.put(TRANSACTION_VALUE, new BigInteger("25"));
		boolean result = ethPip.verifyTransaction(saplObject, null).blockFirst().asBoolean();
		assertFalse("Transaction was not validated as false although the value was false.", result);

	}

	@Test
	public void verifyTransactionShouldReturnFalseWithFalseSender() throws IOException {
		optionalTransactionFromChain = Optional.of(transactionFromChain);
		when(web3j.ethGetTransactionByHash(TEST_TRANSACTION_HASH).send()).thenReturn(ethTransaction);
		when(ethTransaction.getTransaction()).thenReturn(optionalTransactionFromChain);
		when(transactionFromChain.getFrom()).thenReturn(TEST_FROM_ACCOUNT);
		when(transactionFromChain.getValue()).thenReturn(TEST_TRANSACTION_VALUE);
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(TRANSACTION_HASH, TEST_TRANSACTION_HASH);
		saplObject.put(FROM_ACCOUNT, TEST_FALSE_ACCOUNT);
		saplObject.put(TO_ACCOUNT, TEST_TO_ACCOUNT);
		saplObject.put(TRANSACTION_VALUE, TEST_TRANSACTION_VALUE);
		boolean result = ethPip.verifyTransaction(saplObject, null).blockFirst().asBoolean();
		assertFalse("Transaction was not validated as false although the sender was false.", result);

	}

	@Test
	public void verifyTransactionShouldReturnFalseWithFalseRecipient() throws IOException {
		optionalTransactionFromChain = Optional.of(transactionFromChain);
		when(web3j.ethGetTransactionByHash(TEST_TRANSACTION_HASH).send()).thenReturn(ethTransaction);
		when(ethTransaction.getTransaction()).thenReturn(optionalTransactionFromChain);
		when(transactionFromChain.getFrom()).thenReturn(TEST_FROM_ACCOUNT);
		when(transactionFromChain.getTo()).thenReturn(TEST_TO_ACCOUNT);
		when(transactionFromChain.getValue()).thenReturn(TEST_TRANSACTION_VALUE);
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(TRANSACTION_HASH, TEST_TRANSACTION_HASH);
		saplObject.put(FROM_ACCOUNT, TEST_FROM_ACCOUNT);
		saplObject.put(TO_ACCOUNT, TEST_FALSE_ACCOUNT);
		saplObject.put(TRANSACTION_VALUE, TEST_TRANSACTION_VALUE);
		boolean result = ethPip.verifyTransaction(saplObject, null).blockFirst().asBoolean();
		assertFalse("Transaction was not validated as false although the recipient was false.", result);

	}

	@Test
	public void verifyTransactionShouldReturnFalseWithFalseTransactionHash() throws IOException {
		when(web3j.ethGetTransactionByHash(TEST_TRANSACTION_HASH).send()).thenReturn(ethTransaction);
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(TRANSACTION_HASH, TEST_FALSE_TRANSACTION_HASH);
		saplObject.put(FROM_ACCOUNT, TEST_FROM_ACCOUNT);
		saplObject.put(TO_ACCOUNT, TEST_TO_ACCOUNT);
		saplObject.put(TRANSACTION_VALUE, TEST_TRANSACTION_VALUE);
		boolean result = ethPip.verifyTransaction(saplObject, null).blockFirst().asBoolean();
		assertFalse("Transaction was not validated as false although the TransactionHash was false.", result);

	}

	@Test
	public void verifyTransactionShouldReturnFalseWithNullInput() throws IOException {
		when(web3j.ethGetTransactionByHash(TEST_TRANSACTION_HASH).send()).thenReturn(ethTransaction);
		when(transactionFromChain.getValue()).thenReturn(TEST_TRANSACTION_VALUE);
		boolean result = ethPip.verifyTransaction(null, null).blockFirst().asBoolean();
		assertFalse("Transaction was not validated as false although the input was null.", result);

	}

	@Test
	public void verifyTransactionShouldReturnFalseWithWrongInput() throws IOException {
		when(web3j.ethGetTransactionByHash(TEST_TRANSACTION_HASH).send()).thenReturn(ethTransaction);
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(WRONG_NAME, TEST_TRANSACTION_HASH);
		saplObject.put(FROM_ACCOUNT, TEST_FROM_ACCOUNT);
		saplObject.put(TO_ACCOUNT, TEST_TO_ACCOUNT);
		saplObject.put(TRANSACTION_VALUE, TEST_TRANSACTION_VALUE);
		boolean result = ethPip.verifyTransaction(saplObject, null).blockFirst().asBoolean();
		assertFalse("Transaction was not validated as false although the input was erroneous.", result);

	}

	// loadContractInformation

	@Test
	public void loadContractInformationShouldReturnCorrectValue() throws IOException, ClassNotFoundException {
		JsonNode saplObject = createSaplObject();
		org.web3j.protocol.core.methods.request.Transaction transaction = org.web3j.protocol.core.methods.request.Transaction
				.createEthCallTransaction(null, TEST_DATA_LCI_CONTRACT_ADDRESS, TEST_DATA_LCI_ENCODED_FUNCTION);
		DefaultBlockParameter dbp = DefaultBlockParameter.valueOf(LATEST);

		mockStatic(org.web3j.protocol.core.methods.request.Transaction.class);
		when(org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(null,
				TEST_DATA_LCI_CONTRACT_ADDRESS, TEST_DATA_LCI_ENCODED_FUNCTION)).thenReturn(transaction);
		when(web3j.ethCall(transaction, dbp).send().getValue()).thenReturn(TEST_DATA_LCI_RESULT);

		JsonNode result = ethPip.loadContractInformation(saplObject, null).blockFirst();

		assertTrue("False was returned although user2 was authorized and result should have been true.",
				result.get(0).get(VALUE).asBoolean());

	}

	// clientVersion

	@Test
	public void web3ClientVersionShouldReturnTheClientVersion() throws IOException {
		when(web3j.web3ClientVersion().send().getWeb3ClientVersion()).thenReturn(TEST_DATA_CLIENT_VERSION);
		String pipResult = ethPip.web3ClientVersion(null, null).blockFirst().asText();
		assertEquals("The web3ClientVersion from the PIP was not loaded correctly.", TEST_DATA_CLIENT_VERSION,
				pipResult);
	}

	// sha3
	@Test
	public void web3Sha3ShouldReturnCorrectValuer() throws IOException {
		when(web3j.web3Sha3(USER3_PRIVATE_KEY).send().getResult()).thenReturn(TEST_DATA_SHA3_RESULT);
		JsonNode saplObject = JSON.textNode(USER3_PRIVATE_KEY);
		String pipResult = ethPip.web3Sha3(saplObject, null).blockFirst().textValue();
		assertEquals("The web3Sha3 method did not work correctly.", TEST_DATA_SHA3_RESULT, pipResult);
	}

	// netVersion
	@Test
	public void netVersionShouldReturnCorrectValue() throws IOException {
		String version = "2018";
		when(web3j.netVersion().send().getNetVersion()).thenReturn(version);
		String pipResult = ethPip.netVersion(null, null).blockFirst().textValue();
		assertEquals("The netVersion method did not work correctly.", version, pipResult);

	}

	// listening
	@Test
	public void netListeningShouldReturnTrueWhenListeningToNetworkConnections() throws IOException {
		when(web3j.netListening().send().isListening()).thenReturn(true);
		assertTrue("The netListening method did not return true although the Client by default is listening.",
				ethPip.netListening(null, null).blockFirst().asBoolean());
	}

	// peerCount
	@Test
	public void netPeerCountShouldReturnTheCorrectNumber() throws IOException {
		BigInteger peerCount = BigInteger.valueOf(5L);
		when(web3j.netPeerCount().send().getQuantity()).thenReturn(peerCount);

		BigInteger pipResult = ethPip.netPeerCount(null, null).blockFirst().bigIntegerValue();

		assertEquals("The netPeerCount method did not return the correct number.", peerCount, pipResult);
	}

	// protocolVersion
	@Test
	public void protocolVersionShouldReturnTheCorrectValue() throws IOException {
		String protocolVersion = "0x3f";
		when(web3j.ethProtocolVersion().send().getProtocolVersion()).thenReturn(protocolVersion);
		String pipResult = ethPip.ethProtocolVersion(null, null).blockFirst().textValue();
		assertEquals("The ethProtocolVersion method did not return the correct value.", protocolVersion, pipResult);
	}

	// syncing
	@Test
	public void ethSyncingShouldReturnTheCorrectValue() throws IOException {
		when(web3j.ethSyncing().send().isSyncing()).thenReturn(true);
		boolean pipResult = ethPip.ethSyncing(null, null).blockFirst().asBoolean();
		assertTrue("The ethSyncing method did not return the correct value.", pipResult);
	}

	// coinbase
	@Test
	public void ethCoinbaseShouldReturnTheCorrectValue() throws IOException {
		when(web3j.ethCoinbase().send().getResult()).thenReturn(USER1_ADDRESS);
		String pipResult = ethPip.ethCoinbase(null, null).blockFirst().textValue();

		assertEquals("The ethCoinbase method did not return the correct value.", USER1_ADDRESS, pipResult);
	}

	// mining
	@Test
	public void ethMiningShouldReturnTheCorrectValue() throws IOException {
		when(web3j.ethMining().send().isMining()).thenReturn(true);
		boolean pipResult = ethPip.ethMining(null, new HashMap<>()).blockFirst().asBoolean();
		assertTrue("The ethMining method did not return the correct value.", pipResult);
	}

	// hashrate
	@Test
	public void ethHashrateShouldReturnTheCorrectValue() throws IOException {
		BigInteger testValue = BigInteger.valueOf(267L);
		HashMap<String, JsonNode> map = new HashMap<>();
		map.put(ETH_POLLING_INTERVAL, JSON.numberNode(1000L));

		when(web3j.ethHashrate().send().getHashrate()).thenReturn(testValue);

		BigInteger pipResult = ethPip.ethHashrate(null, map).blockFirst().bigIntegerValue();

		assertEquals("The ethHashrate should be returned correctly.", testValue, pipResult);
	}

	// gasPrice
	@Test
	public void ethGasPriceShouldReturnTheCorrectValue() throws IOException {
		BigInteger gasPrice = BigInteger.valueOf(1000L);
		when(web3j.ethGasPrice().send().getGasPrice()).thenReturn(gasPrice);
		BigInteger pipResult = ethPip.ethGasPrice(null, null).blockFirst().bigIntegerValue();

		assertEquals("The ethGasPrice method did not return the correct number.", gasPrice, pipResult);
	}

	// accounts
	@Test
	public void ethAccountsShouldReturnTheCorrectValue() throws IOException {
		List<String> accountList = Arrays.asList(USER1_ADDRESS, USER2_ADDRESS, USER3_ADDRESS);
		when(web3j.ethAccounts().send().getAccounts()).thenReturn(accountList);

		List<JsonNode> result = new ArrayList<JsonNode>();
		ethPip.ethAccounts(null, null).blockFirst().elements().forEachRemaining(result::add);
		List<String> pipResult = result.stream().map(s -> s.textValue()).collect(Collectors.toList());

		assertEquals("The accounts method did not return the correct accounts.", accountList, pipResult);
	}

	// blockNumber
	@Test
	public void ethBlockNumberShouldReturnTheCorrectValue() throws IOException {
		BigInteger blockNumber = BigInteger.valueOf(3002L);
		when(web3j.ethBlockNumber().send().getBlockNumber()).thenReturn(blockNumber);
		BigInteger pipResult = ethPip.ethBlockNumber(null, null).blockFirst().bigIntegerValue();

		assertEquals("The ethBlockNumber method did not return the correct value.", blockNumber, pipResult);
	}

	// balance
	@Test
	public void ethGetBalanceShouldReturnTheCorrectValue() throws IOException {
		BigInteger balance = new BigInteger("5436700000000000000000");
		when(web3j.ethGetBalance(USER1_ADDRESS, DefaultBlockParameter.valueOf(LATEST)).send().getBalance())
				.thenReturn(balance);

		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(ADDRESS, USER1_ADDRESS);
		saplObject.put(DEFAULT_BLOCK_PARAMETER, LATEST);
		JsonNode pipRes = ethPip.ethGetBalance(saplObject, null).blockFirst();
		BigInteger pipResult = pipRes.bigIntegerValue();

		assertEquals("The ethGetBalance method did not return the correct value.", balance, pipResult);
	}

	// storage
	@Test
	public void ethGetStorageAtShouldReturnTheCorrectValue() throws IOException {
		when(web3j.ethGetStorageAt(TEST_DATA_STORAGE_ADDRESS, BigInteger.ZERO, DefaultBlockParameter.valueOf(LATEST))
				.send().getData()).thenReturn(TEST_DATA_STORAGE_RETURN_VALUE);

		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(ADDRESS, TEST_DATA_STORAGE_ADDRESS);
		saplObject.put(POSITION, BigInteger.ZERO);
		saplObject.put(DEFAULT_BLOCK_PARAMETER, LATEST);
		String pipResult = ethPip.ethGetStorageAt(saplObject, null).blockFirst().textValue();

		assertEquals("The ethGetStorageAt method did not return the correct value.", TEST_DATA_STORAGE_RETURN_VALUE,
				pipResult);
	}

	// transactionCount
	@Test
	public void ethGetTransactionCountShouldReturnTheCorrectValue() throws IOException {
		BigInteger transactionCount = BigInteger.valueOf(5L);
		when(web3j.ethGetTransactionCount(USER1_ADDRESS, DefaultBlockParameter.valueOf(LATEST)).send()
				.getTransactionCount()).thenReturn(transactionCount);

		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(ADDRESS, USER1_ADDRESS);
		saplObject.put(DEFAULT_BLOCK_PARAMETER, LATEST);
		BigInteger pipResult = ethPip.ethGetTransactionCount(saplObject, null).blockFirst().bigIntegerValue();

		assertEquals("The ethGetTransactionCount method did not return the correct value.", transactionCount,
				pipResult);
	}

	// blockTransactionCountByHash
	@Test
	public void ethGetBlockTransactionCountByHashShouldReturnTheCorrectValue() throws IOException {
		BigInteger count = BigInteger.valueOf(20L);
		when(web3j.ethGetBlockTransactionCountByHash(TEST_DATA_BLOCKHASH).send().getTransactionCount())
				.thenReturn(count);

		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(BLOCK_HASH, TEST_DATA_BLOCKHASH);
		BigInteger pipResult = ethPip.ethGetBlockTransactionCountByHash(saplObject, null).blockFirst()
				.bigIntegerValue();

		assertEquals("The ethGetBlockTransactionCountByHash method did not return the correct value.", count,
				pipResult);
	}

	// blockTransactionCountByNumber
	@Test
	public void ethGetBlockTransactionCountByNumberShouldReturnTheCorrectValue() throws IOException {
		BigInteger blockNumber = BigInteger.valueOf(2314L);
		BigInteger count = BigInteger.valueOf(77L);
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(DEFAULT_BLOCK_PARAMETER, blockNumber);
		DefaultBlockParameter dbp = DefaultBlockParameter.valueOf(blockNumber);

		mockStatic(EthereumPipFunctions.class);
		when(EthereumPipFunctions.getDefaultBlockParameter(saplObject)).thenReturn(dbp);
		when(web3j.ethGetBlockTransactionCountByNumber(dbp).send().getTransactionCount()).thenReturn(count);

		BigInteger pipResult = ethPip.ethGetBlockTransactionCountByNumber(saplObject, null).blockFirst()
				.bigIntegerValue();

		assertEquals("The ethGetBlockTransactionCountByNumber method did not return the correct value.", count,
				pipResult);
	}

	// uncleCountByBlockHash
	@Test
	public void ethGetUncleCountByBlockHashShouldReturnTheCorrectValue() throws IOException {
		when(web3j.ethGetUncleCountByBlockHash(TEST_DATA_BLOCKHASH).send().getUncleCount()).thenReturn(BigInteger.ONE);

		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(BLOCK_HASH, TEST_DATA_BLOCKHASH);
		BigInteger pipResult = ethPip.ethGetUncleCountByBlockHash(saplObject, null).blockFirst().bigIntegerValue();

		assertEquals("The ethGetUncleCountByBlockHash method did not return the correct value.", BigInteger.ONE,
				pipResult);
	}

	// uncleCountByBlockNumber
	@Test
	public void ethGetUncleCountByBlockNumberShouldReturnTheCorrectValue() throws IOException {
		BigInteger blockNumber = BigInteger.valueOf(46213L);
		BigInteger uncleCount = BigInteger.TEN;
		DefaultBlockParameter dbp = DefaultBlockParameter.valueOf(blockNumber);
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(DEFAULT_BLOCK_PARAMETER, blockNumber);

		mockStatic(EthereumPipFunctions.class);
		when(EthereumPipFunctions.getDefaultBlockParameter(saplObject)).thenReturn(dbp);
		when(web3j.ethGetUncleCountByBlockNumber(dbp).send().getUncleCount()).thenReturn(uncleCount);

		BigInteger pipResult = ethPip.ethGetUncleCountByBlockNumber(saplObject, null).blockFirst().bigIntegerValue();

		assertEquals("The ethGetUncleCountByBlockNumber method did not return the correct value.", uncleCount,
				pipResult);

	}

	// code
	@Test
	public void ethGetCodeShouldReturnTheCorrectValue() throws IOException {
		when(web3j.ethGetCode(TEST_DATA_CONTRACT_ADDRESS, DefaultBlockParameter.valueOf(LATEST)).send().getCode())
				.thenReturn(TEST_DATA_CODE_RETURN);

		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(ADDRESS, TEST_DATA_CONTRACT_ADDRESS);
		String pipResult = ethPip.ethGetCode(saplObject, null).blockFirst().textValue();

		assertEquals("The ethGetCode method did not return the correct value.", TEST_DATA_CODE_RETURN, pipResult);
	}

	// call
	@Test
	public void ethCallShouldReturnTheCorrectValue() throws IOException, ClassNotFoundException {
		org.web3j.protocol.core.methods.request.Transaction transaction = createTestRequestTransaction();
		ObjectNode saplObject = JSON.objectNode();
		saplObject.set(TRANSACTION, mapper.convertValue(transaction, JsonNode.class));
		DefaultBlockParameter dbp = DefaultBlockParameter.valueOf(LATEST);

		mockStatic(EthereumPipFunctions.class);
		when(EthereumPipFunctions.getDefaultBlockParameter(saplObject)).thenReturn(dbp);
		when(EthereumPipFunctions.getTransactionFromJson(saplObject.get(TRANSACTION))).thenReturn(transaction);
		when(web3j.ethCall(transaction, dbp).send().getValue()).thenReturn(TEST_DATA_CALL_RETURN_VALUE);

		String pipResult = ethPip.ethCall(saplObject, null).blockFirst().textValue();
		assertEquals("The ethCall method did not return the correct value.", TEST_DATA_CALL_RETURN_VALUE, pipResult);
	}

	// estimateGas
	@Test
	public void ethEstimateGasCodeShouldReturnTheCorrectValue() throws IOException, ClassNotFoundException {
		org.web3j.protocol.core.methods.request.Transaction transaction = createTestRequestTransaction();
		BigInteger gas = BigInteger.valueOf(23300L);
		ObjectNode saplObject = JSON.objectNode();
		saplObject.set(TRANSACTION, mapper.convertValue(transaction, JsonNode.class));

		mockStatic(EthereumPipFunctions.class);
		when(EthereumPipFunctions.getTransactionFromJson(saplObject.get(TRANSACTION))).thenReturn(transaction);
		when(web3j.ethEstimateGas(transaction).send().getAmountUsed()).thenReturn(gas);

		BigInteger pipResult = ethPip.ethEstimateGas(saplObject, null).blockFirst().bigIntegerValue();

		assertEquals("The ethEstimateGas method did not return the correct value.", gas, pipResult);
	}

	// blockByHash
	@Test
	public void ethGetBlockByHashShouldReturnTheCorrectValue() throws IOException {
		Block testBlock = createTestBlock();
		when(web3j.ethGetBlockByHash(TEST_DATA_BLOCKHASH, false).send().getBlock()).thenReturn(testBlock);

		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(BLOCK_HASH, TEST_DATA_BLOCKHASH);
		saplObject.put(RETURN_FULL_TRANSACTION_OBJECTS, false);
		String pipResult = ethPip.ethGetBlockByHash(saplObject, null).blockFirst().toString();

		assertEquals("The ethGetBlockByHash method did not return the correct value.",
				mapper.convertValue(testBlock, JsonNode.class).toString(), pipResult);
	}

	// blockByNumber
	@Test
	public void ethGetBlockByNumberShouldReturnTheCorrectValue() throws IOException {

		Block testBlock = createTestBlock();
		when(web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(EARLIEST), false).send().getBlock())
				.thenReturn(testBlock);

		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(DEFAULT_BLOCK_PARAMETER, EARLIEST);
		saplObject.put(RETURN_FULL_TRANSACTION_OBJECTS, false);
		JsonNode pipResult = ethPip.ethGetBlockByNumber(saplObject, null).blockFirst();

		assertEquals("The ethGetBlockByNumber method did not return the correct value.",
				mapper.convertValue(testBlock, JsonNode.class), pipResult);
	}

	// transactionByHash
	@Test
	public void ethGetTransactionByHashShouldReturnTheCorrectValue() throws IOException {
		Transaction testTransaction = createTestResponseTransaction();
		when(web3j.ethGetTransactionByHash(TEST_DATA_TRANSACTION_HASH).send().getResult()).thenReturn(testTransaction);

		JsonNode saplObject = JSON.textNode(TEST_DATA_TRANSACTION_HASH);
		JsonNode pipResult = ethPip.ethGetTransactionByHash(saplObject, null).blockFirst();

		assertEquals("The ethGetTransactionByHash method did not return the correct value.",
				mapper.convertValue(testTransaction, JsonNode.class), pipResult);
	}

	// transactionByBlockHashAndIndex
	@Test
	public void ethGetTransactionByBlockHashAndIndexShouldReturnTheCorrectValue() throws IOException {
		Transaction testTransaction = createTestResponseTransaction();
		BigInteger index = BigInteger.ONE;
		when(web3j.ethGetTransactionByBlockHashAndIndex(TEST_DATA_BLOCKHASH, index).send().getResult())
				.thenReturn(testTransaction);

		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(BLOCK_HASH, TEST_DATA_BLOCKHASH);
		saplObject.put(TRANSACTION_INDEX, index);

		JsonNode pipResult = ethPip.ethGetTransactionByBlockHashAndIndex(saplObject, null).blockFirst();

		assertEquals("The ethGetTransactionByBlockHashAndIndex method did not return the correct value.",
				mapper.convertValue(testTransaction, JsonNode.class), pipResult);
	}

	// transactionByBlockNumberAndIndex
	@Test
	public void ethGetTransactionByBlockNumberAndIndexShouldReturnTheCorrectValue() throws IOException {
		Transaction testTransaction = createTestResponseTransaction();
		BigInteger blockNumber = BigInteger.valueOf(458L);
		BigInteger index = BigInteger.ONE;
		DefaultBlockParameter dbp = DefaultBlockParameter.valueOf(blockNumber);
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(DEFAULT_BLOCK_PARAMETER, blockNumber);
		saplObject.put(TRANSACTION_INDEX, index);

		mockStatic(EthereumPipFunctions.class);
		when(EthereumPipFunctions.getDefaultBlockParameter(saplObject)).thenReturn(dbp);
		when(web3j.ethGetTransactionByBlockNumberAndIndex(dbp, index).send().getResult()).thenReturn(testTransaction);

		JsonNode pipResult = ethPip.ethGetTransactionByBlockNumberAndIndex(saplObject, null).blockFirst();

		assertEquals("The ethGetTransactionByBlockNumberAndIndex method did not return the correct value.",
				mapper.convertValue(testTransaction, JsonNode.class), pipResult);
	}

	// transactionReceipt
	@Test
	public void ethGetTransactionReceiptShouldReturnTheCorrectValue() throws IOException {
		TransactionReceipt receipt = createTestReceipt();
		when(web3j.ethGetTransactionReceipt(TEST_DATA_RECEIPT_TRANSACTION_HASH).send().getResult()).thenReturn(receipt);

		JsonNode saplObject = JSON.textNode(TEST_DATA_RECEIPT_TRANSACTION_HASH);
		JsonNode pipResult = ethPip.ethGetTransactionReceipt(saplObject, null).blockFirst();

		assertEquals("The ethGetTransactionReceipt method did not return the correct value.",
				mapper.convertValue(receipt, JsonNode.class), pipResult);
	}

	// pendingTransactions
	@Test
	public void ethPendingTransactionsShouldReturnTheCorrectValue() {
		when(web3j.ethPendingTransactionHashFlowable())
				.thenReturn(Flowable.fromArray(TEST_DATA_TRANSACTION_HASH, TEST_DATA_TRANSACTION_HASH_2));

		String pipResult = ethPip.ethPendingTransactions(null, null).blockFirst().textValue();

		assertEquals("The ethPendingTransactions method did not return the correct value.", TEST_DATA_TRANSACTION_HASH,
				pipResult);
	}

	// uncleByBlockHashAndIndex
	@Test
	public void ethGetUncleByBlockHashAndIndexShouldReturnTheCorrectValue() throws IOException {
		BigInteger index = BigInteger.ZERO;
		Block testBlock = createTestBlock();
		when(web3j.ethGetUncleByBlockHashAndIndex(TEST_DATA_BLOCKHASH, index).send().getBlock()).thenReturn(testBlock);

		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(BLOCK_HASH, TEST_DATA_BLOCKHASH);
		saplObject.put(UNCLE_INDEX, index);

		JsonNode pipResult = ethPip.ethGetUncleByBlockHashAndIndex(saplObject, null).blockFirst();

		assertEquals("The ethGetUncleByBlockHashAndIndex method did not return the correct value.",
				mapper.convertValue(testBlock, JsonNode.class), pipResult);
	}

	// uncleByBlockNumberAndIndex
	@Test
	public void ethGetUncleByBlockNumberAndIndexShouldReturnTheCorrectValue() throws IOException {
		BigInteger index = BigInteger.ONE;
		Block testBlock = createTestBlock();
		DefaultBlockParameter dbp = DefaultBlockParameter.valueOf(PENDING);
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(DEFAULT_BLOCK_PARAMETER, PENDING);
		saplObject.put(UNCLE_INDEX, index);

		mockStatic(EthereumPipFunctions.class);
		when(EthereumPipFunctions.getDefaultBlockParameter(saplObject)).thenReturn(dbp);
		when(web3j.ethGetUncleByBlockNumberAndIndex(dbp, index).send().getBlock()).thenReturn(testBlock);

		JsonNode pipResult = ethPip.ethGetUncleByBlockNumberAndIndex(saplObject, null).blockFirst();

		assertEquals("The ethGetUncleByBlockNumberAndIndex method did not return the correct value.",
				mapper.convertValue(testBlock, JsonNode.class), pipResult);
	}

	// ethFilterChanges
	@Test
	public void ethGetFilterChangesShouldReturnTheCorrectValue() throws IOException {

		BigInteger filterId = BigInteger.valueOf(15L);
		when(web3j.ethGetFilterChanges(filterId).send().getLogs()).thenReturn(Arrays.asList(createLogObject()));

		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(FILTER_ID, filterId);
		JsonNode pipList = ethPip.ethGetFilterChanges(saplObject, null).blockFirst();
		List<String> pipResult = new ArrayList<>();
		for (JsonNode json : pipList) {
			pipResult.add(json.toString());
		}

		List<String> logStringList = Arrays.asList(mapper.convertValue(createLogObject(), JsonNode.class).toString());

		assertEquals("The ethGetFilterChanges method did not return the correct value.", logStringList, pipResult);
	}

	// ethFilterLogs
	@Test
	public void ethGetFilterLogsShouldReturnTheCorrectValue() throws IOException {

		BigInteger filterId = BigInteger.valueOf(22L);
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(FILTER_ID, filterId);

		when(web3j.ethGetFilterLogs(filterId).send().getLogs()).thenReturn(Arrays.asList(createLogObject()));

		JsonNode pipList = ethPip.ethGetFilterLogs(saplObject, null).blockFirst();
		List<String> pipResult = new ArrayList<>();
		for (JsonNode json : pipList) {
			pipResult.add(json.toString());
		}

		List<String> logStringList = Arrays.asList(mapper.convertValue(createLogObject(), JsonNode.class).toString());

		assertEquals("The ethGetFilterLogs method did not return the correct value.", logStringList, pipResult);
	}

	// logs
	@Test
	public void ethGetLogsShouldReturnTheCorrectValue() throws IOException {
		EthFilter filter = getTestFilter();
		JsonNode saplObject = mapper.convertValue(filter, JsonNode.class);

		mockStatic(EthereumPipFunctions.class);
		when(EthereumPipFunctions.getEthFilterFrom(saplObject)).thenReturn(filter);
		when(web3j.ethGetLogs(filter).send().getLogs()).thenReturn(Arrays.asList(createLogObject()));

		JsonNode pipList = ethPip.ethGetLogs(saplObject, null).blockFirst();
		List<String> pipResult = new ArrayList<>();
		for (JsonNode json : pipList) {
			pipResult.add(json.toString());
		}

		List<String> logStringList = Arrays.asList(mapper.convertValue(createLogObject(), JsonNode.class).toString());

		assertEquals("The ethGetFilterLogs method did not return the correct value.", logStringList, pipResult);
	}

	// sign
	@Test
	public void ethSignShouldReturnTheCorrectValue() throws IOException {
		when(web3j.ethSign(TEST_DATA_SIGN_ADDRESS, TEST_DATA_SIGN_MESSAGE).send().getSignature())
				.thenReturn(TEST_DATA_SIGN_RESULT);

		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(ADDRESS, TEST_DATA_SIGN_ADDRESS);
		saplObject.put(SHA3_HASH_OF_DATA_TO_SIGN, TEST_DATA_SIGN_MESSAGE);
		String pipResult = ethPip.ethSign(saplObject, null).blockFirst().textValue();
		assertEquals("The ethSign method did not return the correct value.", TEST_DATA_SIGN_RESULT, pipResult);
	}

	// shhFilterChanges
	@Test
	public void shhFilterChangesShouldReturnCorrectValue() throws IOException {

		BigInteger filterId = BigInteger.valueOf(7L);
		List<SshMessage> sshList = new ArrayList<>();
		sshList.add(createTestMessage());

		when(web3j.shhGetFilterChanges(filterId).send().getMessages()).thenReturn(sshList);

		JsonNode saplObject = JSON.numberNode(filterId);
		JsonNode pipList = ethPip.shhGetFilterChanges(saplObject, null).blockFirst();
		List<String> pipResult = new ArrayList<>();
		for (JsonNode json : pipList) {
			pipResult.add(json.toString());
		}

		List<String> sshStringList = new ArrayList<>();
		for (SshMessage sshMessage : sshList) {
			sshStringList.add(mapper.convertValue(sshMessage, JsonNode.class).toString());
		}
		assertEquals("The shhGetFilterChanges method did not work correctly.", sshStringList, pipResult);

	}

	// messages
	@Test
	public void shhGetMessagesShouldReturnCorrectValue() throws IOException {

		BigInteger filterId = BigInteger.valueOf(7L);
		List<SshMessage> sshList = new ArrayList<>();
		sshList.add(createTestMessage());

		when(web3j.shhGetMessages(filterId).send().getMessages()).thenReturn(sshList);

		JsonNode saplObject = JSON.numberNode(filterId);
		JsonNode pipList = ethPip.shhGetMessages(saplObject, null).blockFirst();
		List<String> pipResult = new ArrayList<>();
		for (JsonNode json : pipList) {
			pipResult.add(json.toString());
		}

		List<String> sshStringList = new ArrayList<>();
		for (SshMessage sshMessage : sshList) {
			sshStringList.add(mapper.convertValue(sshMessage, JsonNode.class).toString());
		}
		assertEquals("The shhGetMessages method did not work correctly.", sshStringList, pipResult);

	}

	// shhVersion
	@Test
	public void shhVersionShouldReturnCorrectValue() throws IOException {
		when(web3j.shhVersion().send().getVersion()).thenReturn(TEST_DATA_SHH_VERSION);
		String pipResult = ethPip.shhVersion(null, null).blockFirst().textValue();
		assertEquals("The shhVersion method did not work correctly.", TEST_DATA_SHH_VERSION, pipResult);

	}

	// work
	@Test
	public void ethGetWorkShouldReturnCorrectValue() throws IOException {
		List<String> ethWorkList = getEthWorkList();
		when(web3j.ethGetWork().send().getResult()).thenReturn(ethWorkList);

		JsonNode pipList = ethPip.ethGetWork(null, null).blockFirst();
		List<String> pipResult = new ArrayList<>();
		for (JsonNode json : pipList) {
			pipResult.add(json.textValue());
		}
		assertEquals("The ethGetWork method did not work correctly.", ethWorkList, pipResult);

	}

	// hasIdentity
	@Test
	public void shhHasIdentityShouldReturnCorrectValue() throws IOException {

		JsonNode saplObject = JSON.textNode(TEST_DATA_HAS_IDENTITY);

		when(web3j.shhHasIdentity(TEST_DATA_HAS_IDENTITY).send().getResult()).thenReturn(true);

		boolean pipResult = ethPip.shhHasIdentity(saplObject, null).blockFirst().asBoolean();
		assertTrue("The shhHasIdentity method did not work correctly.", pipResult);

	}

	private static SshMessage createTestMessage() {
		return new SshMessage("0x33eb2da77bf3527e28f8bf493650b1879b08c4f2a362beae4ba2f71bafcd91f9",
				"0xc931d93e97ab07fe42d923478ba2465f283f440fd6cabea4dd7a2c807108f651b7135d1d6ca9007d5b68aa497e4619ac10aa3b27726e1863c1fd9b570d99bbaf",
				"0x04f96a5e25610293e42a73908e93ccc8c4d4dc0edcfa9fa872f50cb214e08ebf61a03e245533f97284d442460f2998cd41858798ddfd4d661997d3940272b717b1",
				"0x54caa50a", "0x64", "0x54ca9ea2", Arrays.asList("0x6578616d"), "0x12345678", "0x0");
	}

	private static LogObject createLogObject() {
		return new LogObject(false, "0x1", "0x0", "0xdf829c5a142f1fccd7d8216c5785ac562ff41e2dcfdf5785ac562ff41e2dcf",
				"0x8216c5785ac562ff41e2dcfdf5785ac562ff41e2dcfdf829c5a142f1fccd7d", "0x1b4",
				"0x16c5785ac562ff41e2dcfdf829c5a142f1fccd7d",
				"0x0000000000000000000000000000000000000000000000000000000000000000", "0x0",
				Arrays.asList("0x59ebeb90bc63057b6515673c3ecf9438e5058bca0f92585014eced636878c9a5"));
	}

	private static List<String> getEthWorkList() {
		return Arrays.asList("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
				"0x5EED00000000000000000000000000005EED0000000000000000000000000000",
				"0xd1ff1c01710000000000000000000000d1ff1c01710000000000000000000000");
	}

	private static Block createTestBlock() {
		List<String> stringList = new ArrayList<String>();
		return new Block("0x512349", TEST_DATA_BLOCKHASH,
				"0x752f55ad698ae0c96f5d0d78038e755c5d2c45668c5b54289e4d317a80ab8e2b", "0x14359298949513203460",
				"0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347",
				"0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
				"0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421",
				"0x344f2ffa4934f578489ca6c2f2c0fe6372dbe6cb10f9875e8bed4ad3e3a95a04",
				"0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421", null,
				"0xfe3b557e8fb62b89f4916b721be55ceb828dbd73", null, "0x100", "0x128536", "0x", "0x514",
				"0x9007199254740991", "0x0", "0x1578657799",
				Arrays.asList(createTransactionObject()).stream().collect(Collectors.toList()), stringList, null);
	}

	private static TransactionObject createTransactionObject() {
		return new TransactionObject("0x1ef9c4f12d3d8cd99e7cd5b9dd59d73d89519f70b2f4bd0fcbb3f8374f07201a", "0x8",
				TEST_DATA_BLOCKHASH, "0x512349", "0x0", USER1_ADDRESS, USER2_ADDRESS, "0x1578657799", "0x1000",
				"0x21000", "0x", null, null, null, "0x85d7e7ddef29b37760bd3098aa6f9bba0471befcaf133151374ccc9ed69242a",
				"0x1e80b9c2e381c6ddca0da30e69f1f9bee1ec57ae69b6c82e5e829f0c41844d6b", 28);
	}

	private static Transaction createTestResponseTransaction() {
		return new Transaction(TEST_DATA_TRANSACTION_HASH, "0x0",
				"0x1ef9c4f12d3d8cd99e7cd5b9dd59d73d89519f70b2f4bd0fcbb3f8374f07201a", "0x8", "0x0",
				"0xfe3b557e8fb62b89f4916b721be55ceb828dbd73", "0x627306090abab3a6e1400e9345bc60c78a8bef57",
				"0x2000000000000000000", "0x1000", "0x21000", "0x", null, null, null,
				"0x85d7e7ddef29b37760bd3098aa6f9bba0471befcaf133151374ccc9ed69242a",
				"0x1e80b9c2e381c6ddca0da30e69f1f9bee1ec57ae69b6c82e5e829f0c41844d6b", 28L);
	}

	private static org.web3j.protocol.core.methods.request.Transaction createTestRequestTransaction()
			throws ClassNotFoundException {
		List<TypeReference<?>> outputParameters = new ArrayList<>();
		outputParameters.add(TypeReference.makeTypeReference(BOOL));
		List<Type<?>> inputList = new ArrayList<>();
		inputList.add(new Address(USER2_ADDRESS.substring(2)));
		Function function = new Function(IS_AUTHORIZED, inputList.stream().collect(Collectors.toList()),
				outputParameters);
		String encodedFunction = FunctionEncoder.encode(function);
		return org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(USER1_ADDRESS,
				TEST_DATA_CONTRACT_ADDRESS, encodedFunction);
	}

	private static TransactionReceipt createTestReceipt() {
		return new TransactionReceipt(TEST_DATA_RECEIPT_TRANSACTION_HASH, "0x0",
				"0x6c544c576658b51a738245a3a006eca1614336e2cd6c5214da6597a609e1925e", "0x10", "0x21000", "0x21000",
				null, null, "0x1", "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
				"0x627306090abab3a6e1400e9345bc60c78a8bef57", Arrays.asList(createLogObject()),
				"0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
	}

	private static EthFilter getTestFilter() {
		BigInteger fromBlock = BigInteger.valueOf(522L);
		BigInteger toBlock = BigInteger.valueOf(1078L);
		List<String> addresses = Arrays.asList(USER1_ADDRESS, USER2_ADDRESS);
		return new EthFilter(DefaultBlockParameter.valueOf(fromBlock), DefaultBlockParameter.valueOf(toBlock),
				addresses);
	}

	private static JsonNode createSaplObject() {
		ObjectNode saplObject = JSON.objectNode();
		saplObject.put(CONTRACT_ADDRESS, TEST_DATA_LCI_CONTRACT_ADDRESS);
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
		return saplObject;
	}

}
