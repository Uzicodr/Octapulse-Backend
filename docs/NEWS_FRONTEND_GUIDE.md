# News Feed — Frontend Guide

As of 2026-10-07. This guide covers the MMA headlines feature: the API, the publisher rules the app must follow, and a Flutter implementation that fits the existing Riverpod and repository layout.

## How the data gets there

The agent repo's `sync_news` job runs hourly on GitHub Actions. It reads the RSS feeds below, tags each story with the fighters it names, sorts it into a kind, and stores it in `news_items`. It keeps 30 days of stories. The backend only reads that table.

| Source key | Credit to show | Feed |
| --- | --- | --- |
| `espn` | ESPN | `https://www.espn.com/espn/rss/mma/news` |
| `ufc` | UFC.com | `https://www.ufc.com/rss/news` |
| `sherdog` | Sherdog | `https://www.sherdog.com/rss/news.xml` |

None of these feeds include images, so `imageUrl` is almost always `null`. Use the first tagged fighter's photo, or a source badge, as the thumbnail.

## Rules the UI must follow

These come from the publishers' feed terms (ESPN's are the strictest). Breaking them risks losing access to the feed.

- Show `title` and `summary` exactly as returned. Never rewrite, translate or summarize them, by hand or with AI. Truncating with an ellipsis via `maxLines` is fine.
- Show the credit on every story: `sourceName` (for example "ESPN").
- Tapping a story opens `url` in the external browser. Don't scrape or render the full article inside the app.
- Don't put ads inside or between news items.

## API

### `GET /news`

Public. Signed-in users get the same response.

| Query param | Meaning |
| --- | --- |
| `fighter` | Optional fighter **slug**. Only stories tagged with that fighter. Unknown slug: `404`. |
| `kind` | Optional. One of `announcement`, `result`, `injury`, `rumor`, `news`. Anything else: `400`. |
| `page`, `limit` | Standard paging. `limit` is 1–100, default 20. |

The response is newest first:

```json
{
  "data": [
    {
      "id": "6c1b2a9e-0d8f-4a51-9c35-2f7e4b1d8a10",
      "source": "espn",
      "sourceName": "ESPN",
      "title": "Prochazka to fight Stirling at UFC Fight Night in Qatar",
      "summary": "Jiri Prochazka will face Navajo Stirling ...",
      "url": "https://www.espn.com/mma/story/_/id/...",
      "imageUrl": null,
      "kind": "announcement",
      "publishedAt": "2026-10-06T14:13:53Z",
      "fighters": [
        { "id": "230ed31a-bfe9-5c84-bb64-04c68c5ecd46", "slug": "jiri-prochazka", "name": "Jiri Prochazka" },
        { "id": "…", "slug": "navajo-stirling", "name": "Navajo Stirling" }
      ]
    }
  ],
  "meta": { "page": 1, "limit": 20, "total": 87, "totalPages": 5 }
}
```

| Field | Notes |
| --- | --- |
| `summary` | Plain text with HTML removed. Can be `null`. |
| `imageUrl` | `https` image from the feed, usually `null`. |
| `kind` | Matched from headline keywords, so it can be wrong. Use it for a small badge or a filter chip, not for anything important. |
| `fighters` | 0–6 fighters named in the title or summary, sorted by name. Use them for chips that open `/fighter/:slug`. |

Caching: the response has `Cache-Control: public, max-age=60` and an `ETag`. New stories arrive at most once an hour, so a pull-to-refresh is enough; don't poll.

`GET /meta/last-sync` now also has `dataUpdatedAt.news` (the newest fetch time) and a `sync_news` entry in `jobs`.

## Flutter implementation

### 1. Dependency

Add `url_launcher` for opening stories in the browser:

```yaml
dependencies:
  url_launcher: ^6.3.1
```

On Android 11+, add this inside `<manifest>` in `android/app/src/main/AndroidManifest.xml`, or `canLaunchUrl` returns false:

```xml
<queries>
  <intent>
    <action android:name="android.intent.action.VIEW" />
    <data android:scheme="https" />
  </intent>
</queries>
```

### 2. Model — `lib/data/models/news.dart`

```dart
import 'pick.dart' show parseDate;

enum NewsKind { announcement, result, injury, rumor, news }

class NewsFighter {
  const NewsFighter({required this.id, required this.slug, required this.name});

  final String id;
  final String slug;
  final String name;

  factory NewsFighter.fromJson(Map<String, dynamic> json) => NewsFighter(
        id: json['id'] as String,
        slug: json['slug'] as String,
        name: json['name'] as String,
      );
}

class NewsItem {
  const NewsItem({
    required this.id,
    required this.source,
    required this.sourceName,
    required this.title,
    required this.url,
    required this.kind,
    required this.publishedAt,
    required this.fighters,
    this.summary,
    this.imageUrl,
  });

  final String id;
  final String source;
  final String sourceName;
  final String title;
  final String? summary;
  final String url;
  final String? imageUrl;
  final NewsKind kind;
  final DateTime publishedAt;
  final List<NewsFighter> fighters;

  factory NewsItem.fromJson(Map<String, dynamic> json) => NewsItem(
        id: json['id'] as String,
        source: json['source'] as String,
        sourceName: json['sourceName'] as String,
        title: json['title'] as String,
        summary: json['summary'] as String?,
        url: json['url'] as String,
        imageUrl: json['imageUrl'] as String?,
        kind: NewsKind.values.asNameMap()[json['kind']] ?? NewsKind.news,
        publishedAt: parseDate(json['publishedAt']) ?? DateTime.now(),
        fighters: (json['fighters'] as List? ?? const [])
            .map((f) => NewsFighter.fromJson(f as Map<String, dynamic>))
            .toList(),
      );
}
```

### 3. Repository — `lib/data/repositories/news_repository.dart`

`Page` is the existing paging model in `lib/data/models/social.dart`.

```dart
import '../models/news.dart';
import '../models/social.dart' show Page;
import '../services/api_client.dart';

class NewsRepository {
  NewsRepository(this._api);

  final ApiClient _api;

  Future<Page<NewsItem>> list({String? fighterSlug, NewsKind? kind, int page = 1, int limit = 20}) async {
    final json = await _api.get<Map<String, dynamic>>('/news', query: {
      if (fighterSlug != null) 'fighter': fighterSlug,
      if (kind != null) 'kind': kind.name,
      'page': page,
      'limit': limit,
    });
    return Page.fromJson(json, NewsItem.fromJson);
  }
}
```

Register it next to the others in `lib/ui/core/providers.dart`:

```dart
final newsRepositoryProvider = Provider((ref) => NewsRepository(ref.watch(apiClientProvider)));
```

### 4. Providers — `lib/ui/features/news/view_models/news_view_models.dart`

```dart
/// Latest headlines for the home card and the News screen, filtered by kind (null = all).
final newsProvider = FutureProvider.autoDispose.family<List<NewsItem>, NewsKind?>((ref, kind) async {
  return (await ref.watch(newsRepositoryProvider).list(kind: kind)).items;
});

/// Stories that name one fighter, for the fighter detail page.
final fighterNewsProvider = FutureProvider.autoDispose.family<List<NewsItem>, String>((ref, slug) async {
  return (await ref.watch(newsRepositoryProvider).list(fighterSlug: slug, limit: 5)).items;
});
```

For an endless list on the News screen, load page 1 from `newsProvider`, then fetch `page + 1` while `Page.hasMore` is true and append the items, the same way the follower lists page.

### 5. Opening a story

```dart
Future<void> openStory(BuildContext context, NewsItem item) async {
  final ok = await launchUrl(Uri.parse(item.url), mode: LaunchMode.externalApplication);
  if (!ok && context.mounted) {
    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Could not open the story')));
  }
}
```

Use `LaunchMode.externalApplication`, not an in-app web view, so the story opens on the publisher's own site.

### 6. Story tile — `lib/ui/features/news/views/news_tile.dart`

```
┌──────────────────────────────────────────────┐
│ (avatar)  ESPN · 3h ago            [RESULT]  │
│           Natalia Silva wins women's         │
│           flyweight title in record ...      │
│           Natalia Silva defeated Wang ...    │
│           [Natalia Silva] [Wang Cong]        │
└──────────────────────────────────────────────┘
```

- Thumbnail: `imageUrl` with `CachedNetworkImage` when present. Otherwise show `FighterAvatar` for `fighters.first`, building a `Fighter(id:, slug:, name:)` from the tag so the ImageKit photo and overrides in `Env.fighterImageUrl` still apply. With no fighters, show a circle with the source initial.
- Credit line: `'${item.sourceName} · ${Dates.ago(item.publishedAt.toLocal())}'`. `Dates.ago` is in `lib/ui/core/widgets/common.dart`.
- Title: `maxLines: 3`. Summary: `maxLines: 2`, secondary colour. Show both unchanged.
- Kind badge: show it only for `announcement` (primary colour), `result` (`AppColors.win`), `injury` (`AppColors.loss`) and `rumor` (muted, labelled "Rumor"). Skip it for `news`.
- Fighter chips: `context.push('/fighter/${f.slug}')`. The chip's tap must not also open the story.
- Tapping anywhere else on the tile calls `openStory`.

### 7. Where to show it

1. **Home**: add a "Latest news" section below the events in `home_view.dart` with the first 3 items from `newsProvider(null)` and a "See all" link to `/news`. Invalidate `newsProvider` in the home `RefreshIndicator`.
2. **News screen**: add `GoRoute(path: '/news', builder: (_, __) => const NewsView())` in `router.dart`. Use filter chips for All, Announcements, Results and Injuries (`newsProvider(kind)`), a paged list of `NewsTile`, and pull-to-refresh.
3. **Fighter detail**: add a "Latest news" section in `fighter_detail_view.dart` between the follow button and "Fight history", using `fighterNewsProvider(fighter.slug)`. Hide the section when the list is empty. Don't show an empty state, because most fighters have no recent stories.

### 8. States

| State | What to show |
| --- | --- |
| Loading | `Shimmer` with three `SkeletonTile`s, as in fighter history. |
| Error | `ErrorState` with retry on the News screen. On home and fighter detail, hide the section instead, so a news failure doesn't break the page. |
| Empty | News screen: "No news yet. Check back soon." Home and fighter detail: hide the section. |
| Cold start | The first call after the Render instance sleeps can take up to 60 s. Keep the shimmer up. |

## Build order

1. Add `url_launcher` and the Android `<queries>` block.
2. Add the model, repository and providers.
3. Build `NewsTile` and `openStory`.
4. Add the News screen and route.
5. Add the home section, then the fighter detail section.
