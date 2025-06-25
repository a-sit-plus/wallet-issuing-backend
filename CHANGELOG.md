# Changelog

5.7.2:
 - Implement revocation of issued credentials on Web UI

5.7.1:
 - Update to VC-K 5.7.1
 - Update mDL credential to 1.2.0
 - Update EU PID credential to 3.1.0
 - Update EU PID credential SD-JWT to 1.1.0

5.7.0:
 - Update to VC-K 5.7.0

5.6.1:
 - Update to VC-K 5.6.6
 - Update EHIC credential to 1.1.0, adding new claims

5.6.0:
 - Update to VC-K 5.6.0
 - Update Tax ID credential to 1.1.1, fixing the `sdJwtType`

5.5.3:
 - Update to VC-K 5.5.3
 - Issue claims for EU PID credential with vct `urn:eudi:pid:1`

5.5.2:
 - Update to VC-K 5.5.2

5.5.1:
 - Issue claims with byte arrays without encoding them to Base64
 - Update to VC-K 5.5.1
 - define a revocation identifier for non-empty MDOC credentials
 - expose revocation identifier 

5.5.0:
 - Update to VC-K 5.5.0
 - Update issuing process to OID4VCI Draft 15
 - Do not issue Company Registration with selectively disclosable claims

5.4.2:
 - Include indexes for all credentials in token status list
 - Update Power of Representation credential to 1.2.0, fixing the `sdJwtType`
 - Update Company Registration credential to 1.1.0, fixing the `sdJwtType`

5.4.1
 - Do not issue all credentials in SD-JWT with disclosures, some are not selectively disclosable

5.4.0
- Update to VC-K 5.4.0
- Fix encoding of status list in JWT or CWT
- Replace ePrescription with HealthID
- Update EU PID to ARF 1.5.0

5.3.0
 - Update to VC-K 5.3.0, switching from [Revocation List 2020](https://w3c-ccg.github.io/vc-status-rl-2020/) to [Token Status List](https://datatracker.ietf.org/doc/draft-ietf-oauth-status-list/06/)

5.2.4
 - Issue EU PID in SD-JWT with ISO claim names, so *do not* use claim names from <https://github.com/eu-digital-identity-wallet/eudi-doc-architecture-and-reference-framework/pull/160>
 - Update Certificate of Residence to issue complex SD-JWT claims (in dot-notation)

5.2.3
 - Update dependency on VC-K to 5.2.3
 - Enable form login for easy testing

5.2.2
 - Update dependency on VC-K to 5.2.2
 - Issue EU PID in SD-JWT with mapped claim names from <https://github.com/eu-digital-identity-wallet/eudi-doc-architecture-and-reference-framework/pull/160>
 - Update credentials to issue more `age_over_NN` attributes

5.2.1:
 - Update dependency on VC-K to 5.2.1

5.2.0:
 - Update dependency on VC-K to 5.2.0

5.1.1:
 - Add issuance of company registration credentials

5.1.0:
 - Update dependency on vclib to 5.1.0

5.0.0:
 - Update dependency on vclib to 5.0.0
 - Issue values for all claims in all credentials for testing purposes

4.0.0:
 - Update dependency on vclib to 4.0.0
 - Add issuance of ePrescription credentials, using external OTT service (see `backend.eprescription` configuration)

3.8.0:
 - Update dependency on vclib to 3.8.0
 - Issue PoR, CoR

3.7.0:
 - Update dependency on vclib to 3.7.0
 - Rework OID4VCI issuing process, again

3.6.0:
 - Update dependency on vclib to 3.6.0
 - Rework OID4VCI issuing process

3.5.0:
 - Update dependency on vclib to 3.5.0

3.2.0:
 - Remove everything related to PupilIds
 - Remove all submodules
 - Update dependency on vclib to 3.4.0
 - Issue ID Austria credentials
 - Issue EU PID credentials

2.0.0:
 - Implement OpenID for VerifiableCredentialIssuance, as well as App Authentication over OpenID Connect
 - Add debug endpoint to create credentials with custom attribute values
 - depend on locally built umbrella lib
 - add new `purge` task to purge included umbrella lib maven artifacts
 - attestation-library v0.4.0 can now ignore timely validity of Android leaf certificates

1.6.2:
 - update attestation lib

1.6.1:
 - update attestation lib for more detailed error messages from android certificate verification

1.6.0:
 - Uses `libwebp` over a `libwebp-jni` instead of OpenCV to create webp images

1.5.0:
 - Emit revocation event when revoking bindings too
 - Cleanup logs
 - Update to Umbrella Lib 1.2.0

1.4.0:
 - Use new format for PupilIDs with picture and scaled picture externalized from the VC
 - revamped revocation list caching
 - correct calculation of grace period
 - propagate error when no picture is present in eco
 - Kotlin 1.8
 - depend on umbrella library, which provides
   - pupilidlib 1.6.1
   - vclib 1.5.0
   - kmmresult 1.4.0
   - kotlinx-serialization (json, properties) 1.4.1
   - bouncy castle pkix 1.72
   - napier 2.6.1
   - kotlinx.datetime 0.4.0
   - coroutines 1.6.4
 - attestation 0.3.0 with improved apple key attestation (integration needs to be re-examined)
