package at.asitplus.wallet.backend.config

/**
 * Claim-name (and scheme-identifier) constants for the credentials that vck does not ship `*DataElements` objects
 * for. EU PID (ISO + SD-JWT) and mDL use vck's `EuPid*` / `MobileDrivingLicence*DataElements` directly; the
 * credentials below had their libraries dropped, so their claim names live here instead of as raw string literals
 * scattered through [DataExtractor]. Values mirror the remote type metadata documents.
 */

/** vct `urn:eu.europa.ec.eudi:tax:1`. */
object TaxIdClaims {
    const val VCT = "urn:eu.europa.ec.eudi:tax:1"
    const val TAX_NUMBER = "tax_number"
    const val AFFILIATION_COUNTRY = "affiliation_country"
    const val REGISTERED_GIVEN_NAME = "registered_given_name"
    const val REGISTERED_FAMILY_NAME = "registered_family_name"
    const val RESIDENT_ADDRESS = "resident_address"
    const val BIRTH_DATE = "birth_date"
    const val CHURCH_TAX_ID = "church_tax_ID"
    const val IBAN = "iban"
    const val PID_ID = "pid_id"
    const val ISSUANCE_DATE = "issuance_date"
    const val EXPIRY_DATE = "expiry_date"
    const val ISSUING_AUTHORITY = "issuing_authority"
    const val ISSUING_COUNTRY = "issuing_country"
    const val ISSUING_JURISDICTION = "issuing_jurisdiction"
    const val DOCUMENT_NUMBER = "document_number"
    const val ADMINISTRATIVE_NUMBER = "administrative_number"
}

/** vct `urn:eu.europa.ec.eudi:por:1`. */
object PowerOfRepresentationClaims {
    const val VCT = "urn:eu.europa.ec.eudi:por:1"
    const val LEGAL_PERSON_IDENTIFIER = "legal_person_identifier"
    const val LEGAL_NAME = "legal_name"
    const val FULL_POWERS = "full_powers"
    const val EFFECTIVE_FROM_DATE = "effective_from_date"
    const val EFFECTIVE_UNTIL_DATE = "effective_until_date"
    const val ISSUANCE_DATE = "issuance_date"
    const val EXPIRY_DATE = "expiry_date"
    const val ISSUING_AUTHORITY = "issuing_authority"
    const val ISSUING_COUNTRY = "issuing_country"
    const val ISSUING_JURISDICTION = "issuing_jurisdiction"
    const val DOCUMENT_NUMBER = "document_number"
    const val ADMINISTRATIVE_NUMBER = "administrative_number"
}

/** vct `eu.europa.ec.eudi.cor.1`. */
object CertificateOfResidenceClaims {
    const val VCT = "eu.europa.ec.eudi.cor.1"
    const val FAMILY_NAME = "family_name"
    const val GIVEN_NAME = "given_name"
    const val BIRTH_DATE = "birth_date"
    const val RESIDENCE_ADDRESS = "residence_address"
    const val GENDER = "gender"
    const val BIRTH_PLACE = "birth_place"
    const val ARRIVAL_DATE = "arrival_date"
    const val NATIONALITY = "nationality"
    const val ISSUANCE_DATE = "issuance_date"
    const val EXPIRY_DATE = "expiry_date"
    const val ISSUING_AUTHORITY = "issuing_authority"
    const val DOCUMENT_NUMBER = "document_number"
    const val ADMINISTRATIVE_NUMBER = "administrative_number"
    const val ISSUING_COUNTRY = "issuing_country"
    const val ISSUING_JURISDICTION = "issuing_jurisdiction"

    /** Sub-claims of [RESIDENCE_ADDRESS]. */
    object ResidenceAddress {
        const val THOROUGHFARE = "thoroughfare"
        const val LOCATOR_DESIGNATOR = "locator_designator"
        const val POST_CODE = "post_code"
        const val POST_NAME = "post_name"
        const val ADMIN_UNIT_L1 = "admin_unit_L1"
        const val ADMIN_UNIT_L2 = "admin_unit_L2"
        const val FULL_ADDRESS = "full_address"
    }
}

/** vct `urn:eudi:ehic:1`. */
object EhicClaims {
    const val VCT = "urn:eudi:ehic:1"
    const val ISSUING_COUNTRY = "issuing_country"
    const val PERSONAL_ADMINISTRATIVE_NUMBER = "personal_administrative_number"
    const val DOCUMENT_NUMBER = "document_number"
    const val ISSUING_AUTHORITY = "issuing_authority"
    const val ISSUING_AUTHORITY_ID = "issuing_authority.id"
    const val ISSUING_AUTHORITY_NAME = "issuing_authority.name"
    const val AUTHENTIC_SOURCE = "authentic_source"
    const val AUTHENTIC_SOURCE_ID = "authentic_source.id"
    const val AUTHENTIC_SOURCE_NAME = "authentic_source.name"
    const val DATE_OF_ISSUANCE = "date_of_issuance"
    const val DATE_OF_EXPIRY = "date_of_expiry"
    const val STARTING_DATE = "starting_date"
    const val ENDING_DATE = "ending_date"

    /** Sub-claims of the nested [ISSUING_AUTHORITY] / [AUTHENTIC_SOURCE] objects. */
    const val ID = "id"
    const val NAME = "name"
}

/** vct `urn:eidgvat:credentials.ida15binding`. */
object Ida15BindingClaims {
    const val VCT = "urn:eidgvat:credentials.ida15binding"
    const val SIGNER_CERTIFICATE = "urn:oid:1.2.40.0.10.2.1.1.261.66"
    const val IDENTITY_TYPE = "urn:oid:1.2.40.0.10.2.1.1.261.109"
    const val ISSUING_COUNTRY = "urn:oid:1.2.40.0.10.2.1.1.261.32"
    const val EID_STATUS = "urn:eidgvat:attributes.eid.status"
    const val VSZ_SHA256 = "urn:eidgvat:attributes.vsz.sha256"
}

/** ISO docType `eu.europa.ec.av.1`; dispatched via [AV_DOCTYPE]. */
object AgeVerificationClaims {
    const val AGE_OVER_12 = "age_over_12"
    const val AGE_OVER_13 = "age_over_13"
    const val AGE_OVER_14 = "age_over_14"
    const val AGE_OVER_16 = "age_over_16"
    const val AGE_OVER_18 = "age_over_18"
    const val AGE_OVER_21 = "age_over_21"
    const val AGE_OVER_25 = "age_over_25"
    const val AGE_OVER_60 = "age_over_60"
    const val AGE_OVER_62 = "age_over_62"
    const val AGE_OVER_65 = "age_over_65"
    const val AGE_OVER_68 = "age_over_68"
}
