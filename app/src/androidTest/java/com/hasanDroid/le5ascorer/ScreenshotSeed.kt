package com.hasanDroid.le5ascorer

import android.content.Context
import com.hasanDroid.le5ascorer.data.local.entity.ActionType
import com.hasanDroid.le5ascorer.data.repository.LeekhaRepository
import com.hasanDroid.le5ascorer.domain.model.ScoreAction
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking

/**
 * Deterministic fixture data for the screenshot run.
 *
 * Instrumentation runs against the real @HiltAndroidApp application, so the
 * production repository can be pulled straight out of the singleton graph — no
 * HiltTestApplication and no dependency swapping required.
 *
 * SampleDataGenerator already exists but shuffles names and randomises scores,
 * which would make every screenshot differ from the last. These fixtures are
 * fixed so runs are comparable.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScreenshotSeedEntryPoint {
    fun repository(): LeekhaRepository
}

object ScreenshotSeed {

    private const val TERMINAL = 101

    /** A round where the four players take 13 hearts, the Q and the 10. */
    private fun round(
        hearts: List<Int>,
        queenTo: Int,
        tenTo: Int
    ): List<ScoreAction> {
        val actions = mutableListOf<ScoreAction>()
        hearts.forEachIndexed { index, count ->
            repeat(count) {
                actions += ScoreAction(
                    roundId = 0,
                    receiverIndex = index,
                    actionType = ActionType.HEART,
                    delta = 1
                )
            }
        }
        actions += ScoreAction(
            roundId = 0, receiverIndex = queenTo, actionType = ActionType.Q_SPADES, delta = 13
        )
        actions += ScoreAction(
            roundId = 0, receiverIndex = tenTo, actionType = ActionType.TEN_DIAMONDS, delta = 10
        )
        return actions
    }

    fun seed(context: Context) = runBlocking {
        val repository = EntryPointAccessors
            .fromApplication(context.applicationContext, ScreenshotSeedEntryPoint::class.java)
            .repository()

        // 1. An in-progress match with a few rounds, so the match card shows a
        //    leader and the scoreboard has columns to align.
        val inProgress = repository.createMatch(
            player1Name = "Ahmad",
            player2Name = "Sara",
            player3Name = "Khaled",
            player4Name = "Fatima",
            terminalScore = TERMINAL
        )
        repository.addRound(inProgress, round(hearts = listOf(5, 3, 4, 1), queenTo = 0, tenTo = 2))
        repository.addRound(inProgress, round(hearts = listOf(2, 6, 1, 4), queenTo = 1, tenTo = 3))
        repository.addRound(inProgress, round(hearts = listOf(1, 2, 7, 3), queenTo = 2, tenTo = 0))

        // 2. A second, untouched match so the list has more than one card.
        repository.createMatch(
            player1Name = "Hasan",
            player2Name = "Layla",
            player3Name = "Omar",
            player4Name = "Zahra",
            terminalScore = 51
        )

        inProgress
    }

    /**
     * Pushes an existing match past its terminal score so the end-game banner,
     * confetti and dialog can be captured.
     */
    fun seedToGameOver(context: Context, matchId: Long) = runBlocking {
        val repository = EntryPointAccessors
            .fromApplication(context.applicationContext, ScreenshotSeedEntryPoint::class.java)
            .repository()

        // Ahmad takes almost everything each round until he crosses 101.
        repeat(4) {
            repository.addRound(
                matchId,
                round(hearts = listOf(10, 1, 1, 1), queenTo = 0, tenTo = 0)
            )
        }
    }
}
