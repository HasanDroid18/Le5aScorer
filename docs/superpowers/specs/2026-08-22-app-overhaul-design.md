# Le5a Scorer App Overhaul

Date: 2026-08-22
Status: Approved by user, ready for implementation planning

## Context

The app's core scoring loop works, but six problems accumulated as it grew:
buttons are inconsistent in touch target, accessibility, and style
adherence; player names must be retyped every match even though history is
already stored; team scoring is unused/unwanted complexity carried in the
data model and UI; the round-entry screen ("select a player, tap a fixed
icon button") is functionally fine but visually flat for a card-scoring
app; the match-detail/scoreboard screen is a dense table with no sense of
who's winning the match as a whole; and no screen has been audited for
small-phone, tablet, or landscape layouts.

This spec bundles all six into one ordered plan. The order is deliberate:
removing team scoring first shrinks the surface the redesigns need to
handle (no team-vs-individual branching to preserve visually), the button
audit fixes structural issues the round-entry redesign would otherwise
inherit, and the responsive pass runs last because it needs the other five
changes to already be in their final shape.

Sequence:
1. Remove team scoring
2. Fix buttons (consistency + accessibility audit)
3. Player name suggestions from history
4. Round entry redesign
5. Scoreboard redesign
6. Global responsive pass

## 1. Remove team scoring

**Why:** `ScoreRule.TEAM` is unwanted complexity. It's a real, persisted
Room enum, not just a UI toggle — deleting it outright risks breaking
deserialization of any match saved with `TEAM`. The user approved keeping
the enum value internally for backward compatibility while removing every
user-facing path that can produce or explain it.

**Data layer — no destructive migration needed** (string-backed enum
column, `Converters.kt` round-trips via `ScoreRule.valueOf(name)`):
- `data/local/entity/Enums.kt` — keep `ScoreRule.TEAM` in the enum
  (comment explaining it's legacy-only, retained for old saved matches).
- `domain/ScoreEngine.kt:177-` (`checkGameOver`) — the `ScoreRule.TEAM`
  branch (pairs players 0+2 vs 1+3) stays as dead-but-correct code path so
  an old TEAM match still resolves game-over correctly if ever reopened.
  No behavior change here; it's already correct, just unreachable for new
  matches once creation is locked to INDIVIDUAL.

**New Match screen** (`ui/newmatch/NewMatchFragment.kt`,
`fragment_new_match.xml`, `NewMatchViewModel.kt`):
- Remove `toggleScoreRule` (`MaterialButtonToggleGroup` with
  `buttonIndividual`/`buttonTeam`), its section header ("Score rule"),
  and `textRuleDescription` info row from `fragment_new_match.xml`.
- Remove `setupScoreRuleSelector()` / `updateRuleDescription()` from
  `NewMatchFragment.kt`.
- `NewMatchViewModel.updateScoreRule()` and the `scoreRule` field: drop the
  setter; keep `scoreRule` in `NewMatchUiState` hardcoded to
  `ScoreRule.INDIVIDUAL` (never settable from UI).
- The "First team" / "Second team" section headers in `cardPlayers` are
  just labels grouping player-name pairs (1-2, 3-4) — not a functional
  team toggle, per the Explore findings. Since "team" language reads as
  misleading once team scoring is gone (the pairing has no scoring
  meaning — it's purely a 2-and-2 visual grouping of the 4 name fields),
  rename them to neutral labels (e.g. "Players 1 & 2" / "Players 3 & 4").

**Strings** (`values/strings.xml`): remove `rule_team_desc`, `cd_team_rule`,
`first_team`, `second_team` (replaced per the neutral labels above). Keep
`rule_team` only for rendering historical TEAM matches in the match-list
summary (see below) — otherwise remove it too if that display path is
dropped.

**Match list / match card** (`ui/matches/MatchAdapter.kt`,
`item_match.xml`): `textRuleSummary` currently formats
`R.string.rule_individual`/`rule_team` from `match.scoreRule` — keep the
individual-label path, keep the team-label string only if any historical
TEAM matches must still render a readable summary (recommended: keep
`rule_team` string just for this display case, but it's no longer
reachable from creation).

**`SampleDataGenerator.kt`**: remove any TEAM-seeded sample matches so
manual testing / screenshot tests don't exercise the removed path.

## 2. Fix buttons

**Why:** The Explore audit found a real, consistent set of problems, not
isolated bugs — the shared `Widget.Le5a.Button.*` / `Widget.Le5a.Card.*`
style system is good, but many concrete instances silently diverge from
it. Fix by conforming instances to the existing system rather than
inventing new styles.

**Findings to fix** (file:line references from the audit):

- **Accessibility — missing contentDescription on icon-only controls**
  (highest priority, affects every screen):
  - `cardHearts`/`cardQSpades`/`cardTenDiamonds`/`cardDouble` in
    `fragment_round_entry.xml` (lines ~281, 322, 379, 438) — these are
    clickable/long-clickable `MaterialCardView`s with no accessible name
    anywhere (not in XML, not set in `RoundEntryFragment.kt`). Add
    `contentDescription` (can be dynamic in Kotlin, matching the pattern
    already used for `cardPlayerRow` at `RoundEntryFragment.kt:204-205`).
  - `app:navigationIcon` back arrows in all four toolbars
    (`fragment_new_match.xml:28`, `fragment_round_entry.xml:30`,
    `fragment_scoreboard.xml:31`, `fragment_settings.xml:31`) never set
    `app:navigationContentDescription` — zero hits project-wide. Add a
    shared `@string/cd_navigate_back` to all four.
  - `buttonEdit` in `item_scoreboard_row.xml:92` reuses the label string
    `edit_round` as its contentDescription instead of a `cd_`-prefixed
    description string, inconsistent with the rest of the app's naming
    convention (`cd_match_options`, `cd_select_player`, etc.) — rename to
    a proper `cd_edit_round` string for consistency, even though it
    currently works.

- **Style/token consistency:**
  - `radius_pill` override repeated inline on every full-width CTA
    (`buttonSave`, `buttonCreate`, `buttonAddRound`, `buttonBackToHome`,
    `buttonCapturePhoto`, `buttonShowRoundScores`) even though
    `Widget.Le5a.Button.Primary`/`Secondary` default to `radius_md`. Since
    100% of the surveyed full-width CTAs want the pill shape, add
    `Widget.Le5a.Button.Primary.Pill` / `.Secondary.Pill` style variants
    (or change the base default — decide during implementation which is
    less disruptive) and point every call site at it instead of repeating
    the inline override.
  - `buttonCreate` (`fragment_new_match.xml:421`) has an ad hoc
    `app:elevation="@dimen/elev_float"` (8dp) not present on any sibling
    Primary button (which use the style default `elev_raised`, 2dp) —
    remove the override for consistency unless there's an intentional
    reason to keep New Match's CTA more elevated (confirm during
    implementation, default to removing it).
  - Toggle-group buttons in `fragment_new_match.xml` (`button51`,
    `button101`, `button151` — these survive the team-toggle removal
    above) use raw `<Button>` instead of `<MaterialButton>`. Works today
    only because `MaterialButtonToggleGroup` auto-inflates children, but
    should be explicit `MaterialButton` for consistency with every other
    button in the app.
  - `item_player_row.xml:10-25` (`cardPlayerRow`) duplicates
    `clickable`/`focusable`/`minHeight` attributes that already live in
    `Widget.Le5a.Card.Clickable` instead of extending that style. Since
    this card's colors/stroke/elevation are entirely overridden at
    runtime in `RoundEntryFragment.updatePlayerRow` anyway, converge the
    static XML onto the shared style so the two don't drift.
  - `cardLeadingPlayer` (`item_match.xml:67-78`) and the `fab` in
    `fragment_match_list.xml:95-108` similarly duplicate card/FAB style
    attributes inline instead of extending `Widget.Le5a.Card` /
    `Widget.Le5a.Fab`. Converge where the shared style is a clean match;
    note `ExtendedFloatingActionButton` vs `FloatingActionButton` may
    need a dedicated extended-FAB style rather than reusing `Widget.Le5a.Fab`
    verbatim — check during implementation.
  - `item_scoreboard_row.xml:89-90` sizes `buttonEdit` from
    `@dimen/scoreboard_action_col` instead of `@dimen/touch_min` (both
    48dp today, but coupled by coincidence) — resize off `touch_min`
    directly so the two concerns (touch target size vs. column width)
    can vary independently.
  - `buttonReset` (`fragment_round_entry.xml:33-42`) has no explicit
    `layout_width`/`layout_height`, unlike every other standalone button
    in the app — give it explicit sizing so its hit-rect is verifiable
    and consistent.
  - `layoutVersion` row in `fragment_settings.xml:148-155` sets
    `clickable="false" focusable="false"` but still inherits
    `Widget.Le5a.Row`'s tappable-looking `bg_row` background, making a
    static info row visually indistinguishable from the tappable rows
    above it (`layoutContactSupport`, `layoutRateApp`). Give non-clickable
    rows a distinct (flat, no ripple-shaped) background.

This is a conformance pass, not new component design — every fix points
an existing instance at the app's existing style system. No new visual
language is introduced here (that's sections 4 and 5).

## 3. Player name suggestions from history

**Why:** `PlayerDao` already has `getAllPlayers(): Flow<List<PlayerEntity>>`
(ordered `lastUsedAt DESC`) and `searchPlayersByName(query)` — this is
wiring, not new data-layer work.

**Design:** Convert each of the 4 `TextInputEditText` fields in
`fragment_new_match.xml` (`editPlayer1`..`editPlayer4`, inside their
`TextInputLayout`s) to `AutoCompleteTextView` (Material's
`TextInputLayout.ExposedDropdownMenu` pattern — `app:endIconMode` changes
from `clear_text` to the dropdown menu style, or both can coexist per
Material's docs). Each field gets an `ArrayAdapter` sourced from
`getAllPlayers()`, collected once as a `StateFlow<List<String>>` in
`NewMatchViewModel` (map `PlayerEntity.name`, already sorted by recency)
and shared across all four fields — no per-field DB query needed since the
full list is small and filtering is handled client-side by
`AutoCompleteTextView`'s built-in text filter.

**Behavior:**
- Selecting a suggestion fills the field, same as typing.
- Typing a name not in history still works as free text (this is an
  `AutoCompleteTextView`, not a locked picker) — `updatePlayerXName` fires
  on every keystroke exactly as it does today via `addTextChangedListener`.
- A name already selected in one field should not be suggested again for
  the other three (avoid suggesting "Ahmad" as player 2 when player 1 is
  already "Ahmad") — filter the shared adapter list against the other
  three fields' current values before showing the dropdown.
- No new DB migration, no new use case — `NewMatchViewModel` gains one
  injected read of `PlayerDao.getAllPlayers()` (via repository, matching
  existing DI patterns in `AppModule.kt`) and exposes it as UI state.

**Files touched:** `ui/newmatch/NewMatchViewModel.kt`,
`ui/newmatch/NewMatchFragment.kt`, `fragment_new_match.xml`,
`data/repository/LeekhaRepository.kt` (expose `getAllPlayers` if not
already surfaced there — check during implementation).

## 4. Round entry redesign

**Why:** The interaction model (select a player row, tap a suit card to
assign) already works and is validated by a well-tested `Validity` engine
in `RoundEntryFragment.kt` and `RoundEntryViewModel`'s double-cancellation
logic. The ask is specifically about the *visual* flatness of "tap fixed
buttons," not the input model — so this redesign keeps every existing
ViewModel method (`incrementHeart`, `decrementHeart`, `toggleQSpades`,
`toggleTenDiamonds`, `setDouble`, `reset`, `saveRound`) and the existing
`Validity` computation untouched, and changes only how assignment reads
visually. This was presented to the user as "Option A: enhanced tap" vs.
"Option B: true drag-and-drop," and Option A was chosen specifically to
avoid drag-and-drop's accessibility problems (TalkBack cannot drag) and
implementation risk on a screen where entry speed and correctness matter
more than novelty.

**Current structure** (from Explore findings): `containerPlayArea` is a
`FrameLayout` styled as a felt "well," holding a horizontal row of four
equal-weight `MaterialCardView`s (`cardHearts`, `cardQSpades`,
`cardTenDiamonds`, `cardDouble`), all `112dp` tall
(`@dimen/action_card_height`), same style
(`Widget.Le5a.Card.Playing.Clickable`) except `cardDouble`'s inline
danger-red override. Each shows a static icon/text + a count/label. This
is the part being redesigned.

**Design:**
- **Hearts** (`cardHearts`): replace the static heart-icon + count label
  with a **row of up to 13 small heart pip glyphs** inside the card,
  filling left-to-right as `heartCount` increases (already-assigned pips
  render solid `ink_red`; remaining pips render as a faint outline in
  `felt_outline`/`text_tertiary`). Tapping the card still calls
  `incrementHeart`, long-press still calls it 5× — no ViewModel change.
  The visual delta: the card renders `state.playersData[selected].heartCount`
  as filled pips (bind in `RoundEntryFragment.render`/`updateScoringFor`
  path, same place `updatePlayerRow` already reads player state), and a
  new small pip-row indicator directly above `containerPlayArea` (or in
  `cardStatus`) shows the **shared pool** — how many of the 13 hearts are
  still unassigned across all players — reusing the same pip glyph so the
  "pool empties as you deal" reading is consistent between the pool
  indicator and each player's row (`item_player_row.xml`'s existing
  `layoutHearts`/`textHeartCount` already shows count per player; no
  layout change needed there, this is additive).
- **Q♠ / 10♦** (`cardQSpades`, `cardTenDiamonds`): keep the tap-to-assign
  model, but replace the static "Q + spade icon" / "10 + diamond icon"
  content with a small `ImageView`/composed drawable that visually reads
  as an actual mini playing card face (this can reuse existing `ink`/
  `ink_red` colors and `bone` card-face background token already defined
  in `colors.xml` — no new tokens needed). On assignment, animate the
  card cross-fading/translating from `containerPlayArea`'s pool position
  toward the selected player's row using `ObjectAnimator` (the codebase
  already has this pattern in `NewMatchFragment.playEntranceAnimation`/
  `animateButtonEnabled` — follow the same style: short duration ~250-350ms,
  `DecelerateInterpolator`). On de-assignment (tapping again to toggle
  off, or the chip-removal path in `chipGroupActions`), reverse the
  animation back to the pool. Card becomes visually "spent"
  (dimmed/outlined, not removed) in the pool once assigned, matching the
  hearts pip pattern — pool empties, rows fill.
- **Double** (`cardDouble`): unchanged behavior and visual treatment (it's
  already visually distinct as a face-down red card) — no redesign need
  identified here.
- **Player rows** (`item_player_row.xml`): no structural change; the
  existing `layoutHearts`/`layoutQSpades`/`layoutTenDiamonds` sub-rows
  already show assigned suits per player and are the animation's landing
  target.
- Keep `textHeartsHint` (the "+5 hearts" long-press hint) and
  `chipGroupActions` (the removable action chips) exactly as they are —
  they already provide a correction/undo path and aren't part of the
  "buttons feel flat" complaint.

**Explicitly not changing:** `RoundEntryViewModel`'s double-inference
logic (`sum(total)==37`), `Validity` computation in
`RoundEntryFragment.kt`, `saveRound()`'s action-building logic, or the
status card / checklist (`layoutNormalStatus`/`layoutDoubleStatus`).
Those are correct and untouched by this redesign — only
`containerPlayArea`'s four cards and their render/animation code change.

## 5. Scoreboard redesign

**Why:** The user confirmed keeping the table structure (right for
comparing 4 running totals over N rounds) while raising its visual
quality: leader emphasis, trend visibility, more tactile rows, and
call-outs for dramatic moments (doubles).

**Current structure** (from earlier reads): `fragment_scoreboard.xml` has
a fixed player-name header card (`cardPlayerNames`), a `RecyclerView`
(`ScoreboardAdapter`) of `item_scoreboard_row` rows (round # + 4
cumulative-score cells, best/worst color-coded via
`ScoreboardAdapter.highlight()`, edit button), a danger-styled
`cardWinner` banner, and `buttonAddRound`/`buttonBackToHome`.

**Design:**
- **Leading-player column emphasis:** compute the current leader (lowest
  cumulative score — recall higher score is worse, matching
  `checkGameOver`'s "reaching target = loser" semantics) from the latest
  `RoundScores` and highlight that player's entire column in the header
  card and in every row (subtle background tint or accent border on that
  column, not just per-row best/worst text color as today). This is a new
  derived value computed once per `uiState` update, not per-row — expose
  it from `ScoreboardViewModel` as e.g. `leadingPlayerIndex: Int?` in
  `ScoreboardUiState`, and pass it into `ScoreboardAdapter.submitData`.
- **Per-player trend:** add a small sparkline (simple multi-line path,
  4 series) either as a header element above `cardPlayerNames` or a
  collapsible section — plots each player's cumulative score per round.
  Reuses the same `RoundScores` list already in `MatchDetail.scoreboard`,
  no new data source. Keep this simple (a custom-drawn `View` with
  `Canvas.drawLines`, or evaluate a lightweight existing chart approach if
  one is already a dependency — check `build.gradle.kts` during
  implementation before adding a new library; prefer a small custom View
  over a new dependency for 4 short polylines).
- **Tactile round rows:** `item_scoreboard_row.xml` gets alternating row
  background treatment (subtle, staying within `felt_*` tokens) and the
  edit affordance becomes clearer — either a full-row tap target (with
  the pencil icon as a trailing visual cue rather than the sole hit
  target) or a swipe-to-edit gesture. Recommend the full-row-tap approach
  during implementation (lower risk, no new gesture-conflict with the
  containing `NestedScrollView`/`RecyclerView` scroll).
- **Double/big-round call-out:** when a round's total is 37 (the existing
  double-detection convention from `RoundEntryViewModel`, i.e. one
  player's round score is 37), badge that row distinctly (accent
  border/background using the existing `danger`/`warning` token,
  consistent with how `cardDouble` reads in round entry) so it stands out
  when scanning match history.
- Leave `cardWinner`, the confetti/end-game dialog flow
  (`EndGameDialogFragment`, `playFullScreenConfetti`), and
  `buttonAddRound`/`buttonBackToHome` untouched — already approved as
  working well and out of scope.

**Files touched:** `ui/scoreboard/ScoreboardViewModel.kt` (add derived
leader/trend state), `ui/scoreboard/ScoreboardAdapter.kt` (leader-column
+ double-badge rendering), `fragment_scoreboard.xml`,
`item_scoreboard_row.xml`, possibly a new small custom View for the
sparkline.

## 6. Global responsive pass

**Why:** Runs last because it needs the other five changes already in
their final layout shape — auditing before then would mean re-auditing
after.

**Scope:** Sweep all fragments (`fragment_new_match.xml`,
`fragment_round_entry.xml`, `fragment_scoreboard.xml`,
`fragment_match_list.xml`, `fragment_settings.xml`,
`dialog_end_game.xml`) plus `item_match.xml`/`item_scoreboard_row.xml`/
`item_player_row.xml` for:
- Hardcoded dp widths that break on small phones (≤360dp width) — check
  especially `fragment_round_entry.xml`'s `GridLayout` player rows and
  the four suit cards (currently `112dp` fixed height regardless of
  screen width, which is fine for height but the pip/card content added
  in section 4 needs to not overflow on narrow cards).
- Places genuinely better with a two-column layout on tablets/foldables
  (`sw600dp` alternates) — New Match's 4 stacked fields are the clearest
  candidate (two columns of two on wide screens); evaluate others
  case-by-case rather than blanket-applying.
- Text using `sp`/scalable `TextAppearance.Le5a.*` styles already (spot
  check — the existing style system appears to already do this
  correctly; the audit should confirm, not assume).
- Landscape sanity on the two most content-dense screens
  (`fragment_round_entry.xml`, `fragment_scoreboard.xml`) — these are the
  most likely to overflow vertically in landscape given their multiple
  stacked cards.

**Approach:** Add `layout-sw600dp` (or `layout-land`) alternates only
where the phone-default layout demonstrably breaks or wastes significant
space when tested at those configurations — not layout variants for their
own sake. Verify each candidate with the Android Studio layout preview or
an emulator at representative sizes (small phone ~360dp, tablet ~600dp+,
landscape) before deciding a variant is warranted.

## Testing

- `ScoreEngineGameOverTest.kt` already covers `ScoreRule.INDIVIDUAL` and
  `ScoreRule.TEAM` game-over logic — keep the `TEAM` test case (it now
  documents "old matches still resolve correctly," not "new matches can
  be TEAM") and add a test/assertion that new-match creation cannot
  produce `ScoreRule.TEAM`.
- `ScreenshotTest.kt`/`ScreenshotSeed.kt` (existing instrumented test
  infra) should be re-run after each section lands to catch visual
  regressions, and extended to cover the redesigned round-entry pip/card
  states (empty pool, partially filled, double active) and the
  redesigned scoreboard (leader highlight, double-badged row).
- Manual verification per section: build and run on an emulator, exercise
  the changed screen end-to-end (create a match, play a round including a
  double, edit a past round, reach game-over) after each of the 6 items,
  not only at the end.
