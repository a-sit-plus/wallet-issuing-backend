package at.asitplus.wallet.backend.model

import org.hibernate.annotations.CreationTimestamp
import org.springframework.data.annotation.CreatedDate
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table
import java.time.LocalDateTime





@Entity
@Table
data class Identifier(
    @Column val vcId: String,
    @Column var revoked: Boolean,
    @Column var attributeName: String,
    @Column var subjectId: String
) {
    @Id
    @GeneratedValue
    var id: Long? = null

    @Column
    @GeneratedValue
    val revocationListIndex: Long = 0L

    @Column
    @CreationTimestamp
    val createdOn: LocalDateTime? = null
}
