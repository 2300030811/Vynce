# Vynce Changelog

## v3.0.0
- **Native Music Widget**:
  - Modern dark surface card (`#111318`) with 24dp rounded corners and border.
  - High-resolution 80dp rounded album art rendering with fast Coil 3 bitmap pipeline.
  - Real-time progress ticker bar with elapsed & total duration timestamps (1s periodic broadcasts).
  - 5 interactive playback controls: Shuffle toggle, Previous, Play/Pause, Next, and Favorite / Like toggle.
  - Instant two-way state synchronization directly with `MusicService`.
- **Search Experience & Discovery Overhaul**:
  - Re-architected Search Screen matching modern Vynce Web design principles.
  - Curated Mixes relocated to Search page for clean discovery.
  - High-resolution thumbnails and Spotify playlist integration.
  - Accurate search ranking and cross-source metadata resolution.
- **Detailed Insights & Listening Stats**:
  - Direct quick-access button to the Detailed Insights dashboard (`StatsScreen`) from Home header and Settings.
  - Persona DNA listening style calculator, top songs, top artists, and day listening habits distribution.
- **Queue & Playlist Playback Reliability**:
  - Fixed playlist queue progression to guarantee seamless sequential track progression.
  - Added direct download and add-to-playlist action controls on JioSaavn and online playlists/albums.
- **Music Recognition (Shazam-style) UI Optimization**:
  - Polished recognition screen layout and bottom padding for seamless song identification.

## v2.2.0
- **New Features & Streaming Integrations**:
  - Added SoundCloud & Bandcamp streaming support (#35).
  - Refactored core packages to `com.vynce`.
- **UI/UX Enhancements**:
  - Non-blocking horizontal drag gestures for track skipping on MiniPlayer with smooth spring return feedback.
  - Glitch-free artwork swipe-to-skip using `snapshotFlow { pagerState.settledPage }`.
  - SearchBar expand/collapse height offset reset on Home Screen top scroll.
- **Album & Playlist Action Controls**:
  - Added **Download All** and **Add to Playlist** action buttons on online Album & Playlist screens.
- **Library & Downloaded Songs Separation**:
  - Updated `SongFilter.DOWNLOADED` database queries to target explicitly downloaded online songs (`dateDownload IS NOT NULL`), cleanly separating downloaded albums from device local MP3 files.
- **Auto-Updater System Optimizations**:
  - Added instant cached APK installer (`getCachedApk`) to install downloaded updates without re-downloading.
  - Added live megabyte progress indicator (`downloadedMB / totalMB`) and download cancellation support.
- **Database & Architecture Upgrades**:
  - Added Room database column indexing (`@ColumnInfo(index = true)`) and explicit `MIGRATION_22_23` schema migration for fast $O(\log N)$ query lookups.
  - Fixed `Event` foreign key constraint violation handling in `MusicService`.
  - Added regex artist feature extraction (`feat. / ft.`) and album year parsing in `LocalMediaScanner`.
- **Ecosystem & Integrations**:
  - Added `PlaylistExporter` for M3U and JSON playlist export and import.
  - Added `/mcp` and `/api/mcp` JSON-RPC endpoints to `RemoteControlServer`.

## v2.1.1
- Release for testing the app auto-update feature

## v2.1.0
- Added in-app auto-update system powered by GitHub Releases
- Added "Update Available" popup dialog showing release notes and download progress bar
- Cleaned up Gradle build artifacts and prepared for release

## v1.1.1
- Fixed compilation errors in `MainActivity` and `LocalMediaSettings`
- Refactored `ActionPromptDialog` for more flexible UI
- Cleaned up temporary files from the project root
- Bumped version for release

## v1.1.0
- Optimized local media scanner for better reliability
- Refactored `MusicService` for stable queue management
- Cleaned up home screen UI (removed language chips)
- Improved settings architecture and experimental features
- Added confirmation dialogs for destructive "nuke" operations

## v1.0.0
- Initial release
- Material 3 dark theme with purple accent
- JioSaavn music streaming
- Local music playback (MP3, FLAC, OGG, AAC)
- Audio visualizer
- Dynamic color extraction from album art
- Blurred backdrop in fullscreen player
- Redesigned mini player
- Queue with swipe-to-remove
- Last.fm scrobbling
- Listening stats dashboard
- Android Auto support
- Sleep timer, audio normalization, tempo/pitch control
