# Changelog

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
