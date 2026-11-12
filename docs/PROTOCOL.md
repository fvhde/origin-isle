# The SuperX / OriginIsland protocol

This is a distilled reference for vivo OriginOS's "atomic notification" (原子通知) system, known
internally as **SuperX**. It's the mechanism behind the OriginIsland pill.
Everything here was tested on vivo X200 Pro mini, OriginOS, Android 14/API 36.

The full, verbatim bundle-key dictionary lives in
[`OriginIslandConstants.kt`](../app/src/main/java/com/originisle/android/island/OriginIslandConstants.kt);
this document explains how the pieces fit together and what actually works in practice.

## 1. Getting whitelisted: `setSuperXInfosSceneList`

SuperX cards are only rendered for apps OriginOS has explicitly granted. There is a hidden method on
`NotificationManager`:

```java
void setSuperXInfosSceneList(List<String> scenes, List<String> enabled, List<String> pkgs, List<String> allowed)
```

called reflectively (it's not in the public SDK). It's whitelisted **by package name** on vivo's
side — arbitrary third-party apps aren't normally granted it. This project's `applicationId` spoofs
a package (`com.autonavi.minimap`, AMap) that vivo's OS grants by default, purely to get onto that
allow-list; see the main README for the implications of that.

Scenes are named strings: `NAVIGATION`, `MOVIE`, `HEALTH_REGISTER`, `TAXI`, `TAKEOUT`, `DELIEVERY`
(sic), `CAR_STATE`, `METTING` (sic), `TRAIN`, `FLIGHT`, `INCALLING`, `VOIPCALL`, `TIMER`,
`RIDE_GUIDE`, `CRITICAL`. **In practice, only `NAVIGATION` was reliably granted** on the test device
even after requesting all of them — cards posted under any other scene were silently dropped. Use
`NAVIGATION` as the carrier scene and let the card *template* (see below) drive what it actually
looks like.

## 2. Posting a card

A SuperX card is just an ordinary `Notification`, posted with:

- tag = `"VIVO_SUPERX_TAG"` (this is what tells OriginOS to treat it specially),
- `setExtras(bundle)` where `bundle` is built as described below,
- through the normal `NotificationManager.notify(tag, id, notification)`.

Ending a card = posting a small bundle with `operation = 2`, `showNotify = false`, the same `scene`,
and `changedRecord = Int.MAX_VALUE`, under the same tag/id.

## 3. Bundle structure

Top-level keys (`notification.superx.*`) plus four sub-bundles:

| Sub-bundle | Key | Purpose |
|---|---|---|
| `baseInfos` | `notification.superx.baseInfos` | icon, title, content, sub-info (text/capsule/progress/loading), status overlay (success tick / generating spinner) |
| `infos` | `notification.superx.infos` | the **expanded card body** — shape depends on `template` |
| `shortInfos` | `notification.superx.shortInfos` | collapsed-state image/text (rarely the visible pill; superseded by `island`) |
| `island` (`island.superx.*`) | `notification.superx.island` | the actual **pill**: `leftInfo`/`rightInfo`, force-show, click behaviour |
| `capsule` | `notification.superx.capsule` | the tiny status chip (icon + short text, colours) |

### Card body templates (`notification.superx.template`)

| # | Name | Shape |
|---|---|---|
| 1 | Priority info | describe + coreInfo + one image |
| 2 | Progress visual | a progress bar with optional node markers |
| 3 | Text symmetry | `leftMain/leftSub · midIcon+midMsg · rightMain/rightSub` — the scoreboard layout, used for live scores. Also: `midMainType` 1 = icon / 2 = text (`midMainMsg`), `midTopMsg` (a line above the middle), `rightMainExtra` (drawn as a small red dot after the right name). Every text slot is a `SuperXChronometer`: a JSON `TimerFormat` (`base`, `countDown`, `started`, `elapsedFormatS/MS/HMS`…) ticks by itself, but past an hour it switches to the H:M:S format, so it can't show a football minute like `67'` |
| 4 | Base | title + content + sub-text/capsule/progress/loading — the general-purpose card |
| 5 | Navigation | one icon + a nav message string |
| 6 | Custom | (present in the protocol; not used by this app) |
| 7 | Custom RemoteViews | fully custom layout — **not supported on the test device** (`isSupportCustomFun` returns false) |
| 8 | Buttons | up to 3 filled buttons (icon + label + click) |
| 9 | Driving navigation | highlighted/normal text pair + sub text |

### Right-island templates (`island.superx.rightTemplate`)

| # | Shape |
|---|---|
| 1 | Wave (audio visualiser — media playback) |
| 2 | Progress ring |
| 3 | Loading spinner |
| 4 | Text + icon |
| 5 | Icon + text |
| 6 | Capsule text (a small pill of text, optionally coloured) |
| 7 | Two stacked lines (`rightInfo.doublelineText`, exactly two) |

The **left** island template is "icon + content" (1), or "icon + two stacked lines" (3) when
`leftInfo.doublelineText` holds exactly two lines. Live tennis uses 3 on the left and 7 on the
right: both names stacked, their set scores stacked opposite.

### The navigation templates (5 and 9)

Templates 5 and 9 exist for turn-by-turn navigation. `NavigationCard` posts template 9 unconditionally
for any recognised navigation notification (Maps, anything with `CATEGORY_NAVIGATION`) — no toggle,
it's the default path. Template 5 isn't posted by any card yet; only the in-app sample exists.

Waze's driving card can't be supported: its sole notification is a foreground-service notice ("Waze /
Running. Tap to open.") with no subtext, no large icon and all three RemoteViews slots null, identical
whether it is navigating or just open. There is no turn data to read, so Waze is left out of `NAV_APPS`
rather than fed to `NavigationCard` as an empty driving card — it still casts as an ordinary generic
card, same as any other app's plain foreground-service notice. Turn it off in the Apps tab if you don't
want that.

| Key | Template | Carries |
|---|---|---|
| `infos.navIcon` / `infos.navMsg` | 5 | one icon and a single message line |
| `infos.dirIcon` | 9 | the maneuver arrow |
| `infos.mainHighlightText` / `infos.mainNormalText` | 9 | maneuver line (emphasised) + street line |
| `infos.dirSubText` | 9 | journey summary ("12 min · 4,5 km · 14:32") |
| `infos.dirAssistText` + `infos.dirAssistType` (1 = text) | 9 | secondary hint under the maneuver ("Keep left") |

`infos.laneList` (lane guidance) is catalogued in `OriginIslandConstants` but deliberately **not**
written by the builder: the protocol dump gives the key but not its element type, and handing vivo a
wrongly-typed list throws inside the system's own unparcel — on a live navigation card.

Template 9 is confirmed rendering correctly on hardware (X100U). Template 5 is not yet confirmed — a
rejected post looks identical to "the app did nothing" (see §4), so the in-app tester carries a sample
for it: if the sample never appears, the template is being dropped.

## 4. What makes vivo actually accept the post

This is the part that isn't documented anywhere and took the most trial and error. A SuperX
`notify()` missing ANY of the following is **silently dropped** — no crash, no log, no error, the
card simply never appears and there is no record of it:

- The **posting notification channel** must be `IMPORTANCE_HIGH`. `LOW`/`DEFAULT` gets minimised and
  never reaches the island.
- **Real, non-empty** `setContentTitle`/`setContentText`. Don't `setSilent(true)` and don't leave
  content blank — an empty-content SuperX post is treated as invalid and dropped.
- `operation = 0` in the bundle (create/update). A non-zero "update" operation on a card that doesn't
  exist yet is also dropped.
- `notification.superx.displays` set to a real bitmask, e.g. **65809** = notification (1) | lockscreen
  (16) | statusbar (256) | AOD (65536). `0` = shown nowhere.
- **A content intent (`clickResp`) is required.** A SuperX post with no content/click intent is
  rejected outright — always supply one, falling back to just launching the source app if the
  original notification has none.
- **`island.superx.forceShow = true`.** Without it, a card can be *accepted* (it exists, e.g. visible
  via `dumpsys notification_manager`) but never actually surfaces on the pill — it looks exactly like
  "the app did nothing," which is the most confusing failure mode.
- The island is **hidden while the posting app itself is in the foreground** — background the app (or
  check via `dumpsys`) to actually see a card while testing.
- **The notification's signing key does not matter.** Only the whitelisted `applicationId` matters for
  `setSuperXInfosSceneList`; a debug-signed build works identically to a release-signed one.

## 5. Status effects and animation

On the base template (4), `baseInfos` supports a couple of nice extras used by this app's payment
card:

- `iconStatusType` (0 = success, 1 = fail, 2 = error) — overlays an animated tick/cross on the card
  icon.
- `generatingStatus` (> 0) — a loading spinner overlay on the icon.
- `lightEffectInfo.mainColor` (needs its alpha byte set) — an island **edge glow** in that colour.

Combining these (spinner → tick, a beat apart, same card id) is how this app fakes an Apple-Pay-style
"Processing… → Paid ✓" animation out of two ordinary posts.

## 6. Practical gotchas found via testing

- **Card ids and scenes**: OriginOS merges/replaces same-scene cards, so if you post several distinct
  live things (e.g. two football matches) under the one granted scene, ending one mid-update can leave
  another half-rendered. This app scopes "replace the previous card" logic to same-purpose ids only.
- **A crowded island clips content.** When two cards are up, the reduced side card's content can be
  visually clipped — text needs to be short and unambiguous (e.g. a compact `"1-1"` instead of
  `"1 - 1"`) to survive.
- **A notification channel's importance cannot be raised after creation.** If you shipped a channel at
  `LOW` and need `HIGH`, you must create a channel under a new id — Android has no API to change an
  existing channel's importance.
