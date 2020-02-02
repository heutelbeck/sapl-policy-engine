# Sapl-Ethereum Documentation

In this documentation you will find an introduction on how to use the **EthereumPolicyInformationPoint (EthPIP)**. This is a general attribute finder created to facilitate access to information from the Ethereum blockchain environment inside Sapl policies. For more information about using a **Policy Decision Point (PDP)** with an attribute finder in general and on how to access it inside a policy please refer to the [SAPL Documentation](https://github.com/heutelbeck/sapl-policy-engine/blob/master/sapl-documentation/src/asciidoc/sapl-reference.adoc). 

## The EthereumPolicyInformationPoint
To get started, you have to include the dependency of the **sapl-ethereum** module in the `pom.xml` of your maven project like this:

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

Please note that you can define the interval in which the EthPIP requests information from the blockchain. By default an interval of 5 seconds is used, as by now the intermediate time between new blocks on the Ethereum mainnet is at about 12 seconds, rendering it unnecessary to aim for higher accuracy. If you want to adjust this polling interval you can do so in the PDP configuration file `pdp.json`. Just add a variable with the key `ethPollingInterval` and the time between polls in milliseconds:

```json
{
    "algorithm": "DENY_UNLESS_PERMIT",
    "variables": {"ethPollingInterval":1000}
}
```

## User-friendly methods
Now we will explain how to use the methods included in this policy information point. In this section we will look at the user friendly methods that don't require deep understanding of the **Web3j API** or the Ethereum blockchain. You can use them by just looking at the code of a smart contract or by using basic information like transaction hashes and addresses. If you have no clue of these things yet, you can start at the official [Ethereum Website](https://ethereum.org/what-is-ethereum/).

If you are already an advanced Ethereum user and want to get even more options to receive information from the blockchain there will be a section with advanced methods later on.

### contract

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

  string public certificateName = "Device_Operator_Certificate";

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
	"functionName":"hasCertificate",
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
//  subject.functionName == "hasCertificate" &&
  subject.<ethereum.contract>[0].value;
```

If you have policies for multiple contracts there are two options (both shown here in the commented sections):
1. You make a new policy set for each contract and mark the policy set with
`for subject.contractAddress == "addressOfTheContract"`
2. If you prefer to keep the policies in the same set you can make a global variable for each contract:
`var contract1 = "addressOfTheContract";` and then you can define the contract the policy belongs to in the where-section:
`subject.contractAddress == contract1`

This scheme is also helpful when calling different functions from a contract.
In this case you would check `subject.functionName == "nameOfTheFunction"` in the where-section.
