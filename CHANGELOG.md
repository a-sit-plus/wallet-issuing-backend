# Changelog

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
