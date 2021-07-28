package at.asitplus.wallet.backend.model

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table


@Entity
@Table
data class Identifier(@Id val key: String, @Column var revoked: Boolean)
