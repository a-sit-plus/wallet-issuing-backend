package at.asitplus.wallet.backend.model

import javax.persistence.*


@Entity
@Table
data class Identifier(
    @Column val key: String,
    @Column var revoked: Boolean
) {
    @Id
    @GeneratedValue
    var id: Long? = null
}
