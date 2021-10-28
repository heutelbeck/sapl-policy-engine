# SAPL JSON Web Token (JWT) support library

This module implements essential functions and attributes for handling
JWTs in SAPL policies.

## Function Library

The function library can be used, if the subject contains a raw JWT the information within has to be accessed,
e.g. claims, from the token. The supplied function `jwt.parseJwt` maps the raw token to a matching JSON object.
Please note, that this function library does not validate the JWT token for validity. The reason for this is, 
that a proper full validation requires interaction with external resources, e.g., a public key server. 
This is prohibited in SAPL functions. The JWT attributes however offer validation support.

### Example

Given a subject object containing a raw token:

```json
{
	"token":"eyJraWQiOiI2YzMwMzliMC02MzU4LTQxYWYtODgyZC0yZjIyM2JhNTg1NTkiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyMSIsImF1ZCI6Im1pc2thdG9uaWMtY2xpZW50IiwibmJmIjoxNjM1NDYxMDk3LCJzY29wZSI6WyJiZXN0aWFyeS5yZWFkIiwiZmFjdWx0eS5yZWFkIiwiYm9va3MucmVhZCJdLCJpc3MiOiJodHRwOlwvXC9hdXRoLXNlcnZlcjo5MDAwIiwiZXhwIjoxNjM1NDYxMzk3LCJpYXQiOjE2MzU0NjEwOTd9.mc4UCLXwlHLT8HFYvw0-P3HtV8-dggqcvR_mT_Epk7UrbEz_5ZmN6QXKOAdEs5m_YsdwqJUnTZx3IJbjXp1ykvIKw6sU4eOzTJwry3XwHcds5SK-bkXA03x6Nv1dJ6o_Cg_gOQrnqz7-8txsXnVsWJrLCuGXg1yE5NWJBCyq2gIguS7sLPZFoko-UxA4mEPBofyxRMO46T7rX7jJQwkzcdsTKLd0TFlZedK7s-NAID7il2DZwrPJA2MWspsU5Gsyutuj8L4K9Nf0wtJwr5JaolERhW7OFUHTiWDHk5iT-6gTtUGh9RABqgEo_Z3qC4N7oZWqu9ZjRmONSPlCwdfRAQ",
	"username" : "user1",
	...
}
```

The expression `jwt.parseJwt(subject.token)` will return a value containing the following JSON Object:

```json
{
   "header":{
      "kid":"6c3039b0-6358-41af-882d-2f223ba58559",
      "alg":"RS256"
   },
   "payload":{
      "sub":"user1",
      "aud":"miskatonic-client",
      "nbf":"2021-10-28T22:44:57Z",
      "scope":[
         "bestiary.read",
         "faculty.read",
         "books.read"
      ],
      "iss":"http://auth-server:9000",
      "exp":"2021-10-28T22:49:57Z",
      "iat":"2021-10-28T22:44:57Z"
}
```

## JWT Attributes

The validity of a JWT depends on both the ability to validate the signature based on the public key of the issuer
and the the time for which the token is issued. Accessing the public key may require communication with a public 
key server, and the validity may also change over time. Thus, validity is an attribute of the token, which can 
only be derived from accessing Policy Information Points, and it is a time sequence changing over time.

### `jwt.validity`

The attribute `jwt.validity` returns a sequence of statements about the validity of the token at any time. 

* `"VALID"`:  the JWT is valid
* `"EXPIRED"`: the JWT has expired
* `"NEVERVALID"`: the JWT expires before it becomes valid, so it is never valid
* `"IMMATURE"`: the JWT will become valid in future
* `"UNTRUSTED"`: the JWT's signature could not be validated. I.e., either the payload has been tampered with, the public key could not be obtained, or the public key does not match the signature.
* `"INCOMPATIBLE"`: the JWT is incompatible. I.e., either an unsupported hashing algorithm has been used or required fields do not have the correct format.
* `"INCOMPLETE"`: the JWT is missing required fields.
* `"MALFORMED"`: the JWT is malformed.


Example:

```
policy "Policy to test timeout of token"
permit resource == "mysteries"
where
	subject.principal.tokenValue.<jwt.validity> == "VALID";
```

### `jwt.valid`

The `subject.principal.tokenValue.<jwt.valid>` is a shorthand for the expression 	`subject.principal.tokenValue.<jwt.validity> == "VALID"`.




