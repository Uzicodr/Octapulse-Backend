# Push Notifications — Setup

As of 2026-10-08. The code for push is in all three repos. It stays switched off until the steps below are done; until then every notification still lands in the in-app inbox.

## How it fits together

```
agent (GitHub Actions)                 backend (Render)                      app (Flutter)
saves live results / news  ──POST /internal/notify──▶  settle fights, build notifications
                                         │ store in inbox (always)
                                         │ push if the user's setting allows
                                         └──▶ Firebase Cloud Messaging ──▶ phone
```

- The agent calls `POST /internal/notify` after it saves live results (`live_event`) or new stories (`sync_news`). That wakes the sleeping free instance and sends notifications at once.
- The backend's own timers (every 5 minutes, and hourly for reminders) catch anything missed, but only while it is awake.
- Firebase Cloud Messaging is free with no message limit. iOS also needs an Apple developer account ($99/year).

## 1. Firebase project

1. Create a project at https://console.firebase.google.com (Analytics can stay off).
2. **Pick the real Android package name first.** The app still uses `com.example.octapulsev2`, and Google Play rejects `com.example`. Change `applicationId` and `namespace` in `android/app/build.gradle.kts` (for example to `com.octapulse.app`) before registering, because Firebase ties the app to that name.
3. Add an Android app with that package name and download `google-services.json` into `Octapulse-Frontend/android/app/`.
4. For iOS: add an iOS app with the bundle ID, put `GoogleService-Info.plist` in `ios/Runner/` through Xcode, upload an APNs auth key under Project settings → Cloud Messaging, and turn on the Push Notifications and Background Modes → Remote notifications capabilities.

The Gradle build applies the Google services plugin only when `google-services.json` exists, so the app builds without it.

## 2. Backend (Render)

1. Firebase console → Project settings → Service accounts → Generate new private key. This downloads a JSON key. Keep it out of git.
2. In Render → Environment, add:

| Variable | Value |
| --- | --- |
| `FIREBASE_CREDENTIALS` | The whole JSON key, or `base64` of it if pasting JSON is awkward. |
| `INTERNAL_TOKEN` | A long random string, for example from `openssl rand -hex 32`. |
| `MAX_NEWS_PUSHES_PER_DAY` | Optional, default `3`. |

On start the log says `push notifications go through Firebase project <id>`. Without `FIREBASE_CREDENTIALS` it says pushes are logged, not sent. With an empty `INTERNAL_TOKEN`, `/internal/notify` answers `404`.

## 3. Agent (GitHub)

In the agent repo → Settings → Secrets and variables → Actions:

| Kind | Name | Value |
| --- | --- | --- |
| Variable | `BACKEND_URL` | `https://octapulse-backend.onrender.com` |
| Secret | `INTERNAL_TOKEN` | The same string as on Render |

Each `sync_news` and `live_event` run then prints `notified` with the counts the backend sent.

## 4. Test on a phone

Push needs a real Android phone, or an emulator image with Google Play.

1. Install a build with `google-services.json`, sign in, and allow notifications.
2. Check that the `device_tokens` table has a row for your user.
3. Follow a fighter, or make a pick on an upcoming event.
4. Run the **Sync news** workflow, or call the endpoint yourself:
   `curl -X POST -H "X-Internal-Token: <token>" https://octapulse-backend.onrender.com/internal/notify`
   The response lists how many notifications of each kind went out.

## Kinds and settings

| Type | Setting | Sent when |
| --- | --- | --- |
| `event_live` | Live events | The live watcher marks an event live: users with picks on it or following a fighter on the card |
| `fight_result` | Results | A fight has a result: users who picked it or follow either fighter |
| `event_settled` | Results | All of a user's picks on an event are scored |
| `fight_booked` | Fight announcements | A followed fighter is added to an upcoming card |
| `fighter_news` | Fighter news | An announcement or injury story names a followed fighter (at most 3 pushes a day) |
| `event_reminder` | Pick reminders | An event starts within 24 hours and the user has unpicked fights |

Users change these under Profile → Settings → Notifications, or from the inbox's settings button. A switched-off kind still reaches the inbox.
