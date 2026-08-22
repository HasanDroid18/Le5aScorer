# Le5a Scorer App Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove team scoring, fix button consistency/accessibility across every screen, add player-name suggestions from history, redesign the round-entry action cards and the scoreboard, and finish with a responsive-layout pass.

**Architecture:** No structural rewrite. `ScoreRule.TEAM` becomes legacy-only (kept for backward compat, unreachable from new-match creation). Button fixes conform existing widget instances to the app's already-defined `Widget.Le5a.*` style system. Player-name suggestions wire the already-existing `PlayerDao.getAllPlayers()` into `AutoCompleteTextView`s — no new DB work. The round-entry redesign changes only the visual/animation layer of the four action cards (`cardHearts`/`cardQSpades`/`cardTenDiamonds`/`cardDouble`); `RoundEntryViewModel`'s state and validity logic are untouched. The scoreboard redesign adds a derived `leadingPlayerIndex` to `ScoreboardUiState` and new row/header rendering; `ScoreboardAdapter`'s diffing and `MatchDetail`/`RoundScores` data model are untouched. The responsive pass runs last and only adds `sw600dp`/`land` layout alternates where testing shows the phone-default layout actually breaks.

**Tech Stack:** Kotlin, Android Views + ViewBinding (no Compose), Hilt DI, Room, Material Components (MaterialButton/Card/TextInputLayout/ChipGroup), JUnit for unit tests, AndroidX instrumented tests for screenshots.

**Spec:** `docs/superpowers/specs/2026-08-22-app-overhaul-design.md`

## Global Constraints

- Reuse existing design tokens (`space_*`, `radius_*`, `elev_*`, `felt_*`/`brass_*`/`ink*`/`text_*` colors) — no new literals or ad hoc dp values.
- Every new/fixed icon-only tappable control gets a `contentDescription` following the existing `cd_*` string naming convention (e.g. `cd_match_options`, `cd_select_player`).
- `ScoreRule.TEAM` stays in the enum (legacy-only, never producible from new-match creation) — do not delete it or write a destructive Room migration for it.
- "Leading"/leader always means **highest cumulative score**, matching the existing match-list card convention (`LeekhaRepository.getMatchesByStatus`, comment "HIGHEST score leads") — the new scoreboard leader highlight must use the same definition, not `checkGameOver`'s "reaching target = loser" semantics.
- Don't "fix" the pre-existing TEAM-pairing inconsistency between `LeekhaRepository.getMatchesByStatus` (pairs 0+1 vs 2+3) and `ScoreEngine.checkGameOver` (pairs 0+2 vs 1+3) — leave both as-is since TEAM is legacy-only.
- Follow TDD for all ViewModel/domain logic changes: write the failing test first, then the minimal implementation.
- Commit after each task.

---

## Phase 1: Remove team scoring

### Task 1: Lock new-match creation to INDIVIDUAL and remove the score-rule UI

**Files:**
- Modify: `app/src/main/java/com/hasanDroid/le5ascorer/ui/newmatch/NewMatchViewModel.kt`
- Modify: `app/src/main/java/com/hasanDroid/le5ascorer/ui/newmatch/NewMatchFragment.kt`
- Modify: `app/src/main/res/layout/fragment_new_match.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/hasanDroid/le5ascorer/ui/newmatch/NewMatchViewModelTest.kt` (new file)

**Interfaces:**
- Consumes: `NewMatchUiState` (existing: `player1Name..4Name: String`, `terminalScore: Int`, `scoreRule: ScoreRule`, `isValid: Boolean`, `isCreating: Boolean`, `createdMatchId: Long?`), `CreateMatchUseCase` (existing).
- Produces: `NewMatchUiState.scoreRule` is now always `ScoreRule.INDIVIDUAL` and has no setter path from UI. Later tasks (none) don't depend on this beyond the state shape already existing.

- [ ] **Step 1: Write the failing test asserting scoreRule is always INDIVIDUAL**

Create `app/src/test/java/com/hasanDroid/le5ascorer/ui/newmatch/NewMatchViewModelTest.kt`. Check whether a Hilt/coroutine test harness pattern already exists in `app/src/test` before writing this — if `NewMatchViewModel` requires Hilt injection of `CreateMatchUseCase`, construct it directly with a fake/mock use case (no Hilt needed in JVM unit tests, Hilt is only for Android instrumentation).

Note: Task 8 later adds a `LeekhaRepository` constructor parameter to `NewMatchViewModel` for player-name suggestions, and updates the `viewModel = NewMatchViewModel(...)` construction below to match at that time — don't add it here, this task targets the constructor as it exists today (`CreateMatchUseCase` only).

```kotlin
package com.hasanDroid.le5ascorer.ui.newmatch

import com.hasanDroid.le5ascorer.data.local.entity.ScoreRule
import com.hasanDroid.le5ascorer.domain.usecase.CreateMatchUseCase
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class NewMatchViewModelTest {

    private val createMatchUseCase: CreateMatchUseCase = mockk(relaxed = true)
    private val viewModel = NewMatchViewModel(createMatchUseCase)

    @Test
    fun `initial state score rule is always INDIVIDUAL`() {
        assertEquals(ScoreRule.INDIVIDUAL, viewModel.uiState.value.scoreRule)
    }

    @Test
    fun `there is no public method to change score rule away from INDIVIDUAL`() {
        // Compile-time check: NewMatchViewModel must not expose updateScoreRule().
        // This test documents the constraint; the real enforcement is the
        // absence of the method (see Step 3 removing it).
        assertEquals(ScoreRule.INDIVIDUAL, viewModel.uiState.value.scoreRule)
    }
}
```

Check `app/build.gradle.kts` for whether `mockk` is already a test dependency; if not, add `testImplementation("io.mockk:mockk:<latest stable>")` — check mockk's current stable version before pinning one.

- [ ] **Step 2: Run test to verify it fails (or compiles against current code)**

Run: `./gradlew testDebugUnitTest --tests "com.hasanDroid.le5ascorer.ui.newmatch.NewMatchViewModelTest"`
Expected: Passes trivially right now since default `scoreRule = ScoreRule.INDIVIDUAL` already and `updateScoreRule` hasn't been removed yet — this test is a regression guard for Step 3 (which removes the method), not a red/green TDD cycle for new behavior. Confirm it compiles and passes before proceeding.

- [ ] **Step 3: Remove `updateScoreRule` and the `scoreRule` setter path**

In `NewMatchViewModel.kt`, delete the `updateScoreRule(rule: ScoreRule)` method entirely. Leave `scoreRule: ScoreRule = ScoreRule.INDIVIDUAL` in `NewMatchUiState` (still passed to `createMatchUseCase` in `createMatch()`, always `ScoreRule.INDIVIDUAL`).

- [ ] **Step 4: Remove the score-rule toggle and description from the fragment**

In `NewMatchFragment.kt`, delete `setupScoreRuleSelector()` and `updateRuleDescription()`, and their call in `onViewCreated`.

- [ ] **Step 5: Remove the score-rule UI and rename team labels in the layout**

In `fragment_new_match.xml`:
- Remove `toggleScoreRule` (`MaterialButtonToggleGroup` with `buttonIndividual`/`buttonTeam`), its "Score rule" section header, and the `textRuleDescription` info-note row entirely — from `cardRules`.
- In `cardPlayers`, rename the "First team"/"Second team" section headers' text to reference `@string/players_group_1_2` / `@string/players_group_3_4` (new strings, see Step 6) instead of `@string/first_team` / `@string/second_team`.

- [ ] **Step 6: Update strings.xml**

In `app/src/main/res/values/strings.xml`:
- Remove `rule_team_desc`, `cd_team_rule`, `first_team`, `second_team`.
- Add:
```xml
<string name="players_group_1_2">Players 1 &amp; 2</string>
<string name="players_group_3_4">Players 3 &amp; 4</string>
```
- Keep `rule_team` (still used for rendering historical TEAM matches in the match-list summary — see Task 3) and `rule_individual`/`rule_individual_desc`.

- [ ] **Step 7: Run the test and build**

Run: `./gradlew testDebugUnitTest --tests "com.hasanDroid.le5ascorer.ui.newmatch.NewMatchViewModelTest"` — expect PASS.
Run: `./gradlew assembleDebug` — expect BUILD SUCCESSFUL (catches any leftover references to removed views/strings).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/hasanDroid/le5ascorer/ui/newmatch/ app/src/main/res/layout/fragment_new_match.xml app/src/main/res/values/strings.xml app/src/test/java/com/hasanDroid/le5ascorer/ui/newmatch/
git commit -m "Remove team-scoring UI from New Match screen

New matches are now always created with ScoreRule.INDIVIDUAL. The
score-rule toggle, its description note, and the 'First/Second team'
section labels (renamed to neutral 'Players 1 & 2' / '3 & 4' groupings)
are removed. ScoreRule.TEAM remains in the enum for backward
compatibility with existing matches.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 2: Document ScoreEngine's TEAM branch as legacy-only

**Files:**
- Modify: `app/src/main/java/com/hasanDroid/le5ascorer/domain/ScoreEngine.kt:177-` (`checkGameOver`)
- Modify: `app/src/test/java/com/hasanDroid/le5ascorer/domain/ScoreEngineGameOverTest.kt`

**Interfaces:**
- Consumes: existing `ScoreEngine.checkGameOver(scoreboard, terminalScore, scoreRule)` signature — unchanged.
- Produces: no signature change; this task only adds a comment and a regression test, not new behavior.

- [ ] **Step 1: Add a doc comment marking the TEAM branch as legacy**

In `ScoreEngine.kt`, immediately above the `ScoreRule.TEAM ->` branch inside `checkGameOver` (around line 195), add:

```kotlin
            // Legacy path: ScoreRule.TEAM can no longer be produced by new
            // matches (NewMatchViewModel always creates ScoreRule.INDIVIDUAL
            // as of the 2026-08-22 app overhaul). This branch exists only so
            // matches created before that change still resolve game-over
            // correctly if reopened. Do not remove without a data migration.
            ScoreRule.TEAM -> {
```

- [ ] **Step 2: Rename the existing TEAM test to document the legacy intent**

In `ScoreEngineGameOverTest.kt`, rename the test `` `team - game over when team reaches 101 or more (loser)` `` to:

```kotlin
    @Test
    fun `legacy team match - game over when team reaches 101 or more (loser)`() {
```

Keep the test body unchanged — it already correctly covers the 0+2 vs 1+3 pairing.

- [ ] **Step 3: Add a regression test confirming new-match creation cannot produce TEAM**

Add to `ScoreEngineGameOverTest.kt` (or, if that file is strictly `ScoreEngine`-scoped, add to `NewMatchViewModelTest.kt` from Task 1 instead — prefer `NewMatchViewModelTest.kt` since this is really a `NewMatchViewModel` behavior, not a `ScoreEngine` one):

Already covered by Task 1 Step 1's `initial state score rule is always INDIVIDUAL` test. Skip duplicating here — just confirm that test still exists and passes.

- [ ] **Step 4: Run tests**

Run: `./gradlew testDebugUnitTest --tests "com.hasanDroid.le5ascorer.domain.ScoreEngineGameOverTest"`
Expected: PASS (3 tests, one renamed).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hasanDroid/le5ascorer/domain/ScoreEngine.kt app/src/test/java/com/hasanDroid/le5ascorer/domain/ScoreEngineGameOverTest.kt
git commit -m "Document ScoreEngine TEAM branch as legacy-only

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 3: Clean up sample data and verify match-list team-match rendering

**Files:**
- Modify: `app/src/main/java/com/hasanDroid/le5ascorer/util/SampleDataGenerator.kt`

**Interfaces:**
- Consumes: whatever `SampleDataGenerator` currently constructs (read the file first — it wasn't in the earlier Explore output in full, so inspect it directly during this task before editing).
- Produces: no new interface; this is a cleanup-only task.

- [ ] **Step 1: Read SampleDataGenerator.kt and remove any TEAM-seeded matches**

Read `app/src/main/java/com/hasanDroid/le5ascorer/util/SampleDataGenerator.kt` in full. If it constructs any match with `scoreRule = ScoreRule.TEAM`, either delete that sample or change it to `ScoreRule.INDIVIDUAL`, so manual testing / screenshot seeding never exercises the removed creation path. If it constructs no TEAM matches, no change needed — note that in the commit message as "no change required" rather than committing an empty diff.

- [ ] **Step 2: Manually verify match-list rendering for a historical TEAM match is unaffected**

Since `MatchAdapter.kt`'s `textRuleSummary` still formats `R.string.rule_team` for any match with `scoreRule == ScoreRule.TEAM` (kept per Task 1 Step 6), and no code path can create one going forward, this is a dead-code path with no test needed — confirm by grep that `rule_team` string is still referenced only by `MatchAdapter.kt`:

Run: `grep -rn "rule_team\b" app/src/main`
Expected: one hit in `values/strings.xml` (definition) and one hit in `MatchAdapter.kt` (usage) — no orphaned references, no leftover reference to removed `rule_team_desc`/`cd_team_rule`/`first_team`/`second_team`.

- [ ] **Step 3: Commit (only if Step 1 made a change)**

```bash
git add app/src/main/java/com/hasanDroid/le5ascorer/util/SampleDataGenerator.kt
git commit -m "Remove TEAM-seeded sample data

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Phase 2: Fix buttons

### Task 4: Add accessible content descriptions to unlabeled icon controls

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/layout/fragment_round_entry.xml`
- Modify: `app/src/main/java/com/hasanDroid/le5ascorer/ui/round/RoundEntryFragment.kt`
- Modify: `app/src/main/res/layout/fragment_new_match.xml`
- Modify: `app/src/main/res/layout/fragment_scoreboard.xml`
- Modify: `app/src/main/res/layout/fragment_settings.xml`
- Modify: `app/src/main/res/layout/item_scoreboard_row.xml`

**Interfaces:**
- Consumes: nothing new.
- Produces: nothing consumed by later tasks — this is a leaf accessibility fix.

- [ ] **Step 1: Add new content-description strings**

In `strings.xml`, add:
```xml
<string name="cd_navigate_back">Navigate back</string>
<string name="cd_hearts_card">Assign a heart to the selected player</string>
<string name="cd_q_spades_card">Assign the Queen of Spades to the selected player</string>
<string name="cd_ten_diamonds_card">Assign the Ten of Diamonds to the selected player</string>
<string name="cd_double_card">Mark the selected player as taking all cards (a double)</string>
<string name="cd_edit_round">Edit round %1$d</string>
```
Then remove the old `edit_round` label reuse from `item_scoreboard_row.xml`'s `buttonEdit` contentDescription (see Step 4) — keep `edit_round` itself if it's used elsewhere as a visible label (check with `grep -n "edit_round" app/src/main -r` first; if it's only ever used as a contentDescription, it can be fully replaced by `cd_edit_round`).

- [ ] **Step 2: Add static contentDescription to the four round-entry action cards**

In `fragment_round_entry.xml`, add to each card:
```xml
<!-- cardHearts -->
android:contentDescription="@string/cd_hearts_card"
<!-- cardQSpades -->
android:contentDescription="@string/cd_q_spades_card"
<!-- cardTenDiamonds -->
android:contentDescription="@string/cd_ten_diamonds_card"
<!-- cardDouble -->
android:contentDescription="@string/cd_double_card"
```
These are static (don't depend on which player is selected), so a plain XML attribute is sufficient — no Kotlin binding needed, unlike `cardPlayerRow`'s per-player dynamic description.

- [ ] **Step 3: Add navigationContentDescription to all four toolbars**

In `fragment_new_match.xml`, `fragment_round_entry.xml`, `fragment_scoreboard.xml`, `fragment_settings.xml`, add to each `MaterialToolbar`:
```xml
app:navigationContentDescription="@string/cd_navigate_back"
```

- [ ] **Step 4: Fix buttonEdit's contentDescription in item_scoreboard_row.xml**

Replace the current `android:contentDescription="@string/edit_round"` with a Kotlin-bound dynamic description in `ScoreboardAdapter.kt`'s `bind()` (matches the existing pattern used for `cell.contentDescription` a few lines above it):
```kotlin
binding.buttonEdit.contentDescription = binding.root.context.getString(
    R.string.cd_edit_round, roundScores.roundIndex + 1
)
```
Remove the static `android:contentDescription="@string/edit_round"` attribute from `item_scoreboard_row.xml` since it's now set programmatically (matching how `cardPlayerRow`'s description is set in `RoundEntryFragment.updatePlayerRow` rather than XML).

- [ ] **Step 5: Build and manually verify with TalkBack or Accessibility Scanner**

Run: `./gradlew assembleDebug` — expect BUILD SUCCESSFUL.
Manually: enable TalkBack (or use Android Studio's Layout Inspector accessibility check) on an emulator, navigate to Round Entry and Scoreboard, confirm every icon-only control now announces a name.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/layout/fragment_round_entry.xml app/src/main/res/layout/fragment_new_match.xml app/src/main/res/layout/fragment_scoreboard.xml app/src/main/res/layout/fragment_settings.xml app/src/main/res/layout/item_scoreboard_row.xml app/src/main/java/com/hasanDroid/le5ascorer/ui/scoreboard/ScoreboardAdapter.kt
git commit -m "Add contentDescription to unlabeled icon buttons

Round-entry action cards, all four toolbar back arrows, and the
scoreboard row edit button previously had no accessible name.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 5: Add pill button style variants and remove inline radius/elevation overrides

**Files:**
- Modify: `app/src/main/res/values/styles.xml`
- Modify: `app/src/main/res/layout/fragment_round_entry.xml` (`buttonSave`)
- Modify: `app/src/main/res/layout/fragment_new_match.xml` (`buttonCreate`)
- Modify: `app/src/main/res/layout/fragment_scoreboard.xml` (`buttonAddRound`, `buttonBackToHome`)
- Modify: `app/src/main/res/layout/dialog_end_game.xml` (`buttonCapturePhoto`, `buttonShowRoundScores`)

**Interfaces:**
- Consumes: existing `Widget.Le5a.Button.Primary`/`Widget.Le5a.Button.Secondary` (styles.xml:74-98).
- Produces: two new styles `Widget.Le5a.Button.Primary.Pill` and `Widget.Le5a.Button.Secondary.Pill`, consumed by every full-width CTA site listed above.

- [ ] **Step 1: Add the two pill style variants**

In `styles.xml`, immediately after `Widget.Le5a.Button.Primary` (after line 85):
```xml
<!-- Full-width call-to-action shape. Every surveyed full-width primary/
     secondary button in the app wants this — previously each usage site
     repeated app:cornerRadius="@dimen/radius_pill" inline instead of
     having a named variant. -->
<style name="Widget.Le5a.Button.Primary.Pill">
    <item name="cornerRadius">@dimen/radius_pill</item>
</style>
```
And immediately after `Widget.Le5a.Button.Secondary` (after line 98):
```xml
<style name="Widget.Le5a.Button.Secondary.Pill">
    <item name="cornerRadius">@dimen/radius_pill</item>
</style>
```

- [ ] **Step 2: Point every full-width CTA at the pill variant and remove inline overrides**

In each of the five files, change `style="@style/Widget.Le5a.Button.Primary"` to `style="@style/Widget.Le5a.Button.Primary.Pill"` (or `.Secondary.Pill` for `buttonBackToHome`/`buttonShowRoundScores`), and remove the now-redundant `app:cornerRadius="@dimen/radius_pill"` attribute from each:
- `fragment_round_entry.xml`: `buttonSave`
- `fragment_new_match.xml`: `buttonCreate`
- `fragment_scoreboard.xml`: `buttonAddRound`, `buttonBackToHome`
- `dialog_end_game.xml`: `buttonCapturePhoto`, `buttonShowRoundScores`

- [ ] **Step 3: Remove buttonCreate's ad hoc elevation override**

In `fragment_new_match.xml`, remove `app:elevation="@dimen/elev_float"` from `buttonCreate` so it falls back to `Widget.Le5a.Button.Primary`'s default `elev_raised`, matching every sibling Primary button (`buttonSave`, `buttonAddRound`, `buttonCapturePhoto`).

- [ ] **Step 4: Build and visually verify**

Run: `./gradlew assembleDebug` — expect BUILD SUCCESSFUL.
Manually: launch the app, visit New Match, Round Entry, Scoreboard, and the end-game dialog; confirm all five buttons still render as pills with no visual regression (elevation on Create should now look identical to Save/Add Round).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/styles.xml app/src/main/res/layout/fragment_round_entry.xml app/src/main/res/layout/fragment_new_match.xml app/src/main/res/layout/fragment_scoreboard.xml app/src/main/res/layout/dialog_end_game.xml
git commit -m "Add Button.Primary/Secondary.Pill style variants

Every full-width CTA repeated app:cornerRadius=radius_pill inline
instead of having a named style variant. Also removed buttonCreate's
lone elevation override so it matches its sibling Primary buttons.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 6: Convert toggle-group children to explicit MaterialButton and give buttonReset explicit sizing

**Files:**
- Modify: `app/src/main/res/layout/fragment_new_match.xml`
- Modify: `app/src/main/res/layout/fragment_round_entry.xml`

**Interfaces:** None — pure XML tag/attribute changes, no code interface change.

- [ ] **Step 1: Convert button51/button101/button151 to MaterialButton**

In `fragment_new_match.xml`, change the three `<Button ...>` tags for `button51`, `button101`, `button151` (inside `toggleTerminalScore`) to `<com.google.android.material.button.MaterialButton ...>`, keeping every existing attribute unchanged.

- [ ] **Step 2: Give buttonReset explicit dimensions**

In `fragment_round_entry.xml`, add explicit sizing to `buttonReset` matching the app's icon/text-button convention:
```xml
android:layout_width="wrap_content"
android:layout_height="@dimen/touch_min"
```
(It currently has neither set explicitly — verify current attributes first via Read before editing, since the exact current tag content should be preserved otherwise.)

- [ ] **Step 3: Build**

Run: `./gradlew assembleDebug` — expect BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/fragment_new_match.xml app/src/main/res/layout/fragment_round_entry.xml
git commit -m "Use explicit MaterialButton in toggle groups; size buttonReset explicitly

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 7: Converge card style duplication (player row, match card avatar, scoreboard edit button, settings version row)

**Files:**
- Modify: `app/src/main/res/layout/item_player_row.xml`
- Modify: `app/src/main/res/layout/item_match.xml`
- Modify: `app/src/main/res/layout/item_scoreboard_row.xml`
- Modify: `app/src/main/res/layout/fragment_settings.xml`
- Modify: `app/src/main/res/values/styles.xml` (only if a distinct non-clickable-row background is added)
- Modify: `app/src/main/res/drawable/` (only if a new flat-background drawable is added for the settings version row)

**Interfaces:** None — pure XML style-reference changes.

- [ ] **Step 1: Converge item_player_row.xml's cardPlayerRow onto Widget.Le5a.Card.Clickable**

In `item_player_row.xml`, change `cardPlayerRow`'s style to reference `Widget.Le5a.Card.Clickable` (add `style="@style/Widget.Le5a.Card.Clickable"`), and remove the now-redundant inline `android:clickable="true"` and `android:focusable="true"` attributes (they're inherited from the style). Keep `android:minHeight="@dimen/touch_min"` and the inline `cardBackgroundColor`/`cardCornerRadius`/`cardElevation`/`rippleColor`/`strokeColor`/`strokeWidth` attributes as-is — per the spec, these are legitimately overridden at runtime in `RoundEntryFragment.updatePlayerRow` and the XML values are just the unselected-state defaults, so only the clickable/focusable duplication is being removed here, not the color/stroke defaults.

- [ ] **Step 2: Converge item_match.xml's cardLeadingPlayer onto Widget.Le5a.Card**

In `item_match.xml`, add `style="@style/Widget.Le5a.Card"` to `cardLeadingPlayer`, and remove any inline attribute that now exactly duplicates the style's default (`cardCornerRadius`, `strokeWidth` if they match `radius_md`/`stroke_hairline`) — keep `cardBackgroundColor="@color/felt_700"` and `strokeColor="@color/felt_outline"` inline since those differ from `Widget.Le5a.Card`'s defaults (`felt_800`/`felt_outline_soft`), matching the pattern already used by `Widget.Le5a.Card.Raised`. Read the current full attribute list before editing to confirm exact values that do vs. don't match the base style.

- [ ] **Step 3: Resize item_scoreboard_row.xml's buttonEdit off touch_min instead of scoreboard_action_col**

In `item_scoreboard_row.xml`, change `buttonEdit`'s `layout_width`/`layout_height` from `@dimen/scoreboard_action_col` to `@dimen/touch_min`. Since both currently resolve to 48dp, this is a no-visual-change refactor — verify by comparing `dimens.xml` values for both tokens before and after (already confirmed identical at 48dp per the spec).

- [ ] **Step 4: Give the non-clickable settings version row a distinct background**

In `fragment_settings.xml`, `layoutVersion` currently sets `clickable="false" focusable="false"` but still inherits `Widget.Le5a.Row`'s `bg_row` (ripple-shaped) background via style, making it visually indistinguishable from the genuinely tappable rows above it (`layoutContactSupport`, `layoutRateApp`). Remove the `android:background` inheritance for this one row by overriding it directly on `layoutVersion` with `android:background="@android:color/transparent"` (simplest fix — no new drawable needed, the row keeps its padding/layout from `Widget.Le5a.Row` but drops the tappable-looking background).

- [ ] **Step 5: Build and visually verify**

Run: `./gradlew assembleDebug` — expect BUILD SUCCESSFUL.
Manually: visit Round Entry (player rows still render/select correctly), Match List (leading-player avatar unchanged), Scoreboard (edit button unchanged size), Settings (version row no longer looks tappable, Contact/Rate rows still do).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/layout/item_player_row.xml app/src/main/res/layout/item_match.xml app/src/main/res/layout/item_scoreboard_row.xml app/src/main/res/layout/fragment_settings.xml
git commit -m "Converge duplicated card/row style attributes onto shared styles

Also decouples buttonEdit's size from scoreboard_action_col (was
coincidentally equal to touch_min) and removes the tappable-looking
background from the non-clickable settings version row.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Phase 3: Player name suggestions from history

### Task 8: Expose player-name history as UI state in NewMatchViewModel

**Files:**
- Modify: `app/src/main/java/com/hasanDroid/le5ascorer/ui/newmatch/NewMatchViewModel.kt`
- Test: `app/src/test/java/com/hasanDroid/le5ascorer/ui/newmatch/NewMatchViewModelTest.kt`

**Interfaces:**
- Consumes: `LeekhaRepository.getAllPlayers(): Flow<List<Player>>` (already exists, `data/repository/LeekhaRepository.kt:37-41`), `Player(id: Long, name: String)` (existing, `domain/model/Models.kt`).
- Produces: `NewMatchUiState.knownPlayerNames: List<String>` (new field, sorted by recency per the DAO's existing `ORDER BY lastUsedAt DESC`), consumed by Task 9's Fragment.

- [ ] **Step 1: Write the failing test**

Update `NewMatchViewModelTest.kt`'s class-level fixture to add the repository dependency (rather than declaring a second, shadowing local `viewModel` inside one test) — this keeps the two existing tests from Task 1 compiling against the new constructor shape:
```kotlin
class NewMatchViewModelTest {

    private val createMatchUseCase: CreateMatchUseCase = mockk(relaxed = true)
    private val repository: LeekhaRepository = mockk()
    private val viewModel = NewMatchViewModel(createMatchUseCase, repository)

    @Test
    fun `initial state score rule is always INDIVIDUAL`() {
        every { repository.getAllPlayers() } returns emptyFlow()
        assertEquals(ScoreRule.INDIVIDUAL, viewModel.uiState.value.scoreRule)
    }

    @Test
    fun `there is no public method to change score rule away from INDIVIDUAL`() {
        every { repository.getAllPlayers() } returns emptyFlow()
        assertEquals(ScoreRule.INDIVIDUAL, viewModel.uiState.value.scoreRule)
    }

    @Test
    fun `known player names are populated from repository on init`() = runTest {
        every { repository.getAllPlayers() } returns flowOf(
            listOf(Player(1, "Ahmad"), Player(2, "Sara"))
        )

        advanceUntilIdle()

        assertEquals(listOf("Ahmad", "Sara"), viewModel.uiState.value.knownPlayerNames)
    }
}
```
Note the `every { repository.getAllPlayers() } returns ...` line moved inside each existing test — with a mocked (not relaxed) `repository`, `getAllPlayers()` must be stubbed before `NewMatchViewModel`'s `init` block calls it, which happens at `viewModel` field construction. Since Kotlin initializes class-level `val`s in declaration order top-to-bottom, `viewModel`'s `init { }` runs before any `@Test` method's stub is set — meaning the class-level `viewModel = NewMatchViewModel(...)` call happens with `getAllPlayers()` still unstubbed. Instead, mark `repository` with a default relaxed stub for `getAllPlayers()`, then override it per-test where needed:
```kotlin
    private val repository: LeekhaRepository = mockk {
        every { getAllPlayers() } returns emptyFlow()
    }
    private val viewModel = NewMatchViewModel(createMatchUseCase, repository)
```
Then only the new `known player names are populated from repository on init` test needs its own repository instance (constructed locally in that test, not the shared class-level one) so it can stub a non-empty flow before construction:
```kotlin
    @Test
    fun `known player names are populated from repository on init`() = runTest {
        val repositoryWithHistory: LeekhaRepository = mockk {
            every { getAllPlayers() } returns flowOf(
                listOf(Player(1, "Ahmad"), Player(2, "Sara"))
            )
        }
        val viewModelWithHistory = NewMatchViewModel(createMatchUseCase, repositoryWithHistory)

        advanceUntilIdle()

        assertEquals(listOf("Ahmad", "Sara"), viewModelWithHistory.uiState.value.knownPlayerNames)
    }
```
This local-instance approach for the one test that needs specific data (while the shared class-level `viewModel`/`repository` fixture serves the other two tests with an empty default) avoids fighting Kotlin's property-initialization order. Add necessary imports (`io.mockk.every`, `io.mockk.mockk`, `kotlinx.coroutines.flow.flowOf`, `kotlinx.coroutines.flow.emptyFlow`, `kotlinx.coroutines.test.runTest`, `kotlinx.coroutines.test.advanceUntilIdle`, `com.hasanDroid.le5ascorer.domain.model.Player`, `com.hasanDroid.le5ascorer.data.repository.LeekhaRepository`). Check `app/build.gradle.kts` for `kotlinx-coroutines-test` dependency; add it to `testImplementation` if missing, matching whatever version the `kotlinx-coroutines-*` main dependency uses.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hasanDroid.le5ascorer.ui.newmatch.NewMatchViewModelTest"`
Expected: FAIL to compile — `NewMatchViewModel` has no `LeekhaRepository` constructor parameter and `NewMatchUiState` has no `knownPlayerNames` field yet.

- [ ] **Step 3: Add the field and wire the repository**

In `NewMatchViewModel.kt`:
```kotlin
data class NewMatchUiState(
    val player1Name: String = "",
    val player2Name: String = "",
    val player3Name: String = "",
    val player4Name: String = "",
    val terminalScore: Int = 101,
    val scoreRule: ScoreRule = ScoreRule.INDIVIDUAL,
    val isValid: Boolean = false,
    val isCreating: Boolean = false,
    val createdMatchId: Long? = null,
    val knownPlayerNames: List<String> = emptyList()
)
```
Add `private val repository: LeekhaRepository` to the `@Inject constructor`, and in `init { }` (add one if it doesn't exist):
```kotlin
init {
    viewModelScope.launch {
        repository.getAllPlayers().collect { players ->
            _uiState.value = _uiState.value.copy(
                knownPlayerNames = players.map { it.name }
            )
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.hasanDroid.le5ascorer.ui.newmatch.NewMatchViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hasanDroid/le5ascorer/ui/newmatch/NewMatchViewModel.kt app/src/test/java/com/hasanDroid/le5ascorer/ui/newmatch/NewMatchViewModelTest.kt app/build.gradle.kts
git commit -m "Expose known player names in NewMatchViewModel

Wires the already-existing LeekhaRepository.getAllPlayers() (backed by
PlayerDao, ordered by lastUsedAt DESC) as new-match UI state, ready to
back autocomplete suggestions in the Fragment.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 9: Convert the four player-name fields to autocomplete dropdowns

**Files:**
- Modify: `app/src/main/res/layout/fragment_new_match.xml`
- Modify: `app/src/main/java/com/hasanDroid/le5ascorer/ui/newmatch/NewMatchFragment.kt`

**Interfaces:**
- Consumes: `NewMatchUiState.knownPlayerNames: List<String>` (from Task 8).
- Produces: nothing consumed further.

- [ ] **Step 1: Change the four TextInputEditText fields to AutoCompleteTextView**

In `fragment_new_match.xml`, for each of `editPlayer1`..`editPlayer4`: change the tag from `<com.google.android.material.textfield.TextInputEditText ...>` to `<androidx.appcompat.widget.AppCompatAutoCompleteTextView ...>` (Material's exposed-dropdown pattern typically uses `AutoCompleteTextView`; if the project already has a Material `TextInputLayout.ExposedDropdownMenu` example elsewhere in the codebase, follow that exact pattern instead — check with `grep -rn "AutoCompleteTextView\|ExposedDropdown" app/src/main/res/layout` first). Keep every existing attribute (`hint` inheritance via parent `TextInputLayout`, `imeOptions`, `inputType`, `maxLines`). Do not change `app:endIconMode="clear_text"` unless following an existing exposed-dropdown example dictates `app:endIconMode="dropdown_menu"` — clear-text and a manually-shown dropdown can coexist since this is free-text-with-suggestions, not a locked picker.

- [ ] **Step 2: Wire a shared ArrayAdapter filtered per-field in the Fragment**

In `NewMatchFragment.kt`, replace `setupInputs()` with a version that also configures autocomplete:
```kotlin
private fun setupInputs() {
    binding.editPlayer1.addTextChangedListener { viewModel.updatePlayer1Name(it.toString()) }
    binding.editPlayer2.addTextChangedListener { viewModel.updatePlayer2Name(it.toString()) }
    binding.editPlayer3.addTextChangedListener { viewModel.updatePlayer3Name(it.toString()) }
    binding.editPlayer4.addTextChangedListener { viewModel.updatePlayer4Name(it.toString()) }
}

private fun setupAutocomplete(state: NewMatchUiState) {
    bindSuggestions(binding.editPlayer1, state.knownPlayerNames, exclude = listOf(state.player2Name, state.player3Name, state.player4Name))
    bindSuggestions(binding.editPlayer2, state.knownPlayerNames, exclude = listOf(state.player1Name, state.player3Name, state.player4Name))
    bindSuggestions(binding.editPlayer3, state.knownPlayerNames, exclude = listOf(state.player1Name, state.player2Name, state.player4Name))
    bindSuggestions(binding.editPlayer4, state.knownPlayerNames, exclude = listOf(state.player1Name, state.player2Name, state.player3Name))
}

private fun bindSuggestions(
    field: android.widget.AutoCompleteTextView,
    allNames: List<String>,
    exclude: List<String>
) {
    val suggestions = allNames.filter { it !in exclude }
    val adapter = android.widget.ArrayAdapter(
        requireContext(), android.R.layout.simple_dropdown_item_1line, suggestions
    )
    if (field.adapter == null || field.tag != suggestions) {
        field.setAdapter(adapter)
        field.tag = suggestions
    }
}
```
Call `setupAutocomplete(state)` from inside `observeUiState()`'s `collect { state -> ... }` block (added alongside the existing `isEnabled`/`createdMatchId` handling), so the adapter refreshes whenever `knownPlayerNames` or any of the four current names change. The `field.tag != suggestions` check avoids rebuilding the adapter (and dismissing an open dropdown) on every keystroke when the suggestion list hasn't actually changed.

- [ ] **Step 3: Build and manually verify**

Run: `./gradlew assembleDebug` — expect BUILD SUCCESSFUL.
Manually: create a match with 4 names, complete it, start a new match — confirm the 4 names now appear as suggestions, confirm selecting a suggestion fills the field, confirm typing a brand-new name still works, confirm a name already entered in field 1 doesn't suggest itself again in fields 2-4.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/fragment_new_match.xml app/src/main/java/com/hasanDroid/le5ascorer/ui/newmatch/NewMatchFragment.kt
git commit -m "Add player-name autocomplete suggestions from match history

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Phase 4: Round entry redesign

### Task 10: Render hearts as a filling pip row instead of a static icon + counter

**Files:**
- Modify: `app/src/main/res/layout/fragment_round_entry.xml` (`cardHearts` contents)
- Create: `app/src/main/res/drawable/ic_heart_pip_filled.xml`, `app/src/main/res/drawable/ic_heart_pip_outline.xml`
- Modify: `app/src/main/java/com/hasanDroid/le5ascorer/ui/round/RoundEntryFragment.kt`
- Modify: `app/src/main/res/values/dimens.xml`

**Interfaces:**
- Consumes: `RoundEntryUiState.playersData: List<PlayerRoundData>` (existing, unchanged), specifically `heartCount: Int` per player (existing field).
- Produces: nothing new consumed by later tasks — this task is self-contained to the hearts card's rendering.

- [ ] **Step 1: Add pip drawables**

Create two small vector drawables (12dp, matching the existing heart icon's proportions — copy `ic_suit_heart.xml`'s path data and just vary fill vs. stroke-only):
`ic_heart_pip_filled.xml` — solid heart, `android:fillColor="?attr/colorControlNormal"` overridden via tint at runtime (tinted `ink_red` when filled).
`ic_heart_pip_outline.xml` — outline-only heart at low opacity (tinted `felt_outline`/`text_tertiary` when empty).

Reuse the existing `ic_suit_heart.xml` path geometry rather than inventing new path data — read that file first and adapt it to a smaller viewport if needed for a 12dp pip size.

- [ ] **Step 2: Add a pip-row container to cardHearts in the layout**

In `fragment_round_entry.xml`, inside `cardHearts`'s inner `LinearLayout`, replace the existing static `ImageView` icon with a horizontal `LinearLayout` container (`id="layoutHeartPips"`, `gravity="center"`, `marginTop="@dimen/space_xs"`) that will hold 13 small `ImageView` pips, added programmatically (13 fixed views inflated once, then their drawable/tint toggled — not re-inflated per render). Add a new dimen `heart_pip_size` (10dp) to `dimens.xml` for the pip `ImageView`s' width/height.

Keep the existing label ("Hearts") and value TextViews as-is beneath the pip row — only the icon is replaced by the pip row, the counter text is removed per the spec (the pips themselves communicate the count) — update the `hearts_value` string usage: remove the value TextView reference to `@string/hearts_value` from the card's inner layout (the pip row now carries this information visually) but do NOT delete the `hearts_value` string resource itself in case it's reused elsewhere (check with grep first).

- [ ] **Step 3: Add pip-row population and update logic to the Fragment**

In `RoundEntryFragment.kt`, add:
```kotlin
private val heartPipViews: List<ImageView> by lazy {
    (0 until REQUIRED_HEARTS).map { index ->
        ImageView(requireContext()).apply {
            val size = resources.getDimensionPixelSize(R.dimen.heart_pip_size)
            layoutParams = ViewGroup.MarginLayoutParams(size, size).apply {
                marginEnd = resources.getDimensionPixelSize(R.dimen.space_2xs_or_existing_smallest_gap)
            }
            contentDescription = null // decorative; the card's own contentDescription carries meaning
        }
    }
}

private fun setupHeartPips() {
    binding.layoutHeartPips.removeAllViews()
    heartPipViews.forEach { binding.layoutHeartPips.addView(it) }
}

private fun updateHeartPips(totalHeartsAssigned: Int) {
    heartPipViews.forEachIndexed { index, pip ->
        val filled = index < totalHeartsAssigned
        pip.setImageResource(
            if (filled) R.drawable.ic_heart_pip_filled else R.drawable.ic_heart_pip_outline
        )
        pip.tint(if (filled) R.color.ink_red else R.color.text_tertiary)
    }
}
```
Check `dimens.xml` for the smallest existing gap token (likely `space_xs` at 4dp — do not invent `space_2xs_or_existing_smallest_gap`, that placeholder name must be replaced with the real token found during implementation, e.g. `R.dimen.space_xs`).

Call `setupHeartPips()` once from `onViewCreated` (after `setupCardActions()`), and call `updateHeartPips(state.playersData.sumOf { it.heartCount })` from `updateScoringFor` or a new small private method called from `render(state)` — total hearts assigned across all players is the "pool" reading described in the spec (13 total, depleting as they're assigned to any player), which is exactly `Validity.totalHearts` already computed in `RoundEntryFragment`'s `Validity.of()` — reuse that value rather than recomputing.

- [ ] **Step 4: Build and manually verify**

Run: `./gradlew assembleDebug` — expect BUILD SUCCESSFUL.
Manually: open Round Entry, tap Hearts repeatedly for different players, confirm pips fill left-to-right up to 13 and stop incrementing past 13 (existing `incrementHeart` cap), confirm Reset empties all pips.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/fragment_round_entry.xml app/src/main/res/drawable/ic_heart_pip_filled.xml app/src/main/res/drawable/ic_heart_pip_outline.xml app/src/main/java/com/hasanDroid/le5ascorer/ui/round/RoundEntryFragment.kt app/src/main/res/values/dimens.xml
git commit -m "Render hearts as a filling pip row on the round-entry card

Replaces the static heart icon + count label with 13 pip glyphs that
fill as hearts are assigned across all players, making the 'pool of
13' constraint visually self-evident.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 11: Animate Q♠/10♦ cards moving from the pool to the selected player's row on assignment

**Files:**
- Modify: `app/src/main/java/com/hasanDroid/le5ascorer/ui/round/RoundEntryFragment.kt`
- Modify: `app/src/main/res/layout/fragment_round_entry.xml` (`cardQSpades`/`cardTenDiamonds` visual content, if the mini-card face needs restyling)

**Interfaces:**
- Consumes: `RoundEntryUiState.playersData[i].qSpadesCount`/`tenDiamondsCount` (existing, 0 or 1), `RoundEntryFragment.playerRowBindings()` (existing private method returning the 4 `ItemPlayerRowBinding`s), `selectedPlayerIndex` (existing field).
- Produces: nothing new consumed by later tasks.

- [ ] **Step 1: Add a "spent" visual state to cardQSpades/cardTenDiamonds**

In `fragment_round_entry.xml`, no structural change needed if the existing card content (large "Q"/"10" glyph + suit icon) already reads as a mini playing card face (per the Explore findings, it does — `Score.Large` text + suit icon on a `bone`-colored `Widget.Le5a.Card.Playing` card). Confirm during implementation whether a visual "spent" (dimmed/outlined) treatment is needed when `qSpadesCount`/`tenDiamondsCount` is 1 vs 0 — if so, add alpha/tint changes bound in Step 2 rather than new layout elements.

- [ ] **Step 2: Add the assignment animation to RoundEntryFragment**

In `RoundEntryFragment.kt`, add a helper that animates a source card view translating toward a destination player row, then triggers the existing render pipeline:
```kotlin
private fun animateCardToPlayerRow(sourceCard: View, playerIndex: Int, onEnd: () -> Unit) {
    val targetRow = playerRowBindings().getOrNull(playerIndex)?.cardPlayerRow
    if (targetRow == null) {
        onEnd()
        return
    }
    val startLocation = IntArray(2).also { sourceCard.getLocationOnScreen(it) }
    val endLocation = IntArray(2).also { targetRow.getLocationOnScreen(it) }
    val deltaX = (endLocation[0] - startLocation[0]).toFloat()
    val deltaY = (endLocation[1] - startLocation[1]).toFloat()

    sourceCard.animate()
        .translationX(deltaX * 0.3f)
        .translationY(deltaY * 0.3f)
        .alpha(0.4f)
        .setDuration(280)
        .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
        .withEndAction {
            sourceCard.translationX = 0f
            sourceCard.translationY = 0f
            sourceCard.alpha = 1f
            onEnd()
        }
        .start()
}
```
This follows the existing animation style already used in `NewMatchFragment.playEntranceAnimation`/`animateButtonEnabled` (short `ObjectAnimator`/`ViewPropertyAnimator` durations, `DecelerateInterpolator`) rather than introducing a new animation library. The 0.3x-distance partial translate (rather than a full translate all the way to the row, which would visually clip against sibling cards in the equal-weight row layout) keeps the motion readable as "moving toward" without needing to reparent the view — reads as a quick dealing gesture, not a literal drag-and-drop.

- [ ] **Step 3: Wire the animation into the tap handlers**

In `setupCardActions()`, change:
```kotlin
binding.cardQSpades.setOnClickListener {
    val wasAssigned = viewModel.uiState.value.playersData
        .getOrNull(selectedPlayerIndex)?.qSpadesCount ?: 0 > 0
    if (!wasAssigned) {
        animateCardToPlayerRow(binding.cardQSpades, selectedPlayerIndex) {
            viewModel.toggleQSpades(selectedPlayerIndex)
        }
    } else {
        viewModel.toggleQSpades(selectedPlayerIndex)
    }
}
```
Same pattern for `cardTenDiamonds`. Only animate on assignment (0 → 1), not on de-assignment (1 → 0, e.g. via the chip-removal path in `chipGroupActions`) or reassignment to a different player — those cases call the ViewModel directly without the animation, since the spec calls for animating the "dealing" motion specifically, not every state change.

- [ ] **Step 4: Build and manually verify**

Run: `./gradlew assembleDebug` — expect BUILD SUCCESSFUL.
Manually: select a player, tap Q♠, confirm a brief motion plays and the card appears assigned in that player's row; tap a different player, tap Q♠ again (reassignment), confirm it still works correctly (existing toggle logic moves it, animation is cosmetic only and doesn't block correctness); remove via chip, confirm instant removal (no animation) still works.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hasanDroid/le5ascorer/ui/round/RoundEntryFragment.kt app/src/main/res/layout/fragment_round_entry.xml
git commit -m "Animate Q-spades/10-diamonds cards toward the assigned player's row

Cosmetic only — ViewModel toggle logic and validity computation are
unchanged, only the visual feedback on assignment changes.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Phase 5: Scoreboard redesign

### Task 12: Add leadingPlayerIndex to ScoreboardUiState

**Files:**
- Modify: `app/src/main/java/com/hasanDroid/le5ascorer/ui/scoreboard/ScoreboardViewModel.kt`
- Test: `app/src/test/java/com/hasanDroid/le5ascorer/ui/scoreboard/ScoreboardViewModelTest.kt` (new file)

**Interfaces:**
- Consumes: `MatchDetail.scoreboard: List<RoundScores>` (existing), `RoundScores.playerScores: List<PlayerScore>` (existing, has `cumulativeScore: Int`).
- Produces: `ScoreboardUiState.leadingPlayerIndex: Int?` — highest `cumulativeScore` in the latest `RoundScores`, `null` if `scoreboard` is empty. Consumed by Task 13.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/hasanDroid/le5ascorer/ui/scoreboard/ScoreboardViewModelTest.kt`:
```kotlin
package com.hasanDroid.le5ascorer.ui.scoreboard

import com.hasanDroid.le5ascorer.domain.model.PlayerScore
import com.hasanDroid.le5ascorer.domain.model.RoundScores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoreboardViewModelTest {

    @Test
    fun `leading player is whoever has the highest cumulative score in the latest round`() {
        val scoreboard = listOf(
            RoundScores(0, listOf(
                PlayerScore(0, "A", 10, 10),
                PlayerScore(1, "B", 20, 20),
                PlayerScore(2, "C", 5, 5),
                PlayerScore(3, "D", 15, 15)
            ))
        )
        assertEquals(1, computeLeadingPlayerIndex(scoreboard))
    }

    @Test
    fun `no leader when scoreboard is empty`() {
        assertNull(computeLeadingPlayerIndex(emptyList()))
    }
}
```
This test calls a top-level or companion-object function `computeLeadingPlayerIndex` — decide during implementation whether to make it a private method tested indirectly via `uiState`, or a small internal/testable pure function. Prefer a pure function (easier to unit test without mocking the full ViewModel's Hilt dependencies) — add it as a private function in `ScoreboardViewModel.kt` but exposed as `internal` (not `private`) so the test in the same module can call it directly, or as a top-level function in the same file.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hasanDroid.le5ascorer.ui.scoreboard.ScoreboardViewModelTest"`
Expected: FAIL — `computeLeadingPlayerIndex` doesn't exist yet.

- [ ] **Step 3: Implement**

In `ScoreboardViewModel.kt`:
```kotlin
data class ScoreboardUiState(
    val matchDetail: MatchDetail? = null,
    val isLoading: Boolean = true,
    val gameOver: GameOverResult? = null,
    val leadingPlayerIndex: Int? = null
)

internal fun computeLeadingPlayerIndex(scoreboard: List<RoundScores>): Int? {
    val latestRound = scoreboard.lastOrNull() ?: return null
    return latestRound.playerScores.maxByOrNull { it.cumulativeScore }?.playerIndex
}
```
In `loadMatch()`, add `leadingPlayerIndex = computeLeadingPlayerIndex(it.scoreboard)` to the `_uiState.value = ScoreboardUiState(...)` construction.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.hasanDroid.le5ascorer.ui.scoreboard.ScoreboardViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hasanDroid/le5ascorer/ui/scoreboard/ScoreboardViewModel.kt app/src/test/java/com/hasanDroid/le5ascorer/ui/scoreboard/ScoreboardViewModelTest.kt
git commit -m "Add leadingPlayerIndex to ScoreboardUiState

Highest cumulative score, matching the existing match-list card's
'Leading' convention (LeekhaRepository.getMatchesByStatus).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 13: Highlight the leading player's column and badge double rounds in the scoreboard

**Files:**
- Modify: `app/src/main/java/com/hasanDroid/le5ascorer/ui/scoreboard/ScoreboardFragment.kt`
- Modify: `app/src/main/java/com/hasanDroid/le5ascorer/ui/scoreboard/ScoreboardAdapter.kt`
- Modify: `app/src/main/res/layout/fragment_scoreboard.xml`
- Modify: `app/src/main/res/layout/item_scoreboard_row.xml`

**Interfaces:**
- Consumes: `ScoreboardUiState.leadingPlayerIndex: Int?` (from Task 12), `ScoreboardAdapter.submitData(roundScores, roundList)` (existing signature — will gain a third parameter).
- Produces: `ScoreboardAdapter.submitData(roundScores: List<RoundScores>, roundList: List<Round>, leadingPlayerIndex: Int?)` — new signature, breaking change to the existing call site in `ScoreboardFragment.kt`.

- [ ] **Step 1: Update ScoreboardAdapter.submitData signature and store leadingPlayerIndex**

In `ScoreboardAdapter.kt`, add a field and extend the method:
```kotlin
private var leadingPlayerIndex: Int? = null

fun submitData(roundScores: List<RoundScores>, roundList: List<Round>, leadingPlayerIndex: Int?) {
    roundEntities = roundList
    this.leadingPlayerIndex = leadingPlayerIndex
    submitList(roundScores)
}
```

- [ ] **Step 2: Add leader-indicator views to item_scoreboard_row.xml**

In `item_scoreboard_row.xml`, add four small indicator views, one per player cell (e.g. a 4dp-tall colored bar or dot positioned directly below each score `TextView`): ids `leaderDot1`..`leaderDot4`, default `android:visibility="gone"`, background `@color/brass_400` to match the app's accent color.

- [ ] **Step 3: Highlight the leading player's cell in each row and badge double rounds**

In `ScoreboardAdapter.ScoreboardViewHolder.bind()`, after the existing `highlight()` loop, add:
```kotlin
val dots = listOf(binding.leaderDot1, binding.leaderDot2, binding.leaderDot3, binding.leaderDot4)
cells.forEachIndexed { index, cell ->
    val isLeader = index == leadingPlayerIndex
    cell.setTypeface(cell.typeface, if (isLeader) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    dots[index].visibility = if (isLeader) View.VISIBLE else View.GONE
}

val isDoubleRound = roundScores.playerScores.any { it.roundScore == DOUBLE_ROUND_SCORE }
binding.root.setBackgroundColor(
    if (isDoubleRound) ContextCompat.getColor(binding.root.context, R.color.danger_container)
    else android.graphics.Color.TRANSPARENT
)
```
Add `private const val DOUBLE_ROUND_SCORE = 37` to the companion object (matches `RoundEntryFragment`'s existing `DOUBLE_TOTAL = 37` constant convention — this is the same domain concept surfaced in a different file, so redefine locally rather than sharing a cross-module constant, since these two files don't currently share a constants file and introducing one is out of scope here). Note: `roundScore` (this round only) is the right field to check against 37, not `cumulativeScore` — verify `PlayerScore`'s field names match (`domain/model/Models.kt:41-46` confirms `roundScore: Int` exists).

- [ ] **Step 4: Add a header leader highlight and update the ScoreboardFragment call site**

In `fragment_scoreboard.xml`, apply the same leader treatment to `cardPlayerNames`' header row — no new ids needed, the existing `textHeaderPlayer1`..`textHeaderPlayer4` TextViews just need their typeface toggled. Wire this in `ScoreboardFragment.observeUiState()` where `headers.forEachIndexed { index, view -> ... }` already iterates the four header TextViews:
```kotlin
headers.forEachIndexed { index, view ->
    view.text = playerNames.getOrNull(index).orEmpty()
    view.setTypeface(
        view.typeface,
        if (index == state.leadingPlayerIndex) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
    )
}
```

In `ScoreboardFragment.kt`'s `observeUiState()`, change:
```kotlin
adapter.submitData(detail.scoreboard, detail.rounds)
```
to:
```kotlin
adapter.submitData(detail.scoreboard, detail.rounds, state.leadingPlayerIndex)
```

- [ ] **Step 5: Build and manually verify**

Run: `./gradlew assembleDebug` — expect BUILD SUCCESSFUL.
Manually: open a match with several rounds including a double round, confirm the header and score cells for the current highest-score player are visually emphasized, confirm the double round's row is badged distinctly, confirm editing a round updates the leader highlight correctly (e.g. play a round that changes who's ahead).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hasanDroid/le5ascorer/ui/scoreboard/ app/src/main/res/layout/fragment_scoreboard.xml app/src/main/res/layout/item_scoreboard_row.xml
git commit -m "Highlight the leading player's column and badge double rounds

Leader = highest cumulative score, matching the match-list card's
existing convention. Double rounds (37-point sweeps) get a distinct
row treatment so they stand out when scanning match history.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 14: Add a per-player score trend sparkline

**Files:**
- Create: `app/src/main/java/com/hasanDroid/le5ascorer/ui/scoreboard/ScoreTrendView.kt`
- Modify: `app/src/main/res/layout/fragment_scoreboard.xml`
- Modify: `app/src/main/java/com/hasanDroid/le5ascorer/ui/scoreboard/ScoreboardFragment.kt`

**Interfaces:**
- Consumes: `MatchDetail.scoreboard: List<RoundScores>` (existing).
- Produces: `ScoreTrendView.setData(scoreboard: List<RoundScores>, playerColors: List<Int>)` — a small custom `View` with no other consumers.

- [ ] **Step 0: Check for an existing chart dependency before writing a custom View**

Run: `grep -n "mpandroidchart\|vico\|chart" app/build.gradle.kts`
If a charting library is already a dependency, use it instead of a custom View and adjust this task's steps accordingly. Per the spec's default (confirmed during brainstorming), prefer a small custom `View` over adding a new dependency if none exists — the plan below assumes no existing library.

- [ ] **Step 1: Write ScoreTrendView as a custom View drawing 4 polylines**

```kotlin
package com.hasanDroid.le5ascorer.ui.scoreboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.hasanDroid.le5ascorer.domain.model.RoundScores

class ScoreTrendView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var scoreboard: List<RoundScores> = emptyList()
    private var playerColors: List<Int> = emptyList()
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    fun setData(scoreboard: List<RoundScores>, playerColors: List<Int>) {
        this.scoreboard = scoreboard
        this.playerColors = playerColors
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (scoreboard.size < 2) return

        val maxScore = scoreboard.flatMap { it.playerScores }
            .maxOfOrNull { it.cumulativeScore }?.coerceAtLeast(1) ?: 1
        val stepX = width.toFloat() / (scoreboard.size - 1)

        for (playerIndex in 0 until 4) {
            linePaint.color = playerColors.getOrElse(playerIndex) { 0xFF888888.toInt() }
            var previousX = 0f
            var previousY = height.toFloat()
            scoreboard.forEachIndexed { roundIdx, round ->
                val score = round.playerScores.getOrNull(playerIndex)?.cumulativeScore ?: 0
                val x = roundIdx * stepX
                val y = height - (score.toFloat() / maxScore * height)
                if (roundIdx > 0) canvas.drawLine(previousX, previousY, x, y, linePaint)
                previousX = x
                previousY = y
            }
        }
    }
}
```

- [ ] **Step 2: Add the view to the layout**

In `fragment_scoreboard.xml`, add a `ScoreTrendView` (id `trendView`, e.g. `56dp` height, `marginTop=space_sm`) below `cardPlayerNames` and above the `NestedScrollView`, wrapped in a collapsible container if the spec's "collapsible section" option is chosen — for the first implementation, keep it always-visible and simple (a fixed-height strip); a collapse/expand affordance can be added later if it proves visually heavy, but is not required by the spec's minimum bar.

- [ ] **Step 3: Wire data in the Fragment**

In `ScoreboardFragment.observeUiState()`, after `adapter.submitData(...)`, add:
```kotlin
val playerColors = listOf(
    ContextCompat.getColor(requireContext(), R.color.brass_400),
    ContextCompat.getColor(requireContext(), R.color.ink_red),
    ContextCompat.getColor(requireContext(), R.color.success),
    ContextCompat.getColor(requireContext(), R.color.text_secondary)
)
binding.trendView.setData(detail.scoreboard, playerColors)
```
Hide `trendView` (visibility GONE) when `detail.scoreboard.size < 2` since a single-round trend isn't meaningful — check this alongside the existing `hasRounds` visibility logic already in this method.

- [ ] **Step 4: Build and manually verify**

Run: `./gradlew assembleDebug` — expect BUILD SUCCESSFUL.
Manually: open a match with 3+ rounds, confirm 4 distinct-colored lines render showing each player's cumulative trend; open a match with 0-1 rounds, confirm the trend view is hidden rather than showing a broken/empty chart.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hasanDroid/le5ascorer/ui/scoreboard/ScoreTrendView.kt app/src/main/res/layout/fragment_scoreboard.xml app/src/main/java/com/hasanDroid/le5ascorer/ui/scoreboard/ScoreboardFragment.kt
git commit -m "Add a per-player score trend sparkline to the scoreboard

Custom-drawn 4-line polyline view, no new charting dependency.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Phase 6: Global responsive pass

### Task 15: Audit and fix small-phone / tablet / landscape layout issues

**Files:** Any of `fragment_new_match.xml`, `fragment_round_entry.xml`, `fragment_scoreboard.xml`, `fragment_match_list.xml`, `fragment_settings.xml`, `dialog_end_game.xml`, `item_match.xml`, `item_scoreboard_row.xml`, `item_player_row.xml` — modified only where testing in Step 1 finds a real break.
Possible new files: `app/src/main/res/layout-sw600dp/fragment_new_match.xml` (two-column player fields, only if Step 1 confirms it's warranted).

**Interfaces:** None — layout-only, no code interface changes expected (unless a `sw600dp` alternate needs new/renamed view ids reconciled with the Kotlin binding code, in which case keep ids identical to the phone layout so `ViewBinding` classes stay compatible).

- [ ] **Step 1: Test every screen at three configurations and log concrete breaks**

Using the Android Studio emulator (or the Layout Validation panel with multiple device previews), check each of the six fragments + dialog at:
1. A small phone (~360dp width, e.g. Pixel 4a or a custom 360x800 AVD)
2. A 600dp+ tablet (e.g. Pixel Tablet or Nexus 9 AVD)
3. Landscape orientation on a standard phone (e.g. Pixel 6 rotated)

For each, note concretely what breaks (text clipping, overflow, wasted whitespace, elements overlapping) — do not fix speculatively. This step's output is a list of file:issue pairs that the remaining steps address one at a time.

- [ ] **Step 2: Fix confirmed small-phone breaks**

For each issue logged in Step 1 under "small phone," apply the minimal fix using existing `dimens.xml` tokens (e.g. reducing a hardcoded margin, allowing a `TextView` to wrap instead of truncate, adjusting the round-entry action-card row to scroll horizontally if `112dp`-tall cards at 4-across genuinely don't fit at 360dp — verify this specific case first since it's flagged as a likely candidate in the spec).

- [ ] **Step 3: Add sw600dp alternate for New Match's player fields if confirmed worthwhile**

If Step 1 confirms the 4 stacked player fields waste significant space on a 600dp+ layout, create `app/src/main/res/layout-sw600dp/fragment_new_match.xml` as a copy of the phone layout with `cardPlayers`' inner `LinearLayout` changed to a 2-column `GridLayout` (matching the pattern already used for `layoutPlayerRows` in `fragment_round_entry.xml`) — keep every view id identical to the phone version so `FragmentNewMatchBinding` stays valid for both. Skip this step entirely if testing shows the single-column phone layout reads fine on tablets (centered with side margins) — don't add a layout variant without a demonstrated problem.

- [ ] **Step 4: Fix confirmed landscape breaks on Round Entry and Scoreboard**

For each landscape issue logged in Step 1, apply the minimal fix — likely candidates per the spec are ensuring `fragment_round_entry.xml`'s `ScrollView` genuinely scrolls in landscape (verify `fillViewport`/height constraints don't force clipping) and `fragment_scoreboard.xml`'s `NestedScrollView` similarly. If both already scroll correctly, no landscape-specific layout is needed — verify before adding one.

- [ ] **Step 5: Re-run the three-configuration check after fixes**

Repeat Step 1's checks on every file touched in Steps 2-4 to confirm the fix resolved the issue without introducing a new one at a different configuration (e.g. a small-phone fix that now looks wrong on tablet).

- [ ] **Step 6: Commit (one commit per distinct fix, or grouped by screen if fixes are small)**

```bash
git add <files touched in this step>
git commit -m "Fix <screen> layout for <small phone|tablet|landscape>: <specific issue>

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Phase 7: Regression verification

### Task 16: Update screenshot tests and run the full suite

**Files:**
- Modify: `app/src/androidTest/java/com/hasanDroid/le5ascorer/ScreenshotTest.kt`
- Modify: `app/src/androidTest/java/com/hasanDroid/le5ascorer/ScreenshotSeed.kt`

**Interfaces:** Consumes the existing screenshot-test harness structure (read both files in full at the start of this task — they weren't covered by earlier exploration in this plan).

- [ ] **Step 1: Read the existing screenshot test files**

Read `ScreenshotTest.kt` and `ScreenshotSeed.kt` in full to understand the current seeding/capture pattern before modifying.

- [ ] **Step 2: Remove any TEAM-rule seeded scenario, add round-entry pip/card states**

Update `ScreenshotSeed.kt` to seed only `ScoreRule.INDIVIDUAL` matches (drop any `TEAM` seed data, consistent with Task 3). Add seed scenarios for: an empty round (0 hearts assigned), a partially-filled round (some hearts + Q♠ assigned to different players), and an active double — so `ScreenshotTest.kt` can capture the new pip/card visuals from Phase 4.

- [ ] **Step 3: Add scoreboard screenshot scenarios for the new leader highlight and double badge**

Add a seed scenario with a multi-round match where the leader changes partway through, and one round that's a double (37 points), so the new leader-column highlight and double-row badge from Phase 5 are captured.

- [ ] **Step 4: Run the full test suite**

Run: `./gradlew testDebugUnitTest` — expect all unit tests PASS (including the new `NewMatchViewModelTest`, `ScoreboardViewModelTest`, and the updated `ScoreEngineGameOverTest`).
Run: `./gradlew connectedDebugAndroidTest` (requires an emulator/device) — expect the instrumented/screenshot tests PASS or produce reviewable screenshots for the new states.

- [ ] **Step 5: Commit**

```bash
git add app/src/androidTest/java/com/hasanDroid/le5ascorer/ScreenshotTest.kt app/src/androidTest/java/com/hasanDroid/le5ascorer/ScreenshotSeed.kt
git commit -m "Update screenshot tests for the app overhaul

Drops TEAM-rule seed data, adds round-entry pip/card and scoreboard
leader/double-badge scenarios.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Verification

After all tasks are complete, do a full end-to-end manual pass:

1. `./gradlew assembleDebug` — clean build with no warnings about missing/unused resources introduced by this plan.
2. `./gradlew testDebugUnitTest` — all unit tests pass, including every new test added in this plan.
3. `./gradlew connectedDebugAndroidTest` on an emulator — instrumented/screenshot tests pass.
4. Manual walkthrough on an emulator: create a new match with player-name suggestions active (confirm suggestions from a prior match appear), play several rounds using the redesigned action cards (confirm hearts pip row and Q♠/10♦ animations work, confirm a double round still validates and saves correctly), reach game-over, confirm the end-game dialog and confetti still work unchanged, return to the match list and confirm the "Leading" badge is still consistent with the scoreboard's leader highlight for the same match, open Settings and confirm the version row no longer looks tappable while Contact/Rate rows still do, rotate the device and resize to a tablet-class emulator to spot-check the responsive fixes from Phase 6.
5. Confirm no screen still offers team scoring anywhere in the creation flow, and that a manually-inserted historical `ScoreRule.TEAM` match (if one exists in test data) still opens and displays without crashing.
