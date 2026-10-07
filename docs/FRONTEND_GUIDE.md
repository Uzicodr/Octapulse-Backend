# Octapulse API — Frontend Integration Guide

As of 2026-10-07. The live, always-current contract is the OpenAPI spec at `/v3/api-docs`, with a browsable UI at `/swagger-ui.html`.

## Basics

Every call is JSON over HTTPS. Send the access token as `Authorization: Bearer <accessToken>`; refresh it when you get a 401.

| Topic | What to do |
| --- | --- |
| Base URL | `https://<render-service>.onrender.com` in production, `http://localhost:8080` locally |
| Access token | JWT, valid 15 minutes. Keep it in memory. |
| Refresh token | Opaque string, valid 30 days, **single use**. Store it securely (Keychain / Keystore / httpOnly storage). Each refresh returns a new pair; the old refresh token stops working. |
| Expired token | Protected routes answer `401`. Call `POST /auth/refresh`, retry once, and send the user to login if the refresh also fails. Public routes ignore a bad token and answer as if signed out. |
| Errors | Body is `{timestamp, status, error, message, path}`. Show `message` to users; it is written for humans ("Picks are locked for this fight"). Validation failures are `400`. |
| Status codes | `400` bad input · `401` sign in / refresh · `403` not allowed · `404` missing (also used for leagues you are not in) · `409` conflict (taken username, locked pick) · `429` rate limited |
| Paging | Lists take `page` (1-based) and `limit` (1–100, default 20) and return `{data: [...], meta: {page, limit, total, totalPages}}`. |
| Timestamps | ISO-8601 UTC strings (`2026-10-07T18:00:00Z`). Format in the user's local time. |
| IDs | UUID strings everywhere except fighters, which you address by `slug` in URLs. |
| Caching | `/events`, `/fighters`, `/rankings`, `/news` send `Cache-Control: public, max-age=60`. Those plus `/fights`, `/leaderboard` and `/meta` send an `ETag`; resend it as `If-None-Match` and treat `304` as "use your cached copy". |
| Rate limit | `/auth/*` allows 20 requests per minute per IP. On `429`, wait the `Retry-After` seconds before retrying. |
| Cold start | The free Render instance sleeps after 15 idle minutes. The first request can take 30–60 s; show a loading state and don't time out under 60 s. |

## Auth flows

Signup, login, Google sign-in and refresh all return the same `{accessToken, refreshToken}` pair, so one token handler covers every way in.

| Flow | Call | Body | Success | Notes |
| --- | --- | --- | --- | --- |
| Sign up | `POST /auth/signup` | `{email, username, password}` | `200` tokens | Username 3–32 chars, password 8–128. `409` if email or username is taken. |
| Log in | `POST /auth/login` | `{email, password}` | `200` tokens | `401` "Invalid credentials" for both unknown email and wrong password. |
| Google | `POST /auth/google` | `{idToken}` | `200` tokens | Send the Google ID token from the native/web Google SDK. Creates the account on first use. |
| Refresh | `POST /auth/refresh` | `{refreshToken}` | `200` new tokens | Replace both stored tokens. Guard against two refreshes racing: queue requests until the first refresh returns. |
| Log out | `POST /auth/logout` | `{refreshToken}` | `204` | Then drop both tokens locally. Also unregister the push token (see Notifications). |
| Log out everywhere | `POST /auth/logout-all` | none, needs access token | `204` | Revokes every refresh token for the user. |
| Forgot password | `POST /auth/password-reset/request` | `{email}` | `202` always | Always show "If that email has an account, we sent a link". The email links to `<FRONTEND_URL>/reset-password?token=...`. |
| Reset password | `POST /auth/password-reset/confirm` | `{token, newPassword}` | `204` | Token lasts 30 minutes and works once. `400` if invalid or expired. Signs the user out on every device; send them to login. |

After any sign-in, call `GET /me` to load the profile. It returns `{id, email, username, displayName, avatarUrl, bio, role, hasPassword, googleLinked, createdAt}`. Show admin tools only when `role` is `admin`.

**Web route needed:** `/reset-password?token=...` must exist on the web app, because that is where the reset email points. Mobile can open it as a deep link instead.

## Catalog screens

All catalog reads are public; a signed-in token is optional. Fight objects embed both fighters, so an event card renders from a single call with no extra fighter lookups.

| Screen | Call | What you get |
| --- | --- | --- |
| Events list | `GET /events?when=upcoming\|past&page&limit` | Paged events `{id, slug, name, startsAt, venue, city, country, status}`. Omit `when` for all events, newest first. |
| Event card | `GET /events/{eventId}` | The event plus `fights[]` in bout order. |
| Fight detail | `GET /fights/{fightId}` | `{fight, event, commentCount}`. `event` is a short summary for the header. |
| Round stats | `GET /fights/{fightId}/stats` | Array of `{round, fighterId, strikesLanded, strikesAttempted, sigStrikesLanded, sigStrikesAttempted, takedownsLanded, takedownsAttempted, controlTimeSeconds}`. Empty until the agent loads stats; hide the tab when empty. |
| AI preview | `GET /fights/{fightId}/preview` | `{fightId, content, model, generatedAt}`. `404` means no preview yet; hide the card. `content` is plain text. |
| Fighter search | `GET /fighters?q=&page&limit` | Paged fighters whose name contains `q`. |
| Fighter profile | `GET /fighters/{slug}` | `{id, slug, name, nickname, recordWins, recordLosses, recordDraws, weightClass, heightInches, reachInches, stance, country}` |
| Fighter history | `GET /fighters/{slug}/fights` | Every fight for that fighter, upcoming and past, newest first. |
| Rankings | `GET /rankings?division=` | Rows `{division, rank, fighterId, champion, fetchedAt, fighter}`. Omit `division` for all, grouped by division then rank. |
| Data freshness | `GET /meta/last-sync` | `dataUpdatedAt` (newest write per table: events, fights, fighters, rankings) and `jobs[]` (latest run per agent job). Use it for an "Updated 2h ago" label. |

**Fight object fields:** `id`, `redFighterId`, `blueFighterId`, `redFighter`, `blueFighter`, `weightClass`, `cardSection`, `boutOrder`, `titleFight`, `status`, `winnerFighterId`, `method`, `resultRound`, `resultTime`, `startsAt`, `locked`.

- `redFighter` / `blueFighter` = `{id, slug, name, nickname, country, recordWins, recordLosses, recordDraws}`, or `null` if the agent hasn't loaded that fighter yet. Fall back to the id.
- `locked: true` means picks are closed. Disable the pick buttons and show the result or "Live".
- `method` is free text from the data source ("KO/TKO (Punches)"). Show it as is.

## Picks

A pick is a winner plus an optional method, round and confidence. Each user has at most one pick per fight, editable until the fight locks. Picks are scored automatically within about 5 minutes of a result landing.

| Action | Call | Notes |
| --- | --- | --- |
| Make or change a pick | `POST /picks` | Body `{fightId, pickedFighterId, method?, round?, confidence?}`. Calling it again on the same fight overwrites the pick. |
| Remove a pick | `DELETE /picks/{fightId}` | `204`. `409` once locked. |
| My picks | `GET /picks/me` | All of the user's picks. Join to fights by `fightId`. |
| Crowd split | `GET /fights/{fightId}/consensus` | `{totalPicks, red: {fighterId, picks, percent}, blue: {...}, methods: {koTko, submission, decision, unspecified}}`. Public; excludes the AI. |
| AI picks for an event | `GET /ai/picks?eventId=` | The AI's picks, visible before lock (the point is "beat the AI"). |
| AI profile | `GET /ai` | `{user, stats}` for an AI card or leaderboard badge. |

**Pick fields:** `id`, `fightId`, `pickedFighterId`, `method`, `round`, `confidence`, `createdAt`, `correct`, `points`, `settledAt`, `lockedAt`.

**Validation the UI should mirror (the API returns `400` otherwise):**

- `method` is one of `KO_TKO`, `SUBMISSION`, `DECISION`, or omitted.
- `round` is 1–3, or 1–5 when `titleFight` is true. Don't offer a round when the method is `DECISION`.
- `confidence` is 1–3; default 1.
- `pickedFighterId` must be the red or blue fighter.
- Once `locked` is true, the API answers `409`. Refresh the card instead of showing an error.

**Scoring.** A wrong winner scores 0. A right winner scores its confidence (1–3), plus 1 for the right method, plus 1 more for the right round when that method was a KO/TKO or submission. The maximum is 5 points.

**Result states on a pick:**

| `settledAt` | `correct` | Show |
| --- | --- | --- |
| null | null | Pending |
| set | true | Won, `+points` |
| set | false | Lost |
| set | null | Void (draw, no contest, cancelled). Not counted in accuracy. |

## Profiles, follows, feed and comments

Other users' picks only become visible once a fight locks, so nobody can copy open picks. This applies to profiles and the feed alike; build those screens around past and live fights.

| Feature | Call | Notes |
| --- | --- | --- |
| Edit my profile | `PATCH /me` | Any of `{username, displayName, avatarUrl, bio}`. Omitted fields stay; `""` clears displayName, avatarUrl or bio. Username: 3–32 chars, letters/digits/underscore. avatarUrl must be `https://`. Bio ≤ 280 chars. |
| My stats | `GET /me/stats` | See stats fields below. |
| Public profile | `GET /users/{userId}` or `GET /users/by-username/{username}` | `{id, username, displayName, avatarUrl, bio, ai, createdAt, followers, following, followedByMe, stats}`. `followedByMe` is null when signed out. |
| Their picks | `GET /users/{userId}/picks` | Locked fights only. |
| Follow / unfollow user | `POST` / `DELETE /users/{userId}/follow` | `204`. Needs sign-in. Following yourself is `400`. |
| Followers / following | `GET /users/{userId}/followers`, `/following` | Paged user summaries. |
| Feed | `GET /me/feed?page&limit` | Paged `{user, pick, fight, event}` from people you follow, newest lock first. |
| Follow a fighter | `POST` / `DELETE /fighters/{slug}/follow` | Returns `{fighterId, following, followers}`. Needs sign-in. |
| Fighters I follow | `GET /me/fighter-follows` | Fighter objects; use it to show a filled "Following" button. |
| Fight comments | `GET /fights/{fightId}/comments?page&limit` | Paged `{id, fightId, user, body, createdAt}`, newest first. Public. |
| Post a comment | `POST /fights/{fightId}/comments` | `{body}`, 1–1000 chars. `201`. Needs sign-in. |
| Delete a comment | `DELETE /comments/{commentId}` | Own comments only (admins: any). `204`. |

**User summary** (used in lists, feed, comments, leagues): `{id, username, displayName, avatarUrl, ai}`. Show `displayName` when set, else `@username`. Show an AI badge when `ai` is true.

**Stats fields:** `totalPicks`, `pendingPicks`, `settledPicks`, `correctPicks`, `voidPicks`, `accuracy` (0–1; multiply by 100 for %), `points`, `currentStreak`, `bestStreak`, `globalRank` (null until the first settled pick), `byDivision[]` of `{division, settledPicks, correctPicks, accuracy}`.

## Leaderboards and leagues

One endpoint serves every leaderboard tab; switch tabs by changing `scope`. Ranking is by points, then accuracy, and tied users share a rank. Void picks don't count.

| Tab | Call | Auth |
| --- | --- | --- |
| All time | `GET /leaderboard` | none |
| This month | `GET /leaderboard?scope=month` | none |
| One event | `GET /leaderboard?scope=event&eventId=` | none |
| Friends | `GET /leaderboard?scope=following` | required (you + people you follow) |
| A league | `GET /leagues/{leagueId}/leaderboard` | required, members only |

All take `limit` (default 100, max 500). Each row is `{rank, userId, username, displayName, avatarUrl, ai, points, correctPicks, settledPicks, accuracy}`. The AI user appears like anyone else, with `ai: true`.

**Leagues** are private groups joined by an 8-character invite code. A user can be in up to 20 leagues; a league holds up to 200 members.

| Action | Call | Notes |
| --- | --- | --- |
| Create | `POST /leagues` `{name}` | `201`, returns the league with its `inviteCode`. The creator is owner and first member. |
| Join | `POST /leagues/join` `{inviteCode}` | Code is case-insensitive. `404` bad code; `409` league full or user already in 20 leagues. Joining twice is harmless. |
| My leagues | `GET /me/leagues` | `{id, name, inviteCode, ownerId, memberCount, createdAt}` each. |
| League page | `GET /leagues/{leagueId}` | `{league, members[]}`. `404` for non-members. |
| Leave | `POST /leagues/{leagueId}/leave` | `204`. If the owner leaves, the longest-standing member becomes owner. The last member out deletes the league. |
| New invite code | `POST /leagues/{leagueId}/invite-code` | Owner only. The old code stops working. |
| Remove member | `DELETE /leagues/{leagueId}/members/{userId}` | Owner only. |
| Delete league | `DELETE /leagues/{leagueId}` | Owner only. |

Share invites as a link such as `<app>/join/<inviteCode>` that opens the join screen with the code pre-filled. Show owner-only controls when `ownerId` equals the signed-in user's id.

## Notifications and push

The in-app inbox works today. Device push is wired end to end on the API side, but no push provider (FCM/APNs) is connected yet, so registered devices receive nothing until the backend adds one. Register tokens now anyway; the same build will start receiving pushes later.

| Action | Call | Notes |
| --- | --- | --- |
| Inbox | `GET /me/notifications?unreadOnly=false&page&limit` | Paged `{id, type, title, body, data, readAt, createdAt}`, newest first. |
| Badge count | `GET /me/notifications/unread-count` | `{unread}`. Poll on app foreground. |
| Mark one read | `POST /me/notifications/{id}/read` | Returns the notification. |
| Mark all read | `POST /me/notifications/read-all` | Returns `{unread: 0}`. |
| Register device | `POST /me/devices` `{token, platform}` | Platform is `ios`, `android` or `web`. Call after login and whenever the OS rotates the token. `204`. |
| Unregister device | `POST /me/devices/unregister` `{token}` | Call on logout. `204`. |

**Notification types.** Route taps with `type` plus `data`; `title` and `body` are ready to display.

| `type` | When | `data` | Tap opens |
| --- | --- | --- | --- |
| `event_reminder` | Event starts within 24 h and you have unpicked fights | `{eventId, unpicked}` | Event card |
| `fight_booked` | A fighter you follow is booked on an upcoming card | `{fightId, eventId, fighterId}` | Fight detail |
| `fight_result` | A fight with a fighter you follow has a result | `{fightId, eventId}` | Fight detail |
| `event_settled` | All your picks on an event are scored | `{eventId, correct, settled, points}` | Event card, with picks shown |

Reminders and booking alerts run hourly; results arrive within about 5 minutes of settlement. The free Render instance sleeps when idle and jobs only run while it is awake, so timing can drift until the service is on a paid plan.

## News

`GET /news` returns MMA headlines from ESPN, UFC.com and Sherdog, newest first, with the fighters each story names. Filter with `fighter=<slug>` or `kind=announcement|result|injury|rumor|news`. Show titles and summaries unchanged, credit `sourceName`, and open `url` in the browser. See [NEWS_FRONTEND_GUIDE.md](NEWS_FRONTEND_GUIDE.md) for the publisher rules, the response shape and a Flutter implementation.

## Admin

Admin routes need a user whose `role` is `admin`; everyone else gets `403`. The first admin is set directly in the database (`UPDATE users SET role = 'admin' WHERE email = '...'`); after that, admins promote others through the API.

| Action | Call | Notes |
| --- | --- | --- |
| Agent runs | `GET /admin/agent-runs?job=&status=&page&limit` | Paged `{id, job, status, input, summary, error, provider, model, inputTokens, outputTokens, startedAt, finishedAt}`, newest first. |
| One run | `GET /admin/agent-runs/{runId}` | `{run, steps[]}`; each step has `{step, kind, name, args, result, isError, model, inputTokens, outputTokens, durationMs, createdAt}`. |
| Review queue | `GET /admin/review-queue?status=open` | Paged `{id, runId, entityType, entityId, reason, payload, status, createdAt, resolvedAt}`, oldest first. |
| Resolve item | `POST /admin/review-queue/{id}/resolve` `{status}` | `resolved` or `dismissed`. `404` if already handled. |
| Settle now | `POST /admin/settlement/run` | Scores every finished fight now instead of waiting for the 5-minute job. Returns `{fightsSettled, picksSettled}`. |
| Re-score a fight | `POST /admin/fights/{fightId}/settle` | Use after a result was corrected. `409` if the fight has no result. |
| Change role | `POST /admin/users/{userId}/role` `{role}` | `user` or `admin`. |

`input`, `summary`, `args`, `result` and `payload` are raw JSON from the agent; render them in a collapsible JSON viewer rather than as fixed fields.

## Build order

Build screens in this order. Each step depends only on the ones before it.

1. Token handling: storage, the refresh-on-401 retry, and the cold-start loading state.
2. Auth screens: signup, login, Google, forgot and reset password, logout.
3. Events list and event card with fighter names and lock state.
4. Pick sheet (winner, method, round, confidence) and My Picks with result states.
5. Fight detail: consensus bar, AI pick, preview, round stats, comments.
6. Fighter profile, fight history, follow button; rankings.
7. Profile and stats (`/me`, `/me/stats`, public profiles); follow users; feed.
8. Leaderboard tabs, then leagues (create, join by link, league leaderboard).
9. Notification inbox and badge, then device registration.
10. News: home section, News screen, fighter news.
11. Admin screens, web only.

## Endpoint index

| Area | Method + path | Auth |
| --- | --- | --- |
| Auth | `POST /auth/signup`, `/login`, `/google`, `/refresh`, `/logout` | none |
| Auth | `POST /auth/logout-all` | required |
| Auth | `POST /auth/password-reset/request`, `/password-reset/confirm` | none |
| Me | `GET`, `PATCH /me`; `GET /me/stats`, `/me/feed`, `/me/leagues`, `/me/fighter-follows` | required |
| Me | `GET /me/notifications`, `/me/notifications/unread-count`; `POST /me/notifications/{id}/read`, `/me/notifications/read-all` | required |
| Me | `POST /me/devices`, `/me/devices/unregister` | required |
| Events | `GET /events`, `/events/{eventId}` | none |
| Fights | `GET /fights/{id}`, `/fights/{id}/stats`, `/fights/{id}/consensus`, `/fights/{id}/preview`, `/fights/{id}/comments` | none |
| Fights | `POST /fights/{id}/comments`; `DELETE /comments/{commentId}` | required |
| Fighters | `GET /fighters`, `/fighters/{slug}`, `/fighters/{slug}/fights` | none |
| Fighters | `POST`, `DELETE /fighters/{slug}/follow` | required |
| Rankings | `GET /rankings` | none |
| Picks | `POST /picks`; `GET /picks/me`; `DELETE /picks/{fightId}` | required |
| AI | `GET /ai`, `/ai/picks?eventId=` | none |
| Users | `GET /users/{id}`, `/users/by-username/{username}`, `/users/{id}/picks`, `/users/{id}/followers`, `/users/{id}/following` | none |
| Users | `POST`, `DELETE /users/{id}/follow` | required |
| Leaderboard | `GET /leaderboard?scope=all\|month\|event\|following\|league` | following and league: required |
| Leagues | `POST /leagues`, `/leagues/join`, `/leagues/{id}/leave`, `/leagues/{id}/invite-code`; `GET /leagues/{id}`, `/leagues/{id}/leaderboard`; `DELETE /leagues/{id}`, `/leagues/{id}/members/{userId}` | required |
| News | `GET /news?fighter=&kind=` | none |
| Meta | `GET /meta/last-sync`, `/health` | none |
| Admin | everything under `/admin` | admin role |
