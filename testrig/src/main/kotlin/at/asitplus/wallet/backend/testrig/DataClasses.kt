package at.asitplus.wallet.backend.testrig

import kotlinx.serialization.Serializable


@Serializable
data class EcoStudentData(
    val validUntil: String,
    val cardId: String,
    val firstname: String,
    val lastname: String,
    val dateOfBirth: String,
    val schoolName: String,
    val schoolCity: String,
    val schoolZip: String,
    val schoolStreet: String,
    val schoolId: String,
    val studentCity: String?,
    val studentZip: String?,
    val photo: String,
)

@Serializable
data class CardCreationCodeResolveResult(
    val bpk: String,
)

/**
 * /binding/start: BindingParamsRequest  header(X_AUTH_EXT_NONCE, nonce), device name   testrig →   backend
 * BindingParamsResponse                                                                backend →   testrig

 * BindingCsrRequest, header(X_AUTH_TOKEN, BindingParamsResponse.xAuthToken)            testrig →   backend
 * create cert
 * /binding/confirm BindingConfirmRequest(true)                                         testrig →   backend
 *
 *
 * /CardCreationCode/{nonce}: nonce                                                     backend →   fakeEco
 * CardCreationCodeResolveResult    bbk                                                 fakeEco →   backend
 * /Student/{bpk}:  bpk                                                                 backend →   fakeEco
 * EcoStudentData                                                                       fakeEco →   backend
 *
 */
//