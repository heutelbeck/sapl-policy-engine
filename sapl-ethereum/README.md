# Sapl-Ethereum Documentation

In this documentation you will find an introduction on how to use the **EthereumPolicyInformationPoint (EthPIP)**. This is a general attribute finder created to facilitate access to information from the Ethereum blockchain environment inside Sapl policies. For more information about using a **Policy Decision Point (PDP)** with an attribute finder in general and on how to access it inside a policy please refer to the [SAPL Documentation](https://github.com/heutelbeck/sapl-policy-engine/blob/master/sapl-documentation/src/asciidoc/sapl-reference.adoc). 

## The EthereumPolicyInformationPoint

### Getting started
To use the Ethereum Policy Information Point, you have to include the dependency of the **sapl-ethereum** module in the `pom.xml` of your maven project like this:

```xml
<dependency>
    <groupId>io.sapl</groupId>
    <artifactId>sapl-ethereum</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```
Furthermore you will have to use a PDP to work with. In case you want to use an Embedded PDP you can include the following dependency:

```xml
<dependency>
    <groupId>io.sapl</groupId>
    <artifactId>sapl-pdp-embedded</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

In case you want to run a Server PDP, use the artifactId `sapl-pdp-server` instead.

Now you can include your attribute finder with your PDP in your application. If you have an Ethereum node running on your system and don't need any additional configuration this could look like that:

```java

EthereumPolicyInformationPoint ethPip = new EthereumPolicyInformationPoint();
EmbeddedPolicyDecisionPoint pdp = EmbeddedPolicyDecisionPoint.builder()
				.withFilesystemPDPConfigurationProvider("/PATH/TO/CONFIGURATION")
				.withFilesystemPolicyRetrievalPoint("/PATH/TO/POLICIES", IndexType.SIMPLE)
				.withPolicyInformationPoint(ethPip).build();
```

Please note that you can also provide a `Web3j` which defines the way the EthPIP accesses to the blockchain. If you don't do so, a default `HttpService` is used which should automatically detect a running node on your system. If you have a special situation you might want to do something like this with your own specific setup of the `Web3j`:

```java
Web3j web3j = Web3j.build(new HttpService("http://localhost:8545"), 500, Async.defaultExecutorService());
EthereumPolicyInformationPoint ethPip = new EthereumPolicyInformationPoint(web3j);

```

### Getting started with Spring Boot
You can autoconfigure the use of your Policy Decision Point and your Ethereum PIP when using Spring Boot. To do so, just add the following dependencies:

```xml
<dependency>
	<groupId>io.sapl</groupId>
	<artifactId>sapl-spring-security</artifactId>
   <version>2.0.0-SNAPSHOT</version>
</dependency>
<dependency>
	<groupId>io.sapl</groupId>
	artifactId>sapl-spring-pdp-embedded</artifactId>
	<version>2.0.0-SNAPSHOT</version>
</dependency>
<dependency>
      <groupId>io.sapl</groupId>
      <artifactId>sapl-ethereum</artifactId>
      <version>2.0.0-SNAPSHOT</version>
</dependency>
```

If you need a special configuration for your Web3j, you can just add a Web3j Bean to your Spring Boot Application. The Ethereum PIP will automatically use the Web3j from your application.

```java
@Bean
public Web3j web3j() {
	return Web3j.build(new HttpService("http://localhost:7545"));
}
```

### Adjusting the polling interval
Please note that you can define the interval in which the Ethereum PIP requests information from the blockchain. By default an interval of 5 seconds is used, as by now the intermediate time between new blocks on the Ethereum mainnet is at about 12 seconds, rendering it unnecessary to aim for higher accuracy. If you want to adjust this polling interval you can do so in the PDP configuration file `pdp.json`. First add a variable called `ethPipConfig`. Then add a variable with the key `ethPollingInterval` and the time between polls in milliseconds to this config variable:

```json
{
  "algorithm": "DENY_UNLESS_PERMIT",
  "variables": {
                "ethPipConfig": {
                                 "ethPollingInterval":1000
                                 }
  
  }
}
```

## How to access the PIP in a policy
It is quite easy to access the PIP in a policy. The request has to be put in angle brackets with the name of the PIP and the name of the method, separated by a dot. 

```
policy "example_policy"
permit
  action=="anAction" & resource=="someResource"
where
  subject.ethereumAddress.<ethereum.balance> >= 1234567890;
```

The result of the call can be used just like any value in the SAPL policies. For more information about the SAPL policies in general refer to the [SAPL documentation](https://github.com/heutelbeck/sapl-policy-engine/blob/master/sapl-documentation/src/asciidoc/sapl-reference.adoc#policy-structure).


## User-friendly methods
Now we will explain how to use the methods included in this policy information point. In this section we will look at the user friendly methods that don't require deep understanding of the **Web3j API** or the Ethereum blockchain. You can use them by just looking at the code of a smart contract or by using basic information like transaction hashes and addresses. If you have no clue of these things yet, you can start at the official [Ethereum Website](https://ethereum.org/what-is-ethereum/).

If you are already an advanced Ethereum user and want to get even more options to receive information from the blockchain there will be a section with advanced methods later on.


---
#### contract

This function was added to provide a simple, user-friendly way of retreiving information from a contract on the Ethereum Blockchain. It needs to receive a JsonNode with the following information:

 - "fromAccount":  (Optional) The account which the request is send from
 - "contractAddress":    The address of the contract that should be called
 - "functionName": The name of the function that should be called
 - "inputParams":  The type and value of all input params that the called function requires in the same order as in the function declaration
 - "outputParams": The type of all output params that the function returns in the same order as in the function declaration.

All types that can be used are listed in the convertToType-method of the [EthereumPipFunctions](https://github.com/heutelbeck/sapl-policy-engine/blob/sapl-ethereum/sapl-ethereum/src/main/java/io/sapl/interpreter/pip/EthereumPipFunctions.java).

For examples of how to use the types with correct values you can have a look at the [EthereumPipFunctionsTest](https://github.com/heutelbeck/sapl-policy-engine/blob/sapl-ethereum/sapl-ethereum/src/test/java/io/sapl/interpreter/pip/EthereumPipFunctionsTest.java).

 
Let's assume that you want to call the function `hasCertificate` from the following contract:

```solidity
contract Device_Operator_Certificate {

  // The certification authority decides who can issue a certificate
  address public certificationAuthority;

  string public certificate**Name** = "Device_Operator_Certificate";

  uint public timeValid = 365 days;

  struct Certificate {
    bool obtained;
    address issuer;
    uint issueTime;
  }

  // contains true for addresses that are authorized to issue a certificate
  mapping (address => bool) authorizedIssuers;

  // contains all certificates that have been issued
  mapping (address => Certificate) certificateHolders;

  // The creator of the contract is also the certification authority
  constructor() public {
    certificationAuthority = msg.sender;
  }

  function issueCertificate (address graduate) public {
    require(
      authorizedIssuers[msg.sender],
      "Only the authorized issuers can issue certificates."
    );

    certificateHolders[graduate].obtained = true;
    certificateHolders[graduate].issuer = msg.sender;
    // The issue time is the timestamp of the block which contains the
    // transaction that actually issues the certificate
    certificateHolders[graduate].issueTime = block.timestamp;
  }

  function revokeCertificate (address graduate) public {
    require(
      certificateHolders[graduate].issuer == msg.sender,
      "Only the issuer can revoke the certificate."
      );
    certificateHolders[graduate].obtained = false;
  }


  function hasCertificate(address graduate) public view
          returns (bool certificateOwned) {
    // verifies if the certificate is still valid
    // here block.timestamp refers to the timestamp of the block the request
    // is made to (usually the latest)
    if (block.timestamp < certificateHolders[graduate].issueTime + timeValid) {
      return certificateHolders[graduate].obtained;
    }
    return false;
  }

  function addIssuer (address newIssuer) public {
    require(
      msg.sender == certificationAuthority,
      "Only the Certification Authority can name new certificate issuers."
      );
    authorizedIssuers[newIssuer] = true;
  }

  function removeIssuer (address issuerToRemove) public {
    require(
      msg.sender == certificationAuthority,
      "Only the Certification Authority can remove certificate issuers."
      );
    authorizedIssuers[issuerToRemove] = false;
  }

}
```

The contract has been published to the address `0x2d53b58c67ba813c2d1962f8a712ef5533c07c59`.
Furthermore, you want to know if the Ethereum user with the address `3f2cbea2185089ea5bbabbcd7616b215b724885c` has a valid certificate.
In this case your JsonNode should look like that:


```json
{
	"contractAddress":"0x2d53b58c67ba813c2d1962f8a712ef5533c07c59",
	"function**Name**":"hasCertificate",
	"inputParams":[{"type":"address","value":"3f2cbea2185089ea5bbabbcd7616b215b724885c"}],
	"outputParams":["bool"]
}
```

The result will be an ArrayNode with an entry tuple for each returned value. 
Example with one return value of type boolean:

```json
[{"value":true,"typeAsString":"bool"}]
```

Using this in your Application you could have a policy set like this one:

```
set "ethereumPolicies"
deny-unless-permit
//for subject.contractAddress == "0x2d53b58c67ba813c2d1962f8a712ef5533c07c59"
//var certificate_contract = "0x2d53b58c67ba813c2d1962f8a712ef5533c07c59";


policy "test_eth_policy"
permit
  action=="operate" & resource=="device"
where
//  subject.contractAddress == certificate_contract &&
//  subject.function**Name** == "hasCertificate" &&
  subject.<ethereum.contract>[0].value;
```

If you have policies for multiple contracts there are two options (both shown here in the commented sections):
1. You make a new policy set for each contract and mark the policy set with
`for subject.contractAddress == "addressOfTheContract"`
2. If you prefer to keep the policies in the same set you can make a global variable for each contract:
`var contract1 = "addressOfTheContract";` and then you can define the contract the policy belongs to in the where-section:
`subject.contractAddress == contract1`

This scheme is also helpful when calling different functions from a contract.
In this case you would check `subject.function**Name** == "nameOfTheFunction"` in the where-section.


---
#### transaction
This method can be used to see if a transaction was sent and accepted by the network. Therefore, it needs to know the hash, the sender, the recipient and the value of the transaction.

**Input**: The transaction hash, the address of the sender, the address of the recipient and the value of the transaction in wei.

```json
{
 "transactionHash":
 "0xbeac927d1d256e9a21f8d81233cc83c03bf1a7a79a73a4664fa7ffba74101dac",
 "fromAccount":"0x70b6613e37616045a80a97e08e930e1e4d800039",
 "toAccount":"0x3f2cbea2185089ea5bbabbcd7616b215b724885c",
 "transactionValue":2000000000000000000
}
```


**Output**: This method returns true if the transaction has taken place and false otherwise.

## Methods for advanced users
Now we'll come to the methods that bring additional options for getting data from the blockchain. They sometimes need advanced knowledge of Ethereum or the Web3j API, but also many easy-to-use functions can be found here. We will stick to the same grouping the JSON-RPC API uses for these methods.

### Methods from web3
There are only two methods from web3 included here. The method clientVersion simply returns the version of the client and the method sha3 can calculate the keccak-256 hash of a given data.


---
#### clientVersion
**Input**: None.

**Output**: 
The version of the client that the node is running on.

```json
"besu/v1.3.5/linux-x86_64/oracle_openjdk-java-11"
```

---
#### sha3
**Input**: The hex value that should be hashed.

```json
"0xae6ae8e5ccbfb04590405997ee2d52d2b330726137b875053c36d94e974d162f"
```

**Output**: The hash of the given data.

```json
"0x1e8c821d4eb4d148eb679979820c7c3338148b8433b4ffb0b9d713c8f8e33228"
```


### Methods from net
There are three methods returning information about the connection to the network. The method netVersion returns the identification number of the network the client is connected to. Thereby 1 refers to the mainnet, 3 to Ropsten testnet, 4 to Rinkeby testnet and 42 to Kovan testnet. Other network ids can be assigned to private testnets. The method listening only verifies if the client is listening for network connections and the method peerCount returns the number of connected peers.

---
#### netVersion
**Input**: None.

**Output**: The identification number of the network.

```json
"1"
```

---
#### listening
**Input**: None.

**Output**: Returns true if the client is actively listening for network connections and false otherwise.

---
#### peerCount
**Input**: None.

**Output**: The number of peers the client is connected to.

```json
7
```


### Eth methods for general information about the blockchain
As the eth methods are by far the largest section, we will group them by topics in multiple sections. We will start with the methods, that provide general information about the blockchain the client is connected to. These methods are protocolVersion, which returns the current Ethereum protocol used, syncing, which simply states if the client is still syncing with the network and gasPrice, which returns the median gas price of the latest blocks in Wei.

---
#### protocolVersion
**Input**: None.

**Output**: The version of the currently used Ethereum protocol.

```json
"0x3f"
```

---
#### syncing
**Input**: None.

**Output**: True if the client is still syncing with the network and false otherwise.

---
#### gasPrice
**Input**: None.

**Output**: The median price of gas from the last blocks in Wei.

```json
20000
```

### Eth methods for mining
This group includes all methods that most securely are related to mining. They are not very likely to be included in a policy, but still implemented for completion. The first method is coinbase, which returns the address that would receive the mining rewards of the client. Then there is the method mining, which only returns whether the client is actively mining or not. The hashrate method gives information about how many hashes the client processes in its mining per second. Finally, the work method returns information important for mining about the latest block.

---
#### coinbase
**Input**: None.

**Output**: The address of the current coinbase.

```json
"0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"
```

---
####  mining
**Input**: None.

**Output**: True if the client is actively mining and false otherwise.

---
####  hashrate
**Input**: None.

**Output**: The number of hashes per second the client is mining with.

```json
254
```

---
####  work
**Input**: None.

**Output**: Returns information about the latest mined block that can be used for verification, including the block hash, the seed hash and the difficulty.

```json
[
 "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
 "0x5EED00000000000000000000000000005EED0000000000000000000000000000",
 "0xd1ff1c01710000000000000000000000d1ff1c01710000000000000000000000"
]
```


### Eth methods for accounts
This section lists all methods that can give information about accounts. There is the accounts method, that returns a list of all addresses owned by the client. The transactionCount method returns the number of transactions, that have been sent from an account. The balance method returns the balance of a given account.

---
####  accounts
**Input**: None.

**Output**: A list of all accounts owned by the client.

```json
[
 "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
 "0x5EED00000000000000000000000000005EED0000000000000000000000000000",
 "0xd1ff1c01710000000000000000000000d1ff1c01710000000000000000000000"
]
```

---
####  balance
**Input**: The address of the account that should be queried and optionally the default block parameter.

```json
{
 "address":"0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
 "defaultBlockParameter":"latest"
}
```
**Output**: The balance of the account in wei.

```json
5436700000000000000000
```

---
####  transactionCount
**Input**: The address of the account that the transaction count should be retrieved from. Both contract accounts and externally owned accounts can be queried.

```json
{
 "address":"0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
 "defaultBlockParameter":"latest"
}
```


**Output**: The number of transactions sent from the account in the case of an EOA or the number of message calls in the case of a contract account.

```json
121
```


### Eth methods for smart contracts
Apart from the contract method presented above, there are some more useful ways of getting information from a contract. The storage method can be used to access any saved variable from a contract's storage. Calculating the exact position can be complicated and is explained in [JsonRpc]. The code method returns the code of a smart contract. The call method is similar to  the contract method and can be used to get a function result from a smart contract. The difference is, that with call one has to provide a transaction including the encoded function, what is more complicated. Furthermore the result is not being decoded, so the user has to work with the encoded return data.

---
#### storage
**Input**: The address of the contract, the position of the stored value and an optional default block parameter.

```json
{
 "address":"0x9a3dbca554e9f6b9257aaa24010da8377c57c17e",
 "position":0,
 "defaultBlockParameter":"latest"
}
```

**Output**: The stored value in hexadecimal form.

```json
"0x000000000000000000000000fe3b557e8fb62b89f4916b721be55ceb828dbd73"
```

---
#### code
**Input**: The address of the smart contract.

```json
{"address":"0x9a3dbca554e9f6b9257aaa24010da8377c57c17e"}
```

**Output**: The complete code stored for this smart contract as a hex value.

```json
"0x608060405234801561001057600080fd5b50600436106100575760003560e01c8063a87430ba1461005c578063b6a5d7de14610096578063f0b37c04146100be578063f851a440146100e4578063fe9fbb8014610108575b600080fd5b6100826004803603602081101561007257600080fd5b50356001600160a01b031661012e565b604080519115158252519081900360200190f35b6100bc600480360360208110156100ac57600080fd5b50356001600160a01b0316610143565b005b6100bc600480360360208110156100d457600080fd5b50356001600160a01b03166101b3565b6100ec61021d565b604080516001600160a01b039092168252519081900360200190f35b6100826004803603602081101561011e57600080fd5b50356001600160a01b031661022c565b60016020526000908152604090205460ff1681565b6000546001600160a01b0316331461018c5760405162461bcd60e51b815260040180806020018281038252602381526020018061024b6023913960400191505060405180910390fd5b6001600160a01b03166000908152600160208190526040909120805460ff19169091179055565b6000546001600160a01b031633146101fc5760405162461bcd60e51b815260040180806020018281038252602581526020018061026e6025913960400191505060405180910390fd5b6001600160a01b03166000908152600160205260409020805460ff19169055565b6000546001600160a01b031681565b6001600160a01b031660009081526001602052604090205460ff169056fe4f6e6c79207468652061646d696e2063616e20617574686f72697a652075736572732e4f6e6c79207468652061646d696e2063616e20756e617574686f72697a652075736572732ea265627a7a723058205e648b3c949b765bf920a00b4306109e0fdb1a2204a85a0c9ed7cf171576562464736f6c63430005090032"
```

---
#### call
**Input**: The input has to include a node called transaction that contains the sender, the contract and the encoded function that should be called from the contract.

```json
{
 "transaction": 
 {
  "from":"0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
  "to":"0x9a3dbca554e9f6b9257aaa24010da8377c57c17e",
  "data":
  "0xfe9fbb80000000000000000000000000627306090abab3a6e1400e9345bc60c78a8bef57"
 }
}
```

**Output**: The result of the function call encoded as hex value. 

```json
"0x0000000000000000000000000000000000000000000000000000000000000001"
```


### Eth methods for transactions
Now we will look at all methods relating to transactions. The estimateGas method tells how much gas a transaction presumably will require. The sign method calculates an Ethereum specific signature, that is required to send a state changing transaction. It requires that the address to sign with is unlocked in the client. 
The methods that are called transactionByHash, transactionByBlockHashAndIndex and transactionByBlockNumberAndIndex all return a full transaction object with all information about the transaction, while using different input values to do so. The method pendingTransactions returns a list with all transactions, that have been broadcasted in the network, but not mined yet. The method transactionReceipt returns the receipt of a transaction.

---
#### estimateGas
**Input**: A complete transaction object with sender, recipient and data.

```json
{
 "transaction": 
 {
  "from":"0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
  "to":"0x9a3dbca554e9f6b9257aaa24010da8377c57c17e",
  "data":
  "0xfe9fbb80000000000000000000000000627306090abab3a6e1400e9345bc60c78a8bef57"
 }
}
```

**Output**: The quantity in gas that the execution of the transaction will presumably cost.

```json
23300
```


---
#### sign
**Input**: The address that the data should be signed with and the hash of the data that should be signed. Normally used to sign transactions. The address to sign with has to be unlocked in the client.

```json
{
 "address":"0x9b2055d370f73ec7d8a03e965129118dc8f5bf83",
 "sha3HashOfDataToSign":"0xd539efa932"
}
```


**Output**: The signature of the given hash.

```json
"0xa3f20717a250c2b0b729b7e5becbff67fdaef7e0699da4de7ca5895b02a170a12d887fd3b17bfdce3481f10bea41f45ba9f709d39ce8325427b57afcfc994cee1b"
```


---
#### transactionByHash
**Input**: The hash of the transaction that should be retrieved.

```json
"0x610a8276014437089ff619136486474322444f0814f224fbbf9e925bb477e1e4"
```

**Output**: A complete transaction object.

```json
{
"hash":"0x610a8276014437089ff619136486474322444f0814f224fbbf9e925bb477e1e4",
"nonce":0,
"blockHash":"0xdc42581aff4d1e530576c631ff272aec603056e56bdadb44c64629d76913f6ea",
"blockNumber":4,
"transactionIndex":0,
"from":"0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
"to":"0x627306090abab3a6e1400e9345bc60c78a8bef57",
"value":2000000000000000000,
"gasPrice":1000,
"gas":21000,
"input":"0x",
"creates":null,
"publicKey":null,
"raw":null,
"r":"0x85d7e7ddef29b37760bd3098aa6f9bba0471befcaf133151374ccc9ed69242a",
"s":"0x1e80b9c2e381c6ddca0da30e69f1f9bee1ec57ae69b6c82e5e829f0c41844d6b",
"v":28,
"chainId":null,
"nonceRaw":"0x0",
"valueRaw":"0x1bc16d674ec80000",
"gasPriceRaw":"0x3e8",
"gasRaw":"0x5208",
"transactionIndexRaw":"0x0",
"blockNumberRaw":"0x4"
}
```

---
#### transactionByBlockHashAndIndex
**Input**: The hash of the block the transaction has been mined in and the position in the transaction list.

```json
{
 "blockHash":
 "0xdc42581aff4d1e530576c631ff272aec603056e56bdadb44c64629d76913f6ea",
 "transactionIndex":0
}
```

**Output**: Same as in transactionByHash.

---
#### transactionByBlockNumberAndIndex
**Input**: The default block parameter of the block and the position in the transaction list.

```json
{
 "defaultBlockParameter":4,
 "transactionIndex":0
}
```


**Output**: Same as in transactionByHash.

---
#### pendingTransactions
**Input**: None.

**Output**: Returns the hash of each pending transaction as it is broadcasted. This method does not use the timer.

```json
"0x610a8276014437089ff619136486474322444f0814f224fbbf9e925bb477e1e4"
```


---
#### transactionReceipt
**Input**: Only the hash of the corresponding transaction.

```json
"0x610a8276014437089ff619136486474322444f0814f224fbbf9e925bb477e1e4"
```

**Output**: A complete transaction receipt for the given transaction.

```json
{
"transactionHash":
"0x610a8276014437089ff619136486474322444f0814f224fbbf9e925bb477e1e4",
"transactionIndex":0,
"blockHash":
"0x0812a98d28f4b24e2191182a25c2b2bed66585c1f9e3bb22287ab9fd98863c54",
"blockNumber":6,
"cumulativeGasUsed":21000,
"gasUsed":21000,
"contractAddress":null,
"root":null,
"status":"0x1",
"from":"0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
"to":"0x627306090abab3a6e1400e9345bc60c78a8bef57",
"logs":[],
"logsBloom":"0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
"transactionIndexRaw":"0x0",
"blockNumberRaw":"0x6",
"cumulativeGasUsedRaw":"0x5208",
"gasUsedRaw":"0x5208",
"statusOK":true
}
```

### Eth methods for blocks and uncles
There are various methods for getting information about blocks and uncles. The blockNumber method gives us the number of the most recent block. The methods blockTransactionCountByHash and blockTransactionCountByNumber both tell us the number of transactions in a given block. The methods uncleCountByHash and uncleCountByBlockNumber return the number of included uncles of a block. The methods blockByHash and blockByNumber both return a complete block with all information contained in it. They can contain the full transactions or only the transaction hashes. The methods uncleByBlockHashAndIndex and uncleByBlockNumberAndIndex both return a full uncle object, that is similar to a block but doesn't contain transactions. 

---
#### blockNumber
**Input**: None.

**Output**: The number of the latest block.

```json
4716
```

---
####  blockTransactionCountByHash
**Input**: The hash of the block in question.

```json
{
 "blockHash":
 "0x6a004433b76b631ab55e83b92295013ba1274c2129e915c458878dfca1c1d1e2"
}
```

**Output**: The number of transactions in the block.

```json
32
```

---
####  blockTransactionCountByNumber
**Input**: The default block parameter of the block in question.

```json
{"defaultBlockParameter":297}
```
**Output**: Same as in blockTransactionCountByHash.


---
####  uncleCountByBlockHash
**Input**: The hash of the block in question.

```json
{"blockHash":
"0xec475657e1fbf631b5c60eec02dc23abdd3cab7d2f1563a1b20d33bb0c615215"}
```
**Output**: The number of uncles in the block. A block can contain at most 2 uncles.

```json
2
```

---
####  uncleCountByBlockNumber
**Input**: Only the default block parameter of the block in question.

```json
{"defaultBlockParameter":2844}
```
**Output**: Same as in uncleCountByBlockHash.

---
####  blockByHash
**Input**: The hash of the block and a variable stating if the complete transactions should be returned (true) or only their hashes (false).

```json
{
 "blockHash":
 "0x0cfe4ed932bd2df96d08cc86ae3d065c5e830c7c306e65b0544c209cafbcd68b",
 "returnFullTransactionObjects":false
}
```


**Output**: A complete block.

```json
{
"number":399,
"hash":"0x0cfe4ed932bd2df96d08cc86ae3d065c5e830c7c306e65b0544c209cafbcd68b",
"parentHash":"0xb598b5b1bb6aba86ba2cea3fcb9859b8354740462609a4f0730696c70146fa7a",
"nonce":12546444700994140791,
"sha3Uncles":"0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347",
"logsBloom":"0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
"transactionsRoot":"0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421",
"stateRoot":"0xb91d268aca02456933c15a3501c8e9ab0df54243734e4c295ec8cbdf08404a1f",
"receiptsRoot":"0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421",
"author":null,
"miner":"0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
"mixHash":null,
"difficulty":100,
"totalDifficulty":105436,
"extraData":"0x",
"size":514,
"gasLimit":9007199254740991,
"gasUsed":0,
"timestamp":1579928934,
"transactions":[],
"uncles":[],
"sealFields":null,
"nonceRaw":"0xae1decfb4cd67a77",
"gasUsedRaw":"0x0",
"numberRaw":"0x18f",
"difficultyRaw":"0x64",
"totalDifficultyRaw":"0x19bdc",
"sizeRaw":"0x202",
"gasLimitRaw":"0x1fffffffffffff",
"timestampRaw":"0x5e2bcd66"
}
```

---
####  blockByNumber
**Input**: The default block parameter of the block and a variable stating if the complete transactions should be returned (true) or only their hashes (false).

```json
{
 "defaultBlockParameter":3670,
 "returnFullTransactionObjects":false
}
```

**Output**:  Same as in blockByHash.

---
####  uncleByBlockHashAndIndex
**Input**: 

```json
{
 "blockHash":
 "0x0812a98d28f4b24e2191182a25c2b2bed66585c1f9e3bb22287ab9fd98863c54",
 "uncleIndex":0
}
```

**Output**: The complete uncle block which is like a normal block but without transactions.


---
####  uncleByBlockNumberAndIndex
**Input**: The default block parameter and the position of the uncle in the uncles list.

```json
{
 "defaultBlockParameter":32789,
 "uncleIndex":0
}
```

**Output**: Same as in uncleByBlockHashAndIndex.


### Eth methods for filters
There are three methods which all provide the logs of a certain range of blocks. The method ethFilterChanges returns only the new logs since the last poll for a given filter id. The method ethFilterLogs always returns all logs that match a filter with a given id. The method logs is like ethFilterLogs but receives a complete filter object as input and not just and id. 

---
#### ethFilterChanges
**Input**: The identification number of the filter. The filter has to be created in the client first.

```json
{"filterId":221377280851701895309305037010417023971}
```
**Output**: A list of logs that occurred since the last time the filter was polled.

```json
[
 {
  "removed":false,
  "logIndex":1,
  "transactionIndex":0,
  "transactionHash":
  "0xdf829c5a142f1fccd7d8216c5785ac562ff41e2dcfdf5785ac562ff41e2dcf",
  "blockHash":
  "0x8216c5785ac562ff41e2dcfdf5785ac562ff41e2dcfdf829c5a142f1fccd7d",
  "blockNumber":436,
  "address":"0x16c5785ac562ff41e2dcfdf829c5a142f1fccd7d",
  "data":"0x0000000000000000000000000000000000000000000000000000000000000000",
  "type":"0x0",
  "topics":
  ["0x59ebeb90bc63057b6515673c3ecf9438e5058bca0f92585014eced636878c9a5"],
  "blockNumberRaw":"0x1b4",
  "transactionIndexRaw":"0x0",
  "logIndexRaw":"0x1"
 }
]
```

---
#### ethFilterLogs
**Input**:  Same as in ethFilterChanges.

**Output**: Same as in ethFilterChanges, only that each time the complete list of logs that occurred since the starting block of the filter is returned.

---
#### logs
**Input**: This method receives a complete filter object instead of a filter id.

```json
{
 "topics":[],
 "fromBlock":"0x20a",
 "toBlock":"0x436",
 "address":
 [
  "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
  "0x627306090abaB3A6e1400e9345bC60c78a8BEf57"
 ]
}
```
**Output**: Same as in ethFilterLogs.


### Whisper methods
We already introduced the whisper protocol. The shhVersion method returns the version of the current whisper protocol. The method hasIdentity tells us if a client has a certain whisper identity. The methods shhFilterChanges and messages both return the logs of a whisper filter. Just like with the eth filter methods, shhFilterChanges only gets new logs, while messages returns all logs matching the filter.

---
####  shhVersion
**Input**: None.

**Output**: The version of the whisper protocol used by the client.

```json
"2"
```

---
####  hasIdentity
**Input**: The whisper identity that should be verified.

```json
"0x04f96a5e25610293e42a73908e93ccc8c4d4dc0edcfa9fa872f50cb214e08ebf61a03e245533f97284d442460f2998cd41858798ddfd4d661997d3940272b717b1"
```
**Output**: True if the client holds this identity and false otherwise.

---
#### shhFilterChanges
**Input**: The number of the filter that should be queried.

```json
7
```
**Output**: A list with new filter logs since the last poll.

```json
[
 {
  "hash":
  "0x33eb2da77bf3527e28f8bf493650b1879b08c4f2a362beae4ba2f71bafcd91f9",
  "from":
  "0xc931d93e97ab07fe42d923478ba2465f283f440fd6cabea4dd7a2c807108f651b7135d1d6ca9007d5b68aa497e4619ac10aa3b27726e1863c1fd9b570d99bbaf",
  "to":
  "0x04f96a5e25610293e42a73908e93ccc8c4d4dc0edcfa9fa872f50cb214e08ebf61a03e245533f97284d442460f2998cd41858798ddfd4d661997d3940272b717b1",
  "expiry":1422566666,
  "ttl":100,
  "sent":1422565026,
  "topics":["0x6578616d"],
  "payload":"0x12345678",
  "workProved":0,
  "expiryRaw":"0x54caa50a",
  "ttlRaw":"0x64",
  "sentRaw":"0x54ca9ea2",
  "workProvedRaw":"0x0"
 }
]
```


---
#### messages
**Input**: Same as shhFilterChanges.

**Output**: Same as shhFilterChanges, but all logs matching the given filter are returned and not only the new ones. 

