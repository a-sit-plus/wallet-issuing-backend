package at.asitplus.wallet.backend.service

import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.lib.agent.TimePeriodProvider
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.random.Random

class RevocationListSchedulerTest {

    private val writer = mock<RevocationListWriter>()
    private val listOfTimePeriods = listOf(Random.nextInt(), Random.nextInt(), Random.nextInt())
    private val timePeriodProvider = object : TimePeriodProvider {
        override fun getCurrentTimePeriod(clock: Clock): Int = listOfTimePeriods.first()
        override fun getRelevantTimePeriods(clock: Clock): List<Int> = listOfTimePeriods
        override fun getTimePeriodFor(instant: Instant): Int = listOfTimePeriods.first()
    }
    private val scheduler = RevocationListScheduler(
        writer,
        timePeriodProvider,
        BackendConfigurationProperties(),
        mock(),
    ).also { it.postConstruct() }

    @Test
    fun testDirty() {
        val timePeriod = Random.nextInt()
        scheduler.onApplicationEvent(RevocationEvent(this, timePeriod))
        scheduler.writeDirtyRevocationList()

        verify(writer).writeRevocationList(eq(timePeriod))
    }

    @Test
    fun testRegular() {
        scheduler.writeRegularRevocationList()

        listOfTimePeriods.forEach {
            verify(writer).writeRevocationList(eq(it))
        }
    }

}