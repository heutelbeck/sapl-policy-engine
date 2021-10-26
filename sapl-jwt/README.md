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
	"token":"eyJraWQiOiJhZjk2OGRkYy0zYjI5LTQ5NzctYmYyNi1kNTFjNmJmMGNlODQiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyMSIsImF1ZCI6Im1pc2thdG9uaWMtY2xpZW50IiwibmJmIjoxNjM1MjgzMzAyLCJzY29wZSI6WyJmYWN1bHR5LnJlYWQiLCJib29rcy5yZWFkIl0sImlzcyI6Imh0dHA6XC9cL2F1dGgtc2VydmVyOjkwMDAiLCJleHAiOjE2MzUyODM2MDIsImlhdCI6MTYzNTI4MzMwMn0.eYnJ7Rr7o6T3wiOx7K9DuPbf5CFzwVbWP28iQ6HZlIsNzeKnqosY-ShiJYB8roni826v32-_2LPVLLw8ZAxF4-2RhS_0A9FHeg-kPP-5uGdRAaORTAeXfH2EayxbJkssJZF76rHyUupDW9D9Ya_PYIqo38VmylaRXk-5MiP4FoRCR5eLj7LbeM6-UpwToICeB8b_IZsvy2RWgNgnLeBpqA8O2zaK-DABFP-drulMLTBEfos65MMog0Q_X0wgIm2B0kBkCOgWqHGT66H65k_sbmdFygAwzAOKq_2ZsVhswBtS5hV7TJrNlnc7-C4LNhhFVSSYRQkAEChmUrBOuUJQlg",
	"username" : "user1",
	...
}
```

The expression `jwt.parseJwt(subject.token)` will return a value containing the following JSON Object:

```json
{
   "header":{
      "kid":"af968ddc-3b29-4977-bf26-d51c6bf0ce84",
      "alg":"RS256"
   },
   "payload":{
      "sub":"user1",
      "aud":"miskatonic-client",
      "nbf":1635283302,
      "scope":[
         "faculty.read",
         "books.read"
      ],
      "iss":"http://auth-server:9000",
      "exp":1635283602,
      "iat":1635283302
   }
}
```

## JWT Attributes

__TODO__
