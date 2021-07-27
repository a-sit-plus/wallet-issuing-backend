# Wallet Backend Service

This service creates pupil IDs, stores a reference for them in a database, can revoke them.

Functionality:
 - Providing a demo web page showing QR codes to initialize a Wallet App
 - Endpoint for issuing of a new pupil ID at `/issue?keyId={keyId}&token={token}`
 - Providing a revocation list
 
TODO:
 - Store references for issued credentials
 - Provide an endpoint for the revocation list
 - Validate keyId of the client before issuing new VC
 - Issue credentials with a set of random data, including a photo
 - Unit Tests for VC and VP verification
 - Add some text to the demo page
 - Add a demo page to revoke credentials
 - Refactor token auth to use a real Spring Security authentication and session
 - Add configuration property for lifetime of issued credentials
 - Add Spring Boot Admin Client
 - Deploy on `wallet.a-sit.at`, once the server is ready
 - Add configuration property for issuing key, so that apps and this services uses a fixed key (for verification)
 - Maybe extract VC data classes into separate gradle module / library
 - Checkout if W3C provides a recommendation for the API
 - Think about which single claims there are, maybe a BPK so that the Wallet App can authenticate again at this service
