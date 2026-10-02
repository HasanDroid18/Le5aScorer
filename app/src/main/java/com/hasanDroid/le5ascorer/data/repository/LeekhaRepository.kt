package com.hasanDroid.le5ascorer.data.repository

import androidx.room.withTransaction
import com.hasanDroid.le5ascorer.data.local.LeekhaDatabase
import com.hasanDroid.le5ascorer.data.local.dao.*
import com.hasanDroid.le5ascorer.data.local.entity.*
import com.hasanDroid.le5ascorer.domain.TarneebScoreEngine
import com.hasanDroid.le5ascorer.domain.TrixContract
import com.hasanDroid.le5ascorer.domain.TrixScoreEngine
import com.hasanDroid.le5ascorer.domain.TrixSetup
import com.hasanDroid.le5ascorer.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LeekhaRepository @Inject constructor(
    private val playerDao: PlayerDao,
    private val matchDao: MatchDao,
    private val roundDao: RoundDao,
    private val scoreActionDao: ScoreActionDao,
    private val tarneebScoreEngine: TarneebScoreEngine,
    private val trixScoreEngine: TrixScoreEngine,
    private val database: LeekhaDatabase
) {

    // Player operations
    suspend fun getOrCreatePlayer(name: String): Long {
        val existing = playerDao.getPlayerByName(name)
        return if (existing != null) {
            playerDao.updateLastUsed(existing.id)
            existing.id
        } else {
            playerDao.insert(PlayerEntity(name = name))
        }
    }

    fun searchPlayers(query: String): Flow<List<Player>> {
        return playerDao.searchPlayersByName(query).map { entities ->
            entities.map { Player(it.id, it.name) }
        }
    }

    fun getAllPlayers(): Flow<List<Player>> {
        return playerDao.getAllPlayers().map { entities ->
            entities.map { Player(it.id, it.name) }
        }
    }

    /**
     * Past names for one game only. Leekha's chips must not offer Tarneeb team
     * names, and vice versa; see PlayerDao.getPlayersForRule.
     */
    fun getPlayersForRule(scoreRule: ScoreRule): Flow<List<Player>> {
        return playerDao.getPlayersForRule(scoreRule).map { entities ->
            entities.map { Player(it.id, it.name) }
        }
    }

    // Match operations
    suspend fun createMatch(
        player1Name: String,
        player2Name: String,
        player3Name: String,
        player4Name: String,
        terminalScore: Int,
        scoreRule: ScoreRule = ScoreRule.INDIVIDUAL
    ): Long {
        val player1Id = getOrCreatePlayer(player1Name)
        val player2Id = getOrCreatePlayer(player2Name)
        val player3Id = getOrCreatePlayer(player3Name)
        val player4Id = getOrCreatePlayer(player4Name)

        val match = MatchEntity(
            terminalScore = terminalScore,
            scoreRule = scoreRule,
            player1Id = player1Id,
            player2Id = player2Id,
            player3Id = player3Id,
            player4Id = player4Id
        )

        return matchDao.insert(match)
    }

    suspend fun duplicateMatch(matchId: Long): Long? {
        val match = matchDao.getMatchById(matchId) ?: return null

        val newMatch = match.copy(
            id = 0,
            createdAt = System.currentTimeMillis(),
            completedAt = null,
            status = MatchStatus.IN_PROGRESS
        )

        return matchDao.insert(newMatch)
    }

    suspend fun deleteMatch(matchId: Long) {
        matchDao.deleteById(matchId)
    }

    suspend fun completeMatch(matchId: Long) {
        val match = matchDao.getMatchById(matchId) ?: return
        matchDao.update(
            match.copy(
                completedAt = System.currentTimeMillis(),
                status = MatchStatus.COMPLETED
            )
        )
    }

    suspend fun reopenMatch(matchId: Long) {
        val match = matchDao.getMatchById(matchId) ?: return
        matchDao.update(
            match.copy(
                completedAt = null,
                status = MatchStatus.IN_PROGRESS
            )
        )
    }

    suspend fun saveLoserImage(matchId: Long, imagePath: String) {
        val match = matchDao.getMatchById(matchId) ?: return
        matchDao.update(
            match.copy(loserImagePath = imagePath)
        )
    }

    fun getMatchesByStatus(
        status: MatchStatus,
        scoreRule: ScoreRule = ScoreRule.INDIVIDUAL
    ): Flow<List<Match>> {
        // IMPORTANT: `leadingPlayerName/leadingScore` are derived from rounds + score actions.
        // Previously we used only `matchDao.getMatchesByStatus()`; that Flow does NOT re-emit
        // when rounds/actions change, so the RecyclerView never got updated until restart.
        //
        // We combine with lightweight table Flows to force re-emission whenever the underlying
        // scoring data changes.
        return combine(
            matchDao.getMatchesByStatusAndRule(status, scoreRule),
            roundDao.observeAllRounds(),
            scoreActionDao.observeAllScoreActions()
        ) { matchEntities, _, _ ->
            matchEntities.map { matchEntity ->
                val players = listOfNotNull(
                    playerDao.getPlayerById(matchEntity.player1Id),
                    playerDao.getPlayerById(matchEntity.player2Id),
                    playerDao.getPlayerById(matchEntity.player3Id),
                    playerDao.getPlayerById(matchEntity.player4Id)
                ).map { Player(it.id, it.name) }

                val roundCount = roundDao.getRoundCount(matchEntity.id)

                // Calculate leading player/score if there are rounds
                var leadingPlayerName: String? = null
                var leadingScore: Int? = null
                // Already computed below for the leader; kept so the match card
                // can show all four standings without a second pass.
                var playerScores: List<Int> = emptyList()

                if (roundCount > 0) {
                    val rounds = roundDao.getRoundsByMatchIdSync(matchEntity.id).map { roundEntity ->
                        val actions = scoreActionDao.getActionsByRoundIdSync(roundEntity.id).map {
                            ScoreAction(it.id, it.roundId, it.receiverIndex, it.actionType, it.delta)
                        }
                        Round(roundEntity.id, roundEntity.matchId, roundEntity.roundIndex, actions)
                    }

                    // Totals depend on which game this is. Leekha's deltas are
                    // points and simply add up; Tarneeb's are bids and trick
                    // counts, which are meaningless summed and have to be run
                    // through the scoring rules first. Summing them blindly —
                    // which is what this did before Tarneeb existed — would put
                    // nonsense on every Tarneeb card.
                    // Trix stores contracts, not points, and scores per team.
                    val cumulativeScores: List<Int> = when (matchEntity.scoreRule) {
                        ScoreRule.TARNEEB ->
                            tarneebScoreEngine.calculateScoreboard(rounds)
                                .lastOrNull()?.cumulative
                                ?: List(TarneebScoreEngine.TEAMS) { 0 }
                        ScoreRule.TRIX ->
                            trixScoreEngine.calculateScoreboard(
                                rounds, TrixSetup.decode(matchEntity.terminalScore)
                            ).totals
                        ScoreRule.INDIVIDUAL -> {
                            val totals = IntArray(4)
                            rounds.sortedBy { it.roundIndex }.forEach { round ->
                                round.actions.forEach { action ->
                                    if (action.receiverIndex in 0..3) {
                                        totals[action.receiverIndex] += action.delta
                                    }
                                }
                            }
                            totals.toList()
                        }
                    }

                    // Who is "leading" also flips with the game. Leekha's target
                    // is what you are trying to avoid, so the most points is the
                    // worst place to be; Tarneeb's target is what you are racing
                    // toward, so the most points is the best. Either way this is
                    // the standing the card highlights.
                    val leadingIndex = cumulativeScores.indices
                        .maxByOrNull { cumulativeScores[it] } ?: 0
                    playerScores = cumulativeScores

                    leadingPlayerName = players.getOrNull(leadingIndex)?.name
                    leadingScore = cumulativeScores.getOrNull(leadingIndex)
                }

                Match(
                    id = matchEntity.id,
                    createdAt = matchEntity.createdAt,
                    completedAt = matchEntity.completedAt,
                    terminalScore = matchEntity.terminalScore,
                    scoreRule = matchEntity.scoreRule,
                    players = players,
                    status = matchEntity.status,
                    roundCount = roundCount,
                    leadingPlayerName = leadingPlayerName,
                    leadingScore = leadingScore,
                    playerScores = playerScores,
                    loserImagePath = matchEntity.loserImagePath
                )
            }
        }
    }

    fun observeMatchDetail(matchId: Long): Flow<MatchDetail?> {
        // The score_actions flow is here to make editing a round visible.
        //
        // Room re-emits a Flow only when a table that flow's own query reads is
        // written. The actions below are fetched imperatively inside this block,
        // so score_actions was in nobody's observed set — and editing a round
        // writes *only* score_actions. The save succeeded, the screen never
        // changed, and the corrected figures appeared only after adding another
        // round or leaving and re-entering the match.
        //
        // getMatchesByStatus already carries the same third flow for the same
        // reason; this one was missed. It affects both games.
        return combine(
            matchDao.observeMatchById(matchId),
            roundDao.getRoundsByMatchId(matchId),
            scoreActionDao.observeAllScoreActions()
        ) { matchEntity, roundEntities, _ ->
            if (matchEntity == null) return@combine null

            val players = listOfNotNull(
                playerDao.getPlayerById(matchEntity.player1Id),
                playerDao.getPlayerById(matchEntity.player2Id),
                playerDao.getPlayerById(matchEntity.player3Id),
                playerDao.getPlayerById(matchEntity.player4Id)
            ).map { Player(it.id, it.name) }

            val rounds = roundEntities.map { roundEntity ->
                val actions = scoreActionDao.getActionsByRoundIdSync(roundEntity.id).map {
                    ScoreAction(it.id, it.roundId, it.receiverIndex, it.actionType, it.delta)
                }
                Round(roundEntity.id, roundEntity.matchId, roundEntity.roundIndex, actions)
            }

             val match = Match(
                 id = matchEntity.id,
                 createdAt = matchEntity.createdAt,
                 completedAt = matchEntity.completedAt,
                 terminalScore = matchEntity.terminalScore,
                 scoreRule = matchEntity.scoreRule,
                 players = players,
                 status = matchEntity.status,
                 roundCount = rounds.size,
                 loserImagePath = matchEntity.loserImagePath
             )

            MatchDetail(match, rounds, emptyList())
        }
    }

    suspend fun getMatchById(matchId: Long): Match? {
        val matchEntity = matchDao.getMatchById(matchId) ?: return null

        val players = listOfNotNull(
            playerDao.getPlayerById(matchEntity.player1Id),
            playerDao.getPlayerById(matchEntity.player2Id),
            playerDao.getPlayerById(matchEntity.player3Id),
            playerDao.getPlayerById(matchEntity.player4Id)
        ).map { Player(it.id, it.name) }

        val roundCount = roundDao.getRoundCount(matchEntity.id)

        return Match(
            id = matchEntity.id,
            createdAt = matchEntity.createdAt,
            completedAt = matchEntity.completedAt,
            terminalScore = matchEntity.terminalScore,
            scoreRule = matchEntity.scoreRule,
            players = players,
            status = matchEntity.status,
            roundCount = roundCount,
            loserImagePath = matchEntity.loserImagePath
        )
    }

    // Round operations
    /**
     * Appends a round and its actions as one unit.
     *
     * Transactional because a round row with no actions is not a partial save,
     * it is a corrupt one: Tarneeb drops such a round from the scoreboard
     * entirely and Leekha scores it as zero, and neither is recoverable through
     * the UI. This runs on viewModelScope, which is cancelled the moment the
     * user leaves the screen, so being interrupted between the two writes is a
     * real possibility rather than a theoretical one.
     *
     * The count-then-insert of roundIndex is inside the transaction for the same
     * reason — two rounds saved at once would otherwise be able to claim the
     * same index.
     */
    suspend fun addRound(matchId: Long, actions: List<ScoreAction>) = database.withTransaction {
        val roundEntity = RoundEntity(
            matchId = matchId,
            roundIndex = roundDao.getRoundCount(matchId)
        )

        val roundId = roundDao.insert(roundEntity)
        scoreActionDao.insertAll(actions.toEntities(roundId))
    }

    /**
     * Replaces a round's actions in place, keeping its row and its position.
     *
     * Transactional for the reason above, and more sharply so: this deletes
     * before it inserts, so an interruption in between leaves the round with no
     * actions at all and silently removes it from the scoreboard.
     */
    suspend fun updateRound(roundId: Long, actions: List<ScoreAction>) =
        database.withTransaction {
            scoreActionDao.deleteByRoundId(roundId)
            scoreActionDao.insertAll(actions.toEntities(roundId))
        }

    private fun List<ScoreAction>.toEntities(roundId: Long): List<ScoreActionEntity> = map {
        ScoreActionEntity(
            roundId = roundId,
            receiverIndex = it.receiverIndex,
            actionType = it.actionType,
            delta = it.delta
        )
    }

    suspend fun getRoundWithActions(roundId: Long): Round? {
        val roundEntity = roundDao.getRoundById(roundId) ?: return null
        val actions = scoreActionDao.getActionsByRoundIdSync(roundId).map {
            ScoreAction(it.id, it.roundId, it.receiverIndex, it.actionType, it.delta)
        }

        return Round(
            id = roundEntity.id,
            matchId = roundEntity.matchId,
            roundIndex = roundEntity.roundIndex,
            actions = actions
        )
    }

    // ==================== Tarneeb ====================

    /**
     * A Tarneeb match: two partnerships rather than four individuals.
     *
     * The two team names go in the first two player columns and the other two
     * are left at 0. No row ever has id 0 — autoGenerate starts at 1 — so the
     * getPlayerById lookups that build a Match already return null for them and
     * listOfNotNull yields exactly two entries with no special-casing.
     *
     * Reusing the columns rather than adding new ones is deliberate: this
     * database runs fallbackToDestructiveMigration() with no committed schema,
     * and AppModule deletes the database when a migration fails, so an
     * unverifiable schema change risks every stored match.
     */
    suspend fun createTarneebMatch(
        teamAName: String,
        teamBName: String,
        terminalScore: Int
    ): Long {
        val teamAId = getOrCreatePlayer(teamAName)
        val teamBId = getOrCreatePlayer(teamBName)

        return matchDao.insert(
            MatchEntity(
                terminalScore = terminalScore,
                scoreRule = ScoreRule.TARNEEB,
                player1Id = teamAId,
                player2Id = teamBId,
                player3Id = UNUSED_SEAT,
                player4Id = UNUSED_SEAT
            )
        )
    }

    /** Encodes one Tarneeb round as the three actions described on ActionType. */
    suspend fun addTarneebRound(matchId: Long, bidderTeam: Int, bid: Int, tricks: List<Int>) {
        addRound(matchId, tarneebActions(bidderTeam, bid, tricks))
    }

    /** Replaces a Tarneeb round in place, keeping its position in the match. */
    suspend fun updateTarneebRound(roundId: Long, bidderTeam: Int, bid: Int, tricks: List<Int>) {
        updateRound(roundId, tarneebActions(bidderTeam, bid, tricks))
    }

    private fun tarneebActions(bidderTeam: Int, bid: Int, tricks: List<Int>): List<ScoreAction> =
        listOf(
            ScoreAction(
                roundId = 0,
                receiverIndex = bidderTeam,
                actionType = ActionType.TARNEEB_BID,
                delta = bid
            )
        ) + tricks.mapIndexed { team, won ->
            ScoreAction(
                roundId = 0,
                receiverIndex = team,
                actionType = ActionType.TARNEEB_TRICKS,
                delta = won
            )
        }

    // ==================== Trix ====================

    /**
     * A Trix match: four players in seat order, Team A = seats 1+2 and
     * Team B = seats 3+4. terminalScore carries the setup; see TrixSetup.
     */
    suspend fun createTrixMatch(playerNames: List<String>, setup: TrixSetup): Long {
        val ids = playerNames.map { getOrCreatePlayer(it) }
        return matchDao.insert(
            MatchEntity(
                terminalScore = setup.encode(),
                scoreRule = ScoreRule.TRIX,
                player1Id = ids[0],
                player2Id = ids[1],
                player3Id = ids[2],
                player4Id = ids[3]
            )
        )
    }

    /** Appends one contract; its position decides which kingdom it belongs to. */
    suspend fun addTrixRound(matchId: Long, contract: TrixContract) {
        addRound(matchId, trixScoreEngine.toActions(contract))
    }

    /** Replaces a contract in place, keeping its position in the game. */
    suspend fun updateTrixRound(roundId: Long, contract: TrixContract) {
        updateRound(roundId, trixScoreEngine.toActions(contract))
    }

    private companion object {
        /**
         * Seats 3 and 4 of a Tarneeb match. Not a real player id, and never
         * resolves to a row, which is exactly what makes the two-team case fall
         * out of the existing four-seat lookups for free.
         */
        const val UNUSED_SEAT = 0L
    }
}
