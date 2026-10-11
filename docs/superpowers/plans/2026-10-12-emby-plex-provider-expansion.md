# Emby / Plex Provider Expansion Plan

## Goal

Add Emby and Plex as independent media providers while keeping the existing
Compose UI, domain models, repositories, and player contract provider-neutral.
The first delivery targets the main user journey: connect an account, browse a
library, open media details, and play/resume a video. Provider capabilities stay
disabled until their implementation and fixtures prove them.

This is a plan, not an API compatibility claim. Endpoint names, authentication
flows, token scopes, and server-version support must be verified against the
current provider documentation and test servers before implementation.

## Decisions and boundaries

- Implement **Emby first**, then Plex. Emby is the closer API-family fit to the
  existing Jellyfin integration; Plex has a distinct account, resource
  discovery, and server-selection flow and should not be folded into that path.
- Each provider gets its own typed API/wire layer, facade, and
  `MediaProviderAdapter`. Reuse the shared HTTP contract, secure credential
  store, domain contract, repositories, and player API; do not reuse Jellyfin
  wire DTOs or make the UI branch on vendor response shapes.
- Do not add a generic provider framework ahead of real requirements. Extend
  the current registration/session seams only where the new authentication or
  credential lifecycle requires it.
- Add provider capabilities one at a time. An unimplemented or unverified
  operation remains unavailable rather than returning fabricated success or
  empty data.
- Initial scope is the main media workflow. Server administration and
  provider-specific secondary products are not implied by this plan.

## Work sequence

### 0. API and compatibility freeze

Produce a short evidence-backed matrix for Emby and Plex covering:

- supported server versions and server identity / base-path behavior;
- login, account identity, server discovery/selection, and session restore;
- library list, item list, detail, season/episode, people, search, artwork;
- favorite / watched state and continue-watching behavior;
- playback URL preparation, resume position, stream selection, and playback
  progress reporting;
- whether account credentials and server credentials are distinct secrets.

Use official documentation where available, then verify the exact wire shapes
with redacted fixtures and reachable test servers. Record unsupported or
server-dependent operations as capability decisions. Do not start UI work until
the login and saved-session shapes are known.

### 1. Emby provider

1. Add `ProviderKind.Emby` and update exhaustive provider mappings, saved
   connection validation, credential aliases, artwork authorization, and
   session creation/restoration. Keep existing Jellyfin and Feiniu records
   readable without a migration that rewrites their identities.
2. Add provider-owned modules following the existing Jellyfin module pattern:
   shared HTTP transport at the boundary, typed wire DTOs, API groups/facade,
   auth/session, and `data:adapter-emby`.
3. Implement only verified P0 surfaces first: server verification and login,
   account restore, collections/items/detail, seasons/episodes, image URLs,
   and `preparePlayback`/`reportPlayback` to the existing player API.
4. Add search, favorites, watched state, resume, and people as independently
   tested capabilities after their endpoint semantics are verified.
5. Connect the existing login/account-history and media UI through provider
   registration and capability flags; do not fork screens.

### 2. Plex provider

1. Complete the Plex-specific auth/discovery spike before choosing persistence
   fields: establish whether the supported flow uses a Plex account token, a
   PIN/device authorization, a selected server resource token, or a combination.
   Do not assume a browser callback or username/password form.
2. If Plex needs multiple credential values, add only the minimum provider
   credential aliases and validation needed to store and restore them securely;
   never place tokens in `Server`, `Account`, URLs, logs, or settings metadata.
3. Add provider-owned Plex API/facade and `data:adapter-plex`, mapping Plex
   library sections and media metadata to existing domain models.
4. Implement the same P0 browse/detail/artwork/playback journey, with Plex
   capabilities declared separately. Verify resume and reporting semantics
   rather than assuming they match Jellyfin.
5. Integrate the Plex connection/discovery flow into the shared login and
   account-history UX without duplicating the media pages.

### 3. Cross-provider integration and acceptance

- Expand exhaustive `ProviderKind` mappings and architecture checks; ensure
  each adapter uses only its own wire modules.
- Use common adapter contract tests plus provider-specific redacted request and
  response fixtures. Test cancellation, auth expiry, pagination, image auth,
  playback preparation, resume, and capability-disabled paths.
- Run the relevant common/JVM tests and compile Android, iOS, macOS ARM, and
  OHOS targets. Keep Intel macOS and x64 out of the target matrix.
- Verify against at least one real Emby server and one real Plex account/server
  before enabling their login choices in a release build. Record server version,
  enabled features, and evidence per capability.

## Completion gates

1. API/auth decisions are evidenced; no guessed endpoints or hidden fallback.
2. New accounts can log in, survive app restart, switch accounts, and remove
   credentials without affecting Jellyfin or Feiniu sessions.
3. The P0 browse → detail → play/resume path works against real servers and the
   existing shared player on supported targets.
4. Unsupported capabilities are visibly unavailable and covered by tests.
5. Existing Jellyfin and Feiniu login, media browsing, and playback regression
   suites remain green.

## First implementation batch after plan approval

Start with Stage 0 as a bounded API/fixture pass, then implement Emby P0. Keep
Plex discovery/auth research as its own deliverable so uncertainty there does
not block the closer Emby integration. No provider is enabled in the UI merely
because its enum or module skeleton exists.
