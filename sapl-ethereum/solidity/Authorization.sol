pragma solidity >=0.4.0 <0.7.0;

contract Authorization {

  struct User {
    bool authorized;
  }

  address public admin;

  mapping(address => User) public users;

  constructor() public {
    admin = msg.sender;
  }

  function authorize (address user) public {
    require(
      msg.sender == admin,
      "Only the admin can authorize users."
    );

    users[user].authorized = true;
  }

  function unauthorize (address user) public {
    require(
      msg.sender == admin,
      "Only the admin can unauthorize users."
    );

    users[user].authorized = false;
  }

  function isAuthorized(address user) public view
          returns (bool authorized_) {
    authorized_ = users[user].authorized;
  }



}
