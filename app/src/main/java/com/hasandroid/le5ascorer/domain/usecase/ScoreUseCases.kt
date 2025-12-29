package com.hasandroid.le5ascorer.domain.usecase

import com.hasandroid.le5ascorer.data.repository.LeekhaRepository
import com.hasandroid.le5ascorer.domain.ScoreEngine
import com.hasandroid.le5ascorer.domain.model.MatchDetail
import com.hasandroid.le5ascorer.domain.model.Round
import com.hasandroid.le5ascorer.domain.model.ScoreAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetMatchDetailUseCase @Inject constructor(
    private val repository: LeekhaRepository,
    private val scoreEngine: ScoreEngine
) {
    operator fun invoke(matchId: Long): Flow<MatchDetail?> {
        return repository.observeMatchDetail(matchId).map { detail ->
            detail?.let {
                val scoreboard = scoreEngine.calculateScoreboard(it.match, it.rounds)
                it.copy(scoreboard = scoreboard)
            }
        }
    }
}

class AddRoundUseCase @Inject constructor(
    private val repository: LeekhaRepository,
    private val scoreEngine: ScoreEngine
) {
    suspend operator fun invoke(matchId: Long, actions: List<ScoreAction>) {
        repository.addRound(matchId, actions)

        // Check if match should be completed
        val match = repository.getMatchById(matchId) ?: return
        val matchDetail = repository.observeMatchDetail(matchId)

        // This will be checked in the ViewModel after adding the round
    }
}

class UpdateRoundUseCase @Inject constructor(
    private val repository: LeekhaRepository
) {
    suspend operator fun invoke(roundId: Long, actions: List<ScoreAction>) {
        repository.updateRound(roundId, actions)
    }
}

class GetRoundUseCase @Inject constructor(
    private val repository: LeekhaRepository
) {
    suspend operator fun invoke(roundId: Long): Round? {
        return repository.getRoundWithActions(roundId)
    }
}

class CompleteMatchUseCase @Inject constructor(
    private val repository: LeekhaRepository
) {
    suspend operator fun invoke(matchId: Long) {
        repository.completeMatch(matchId)
    }
}

class ReopenMatchUseCase @Inject constructor(
    private val repository: LeekhaRepository
) {
    suspend operator fun invoke(matchId: Long) {
        repository.reopenMatch(matchId)
    }
}

