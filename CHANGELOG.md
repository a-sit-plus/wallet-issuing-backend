# Changelog

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
