# Trix (Partnership) — Design

Date: 2026-09-30
Status: approved in conversation, pending written-spec review

## Goal

Add Partnership Trix as a third playable game, following the flow in the
reference iOS screenshots (IMG_7284–7297): setup → 7♥ holder → kingdom screen →
per-contract entry → running team score, with doubling and early game over.

Out of scope: standard (individual, no-partnership) Trix, "Trix Complex".

## Rules implemented

- Two teams: Team A = seats 1+2, Team B = seats 3+4 (partners sit opposite).
- 4 kingdoms × 5 contracts = 20 contracts. Each contract is played exactly once
  per kingdom; the kingdom owner picks the order.
- The 7♥ holder owns kingdom 1. Ownership rotates through seats
  `[P1, P3, P2, P4]` cyclically, starting at the 7♥ holder.
- Scoring (per team; a full kingdom always nets to zero):

| Contract | Not doubled | Doubled (only if Doubling is on) |
|---|---|---|
| King of Hearts | taker −75 | taker −150, other team +75 |
| Queens (each of 4) | taker −25 | taker −50, other team +25 |
| Diamonds | −10 per diamond, 13 total | never doubled |
| Ltoosh | −15 per trick, 13 total | never doubled |
| Trix | 1st +200, 2nd +150, 3rd +100, 4th +50 to the player's team | never doubled |

  Doubling gives the bonus to "the other team", as in the user's rules and the
  screenshots. The generic rule ("the doubler gains") only differs when the
  doubler's own team takes the card; that case is not modelled.

- Early game over: after each contract completed in kingdom 3 or 4
  (`roundIndex >= 10`), sum the maximum swing of every contract not yet played
  in the whole game. If `|gapA−B| > ceiling` the game ends; equal continues.
  Maximum swing (how far one contract can move the gap toward the trailer):

| Contract | Doubling off | Doubling on |
|---|---|---|
| King of Hearts | 75 | 225 |
| Queens | 100 | 300 |
| Diamonds | 130 | 130 |
| Ltoosh | 195 | 195 |
| Trix | 200 | 200 |

- Game ends after 20 contracts or early game over. Higher total wins; equal
  totals are a draw and both teams are shown as winners.

## Data model — no schema change

Same approach as Tarneeb (see `ActionType.kt`): the database uses
`fallbackToDestructiveMigration()`, so no columns or tables are added.

- `ScoreRule.TRIX` — new enum value on the existing TEXT column.
- Match: four real players in `player1Id..player4Id` (Team A = 1,2; Team B = 3,4).
  `terminalScore` is unused by Trix and stores the setup:
  `terminalScore = openerSeat + (if (doubling) 4 else 0)`, `openerSeat` in 0..3.
  A small `TrixSetup` value class encodes/decodes it; comment explains why.
- One `RoundEntity` per contract played; `roundIndex` 0..19, kingdom =
  `roundIndex / 5`, owner seat = `ROTATION[(ROTATION.indexOf(opener) + kingdom) % 4]`
  with `ROTATION = [0, 2, 1, 3]`.
- New `ActionType` values; contract type is implied by which ones a round holds:
  - `TRIX_KING` ×1 — receiverIndex = team that took it (0/1), delta = doubled (0/1)
  - `TRIX_QUEEN` ×4 — receiverIndex = team, delta = `suit * 2 + doubled`
    (suit 0 ♠, 1 ♥, 2 ♦, 3 ♣)
  - `TRIX_DIAMONDS` ×2 — receiverIndex = team, delta = diamonds taken
  - `TRIX_LTOOSH` ×2 — receiverIndex = team, delta = tricks taken
  - `TRIX_POSITION` ×4 — receiverIndex = seat (0..3), delta = place (1..4)
- Points are never stored; `TrixScoreEngine` derives them so edits recompute.
- Repository: `createTrixMatch(names[4], openerSeat, doubling)`,
  `addTrixRound(matchId, contract)`, `updateTrixRound(roundId, contract)`,
  mirroring the Tarneeb methods. `getMatchesByStatus` gets a TRIX branch for
  list totals.

## Domain

`TrixScoreEngine` (pure Kotlin, `@Inject constructor()`):

- `sealed interface TrixContract` — `King(team, doubled)`,
  `Queens(List<QueenTake(team, doubled)>)` (index = suit), `Diamonds(counts)`,
  `Ltoosh(counts)`, `Trix(places: IntArray by seat)`; plus a `ContractType` enum.
- `readRound(actions): TrixContract?` — null (and skipped) when actions don't
  form a valid contract (wrong counts, totals ≠ 13, places not a permutation).
- `scoreContract(contract, doubling): IntArray` — team points `[a, b]`.
  Doubled flags are ignored when doubling is off.
- `calculateScoreboard(rounds, setup): TrixScoreboard` — running totals,
  history rows (contract, owner seat, round points, totals), current kingdom,
  current owner seat, contract types already played in the current kingdom,
  `isGameOver`, `winner` (0, 1, or null for draw).
- `isEarlyGameOver(totals, playedCount, remaining types, doubling)` as specified
  above.

## UI

Reuses existing styles, button colours, and autocomplete from Leekha/Tarneeb.

1. **Home** — the `tileHand` tile becomes Trix (`ic_star`), opens the match list
   filtered to `ScoreRule.TRIX` like Tarneeb.
2. **`NewTrixMatchFragment`** — Team A two fields, Team B two fields (player
   autocomplete), Doubling switch. *Start Game* switches the same screen to
   "Who has the 7 of Hearts?" with four player buttons; the second *Start Game*
   (enabled after a pick) creates the match and navigates to the scoreboard.
   Back on step 2 returns to step 1.
3. **`TrixScoreboardFragment`** — totals card (team names "A & B", negative in
   red), chevron expands contract history; tapping a history row edits it.
   Header "{Owner}'s Kingdom" + "Kingdom n of 4". Five contract rows with
   rule subtitles (doubled text only when doubling is on); played rows dimmed
   and disabled; tapping an unplayed row selects it. *Enter Round* disabled until
   a selection. When the game is over: match completed, existing
   `EndGameDialogFragment` shows winner or draw.
4. **`TrixRoundEntryFragment`** — one fragment, argument = contract type (and
   optional roundId for edit); shows one panel:
   - King: Team A / Team B cards with live points preview; *Doubled? No / Yes (×2)*
     only when doubling is on.
   - Queens: four suit rows, each with team choice and *Doubled?* when on.
   - Diamonds / Ltoosh: two team steppers (0..13) and an "x / 13" badge.
   - Trix: four player rows, 1st–4th chips with points; choosing a place
     already taken moves it to the new player.
   *Confirm Round* enabled only when the entry is complete and valid.
5. **Match list** — Trix matches show "A & B vs C & D" with team totals.
6. **Rules** — Trix section from the user's rules text.

Editing a past contract recomputes everything; if the game is no longer over,
the match is reopened (`reopenMatch`); if it now is, it is completed.

## Testing

JUnit tests for `TrixScoreEngine`:

- Screenshot game replay: King A doubled → −150/75; Queens (♠B dbl, ♥A, ♦B dbl,
  ♣A dbl) → −175/0; Ltoosh 4/9 → −235/−135; Trix Hasan 4th, Ali 1st, Hsen 2nd,
  Meso 3rd → −35/165; Diamonds 5/8 → −85/85; next owner = Hsen (kingdom 2).
- Each contract with and without doubling; doubled flag ignored when off.
- Rotation from each opener seat.
- Early game over: never before roundIndex 10; gap == ceiling continues;
  gap == ceiling + 1 ends.
- `readRound` rejects malformed rounds.
