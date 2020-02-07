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
package io.sapl.interpreter.pip.contracts;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

/**
 * <p>
 * Auto generated code.
 * <p>
 * <strong>Do not modify!</strong>
 * <p>
 * Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line
 * tools</a>, or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the
 * <a href="https://github.com/web3j/web3j/tree/master/codegen">codegen module</a> to
 * update.
 *
 * <p>
 * Generated with web3j version 4.5.0.
 */
public class Authorization extends Contract {

	private static final String BINARY = "608060405234801561001057600080fd5b50600080546001600160a01b031916331790556102c7806100326000396000f3fe608060405234801561001057600080fd5b50600436106100575760003560e01c8063a87430ba1461005c578063b6a5d7de14610096578063f0b37c04146100be578063f851a440146100e4578063fe9fbb8014610108575b600080fd5b6100826004803603602081101561007257600080fd5b50356001600160a01b031661012e565b604080519115158252519081900360200190f35b6100bc600480360360208110156100ac57600080fd5b50356001600160a01b0316610143565b005b6100bc600480360360208110156100d457600080fd5b50356001600160a01b03166101b3565b6100ec61021d565b604080516001600160a01b039092168252519081900360200190f35b6100826004803603602081101561011e57600080fd5b50356001600160a01b031661022c565b60016020526000908152604090205460ff1681565b6000546001600160a01b0316331461018c5760405162461bcd60e51b815260040180806020018281038252602381526020018061024b6023913960400191505060405180910390fd5b6001600160a01b03166000908152600160208190526040909120805460ff19169091179055565b6000546001600160a01b031633146101fc5760405162461bcd60e51b815260040180806020018281038252602581526020018061026e6025913960400191505060405180910390fd5b6001600160a01b03166000908152600160205260409020805460ff19169055565b6000546001600160a01b031681565b6001600160a01b031660009081526001602052604090205460ff169056fe4f6e6c79207468652061646d696e2063616e20617574686f72697a652075736572732e4f6e6c79207468652061646d696e2063616e20756e617574686f72697a652075736572732ea265627a7a723058205e648b3c949b765bf920a00b4306109e0fdb1a2204a85a0c9ed7cf171576562464736f6c63430005090032";

	public static final String FUNC_USERS = "users";

	public static final String FUNC_AUTHORIZE = "authorize";

	public static final String FUNC_UNAUTHORIZE = "unauthorize";

	public static final String FUNC_ADMIN = "admin";

	public static final String FUNC_ISAUTHORIZED = "isAuthorized";

	@Deprecated
	protected Authorization(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice,
			BigInteger gasLimit) {
		super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
	}

	protected Authorization(String contractAddress, Web3j web3j, Credentials credentials,
			ContractGasProvider contractGasProvider) {
		super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
	}

	@Deprecated
	protected Authorization(String contractAddress, Web3j web3j, TransactionManager transactionManager,
			BigInteger gasPrice, BigInteger gasLimit) {
		super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
	}

	protected Authorization(String contractAddress, Web3j web3j, TransactionManager transactionManager,
			ContractGasProvider contractGasProvider) {
		super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
	}

	public RemoteFunctionCall<Boolean> users(String param0) {
		final Function function = new Function(FUNC_USERS,
				Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, param0)),
				Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {
				}));
		return executeRemoteCallSingleValueReturn(function, Boolean.class);
	}

	public RemoteFunctionCall<TransactionReceipt> authorize(String user) {
		final Function function = new Function(FUNC_AUTHORIZE,
				Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, user)),
				Collections.<TypeReference<?>>emptyList());
		return executeRemoteCallTransaction(function);
	}

	public RemoteFunctionCall<TransactionReceipt> unauthorize(String user) {
		final Function function = new Function(FUNC_UNAUTHORIZE,
				Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, user)),
				Collections.<TypeReference<?>>emptyList());
		return executeRemoteCallTransaction(function);
	}

	public RemoteFunctionCall<String> admin() {
		final Function function = new Function(FUNC_ADMIN, Arrays.<Type>asList(),
				Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {
				}));
		return executeRemoteCallSingleValueReturn(function, String.class);
	}

	public RemoteFunctionCall<Boolean> isAuthorized(String user) {
		final Function function = new Function(FUNC_ISAUTHORIZED,
				Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, user)),
				Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {
				}));
		return executeRemoteCallSingleValueReturn(function, Boolean.class);
	}

	@Deprecated
	public static Authorization load(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice,
			BigInteger gasLimit) {
		return new Authorization(contractAddress, web3j, credentials, gasPrice, gasLimit);
	}

	@Deprecated
	public static Authorization load(String contractAddress, Web3j web3j, TransactionManager transactionManager,
			BigInteger gasPrice, BigInteger gasLimit) {
		return new Authorization(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
	}

	public static Authorization load(String contractAddress, Web3j web3j, Credentials credentials,
			ContractGasProvider contractGasProvider) {
		return new Authorization(contractAddress, web3j, credentials, contractGasProvider);
	}

	public static Authorization load(String contractAddress, Web3j web3j, TransactionManager transactionManager,
			ContractGasProvider contractGasProvider) {
		return new Authorization(contractAddress, web3j, transactionManager, contractGasProvider);
	}

	public static RemoteCall<Authorization> deploy(Web3j web3j, Credentials credentials,
			ContractGasProvider contractGasProvider) {
		return deployRemoteCall(Authorization.class, web3j, credentials, contractGasProvider, BINARY, "");
	}

	public static RemoteCall<Authorization> deploy(Web3j web3j, TransactionManager transactionManager,
			ContractGasProvider contractGasProvider) {
		return deployRemoteCall(Authorization.class, web3j, transactionManager, contractGasProvider, BINARY, "");
	}

	@Deprecated
	public static RemoteCall<Authorization> deploy(Web3j web3j, Credentials credentials, BigInteger gasPrice,
			BigInteger gasLimit) {
		return deployRemoteCall(Authorization.class, web3j, credentials, gasPrice, gasLimit, BINARY, "");
	}

	@Deprecated
	public static RemoteCall<Authorization> deploy(Web3j web3j, TransactionManager transactionManager,
			BigInteger gasPrice, BigInteger gasLimit) {
		return deployRemoteCall(Authorization.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, "");
	}

}

