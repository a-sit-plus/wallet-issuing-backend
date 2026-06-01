package at.asitplus.wallet.backend.service

import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.lib.agent.TimePeriodProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant

class RevocationListSchedulerTest {

    private lateinit var writer: RevocationListWriter
    private lateinit var listOfTimePeriods: List<Int>
    private lateinit var timePeriodProvider: TimePeriodProvider
    private lateinit var scheduler: RevocationListScheduler

    @BeforeEach
    fun beforeEach() {
        writer = mock<RevocationListWriter>()
        listOfTimePeriods =
            listOf(Random.nextInt(0, Int.MAX_VALUE), Random.nextInt(0, Int.MAX_VALUE), Random.nextInt(0, Int.MAX_VALUE))
        timePeriodProvider = object : TimePeriodProvider {
            override fun getCurrentTimePeriod(clock: Clock): Int = listOfTimePeriods.first()
            override fun getRelevantTimePeriods(clock: Clock): List<Int> = listOfTimePeriods
            override fun getTimePeriodFor(instant: Instant): Int = listOfTimePeriods.first()
        }
        scheduler = RevocationListScheduler(
            writer,
            timePeriodProvider,
            BackendConfigurationProperties(),
            mock(),
            CoroutineScope(Dispatchers.Unconfined + SupervisorJob()),
        ).also { it.postConstruct() }
    }

    @Test
    fun testDirty() = runBlocking {
        val timePeriod = Random.nextInt()
        scheduler.onRevocationEvent(RevocationEvent(this@RevocationListSchedulerTest, timePeriod))
        scheduler.writeDirtyRevocationList()

        verify(writer).writeRevocationList(eq(timePeriod))
    }

    @Test
    fun testRegular() = runBlocking {
        scheduler.writeRegularRevocationList()

        listOfTimePeriods.forEach {
            verify(writer).writeRevocationList(eq(it))
        }
    }

}
