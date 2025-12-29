package com.hasanDroid.le5ascorer.data.repository

import com.hasanDroid.le5ascorer.data.local.dao.*
import com.hasanDroid.le5ascorer.data.local.entity.*
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
    private val scoreActionDao: ScoreActionDao
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

    // Match operations
    suspend fun createMatch(
        player1Name: String,
        player2Name: String,
        player3Name: String,
        player4Name: String,
        terminalScore: Int,
        scoreRule: ScoreRule
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

    fun getMatchesByStatus(status: MatchStatus): Flow<List<Match>> {
        // IMPORTANT: `leadingPlayerName/leadingScore` are derived from rounds + score actions.
        // Previously we used only `matchDao.getMatchesByStatus()`; that Flow does NOT re-emit
        // when rounds/actions change, so the RecyclerView never got updated until restart.
        //
        // We combine with lightweight table Flows to force re-emission whenever the underlying
        // scoring data changes.
        return combine(
            matchDao.getMatchesByStatus(status),
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

                if (roundCount > 0) {
                    val rounds = roundDao.getRoundsByMatchIdSync(matchEntity.id).map { roundEntity ->
                        val actions = scoreActionDao.getActionsByRoundIdSync(roundEntity.id).map {
                            ScoreAction(it.id, it.roundId, it.receiverIndex, it.actionType, it.delta)
                        }
                        Round(roundEntity.id, roundEntity.matchId, roundEntity.roundIndex, actions)
                    }

                    // Calculate cumulative scores for each player
                    val cumulativeScores = IntArray(4) { 0 }
                    rounds.sortedBy { it.roundIndex }.forEach { round ->
                        round.actions.forEach { action ->
                            if (action.receiverIndex in 0..3) {
                                cumulativeScores[action.receiverIndex] += action.delta
                            }
                        }
                    }

                    // Find leader based on score rule - HIGHEST score leads
                    val maxScore: Int
                    val leadingIndex: Int

                    when (matchEntity.scoreRule) {
                        ScoreRule.INDIVIDUAL -> {
                            // Highest individual score is leading
                            leadingIndex = cumulativeScores.indices.maxByOrNull { cumulativeScores[it] } ?: 0
                            maxScore = cumulativeScores[leadingIndex]
                        }
                        ScoreRule.TEAM -> {
                            // Highest team score is leading (player 0+1 vs 2+3)
                            val team1Score = cumulativeScores[0] + cumulativeScores[1]
                            val team2Score = cumulativeScores[2] + cumulativeScores[3]

                            if (team1Score >= team2Score) {
                                // Team 1 has higher score - show player with higher score in team
                                leadingIndex = if (cumulativeScores[0] >= cumulativeScores[1]) 0 else 1
                                maxScore = team1Score
                            } else {
                                // Team 2 has higher score
                                leadingIndex = if (cumulativeScores[2] >= cumulativeScores[3]) 2 else 3
                                maxScore = team2Score
                            }
                        }
                    }

                    leadingPlayerName = players.getOrNull(leadingIndex)?.name
                    leadingScore = maxScore
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
                    leadingScore = leadingScore
                )
            }
        }
    }

    fun observeMatchDetail(matchId: Long): Flow<MatchDetail?> {
        return combine(
            matchDao.observeMatchById(matchId),
            roundDao.getRoundsByMatchId(matchId)
        ) { matchEntity, roundEntities ->
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
                roundCount = rounds.size
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
            roundCount = roundCount
        )
    }

    // Round operations
    suspend fun addRound(matchId: Long, actions: List<ScoreAction>) {
        val roundCount = roundDao.getRoundCount(matchId)
        val roundEntity = RoundEntity(
            matchId = matchId,
            roundIndex = roundCount
        )

        val roundId = roundDao.insert(roundEntity)

        val actionEntities = actions.map {
            ScoreActionEntity(
                roundId = roundId,
                receiverIndex = it.receiverIndex,
                actionType = it.actionType,
                delta = it.delta
            )
        }

        scoreActionDao.insertAll(actionEntities)
    }

    suspend fun updateRound(roundId: Long, actions: List<ScoreAction>) {
        // Delete old actions
        scoreActionDao.deleteByRoundId(roundId)

        // Insert new actions
        val actionEntities = actions.map {
            ScoreActionEntity(
                roundId = roundId,
                receiverIndex = it.receiverIndex,
                actionType = it.actionType,
                delta = it.delta
            )
        }

        scoreActionDao.insertAll(actionEntities)
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
}
