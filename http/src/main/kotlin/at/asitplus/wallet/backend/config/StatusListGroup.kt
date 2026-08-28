package at.asitplus.wallet.backend.config

import at.asitplus.wallet.backend.Paths
import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.agent.StatusListAgent

/**
 * One credential signing key, together with the status list it publishes.
 *
 * The default group (with a `null` [credentialIdentifier]) signs everything not listed in
 * [BackendConfigurationProperties.credentialKeys] and keeps the legacy status list URL and cache path, so credentials
 * issued by earlier versions keep resolving. Every other group is addressed by a [slug] derived from the credential
 * identifier it was configured for.
 *
 * All groups share one status list index space (see [at.asitplus.wallet.backend.service.DefaultRevocationService]), so
 * a group's list may carry bits for indices issued in another group. That is harmless: no credential in this group
 * references those indices, so they can never cause a false revocation.
 * ponytail: shared index space across groups; per-group indices would need a `status_list_id` column on
 * `IssuedCredential`/`RevokedCredential` and a new primary key for the latter.
 */
data class StatusListGroup(
    /** `vct` or ISO docType this group signs, `null` for the default group. */
    val credentialIdentifier: String?,
    /** URL and cache path segment, empty for the default group. */
    val slug: String,
    val keyMaterial: KeyMaterial,
    val statusListAgent: StatusListAgent,
) {
    val isDefault get() = credentialIdentifier == null

    companion object {
        /** Path under which a group's status lists are served, i.e. the base URL for `{timePeriod}`. */
        fun statusListPath(slug: String) = Paths.Credentials.StatusUrl + if (slug.isEmpty()) "" else "/$slug"
    }
}

/**
 * Holds every configured [StatusListGroup]. Injected as a single bean rather than a `List`, so Spring does not confuse
 * it with a collection of [StatusListGroup] beans.
 */
class StatusListGroups(val all: List<StatusListGroup>) {

    init {
        all.groupBy { it.slug }.forEach { (slug, sharing) ->
            require(sharing.size == 1) {
                "Credentials ${sharing.map { it.credentialIdentifier }} all map to status list path '$slug'"
            }
        }
    }

    val default: StatusListGroup = all.single { it.isDefault }

    /** The group signing [credentialIdentifier], falling back to [default] for credentials without their own key. */
    fun forCredential(credentialIdentifier: String?): StatusListGroup =
        all.firstOrNull { it.credentialIdentifier != null && it.credentialIdentifier == credentialIdentifier }
            ?: default

    fun bySlug(slug: String): StatusListGroup? = all.firstOrNull { it.slug == slug }
}

/**
 * Turns a credential identifier into a path segment, e.g. `urn:eudi:pid:1` into `urn-eudi-pid-1`. Callers must check
 * that the result stays collision-free across all configured identifiers.
 */
fun String.toStatusListSlug(): String = replace(Regex("[^A-Za-z0-9._-]"), "-")

/** Fails with a readable message if `backend.credential-keys` names a credential this issuer does not offer. */
fun requireKnownCredentialIdentifiers(configured: Set<String>, known: Set<String>) {
    val unknown = configured - known
    require(unknown.isEmpty()) {
        "backend.credential-keys contains unknown credentials $unknown, expected some of $known"
    }
}
