package com.hasandroid.le5ascorer.domain.usecase

import com.hasandroid.le5ascorer.data.local.entity.MatchStatus
import com.hasandroid.le5ascorer.data.local.entity.ScoreRule
import com.hasandroid.le5ascorer.data.repository.LeekhaRepository
import com.hasandroid.le5ascorer.domain.model.Match
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetMatchesUseCase @Inject constructor(
    private val repository: LeekhaRepository
) {
    fun getInProgressMatches(): Flow<List<Match>> {
        return repository.getMatchesByStatus(MatchStatus.IN_PROGRESS)
    }

    fun getCompletedMatches(): Flow<List<Match>> {
        return repository.getMatchesByStatus(MatchStatus.COMPLETED)
    }
}

class CreateMatchUseCase @Inject constructor(
    private val repository: LeekhaRepository
) {
    suspend operator fun invoke(
        player1Name: String,
        player2Name: String,
        player3Name: String,
        player4Name: String,
        terminalScore: Int,
        scoreRule: ScoreRule
    ): Long {
        return repository.createMatch(
            player1Name, player2Name, player3Name, player4Name,
            terminalScore, scoreRule
        )
    }
}

class DeleteMatchUseCase @Inject constructor(
    private val repository: LeekhaRepository
) {
    suspend operator fun invoke(matchId: Long) {
        repository.deleteMatch(matchId)
    }
}

class DuplicateMatchUseCase @Inject constructor(
    private val repository: LeekhaRepository
) {
    suspend operator fun invoke(matchId: Long): Long? {
        return repository.duplicateMatch(matchId)
    }
}

class GetPlayerSuggestionsUseCase @Inject constructor(
    private val repository: LeekhaRepository
) {
    operator fun invoke(query: String): Flow<List<String>> {
        return repository.searchPlayers(query).map { players ->
            players.map { it.name }
        }
    }
}


class MatchUseCases @Inject constructor(
    private val getMatchesUseCase: GetMatchesUseCase,
    private val createMatchUseCase: CreateMatchUseCase,
    private val deleteMatchUseCase: DeleteMatchUseCase,
    private val duplicateMatchUseCase: DuplicateMatchUseCase,
    private val getPlayerSuggestionsUseCase: GetPlayerSuggestionsUseCase
) {
    fun getInProgressMatches(): Flow<List<Match>> = getMatchesUseCase.getInProgressMatches()

    fun getCompletedMatches(): Flow<List<Match>> = getMatchesUseCase.getCompletedMatches()

    suspend fun createMatch(
        player1Name: String,
        player2Name: String,
        player3Name: String,
        player4Name: String,
        terminalScore: Int,
        scoreRule: ScoreRule
    ): Long = createMatchUseCase(player1Name, player2Name, player3Name, player4Name, terminalScore, scoreRule)

    suspend fun deleteMatch(matchId: Long) = deleteMatchUseCase(matchId)

    suspend fun duplicateMatch(matchId: Long): Long? = duplicateMatchUseCase(matchId)

    fun getPlayerSuggestions(query: String): Flow<List<String>> = getPlayerSuggestionsUseCase(query)
}

