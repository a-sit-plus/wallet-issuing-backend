package at.asitplus.wallet.backend.model

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table


@Entity
@Table
data class Identifier(
    @Column val key: String,
    @Column var revoked: Boolean
) {
    @Id
    @GeneratedValue
    var id: Long? = null

    @Column
    @GeneratedValue
    val revocationListIndex: Long = 0L
}
