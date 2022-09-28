package at.asitplus.wallet.backend

import at.asitplus.attestation.android.AndroidAttestationConfiguration
import at.asitplus.attestation.android.PatchLevel
import at.asitplus.wallet.backend.pki.RandomKeyAdapter
import at.asitplus.wallet.backend.service.DefaultCryptoServiceAdapter
import at.asitplus.wallet.lib.decodeBase64ToArray
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.*

class DefaultAttestationServiceTest {
    private val service = DefaultAttestationService(
        DefaultCryptoServiceAdapter(RandomKeyAdapter()),
        AndroidAttestationConfiguration(
            packageName = "at.asitplus.digitalid.wallet.pupilid",
            signatureDigest = byteArrayOf(
                -27,
                65,
                -88,
                -96,
                52,
                -74,
                -11,
                72,
                94,
                92,
                -68,
                -77,
                -41,
                106,
                -27,
                77,
                -80,
                119,
                -22,
                -1,
                -9,
                -9,
                -87,
                -89,
                -82,
                -100,
                -100,
                -92,
                108,
                116,
                -86,
                82
            ),
            appVersion = 1,
            androidVersion = 10000,
            patchLevel = PatchLevel(2021, 8),
            requireStrongBox = false,
            bootloaderUnlockAllowed = true,
            requireRollbackResistance = false
        )
    )

    @Test
    fun `android attestation`() {
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

        service.verifyAttestationClient(attestationChain, bindingCert) shouldBe true

        service.verifyAttestationClient(listOf(attestationChain[0]), bindingCert) shouldBe false
        service.verifyAttestationClient(attestationChain.subList(0, 1), bindingCert) shouldBe false
        service.verifyAttestationClient(attestationChain.subList(0, 2), bindingCert) shouldBe false
    }

    @Test
    fun `iOS attestation`() {
        val attestationStatement = """
            o2NmbXRvYXBwbGUtYXBwYXR0ZXN0Z2F0dFN0bXSiY3g1Y4JZAu0wggLpMIICbqADAgECAgYBgJQ3rNkw
            CgYIKoZIzj0EAwIwTzEjMCEGA1UEAwwaQXBwbGUgQXBwIEF0dGVzdGF0aW9uIENBIDExEzARBgNVBAoM
            CkFwcGxlIEluYy4xEzARBgNVBAgMCkNhbGlmb3JuaWEwHhcNMjIwNTA0MTIzNTE4WhcNMjIwNTA3MTIz
            NTE4WjCBkTFJMEcGA1UEAwxAZjcwYmViMTE5NjA5ZGI5MDgzZDdkNTEzOWFiNWQ5ZjA4NGNhNDczZmNl
            ZTkyN2QyNGQ5YTRjNmQyNTc3ODEzNzEaMBgGA1UECwwRQUFBIENlcnRpZmljYXRpb24xEzARBgNVBAoM
            CkFwcGxlIEluYy4xEzARBgNVBAgMCkNhbGlmb3JuaWEwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATg
            2lfAVwDPbgrFJmbz6DyJ7VegefNQvEOCgNlLnfgYOon/hZbWSVBqz3Hmer09HHimh+eSS6LkJYOAOeH5
            s+6Io4HyMIHvMAwGA1UdEwEB/wQCMAAwDgYDVR0PAQH/BAQDAgTwMH0GCSqGSIb3Y2QIBQRwMG6kAwIB
            Cr+JMAMCAQG/iTEDAgEAv4kyAwIBAb+JMwMCAQG/iTQlBCM5Q1lISk5HNjQ0LmF0LmFzaXRwbHVzLmVk
            dS1kaWdpY2FyZKUGBARza3Mgv4k2AwIBBb+JNwMCAQC/iTkDAgEAv4k6AwIBADAbBgkqhkiG92NkCAcE
            DjAMv4p4CAQGMTUuNC4xMDMGCSqGSIb3Y2QIAgQmMCShIgQg6zFTIq+5SrxoEjF6+ZRr25LK1a8a6PTq
            kKI0z2GawM8wCgYIKoZIzj0EAwIDaQAwZgIxAPVdRwFNrQY4xXSDQaXy6+3TLjbUBlnGloBrNzVcYhRK
            wl+q0c0ZVY6Fy/NnK8LgSAIxAJC47fNUP/uk1E2JQzOg73XBEtiKWaRGsPS9O/r9QaaMWcjyDoCJfr6n
            UvreaiLi6VkCRzCCAkMwggHIoAMCAQICEAm6xeG8QBrZ1FOVvDgaCFQwCgYIKoZIzj0EAwMwUjEmMCQG
            A1UEAwwdQXBwbGUgQXBwIEF0dGVzdGF0aW9uIFJvb3QgQ0ExEzARBgNVBAoMCkFwcGxlIEluYy4xEzAR
            BgNVBAgMCkNhbGlmb3JuaWEwHhcNMjAwMzE4MTgzOTU1WhcNMzAwMzEzMDAwMDAwWjBPMSMwIQYDVQQD
            DBpBcHBsZSBBcHAgQXR0ZXN0YXRpb24gQ0EgMTETMBEGA1UECgwKQXBwbGUgSW5jLjETMBEGA1UECAwK
            Q2FsaWZvcm5pYTB2MBAGByqGSM49AgEGBSuBBAAiA2IABK5bN6B3TXmyNY9A59HyJibxwl/vF4At6rOC
            almHT/jSrRUleJqiZgQZEki2PLlnBp6Y02O9XjcPv6COMp6Ac6mF53Ruo1mi9m8p2zKvRV4hFljVZ6+e
            Jn6yYU3CGmbOmaNmMGQwEgYDVR0TAQH/BAgwBgEB/wIBADAfBgNVHSMEGDAWgBSskRBTM72+aEH/pwyp
            5frq5eWKoTAdBgNVHQ4EFgQUPuNdHAQZqcm0MfiEdNbh4Vdy45swDgYDVR0PAQH/BAQDAgEGMAoGCCqG
            SM49BAMDA2kAMGYCMQC7voiNc40FAs+8/WZtCVdQNbzWhyw/hDBJJint0fkU6HmZHJrota7406hUM/e2
            DQYCMQCrOO3QzIHtAKRSw7pE+ZNjZVP+zCl/LrTfn16+WkrKtplcS4IN+QQ4b3gHu1iUObdncmVjZWlw
            dFkOXzCABgkqhkiG9w0BBwKggDCAAgEBMQ8wDQYJYIZIAWUDBAIBBQAwgAYJKoZIhvcNAQcBoIAkgASC
            A+gxggQZMCsCAQICAQEEIzlDWUhKTkc2NDQuYXQuYXNpdHBsdXMuZWR1LWRpZ2ljYXJkMIIC9wIBAwIB
            AQSCAu0wggLpMIICbqADAgECAgYBgJQ3rNkwCgYIKoZIzj0EAwIwTzEjMCEGA1UEAwwaQXBwbGUgQXBw
            IEF0dGVzdGF0aW9uIENBIDExEzARBgNVBAoMCkFwcGxlIEluYy4xEzARBgNVBAgMCkNhbGlmb3JuaWEw
            HhcNMjIwNTA0MTIzNTE4WhcNMjIwNTA3MTIzNTE4WjCBkTFJMEcGA1UEAwxAZjcwYmViMTE5NjA5ZGI5
            MDgzZDdkNTEzOWFiNWQ5ZjA4NGNhNDczZmNlZTkyN2QyNGQ5YTRjNmQyNTc3ODEzNzEaMBgGA1UECwwR
            QUFBIENlcnRpZmljYXRpb24xEzARBgNVBAoMCkFwcGxlIEluYy4xEzARBgNVBAgMCkNhbGlmb3JuaWEw
            WTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATg2lfAVwDPbgrFJmbz6DyJ7VegefNQvEOCgNlLnfgYOon/
            hZbWSVBqz3Hmer09HHimh+eSS6LkJYOAOeH5s+6Io4HyMIHvMAwGA1UdEwEB/wQCMAAwDgYDVR0PAQH/
            BAQDAgTwMH0GCSqGSIb3Y2QIBQRwMG6kAwIBCr+JMAMCAQG/iTEDAgEAv4kyAwIBAb+JMwMCAQG/iTQl
            BCM5Q1lISk5HNjQ0LmF0LmFzaXRwbHVzLmVkdS1kaWdpY2FyZKUGBARza3Mgv4k2AwIBBb+JNwMCAQC/
            iTkDAgEAv4k6AwIBADAbBgkqhkiG92NkCAcEDjAMv4p4CAQGMTUuNC4xMDMGCSqGSIb3Y2QIAgQmMCSh
            IgQg6zFTIq+5SrxoEjF6+ZRr25LK1a8a6PTqkKI0z2GawM8wCgYIKoZIzj0EAwIDaQAwZgIxAPVdRwFN
            rQY4xXSDQaXy6+3TLjbUBlnGloBrNzVcYhRKwl+q0c0ZVY6Fy/NnK8LgSAIxAJC47fNUP/uk1E2JQzOg
            73XBEtiKWaRGsPS9O/r9QaaMWcjyDoCJfr6nUvreaiLi6TAoAgEEAgEBBCC1QPhtL0fnlOw4FCEyx+T5
            Cck7kGgycMw782HA7Yqu6jBgAgEFAgEBBFhvUmR5dVNqdVVFSEJQdlVhVmJkTW42aGdwTmpJc21lTm5X
            dXB3d1EwNGpKWFJCc2dsN1ZHejJkNi8xZVEzYSsyV3paTlpGNXEwQVV4cFZZVjZPYmZqUT09MA4CAQYC
            AQEEBkFUVEVTVDAPAgEHAgEBBAdzYW5kYm94MCACAQwCAQEEGDIwMjItBDUwNS0wNVQxMjozNToxOC4z
            OTdaMCACARUCAQEEGDIwMjItMDgtMDNUMTI6MzU6MTguMzk3WgAAAAAAAKCAMIIDrjCCA1SgAwIBAgIQ
            WmMk9bZy2t8fhb5kN6oU4jAKBggqhkjOPQQDAjB8MTAwLgYDVQQDDCdBcHBsZSBBcHBsaWNhdGlvbiBJ
            bnRlZ3JhdGlvbiBDQSA1IC0gRzExJjAkBgNVBAsMHUFwcGxlIENlcnRpZmljYXRpb24gQXV0aG9yaXR5
            MRMwEQYDVQQKDApBcHBsZSBJbmMuMQswCQYDVQQGEwJVUzAeFw0yMTA1MDUwNDA3NTJaFw0yMjA2MDQw
            NDA3NTFaMFoxNjA0BgNVBAMMLUFwcGxpY2F0aW9uIEF0dGVzdGF0aW9uIEZyYXVkIFJlY2VpcHQgU2ln
            bmluZzETMBEGA1UECgwKQXBwbGUgSW5jLjELMAkGA1UEBhMCVVMwWTATBgcqhkjOPQIBBggqhkjOPQMB
            BwNCAAQuxd7WO5w3Khzo9lnUcL6ACgu+unY5hELa5TGVzRdO22mxERF16L2nYy81z756qHovUNWW7K0E
            i23gk+v3rKwSo4IB2DCCAdQwDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAWgBTZF/5LZ5A4S5L0287VV4AU
            C489yTBDBggrBgEFBQcBAQQ3MDUwMwYIKwYBBQUHMAGGJ2h0dHA6Ly9vY3NwLmFwcGxlLmNvbS9vY3Nw
            MDMtYWFpY2E1ZzEwMTCCARwGA1UdIASCARMwggEPMIIBCwYJKoZIhvdjZAUBMIH9MIHDBggrBgEFBQcC
            AjCBtgyBs1JlbGlhbmNlIG9uIHRoaXMgY2VydGlmaWNhdGUgYnkgYW55IHBhcnR5IGFzc3VtZXMgYWNj
            ZXB0YW5jZSBvZiB0aGUgdGhlbiBhcHBsaWNhYmxlIHN0YW5kYXJkIHRlcm1zIGFuZCBjb25kaXRpb25z
            IG9mIHVzZSwgY2VydGlmaWNhdGUgcG9saWN5IGFuZCBjZXJ0aWZpY2F0aW9uIHByYWN0aWNlIHN0YXRl
            bWVudHMuMDUGCCsGAQUFBwIBFilodHRwOi8vd3d3LmFwcGxlLmNvbS9jZXJ0aWZpY2F0ZWF1dGhvcml0
            eTAdBgNVHQ4EFgQUgYIFHDboz52JHAUcf2be4RMg5VMwDgYDVR0PAQH/BAQDAgeAMA8GCSqGSIb3Y2QM
            DwQCBQAwCgYIKoZIzj0EAwIDSAAwRQIgRuXoU1t+BUqff/GPKjPW4bIZKlFkENd7KR8Gq5yLqRMCIQC4
            d5e0qEsxLnl9i1DjKNVBti3hl0GC8kfwlMbyis4LFjCCAvkwggJ/oAMCAQICEFb7g9Qr/43DN5kjtVqu
            br0wCgYIKoZIzj0EAwMwZzEbMBkGA1UEAwwSQXBwbGUgUm9vdCBDQSAtIEczMSYwJAYDVQQLDB1BcHBs
            ZSBDZXJ0aWZpY2F0aW9uIEF1dGhvcml0eTETMBEGA1UECgwKQXBwbGUgSW5jLjELMAkGA1UEBhMCVVMw
            HhcNMTkwMzIyMTc1MzMzWhcNMzQwMzIyMDAwMDAwWjB8MTAwLgYDVQQDDCdBcHBsZSBBcHBsaWNhdGlv
            biBJbnRlZ3JhdGlvbiBDQSA1IC0gRzExJjAkBgNVBAsMHUFwcGxlIENlcnRpZmljYXRpb24gQXV0aG9y
            aXR5MRMwEQYDVQQKDApBcHBsZSBJbmMuMQswCQYDVQQGEwJVUzBZMBMGByqGSM49AgEGCCqGSM49AwEH
            A0IABJLOY719hrGrKAo7HOGv+wSUgJGs9jHfpssoNW9ES+Eh5VfdEo2NuoJ8lb5J+r4zyq7NBBnxL0Ml
            +vS+s8uDfrqjgfcwgfQwDwYDVR0TAQH/BAUwAwEB/zAfBgNVHSMEGDAWgBS7sN6hWDOImqSKmd6+veuv
            2sskqzBGBggrBgEFBQcBAQQ6MDgwNgYIKwYBBQUHMAGGKmh0dHA6Ly9vY3NwLmFwcGxlLmNvbS9vY3Nw
            MDMtYXBwbGVyb290Y2FnMzA3BgNVHR8EMDAuMCygKqAohiZodHRwOi8vY3JsLmFwcGxlLmNvbS9hcHBs
            ZXJvb3RjYWczLmNybDAdBgNVHQ4EFgQU2Rf+S2eQOEuS9NvO1VeAFAuPPckwDgYDVR0PAQH/BAQDAgEG
            MBAGCiqGSIb3Y2QGAgMEAgUAMAoGCCqGSM49BAMDA2gAMGUCMQCNb6afoeDk7FtOc4qSfz14U5iP9Nof
            WB7DdUr+OKhMKoMaGqoNpmRt4bmT6NFVTO0CMGc7LLTh6DcHd8vV7HaoGjpVOz81asjF5pKw4WG+gElp
            5F8rqWzhEQKqzGHZOLdzSjCCAkMwggHJoAMCAQICCC3F/IjSxUuVMAoGCCqGSM49BAMDMGcxGzAZBgNV
            BAMMEkFwcGxlIFJvb3QgQ0EgLSBHMzEmMCQGA1UECwwdQXBwbGUgQ2VydGlmaWNhdGlvbiBBdXRob3Jp
            dHkxEzARBgNVBAoMCkFwcGxlIEluYy4xCzAJBgNVBAYTAlVTMB4XDTE0MDQzMDE4MTkwNloXDTM5MDQz
            MDE4MTkwNlowZzEbMBkGA1UEAwwSQXBwbGUgUm9vdCBDQSAtIEczMSYwJAYDVQQLDB1BcHBsZSBDZXJ0
            aWZpY2F0aW9uIEF1dGhvcml0eTETMBEGA1UECgwKQXBwbGUgSW5jLjELMAkGA1UEBhMCVVMwdjAQBgcq
            hkjOPQIBBgUrgQQAIgNiAASY6S89QHKk7ZMicoETHN0QlfHFo05x3BQW2Q7lpgUqd2R7X04407scRLV/
            9R+2MmJdyemEW08wTxFaAP1YWAyl9Q8sTQdHE3Xal5eXbzFc7SudeyA72LlU2V6ZpDpRCjGjQjBAMB0G
            A1UdDgQWBBS7sN6hWDOImqSKmd6+veuv2sskqzAPBgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQEAwIB
            BjAKBggqhkjOPQQDAwNoADBlAjEAg+nBxBZeGl00GNnt7/RsDgBGS7jfskYRxQ/95nqMoaZrzsID1Jz1
            k8Z0uGrfqiMVAjBtZooQytQN1E/NjUM+tIpjpTNu423aF7dkH8hTJvmIYnQ5Cxdby1GoDOgYA+eisigA
            ADGB/TCB+gIBATCBkDB8MTAwLgYDVQQDDCdBcHBsZSBBcHBsaWNhdGlvbiBJbnRlZ3JhdGlvbiBDQSA1
            IC0gRzExJjAkBgNVBAsMHUFwcGxlIENlcnRpZmljYXRpb24gQXV0aG9yaXR5MRMwEQYDVQQKDApBcHBs
            ZSBJbmMuMQswCQYDVQQGEwJVUwIQWmMk9bZy2t8fhb5kN6oU4jANBglghkgBZQMEAgEFADAKBggqhkjO
            PQQDAgRHMEUCID0A97UNknMpHMGEEo6cCKcQbl1QZBomqpZ6wMskLLU3AiEApO6XCURGftyUFnCZAH7d
            J0LdcZjHhFNjO4vKZBIfmKwAAAAAAABoYXV0aERhdGFYpICdH9z8dBnBH6acSbBIsoYGib1/EdgAnlk7
            wecfGjBDQAAAAABhcHBhdHRlc3RkZXZlbG9wACD3C+sRlgnbkIPX1ROatdnwhMpHP87pJ9JNmkxtJXeB
            N6UBAgMmIAEhWCDg2lfAVwDPbgrFJmbz6DyJ7VegefNQvEOCgNlLnfgYOiJYIIn/hZbWSVBqz3Hmer09
            HHimh+eSS6LkJYOAOeH5s+6I
        """.trimMargin().decodeBase64ToArray()!!

        val bindingCertificate = """
            MIIBVTCB/aADAgECAgjzE8vxhgUZnDAKBggqhkjOPQQDAjBLMUkwRwYDVQQDDEBGQTc0OEY4MDc1NERE
            QTgwMkVBODY1MDJERTYyMThDQUFFNkEyMUFBRUQ5MzQzQTBCQ0RFMTY5Mzg3QkQxNDk3MB4XDTIyMDUw
            NTEyMzUxOFoXDTIyMTEwMzEyMzUxOFowGDEWMBQGA1UEAwwNV2FsbGV0QmFja2VuZDBZMBMGByqGSM49
            AgEGCCqGSM49AwEHA0IABLN27MTzPSExQ3t57YTosCzDUqgSlzUBg1RhYnARckIkKBxPIiv11zEcbi5f
            35155jv7q+zn0puI8wxEacT1o6cwCgYIKoZIzj0EAwIDRwAwRAIgb+1T1tKzM7ev3gaG104/OwiCRlQZ
            58FGXfQa3hzx6esCIGmroKWno+R0Ist640lkO0oDsr+TcWpM93GKZPDO7WUc
        """.trimMargin().decodeBase64ToArray()!!

        val bindingCert = CertificateFactory.getInstance("X.509")
            .generateCertificate(bindingCertificate.inputStream()) as X509Certificate

        service.verifyAttestationClient(
            listOf(attestationStatement),
            bindingCert,
            Date.from(Instant.parse("2022-05-05T12:37:00Z"))
        ) shouldBe true
    }

}