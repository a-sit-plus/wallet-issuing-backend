package at.asitplus.wallet.backend

import at.asitplus.attestation.android.AndroidAttestationConfiguration
import at.asitplus.attestation.android.PatchLevel
import at.asitplus.wallet.backend.config.IOSAttestationConfigurationProperties
import at.asitplus.wallet.backend.pki.RandomKeyAdapter
import at.asitplus.wallet.backend.service.DefaultCryptoServiceAdapter
import at.asitplus.wallet.lib.decodeBase64ToArray
import io.kotest.matchers.shouldBe
import io.ktor.util.*
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.time.Duration.Companion.days

class DefaultAttestationServiceTest {
    private var service = attestationService()

    private val iosChallenge = "d6KTUbpAHHsMpQ4x5rEuOqkiGSKTZzkHawXVfU03XIE=".decodeBase64ToArray()!!
    private val iosBindingCert = CertificateFactory.getInstance("X.509")
        .generateCertificate(
            """
            MIIBVTCB/aADAgECAgjzE8vxhgUZnDAKBggqhkjOPQQDAjBLMUkwRwYDVQQDDEBGQTc0OEY4MDc1NERE
            QTgwMkVBODY1MDJERTYyMThDQUFFNkEyMUFBRUQ5MzQzQTBCQ0RFMTY5Mzg3QkQxNDk3MB4XDTIyMDUw
            NTEyMzUxOFoXDTIyMTEwMzEyMzUxOFowGDEWMBQGA1UEAwwNV2FsbGV0QmFja2VuZDBZMBMGByqGSM49
            AgEGCCqGSM49AwEHA0IABLN27MTzPSExQ3t57YTosCzDUqgSlzUBg1RhYnARckIkKBxPIiv11zEcbi5f
            35155jv7q+zn0puI8wxEacT1o6cwCgYIKoZIzj0EAwIDRwAwRAIgb+1T1tKzM7ev3gaG104/OwiCRlQZ
            58FGXfQa3hzx6esCIGmroKWno+R0Ist640lkO0oDsr+TcWpM93GKZPDO7WUc
        """.trimMargin().decodeBase64ToArray()!!.inputStream()
        ) as X509Certificate

    private val iosAttestationStmt = """
            o2NmbXRvYXBwbGUtYXBwYXR0ZXN0Z2F0dFN0bXSiY3g1Y4JZAu8wggLrMIICcqADAgECAgYBg38k/xowCgYIKoZIzj0EAwIwTzEjMCEGA1UE
            AwwaQXBwbGUgQXBwIEF0dGVzdGF0aW9uIENBIDExEzARBgNVBAoMCkFwcGxlIEluYy4xEzARBgNVBAgMCkNhbGlmb3JuaWEwHhcNMjIwOTI2
            MTMzMTE0WhcNMjIwOTI5MTMzMTE0WjCBkTFJMEcGA1UEAwxAZjcwYjUxMjE5NTAyM2E0OTBmOGE4MjFkOTJmNGU5ZGY4MTQ1NzBlYTc0MTY4
            NzhhMmY2NTJmZDYyOTQ5ODhjODEaMBgGA1UECwwRQUFBIENlcnRpZmljYXRpb24xEzARBgNVBAoMCkFwcGxlIEluYy4xEzARBgNVBAgMCkNh
            bGlmb3JuaWEwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAS+KWejP3kku7s2ixJCD816PFwnlt3g22N3tVqDywJ6FfoZSg8i0fBfjv2+oGsc
            xG/YidHl8qsk6YTrZROiy6oLo4H2MIHzMAwGA1UdEwEB/wQCMAAwDgYDVR0PAQH/BAQDAgTwMIGCBgkqhkiG92NkCAUEdTBzpAMCAQq/iTAD
            AgEBv4kxAwIBAL+JMgMCAQG/iTMDAgEBv4k0KgQoM1lZUFA0QjM2Ti5hdC5ndi5ibWJ3Zi5lZHVEaWdpY2FyZFdhbGxldKUGBARza3Mgv4k2
            AwIBBb+JNwMCAQC/iTkDAgEAv4k6AwIBADAZBgkqhkiG92NkCAcEDDAKv4p4BgQEMTUuNjAzBgkqhkiG92NkCAIEJjAkoSIEINrvj5D9dE8L
            3sx8Lh9Qpj1npxr08YKzO8NUQxAbPiflMAoGCCqGSM49BAMCA2cAMGQCMGOKAo/9pQu4c+MauzyKniFVgPq1iRWlSW1vLi2CtK6cDJLnvApa
            zg3I34ZJKrD/7wIwfv3Enn0/24HG+axNlRiJ9ytzpqXa3jiaCSqTSEfUN+vKZB87S4Cl9PLCJpnBdfZ3WQJHMIICQzCCAcigAwIBAgIQCbrF
            4bxAGtnUU5W8OBoIVDAKBggqhkjOPQQDAzBSMSYwJAYDVQQDDB1BcHBsZSBBcHAgQXR0ZXN0YXRpb24gUm9vdCBDQTETMBEGA1UECgwKQXBw
            bGUgSW5jLjETMBEGA1UECAwKQ2FsaWZvcm5pYTAeFw0yMDAzMTgxODM5NTVaFw0zMDAzMTMwMDAwMDBaME8xIzAhBgNVBAMMGkFwcGxlIEFw
            cCBBdHRlc3RhdGlvbiBDQSAxMRMwEQYDVQQKDApBcHBsZSBJbmMuMRMwEQYDVQQIDApDYWxpZm9ybmlhMHYwEAYHKoZIzj0CAQYFK4EEACID
            YgAErls3oHdNebI1j0Dn0fImJvHCX+8XgC3qs4JqWYdP+NKtFSV4mqJmBBkSSLY8uWcGnpjTY71eNw+/oI4ynoBzqYXndG6jWaL2bynbMq9F
            XiEWWNVnr54mfrJhTcIaZs6Zo2YwZDASBgNVHRMBAf8ECDAGAQH/AgEAMB8GA1UdIwQYMBaAFKyREFMzvb5oQf+nDKnl+url5YqhMB0GA1Ud
            DgQWBBQ+410cBBmpybQx+IR01uHhV3LjmzAOBgNVHQ8BAf8EBAMCAQYwCgYIKoZIzj0EAwMDaQAwZgIxALu+iI1zjQUCz7z9Zm0JV1A1vNaHL
            D+EMEkmKe3R+RToeZkcmui1rvjTqFQz97YNBgIxAKs47dDMge0ApFLDukT5k2NlU/7MKX8utN+fXr5aSsq2mVxLgg35BDhveAe7WJQ5t2dyZ
            WNlaXB0WQ5nMIAGCSqGSIb3DQEHAqCAMIACAQExDzANBglghkgBZQMEAgEFADCABgkqhkiG9w0BBwGggCSABIID6DGCBCAwMAIBAgIBAQQoM
            1lZUFA0QjM2Ti5hdC5ndi5ibWJ3Zi5lZHVEaWdpY2FyZFdhbGxldDCCAvkCAQMCAQEEggLvMIIC6zCCAnKgAwIBAgIGAYN/JP8aMAoGCCqGS
            M49BAMCME8xIzAhBgNVBAMMGkFwcGxlIEFwcCBBdHRlc3RhdGlvbiBDQSAxMRMwEQYDVQQKDApBcHBsZSBJbmMuMRMwEQYDVQQIDApDYWxpZ
            m9ybmlhMB4XDTIyMDkyNjEzMzExNFoXDTIyMDkyOTEzMzExNFowgZExSTBHBgNVBAMMQGY3MGI1MTIxOTUwMjNhNDkwZjhhODIxZDkyZjRlO
            WRmODE0NTcwZWE3NDE2ODc4YTJmNjUyZmQ2Mjk0OTg4YzgxGjAYBgNVBAsMEUFBQSBDZXJ0aWZpY2F0aW9uMRMwEQYDVQQKDApBcHBsZSBJb
            mMuMRMwEQYDVQQIDApDYWxpZm9ybmlhMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEvilnoz95JLu7NosSQg/NejxcJ5bd4Ntjd7Vag8sCe
            hX6GUoPItHwX479vqBrHMRv2InR5fKrJOmE62UTosuqC6OB9jCB8zAMBgNVHRMBAf8EAjAAMA4GA1UdDwEB/wQEAwIE8DCBggYJKoZIhvdjZ
            AgFBHUwc6QDAgEKv4kwAwIBAb+JMQMCAQC/iTIDAgEBv4kzAwIBAb+JNCoEKDNZWVBQNEIzNk4uYXQuZ3YuYm1id2YuZWR1RGlnaWNhcmRXY
            WxsZXSlBgQEc2tzIL+JNgMCAQW/iTcDAgEAv4k5AwIBAL+JOgMCAQAwGQYJKoZIhvdjZAgHBAwwCr+KeAYEBDE1LjYwMwYJKoZIhvdjZAgCB
            CYwJKEiBCDa74+Q/XRPC97MfC4fUKY9Z6ca9PGCszvDVEMQGz4n5TAKBggqhkjOPQQDAgNnADBkAjBjigKP/aULuHPjGrs8ip4hVYD6tYkVp
            Ultby4tgrSunAyS57wKWs4NyN+GSSqw/+8CMH79xJ59P9uBxvmsTZUYifcrc6al2t44mgkqk0hH1DfrymQfO0uApfTywiaZwXX2dzAoAgEEA
            gEBBCDo1AuW3jvSYQyW+RE7/ekSS0yZEdlMbCQlqGRuRTnm2jBgAgEFAgEBBFhzNjNaVFl2WXFhTThwWkhIZmVHbWdhd2Ntbk5WOVlSMTRxc
            Cs2Q3lEa2VuOVE4SWkxVHlGMnkrMVo3UWoxSU1EaXBVV3R5VVc4dER1UnVFN2NaVU5NZz09MA4CAQYCAQEEBkFUVEVTVDAPAgEHAgEBBAdzY
            W5kYm94MCACAQwCAQEEPAQYMjAyMi0wOS0yN1QxMzozMToxNC42MjdaMCACARUCAQEEGDIwMjItMTItMjZUMTM6MzE6MTQuNjI3WgAAAAAAA
            KCAMIIDrjCCA1SgAwIBAgIQCTm0vOkMw6GBZTY3L2ZxQTAKBggqhkjOPQQDAjB8MTAwLgYDVQQDDCdBcHBsZSBBcHBsaWNhdGlvbiBJbnRlZ
            3JhdGlvbiBDQSA1IC0gRzExJjAkBgNVBAsMHUFwcGxlIENlcnRpZmljYXRpb24gQXV0aG9yaXR5MRMwEQYDVQQKDApBcHBsZSBJbmMuMQswC
            QYDVQQGEwJVUzAeFw0yMjA0MTkxMzMzMDNaFw0yMzA1MTkxMzMzMDJaMFoxNjA0BgNVBAMMLUFwcGxpY2F0aW9uIEF0dGVzdGF0aW9uIEZyY
            XVkIFJlY2VpcHQgU2lnbmluZzETMBEGA1UECgwKQXBwbGUgSW5jLjELMAkGA1UEBhMCVVMwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQ51
            PmqmxzERdZbphes8sCE7G8HCNWQFKDnbs897jmZqUxr+wFVEFVVZGzajiPgJgEUAtB+E7lUH9i01lfYLpN4o4IB2DCCAdQwDAYDVR0TAQH/B
            AIwADAfBgNVHSMEGDAWgBTZF/5LZ5A4S5L0287VV4AUC489yTBDBggrBgEFBQcBAQQ3MDUwMwYIKwYBBQUHMAGGJ2h0dHA6Ly9vY3NwLmFwc
            GxlLmNvbS9vY3NwMDMtYWFpY2E1ZzEwMTCCARwGA1UdIASCARMwggEPMIIBCwYJKoZIhvdjZAUBMIH9MIHDBggrBgEFBQcCAjCBtgyBs1Jlb
            GlhbmNlIG9uIHRoaXMgY2VydGlmaWNhdGUgYnkgYW55IHBhcnR5IGFzc3VtZXMgYWNjZXB0YW5jZSBvZiB0aGUgdGhlbiBhcHBsaWNhYmxlI
            HN0YW5kYXJkIHRlcm1zIGFuZCBjb25kaXRpb25zIG9mIHVzZSwgY2VydGlmaWNhdGUgcG9saWN5IGFuZCBjZXJ0aWZpY2F0aW9uIHByYWN0a
            WNlIHN0YXRlbWVudHMuMDUGCCsGAQUFBwIBFilodHRwOi8vd3d3LmFwcGxlLmNvbS9jZXJ0aWZpY2F0ZWF1dGhvcml0eTAdBgNVHQ4EFgQU+
            2fTDb9zt5KmJl1IjSzBHZXic/gwDgYDVR0PAQH/BAQDAgeAMA8GCSqGSIb3Y2QMDwQCBQAwCgYIKoZIzj0EAwIDSAAwRQIhAJSQoGc3c+cve
            Ck2diO43VHXyJoJ6rsA45xuRQsFWAvQAiBHNBor0TzAVKgKOqrMPMFFfABUUxjqM419bdX2CyuHLjCCAvkwggJ/oAMCAQICEFb7g9Qr/43DN
            5kjtVqubr0wCgYIKoZIzj0EAwMwZzEbMBkGA1UEAwwSQXBwbGUgUm9vdCBDQSAtIEczMSYwJAYDVQQLDB1BcHBsZSBDZXJ0aWZpY2F0aW9uI
            EF1dGhvcml0eTETMBEGA1UECgwKQXBwbGUgSW5jLjELMAkGA1UEBhMCVVMwHhcNMTkwMzIyMTc1MzMzWhcNMzQwMzIyMDAwMDAwWjB8MTAwL
            gYDVQQDDCdBcHBsZSBBcHBsaWNhdGlvbiBJbnRlZ3JhdGlvbiBDQSA1IC0gRzExJjAkBgNVBAsMHUFwcGxlIENlcnRpZmljYXRpb24gQXV0a
            G9yaXR5MRMwEQYDVQQKDApBcHBsZSBJbmMuMQswCQYDVQQGEwJVUzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABJLOY719hrGrKAo7HOGv+
            wSUgJGs9jHfpssoNW9ES+Eh5VfdEo2NuoJ8lb5J+r4zyq7NBBnxL0Ml+vS+s8uDfrqjgfcwgfQwDwYDVR0TAQH/BAUwAwEB/zAfBgNVHSMEG
            DAWgBS7sN6hWDOImqSKmd6+veuv2sskqzBGBggrBgEFBQcBAQQ6MDgwNgYIKwYBBQUHMAGGKmh0dHA6Ly9vY3NwLmFwcGxlLmNvbS9vY3NwM
            DMtYXBwbGVyb290Y2FnMzA3BgNVHR8EMDAuMCygKqAohiZodHRwOi8vY3JsLmFwcGxlLmNvbS9hcHBsZXJvb3RjYWczLmNybDAdBgNVHQ4EF
            gQU2Rf+S2eQOEuS9NvO1VeAFAuPPckwDgYDVR0PAQH/BAQDAgEGMBAGCiqGSIb3Y2QGAgMEAgUAMAoGCCqGSM49BAMDA2gAMGUCMQCNb6afo
            eDk7FtOc4qSfz14U5iP9NofWB7DdUr+OKhMKoMaGqoNpmRt4bmT6NFVTO0CMGc7LLTh6DcHd8vV7HaoGjpVOz81asjF5pKw4WG+gElp5F8rq
            WzhEQKqzGHZOLdzSjCCAkMwggHJoAMCAQICCC3F/IjSxUuVMAoGCCqGSM49BAMDMGcxGzAZBgNVBAMMEkFwcGxlIFJvb3QgQ0EgLSBHMzEmM
            CQGA1UECwwdQXBwbGUgQ2VydGlmaWNhdGlvbiBBdXRob3JpdHkxEzARBgNVBAoMCkFwcGxlIEluYy4xCzAJBgNVBAYTAlVTMB4XDTE0MDQzM
            DE4MTkwNloXDTM5MDQzMDE4MTkwNlowZzEbMBkGA1UEAwwSQXBwbGUgUm9vdCBDQSAtIEczMSYwJAYDVQQLDB1BcHBsZSBDZXJ0aWZpY2F0a
            W9uIEF1dGhvcml0eTETMBEGA1UECgwKQXBwbGUgSW5jLjELMAkGA1UEBhMCVVMwdjAQBgcqhkjOPQIBBgUrgQQAIgNiAASY6S89QHKk7ZMic
            oETHN0QlfHFo05x3BQW2Q7lpgUqd2R7X04407scRLV/9R+2MmJdyemEW08wTxFaAP1YWAyl9Q8sTQdHE3Xal5eXbzFc7SudeyA72LlU2V6Zp
            DpRCjGjQjBAMB0GA1UdDgQWBBS7sN6hWDOImqSKmd6+veuv2sskqzAPBgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQEAwIBBjAKBggqhkjOP
            QQDAwNoADBlAjEAg+nBxBZeGl00GNnt7/RsDgBGS7jfskYRxQ/95nqMoaZrzsID1Jz1k8Z0uGrfqiMVAjBtZooQytQN1E/NjUM+tIpjpTNu4
            23aF7dkH8hTJvmIYnQ5Cxdby1GoDOgYA+eisigAADGB/jCB+wIBATCBkDB8MTAwLgYDVQQDDCdBcHBsZSBBcHBsaWNhdGlvbiBJbnRlZ3Jhd
            GlvbiBDQSA1IC0gRzExJjAkBgNVBAsMHUFwcGxlIENlcnRpZmljYXRpb24gQXV0aG9yaXR5MRMwEQYDVQQKDApBcHBsZSBJbmMuMQswCQYDV
            QQGEwJVUwIQCTm0vOkMw6GBZTY3L2ZxQTANBglghkgBZQMEAgEFADAKBggqhkjOPQQDAgRIMEYCIQDtHiAO1eGNYjvJSMR6J53y0IvIE119w
            Q1Fr4yI9dJEXQIhAI4cKGPsQSFPtLYkYes5IySkx3LK22S7S0EkGvWrSViJAAAAAAAAaGF1dGhEYXRhWKRrYIb/2QrUnc/R9hI32hsnDb8u4
            S49ud6V8ZtpYot2x0AAAAAAYXBwYXR0ZXN0ZGV2ZWxvcAAg9wtRIZUCOkkPioIdkvTp34FFcOp0FoeKL2Uv1ilJiMilAQIDJiABIVggvilno
            z95JLu7NosSQg/NejxcJ5bd4Ntjd7Vag8sCehUiWCD6GUoPItHwX479vqBrHMRv2InR5fKrJOmE62UTosuqCw==
        """.trimMargin().decodeBase64ToArray()!!


    private val pixelAttestationChain = listOf(
        """
            MIICujCCAmCgAwIBAgIBATAKBggqhkjOPQQDAjA5MQwwCgYDVQQMDANURUUxKTAnBgNVBAUTIDg3ZWVkZjAzYjljZWNlMjIwYzgzMTJhMmI5ZDZiMjZlMB4XDTIy
            MDkyNzExMzY0OVoXDTQ4MDEwMTAwMDAwMFowFTETMBEGA1UEAxMKYmluZGluZ0tleTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABItRPUbUcbA7u0reFB0FHMvDlN/Oc/Ez1DlFsaNX/QHHGr5sgX4rJj3g6BqvwDxFfdjR8VnrB2wVqnAd1vCMNnejggF7MIIBdzAOBgNVHQ8BAf8EBAMCB4AwggFjBgorBgEEAdZ5AgERBIIBUzCCAU8CAgDICgEBAgIAyAoBAQQgg
            NbnnEX4D8L1U7rt6XyzQNZ3WlN/e/H9wDjd/qXvHpsEADBlv4U9CAIGAYN+vD4Xv4VFVQRTMFExKzApBCRhdC5hc2l0cGx1cy5kaWdpdGFsaWQud2FsbGV0LnB1cGlsaWQCAQExIgQg5UGooDS29UheXLyz12rlTbB36v/396mnrpycpGx0qlIwgbOhCDEGAgECAgEDogMCAQOjBAICAQClCzEJAgEAAgECAgEEqgMCAQG/g3gDAgEDv4N5BAICASy/hT4DAgEAv4VATD
            BKBCAPbnXIAYO13sB0sAVNQnHpk4nr5LE2sIGd4fFQug/51wEB/woBAAQgXOGC1inSwG9tTnpx1AflWgKWqTRrXBhScS9NTK0DMlS/hUEFAgMB+9C/hUIFAgMDFeG/hU4GAgQBNIvpv4VPBgIEATSL6TAKBggqhkjOPQQDAgNIADBFAiEA493XrIO83zpV6iMnPvLb9yzyZcp0nRS8PZIvAOdnkBYCIFM4RykcJJ8U984j03Wyb554OWJpBvDenwKKG4MAN/LH
            """.trimMargin(),
        """
            MIIB9DCCAXmgAwIBAgIQCNn9RyXFaa7kWA6JJSodGzAKBggqhkjOPQQDAjA5MQwwCgYDVQQMDANURUUxKTAnBgNVBAUTIDQyNjhmNDA2NjAyNWE1NzYzYmRkMmRkNjk4MzkzM2FhMB4XDTIxMDYxNjE5MTcyNFoXDTMxMDYxNDE5MTcyNFowOTEMMAoGA1UEDAwDVEVFMSkwJwYDVQQFEyA4N2VlZGYwM2I5Y2VjZTIyMGM4MzEyYTJiOWQ2YjI2ZTBZMBMGByqGSM49AgEGCC
            qGSM49AwEHA0IABFlQob02nD9BHPchSCt8rZEdWqz6p27iFV/M4rXw3N2Ccc0k6T/2kNPQRnjWLYawR3FkQennZkxjDiBcDXH+xy6jYzBhMB0GA1UdDgQWBBQs0tQfbzPcTsISzzfFQxV9/FOjBjAfBgNVHSMEGDAWgBSIe1CMIgaSRg9cSrE6FKzs2HlewjAPBgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQEAwICBDAKBggqhkjOPQQDAgNpADBmAjEA3rE7C07MJ2m
            tKwM3wfMEPoZb7G/lYtMtJNV1g+qBGAUQY1kDzDcVyrof1gT079ySAjEAhtBr5YIUnaK27b362eLsiMxao8Zv2VZcd4+ABaFcXbBHa6/nZnQ4Ohxnd3WliDj5
            """.trimMargin(),
        """
            MIIDlDCCAXygAwIBAgIRAMrkPM1rKxfGivQciJsjYTQwDQYJKoZIhvcNAQELBQAwGzEZMBcGA1UEBRMQZjkyMDA5ZTg1M2I2YjA0NTAeFw0yMTA2MTYxOTE1MDNaFw0zMTA2MTQxOTE1MDNaMDkxDD
            AKBgNVBAwMA1RFRTEpMCcGA1UEBRMgNDI2OGY0MDY2MDI1YTU3NjNiZGQyZGQ2OTgzOTMzYWEwdjAQBgcqhkjOPQIBBgUrgQQAIgNiAAT1kqcddIlJIMJ5z6WTaBc5iJH6J/OdrRY4F41bMID/Mk0sEXtlrB4eNi+JbGZxl7gc5Io+KaqSLC8OBogRzV4QmEWvxzX5CWFY9IYrM14I/5L0F27DDgZsmlPnhWMelRajYzBhMB0GA1UdDgQWBBSIe1CMIgaSRg9cSrE6FKz
            s2HlewjAfBgNVHSMEGDAWgBQ2YeEAfIgFCVGLRGxH/xpMyepPEjAPBgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQEAwICBDANBgkqhkiG9w0BAQsFAAOCAgEAhss46pEZIGlrIyg/fQfAon6blQHXjBaKeLKmpgACIDsPC6z8etEhIqsDwDJa9O2pAUi5WyMx51SEWVqPw4Aijw+rFfXnhiqyTQsp/JDJpJ02TK/aJ5OFsMHsGnp+dw0WnUfhi37iim7y2KL2I5pv36d/
            CCLGfljb+GCuyEbCwsC+YIDi0WGGC3cVqL02VSBQg2G7rdo4gAfCXqkMAYZN+78XxnQuHqRmaxxMcGv/EFzQA0yHMykE/If7UUgNYsgjkwqaGdaPzkekDxuc82fen/DkUyKdNY3Q5OEGWGJPnUOGHQdfta+9rTZXaWLQC+AG8U6WmrBVE5Mmkw2d2GonFj7/eKkQ9QoqGp6C8nQIFXL0TqoJ2W9AtHz/QqlaVFkG+ldDUjru/4J8X1YrjI4w5VtxBCcpi50E4xtL5FJ/K
            FMI9CTxxHCTzmFiPvsvcoPZpv16+2T1N1CbBGp/rjk1uvwDGELLxyk54l1lME1RAKVI9+xUqGFR0NCgs261ykTLpoI4lFHvdAUzD+ZdLYPTyXe3mwJFJEMKdf5krNGJQJYC9ldcoJBgNDPwhNOWHf9Lk5V+iLlV8msSaDiU1KJxMuREOSeVLSQ5XrH93PN0cqVodmlwvK31SQFiIkrHK2UGXeldNTLlAAuqciiAcgh2JdjukSgJD2PKx6+99fiJGdc=
            """.trimMargin(),
        """
            MIIFHDCCAwSgAwIBAgIJANUP8luj8tazMA0GCSqGSIb3DQEBCwUAMBsxGTAXBgNVBAUTEGY5MjAwOWU4NTNiNmIwNDUwHhcNMTkxMTIyMjAzNzU4WhcNMzQxMTE4MjAzNzU4WjAbMRkwFwYDVQQFExBmOTIwMDllODUzYjZiMDQ1MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAr7bHgiuxpwHsK7Qui8xUFmOr75gvMsd/dTEDDJdSSxtf6An7xyqpRR90PL2abxM1dEqlX
            nf2tqw1Ne4Xwl5jlRfdnJLmN0pTy/4lj4/7tv0Sk3iiKkypnEUtR6WfMgH0QZfKHM1+di+y9TFRtv6y//0rb+T+W8a9nsNL/ggjnar86461qO0rOs2cXjp3kOG1FEJ5MVmFmBGtnrKpa73XpXyTqRxB/M0n1n/W9nGqC4FSYa04T6N5RIZGBN2z2MT5IKGbFlbC8UrW0DxW7AYImQQcHtGl/m00QLVWutHQoVJYnFPlXTcHYvASLu+RhhsbDmxMgJJ0mcDpvsC4PjvB+T
            xywElgS70vE0XmLD+OJtvsBslHZvPBKCOdT0MS+tgSOIfga+z1Z1g7+DVagf7quvmag8jfPioyKvxnK/EgsTUVi2ghzq8wm27ud/mIM7AY2qEORR8Go3TVB4HzWQgpZrt3i5MIlCaY504LzSRiigHCzAPlHws+W0rB5N+er5/2pJKnfBSDiCiFAVtCLOZ7gLiMm0jhO2B6tUXHI/+MRPjy02i59lINMRRev56GKtcd9qO/0kUJWdZTdA2XoS82ixPvZtXQpUpuL12ab+9
            EaDK8Z4RHJYYfCT3Q5vNAXaiWQ+8PTWm2QgBR/bkwSWc+NpUFgNPN9PvQi8WEg5UmAGMCAwEAAaNjMGEwHQYDVR0OBBYEFDZh4QB8iAUJUYtEbEf/GkzJ6k8SMB8GA1UdIwQYMBaAFDZh4QB8iAUJUYtEbEf/GkzJ6k8SMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgIEMA0GCSqGSIb3DQEBCwUAA4ICAQBOMaBc8oumXb2voc7XCWnuXKhBBK3e2KMGz39t
            7lA3XXRe2ZLLAkLM5y3J7tURkf5a1SutfdOyXAmeE6SRo83Uh6WszodmMkxK5GM4JGrnt4pBisu5igXEydaW7qq2CdC6DOGjG+mEkN8/TA6p3cnoL/sPyz6evdjLlSeJ8rFBH6xWyIZCbrcpYEJzXaUOEaxxXxgYz5/cTiVKN2M1G2okQBUIYSY6bjEL4aUN5cfo7ogP3UvliEo3Eo0YgwuzR2v0KR6C1cZqZJSTnghIC/vAD32KdNQ+c3N+vl2OTsUVMC1GiWkngNx1O
            O1+kXW+YTnnTUOtOIswUP/Vqd5SYgAImMAfY8U9/iIgkQj6T2W6FsScy94IN9fFhE1UtzmLoBIuUFsVXJMTz+Jucth+IqoWFua9v1R93/k98p41pjtFX+H8DslVgfP097vju4KDlqN64xV1grw3ZLl4CiOe/A91oeLm2UHOq6wn3esB4r2EIQKb6jTVGu5sYCcdWpXr0AUVqcABPdgL+H7qJguBw09ojm6xNIrw2OocrDKsudk/okr/AwqEyPKw9WnMlQgLIKw1rODG2N
            vU9oR3GVGdMkUBZutL8VuFkERQGt6vQ2OCw0sV47VMkuYbacK/xyZFiRcrPJPb41zgbQj9XAEyLKCHex0SdDrx+tWUDqG8At2JHA==
            """.trimMargin()
    ).map { it.decodeBase64ToArray()!! }
    private val pixelBindingCert = CertificateFactory.getInstance("X.509")
        .generateCertificate(
            """
            MIICujCCAmCgAwIBAgIBATAKBggqhkjOPQQDAjA5MQwwCgYDVQQMDANURUUxKTAnBgNVBAUTIDg3ZWVkZjAzYjljZWNlMjIwYzgzMTJhMmI5ZDZiMjZlMB4XDTIy
            MDkyNzExMzY0OVoXDTQ4MDEwMTAwMDAwMFowFTETMBEGA1UEAxMKYmluZGluZ0tleTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABItRPUbUcbA7u0reFB0FHMvDlN/Oc/Ez1DlFsaNX/QHHGr5sgX4rJj3g6BqvwDxFfdjR8VnrB2wVqnAd1vCMNnejggF7MIIBdzAOBgNVHQ8BAf8EBAMCB4AwggFjBgorBgEEAdZ5AgERBIIBUzCCAU8CAgDICgEBAgIAyAoBAQQgg
            NbnnEX4D8L1U7rt6XyzQNZ3WlN/e/H9wDjd/qXvHpsEADBlv4U9CAIGAYN+vD4Xv4VFVQRTMFExKzApBCRhdC5hc2l0cGx1cy5kaWdpdGFsaWQud2FsbGV0LnB1cGlsaWQCAQExIgQg5UGooDS29UheXLyz12rlTbB36v/396mnrpycpGx0qlIwgbOhCDEGAgECAgEDogMCAQOjBAICAQClCzEJAgEAAgECAgEEqgMCAQG/g3gDAgEDv4N5BAICASy/hT4DAgEAv4VATD
            BKBCAPbnXIAYO13sB0sAVNQnHpk4nr5LE2sIGd4fFQug/51wEB/woBAAQgXOGC1inSwG9tTnpx1AflWgKWqTRrXBhScS9NTK0DMlS/hUEFAgMB+9C/hUIFAgMDFeG/hU4GAgQBNIvpv4VPBgIEATSL6TAKBggqhkjOPQQDAgNIADBFAiEA493XrIO83zpV6iMnPvLb9yzyZcp0nRS8PZIvAOdnkBYCIFM4RykcJJ8U984j03Wyb554OWJpBvDenwKKG4MAN/LH
            """.trimMargin().decodeBase64ToArray()!!.inputStream()
        ) as X509Certificate
    private val pixelChallenge = "gNbnnEX4D8L1U7rt6XyzQNZ3WlN/e/H9wDjd/qXvHps=".decodeBase64ToArray()!!


    @Test
    fun `android attestation`() {
        service = attestationService(unlockedBootloaderAllowed = true)
        val attestationChain = listOf(
            """
            MIICqzCCAlKgAwIBAgIBATAKBggqhkjOPQQDAjApMRkwFwYDVQQFExA4OTU0MWU4OGNkMWU4OTllMQww
            CgYDVQQMDANURUUwIBcNNzAwMTAxMDAwMDAwWhgPMjEwNjAyMDcwNjI4MTVaMB8xHTAbBgNVBAMMFEFu
            ZHJvaWQgS2V5c3RvcmUgS2V5MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEBYxuoXS3YO054cEC8TQN
            pJcB6N1ulrlWECE1yUW2bDncuqnhKG1boILFbMhCj2c97+EDGOi6mXT8oW40VhpJR6OCAXEwggFtMA4G
            A1UdDwEB/wQEAwIHgDCCAVkGCisGAQQB1nkCAREEggFJMIIBRQIBAwoBAQIBBAoBAQQgo6rk00X3/A+K
            ugSheSf/SFgo8KqemP/s3xXaWJW2H6sEADBlv4U9CAIGAXZM38DIv4VFVQRTMFExKzApBCRhdC5hc2l0
            cGx1cy5kaWdpdGFsaWQud2FsbGV0LnB1cGlsaWQCAQExIgQg5UGooDS29UheXLyz12rlTbB36v/396mn
            rpycpGx0qlIwgauhCDEGAgECAgEDogMCAQOjBAICAQClCzEJAgEEAgECAgEAqgMCAQG/g3gDAgECv4U+
            AwIBAL+FQEwwSgQgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAQAKAQIEIBNFjC4Icu82
            xM2pKPgPA4tIp29nbg1TTqDbhhy+B8vdv4VBBQIDAa2wv4VCBQIDAxV+v4VOBgIEATRlOb+FTwYCBAE0
            ZTkwCgYIKoZIzj0EAwIDRwAwRAIgBnjXfsXP3tvyXzdfeXaQJKCDKmFJu/ycdlwcj3ajTGkCIA82/o9K
            2dPoSH8wMLzE8nn254i+ADpIKOyZPr4f+Xb9
            """.trimMargin(),
            """
            MIICJTCCAaugAwIBAgIKEkh3djdpCZJ2CTAKBggqhkjOPQQDAjApMRkwFwYDVQQFExA1NDQ5ZDJjZGI2
            Yjg5NmU2MQwwCgYDVQQMDANURUUwHhcNMTkwNjEzMTg1NDUyWhcNMjkwNjEwMTg1NDUyWjApMRkwFwYD
            VQQFExA4OTU0MWU4OGNkMWU4OTllMQwwCgYDVQQMDANURUUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNC
            AARok9fwvbGel87DkDJL/3cp97ACmyMa5S181sQOfJqz8VbwpR2H1OyiSZZieU3nVA/U9OdhKLWRTg/G
            UMLRJzExo4G6MIG3MB0GA1UdDgQWBBRjaCAA6DrB1wdRs/n79bm6l7wASjAfBgNVHSMEGDAWgBQjl9W3
            4CQ+E3ekx90ztaY3xoTBZTAPBgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQEAwICBDBUBgNVHR8ETTBL
            MEmgR6BFhkNodHRwczovL2FuZHJvaWQuZ29vZ2xlYXBpcy5jb20vYXR0ZXN0YXRpb24vY3JsLzEyNDg3
            Nzc2Mzc2OTA5OTI3NjA5MAoGCCqGSM49BAMCA2gAMGUCMQC7qqSq/oZni+0N9AYmvcS9/AcRQ6RFHbrB
            eMTwg2owqLTqHFPwLGjaTmHOmAiXD2YCMDBkslEd2F/YDpOko77Hfx8O+y0VYT9W79PS8/0Ou6vjFqpd
            R+S5aN+rmlNe3T3IWw==
            """.trimMargin(),
            """
            MIID0TCCAbmgAwIBAgIKA4gmZ2BliZaF3jANBgkqhkiG9w0BAQsFADAbMRkwFwYDVQQFExBmOTIwMDll
            ODUzYjZiMDQ1MB4XDTE5MDYxMzE4NDQzNFoXDTI5MDYxMDE4NDQzNFowKTEZMBcGA1UEBRMQNTQ0OWQy
            Y2RiNmI4OTZlNjEMMAoGA1UEDAwDVEVFMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEra6nqIzZ7OpSzHIG
            N7BsX35AZ8NBHgEDgDTZpN+3aMvx9/VwNtCYJQ/co+kyh8nby55Dl3gZ2Bxmjxqk+i2EX6Yot7IyEcAK
            Ry3ZMmCgizeZrc4UAjLl2nSQFQRZ2+9bo4G2MIGzMB0GA1UdDgQWBBQjl9W34CQ+E3ekx90ztaY3xoTB
            ZTAfBgNVHSMEGDAWgBQ2YeEAfIgFCVGLRGxH/xpMyepPEjAPBgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB
            /wQEAwICBDBQBgNVHR8ESTBHMEWgQ6BBhj9odHRwczovL2FuZHJvaWQuZ29vZ2xlYXBpcy5jb20vYXR0
            ZXN0YXRpb24vY3JsL0U4RkExOTYzMTREMkZBMTgwDQYJKoZIhvcNAQELBQADggIBAAfUAU2H4mZ13DV9
            Uob8gAWxiNELxMpiLNuh8FW7tYiTbF0BBGS1VLTzihMLLC/rVGkRWy64peKAAQOizpqVnDd+/416jj3X
            pRhl4tvkArIQdL554ydZKoQ6dDQY+L54KwX/Z4mGWE9ltp29po4KK6FDpgLqqh/SJJimrMcJR7vW9UBN
            hZuD3Hio8MrL8TIfpQ75dyZ1WO4Ub3lxnv4ymP1GdlGlkKuIA63wOy+1uCs9QRLRkbRcW5Uo8jbdKZqX
            ajS8la+k3H+vlIa26C59LPfqN4tbjnM22vOz0bJI+LOqE694pGwSS6lqxrbpXht60VAQQ7x9ZRzj5734
            kp03fMqhWAsqGa0MRJ1DodO+j8zJeEnEqIF5RgQst2LWPxTkGF9RcqEjYRTvv4Gi0YbX8IlwvfMBebc0
            yQZm4TRYjQ9VaGZDeT2aWf5SZXT8ADUvpaMHHgHY2n4tlhh3HzM8h2V5fyQXA4g9/HHMvYI18bzyUEco
            4qSFugF4QaC3z+sxVGPmDYje0uTGBxLLoqaRPusLX7IJXyB0drDZhkW5ZpdJj/9L2fSDwWyZLvwJmhwv
            VWrlTDTWoqoqVyBGELltFvhS7SdfEpSKWFG/28MBA4mU/KTzx7tyfS8jFnt5aeo3di8Ul/ZTfPVnEF3E
            000hO9tcwAvkbywK6vXur6KLBNWc
            """.trimMargin(),
            """
            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
            """.trimMargin()
        ).map { it.decodeBase64ToArray()!! }
        val bindingCertificate = """
            MIIBUDCB9qADAgECAgg+W1nC9yFrAjAKBggqhkjOPQQDAjBLMUkwRwYDVQQDDEBBM0FBRTREMzQ1RjdG
            QzBGOEFCQTA0QTE3OTI3RkY0ODU4MjhGMEFBOUU5OEZGRUNERjE1REE1ODk1QjYxRkFCMB4XDTIyMDUw
            NDE0MDAzMloXDTIyMDUwNDE0MDEzMlowETEPMA0GA1UEAwwGSXNzdWVyMFkwEwYHKoZIzj0CAQYIKoZI
            zj0DAQcDQgAEBYxuoXS3YO054cEC8TQNpJcB6N1ulrlWECE1yUW2bDncuqnhKG1boILFbMhCj2c97+ED
            GOi6mXT8oW40VhpJRzAKBggqhkjOPQQDAgNJADBGAiEAjt3ybXoWAp17Iv6OhnaMHtmm1p1BcOVYNUy7
            gU32LxsCIQCR8NMd59KnNhZ78bRCppjpANX1Gu5a3hZovB5j62xACQ==
        """.trimMargin().decodeBase64ToArray()!!
        val bindingCert = CertificateFactory.getInstance("X.509")
            .generateCertificate(bindingCertificate.inputStream()) as X509Certificate

        val challenge = "o6rk00X3/A+KugSheSf/SFgo8KqemP/s3xXaWJW2H6s=".decodeBase64ToArray()!!
        service.verifyAttestationClient(attestationChain, bindingCert, challenge) shouldBe true

        service.verifyAttestationClient(listOf(attestationChain[0]), bindingCert, challenge) shouldBe false
        service.verifyAttestationClient(attestationChain.subList(0, 1), bindingCert, challenge) shouldBe false
        service.verifyAttestationClient(attestationChain.subList(0, 2), bindingCert, challenge) shouldBe false
    }

    @Test
    fun `Pixel 6 attestation ok -- base case`() {
        service = attestationService(unlockedBootloaderAllowed = false)

        TestTimeSource.offset(365.days)
        service.verifyAttestationClient(pixelAttestationChain, pixelBindingCert, pixelChallenge) shouldBe true

        service.verifyAttestationClient(
            listOf(pixelAttestationChain[0]),
            pixelBindingCert,
            pixelChallenge
        ) shouldBe false
        service.verifyAttestationClient(
            pixelAttestationChain.subList(0, 1),
            pixelBindingCert,
            pixelChallenge
        ) shouldBe false
        service.verifyAttestationClient(
            pixelAttestationChain.subList(0, 2),
            pixelBindingCert,
            pixelChallenge
        ) shouldBe false
        TestTimeSource.offset(-(365.days))
    }

    @Test
    fun `Pixel 6 attestation ok -- allow unlocked bootloader`() {
        service = attestationService(unlockedBootloaderAllowed = true)

        TestTimeSource.offset(365.days)
        service.verifyAttestationClient(pixelAttestationChain, pixelBindingCert, pixelChallenge) shouldBe true

        TestTimeSource.offset(-(365.days))
    }

    @Test
    fun `Pixel 6 attestation ok -- requre StrongBox`() {
        service = attestationService(requireStrongBox = true)
        TestTimeSource.offset(365.days)

        service.verifyAttestationClient(pixelAttestationChain, pixelBindingCert, pixelChallenge) shouldBe true
        TestTimeSource.offset(-(365.days))
    }

    @Test
    fun `Pixel 6 attestation ok -- no version check`() {
        service = attestationService(androidVersion = null)

        TestTimeSource.offset(365.days)
        service.verifyAttestationClient(pixelAttestationChain, pixelBindingCert, pixelChallenge) shouldBe true

        TestTimeSource.offset(-(365.days))
    }

    @Test
    fun `Pixel 6 attestation ok -- no patch level`() {
        service = attestationService(androidPatchLevel = null)

        TestTimeSource.offset(365.days)
        service.verifyAttestationClient(pixelAttestationChain, pixelBindingCert, pixelChallenge) shouldBe true

        TestTimeSource.offset(-(365.days))
    }

    @Test
    fun `Pixel 6 attestation fail -- time of verification`() {
        service = attestationService(unlockedBootloaderAllowed = false)
        service.verifyAttestationClient(pixelAttestationChain, pixelBindingCert, pixelChallenge) shouldBe false
    }

    @Test
    fun `Pixel 6 attestation fail -- package name`() {
        service = attestationService(androidPackageName = "org.wrong.package.name")
        TestTimeSource.offset(365.days)

        service.verifyAttestationClient(pixelAttestationChain, pixelBindingCert, pixelChallenge) shouldBe false
        TestTimeSource.offset(-(365.days))
    }

    @Test
    fun `Pixel 6 attestation fail -- signature digest`() {
        service = attestationService(androidAppSignatureDigest = byteArrayOf(0, 32, 55, 29, 120, 22, 0))
        TestTimeSource.offset(365.days)

        service.verifyAttestationClient(pixelAttestationChain, pixelBindingCert, pixelChallenge) shouldBe false
        TestTimeSource.offset(-(365.days))
    }

    @Test
    fun `Pixel 6 attestation fail -- app version`() {
        service = attestationService(androidVersion = 200000)
        TestTimeSource.offset(365.days)

        service.verifyAttestationClient(pixelAttestationChain, pixelBindingCert, pixelChallenge) shouldBe false
        TestTimeSource.offset(-(365.days))
    }

    @Test
    fun `Pixel 6 attestation fail -- patch level`() {
        service = attestationService(androidPatchLevel = PatchLevel(2030, 1))
        TestTimeSource.offset(365.days)

        service.verifyAttestationClient(pixelAttestationChain, pixelBindingCert, pixelChallenge) shouldBe false
        TestTimeSource.offset(-(365.days))
    }

    @Test
    fun `Pixel 6 attestation fail -- rollback resistance`() {
        service = attestationService(requireRollbackResistance = true)
        TestTimeSource.offset(365.days)

        service.verifyAttestationClient(pixelAttestationChain, pixelBindingCert, pixelChallenge) shouldBe false
        TestTimeSource.offset(-(365.days))
    }

    @Test
    fun `iOS ok -- base case`() {

        TestTimeSource.offset(351.days)
        val attestationResult = service.verifyAttestationClient(
            listOf(iosAttestationStmt),
            iosBindingCert,
            iosChallenge
        )
        TestTimeSource.offset(-(351.days))
        attestationResult shouldBe true

    }


    @Test
    fun `iOS fail -- time of verification`() {
        val attestationResult = service.verifyAttestationClient(
            listOf(iosAttestationStmt),
            iosBindingCert,
            iosChallenge
        )
        attestationResult shouldBe false
    }

    @Test
    fun `iOS ok -- noVersion`() {
        service = attestationService(iosVersion = null)

        TestTimeSource.offset(351.days)
        val attestationResult = service.verifyAttestationClient(
            listOf(iosAttestationStmt),
            iosBindingCert,
            iosChallenge
        )
        TestTimeSource.offset(-(351.days))
        attestationResult shouldBe true
    }


    @Test
    fun `iOS ok -- specificVersion`() {
        service = attestationService(iosVersion = "15.0.0")

        TestTimeSource.offset(351.days)
        val attestationResult = service.verifyAttestationClient(
            listOf(iosAttestationStmt),
            iosBindingCert,
            iosChallenge
        )
        TestTimeSource.offset(-(351.days))
        attestationResult shouldBe true
    }


    @Test
    fun `iOS fail -- production stage`() {
        service = attestationService(iosSandbox = false)

        TestTimeSource.offset(351.days)
        val attestationResult = service.verifyAttestationClient(
            listOf(iosAttestationStmt),
            iosBindingCert,
            iosChallenge
        )
        TestTimeSource.offset(-(351.days))
        attestationResult shouldBe false
    }

    @Test
    fun `iOS fail -- challenge`() {
        service = attestationService(iosSandbox = false)

        TestTimeSource.offset(351.days)
        val attestationResult = service.verifyAttestationClient(
            listOf(iosAttestationStmt),
            iosBindingCert,
            byteArrayOf(0, 1, 3, 5, 67, 4, 3, 2, 35, 0)
        )
        TestTimeSource.offset(-(351.days))
        attestationResult shouldBe false
    }

    @Test
    fun `iOS fail -- KID`() {
        service = attestationService(iosKid = byteArrayOf(1, 2, 3, 4).encodeBase64())

        TestTimeSource.offset(351.days)
        val attestationResult = service.verifyAttestationClient(
            listOf(iosAttestationStmt),
            iosBindingCert,
            iosChallenge
        )
        TestTimeSource.offset(-(351.days))
        attestationResult shouldBe false
    }

    @Test
    fun `iOS fail -- Team ID`() {
        service = attestationService(iosTeamIdentifier = "7AAXX0B00M")

        TestTimeSource.offset(351.days)
        val attestationResult = service.verifyAttestationClient(
            listOf(iosAttestationStmt),
            iosBindingCert,
            iosChallenge
        )
        TestTimeSource.offset(-(351.days))
        attestationResult shouldBe false
    }

    @Test
    fun `iOS fail -- bundle identifier`() {
        service = attestationService(iosBundleIdentifier = "org.invalid.bundle.identifier")

        TestTimeSource.offset(351.days)
        val attestationResult = service.verifyAttestationClient(
            listOf(iosAttestationStmt),
            iosBindingCert,
            iosChallenge
        )
        TestTimeSource.offset(-(351.days))
        attestationResult shouldBe false
    }

    @Test
    fun `iOS fail -- iOS version`() {
        service = attestationService(iosVersion = "25.0.0")

        TestTimeSource.offset(351.days)
        val attestationResult = service.verifyAttestationClient(
            listOf(iosAttestationStmt),
            iosBindingCert,
            iosChallenge
        )
        TestTimeSource.offset(-(351.days))
        attestationResult shouldBe false
    }

}


fun attestationService(
    androidPackageName: String = "at.asitplus.digitalid.wallet.pupilid",
    androidAppSignatureDigest: ByteArray = "5UGooDS29UheXLyz12rlTbB36v/396mnrpycpGx0qlI=".decodeBase64ToArray()!!,
    androidVersion: Int? = 10000,
    androidAppVersion: Int? = 1,
    androidPatchLevel: PatchLevel? = PatchLevel(2021, 8),
    requireStrongBox: Boolean = false,
    unlockedBootloaderAllowed: Boolean = false,
    requireRollbackResistance: Boolean = false,
    iosTeamIdentifier: String = "3YYPP4B36N",
    iosBundleIdentifier: String = "at.gv.bmbwf.eduDigicardWallet",
    iosKid: String = "9wtRIZUCOkkPioIdkvTp34FFcOp0FoeKL2Uv1ilJiMg=",
    iosVersion: String? = "14",
    iosSandbox: Boolean = true,
    timeSource: Clock = TestTimeSource,
) =
    DefaultAttestationService(
        DefaultCryptoServiceAdapter(RandomKeyAdapter()),
        AndroidAttestationConfiguration(
            packageName = androidPackageName,
            signatureDigest = androidAppSignatureDigest,
            appVersion = androidAppVersion,
            androidVersion = androidVersion,
            patchLevel = androidPatchLevel,
            requireStrongBox = requireStrongBox,
            bootloaderUnlockAllowed = unlockedBootloaderAllowed,
            requireRollbackResistance = requireRollbackResistance
        ),
        IOSAttestationConfigurationProperties(
            iosTeamIdentifier,
            iosBundleIdentifier,
            sandbox = iosSandbox,
            kid = iosKid,
            iosVersion = iosVersion
        ),
        timeSource
    )


