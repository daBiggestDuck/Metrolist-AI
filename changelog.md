---v15.3
# What's new
- Metro DJ description no longer says "on-device": it's a general AI radio host that picks music using your configured model and API key
- Removed the DJ's pinned commentary banner from the top of the chat (commentary still appears as regular chat messages)
- Chat messages now stay above the keyboard whenever the text box is focused, so the whole conversation stays readable
- Send button is now a paper-plane icon instead of the word "Send"
- Recommendation actions now appear as plain links under the latest DJ reply with a right arrow, and tapping one sends it straight to the DJ

---v15.2
# What's new
- Metro DJ chat now reliably keeps every message above the composer when the keyboard is open, and the sheet still never grows past the status bar
- Added a voice button next to Send in Metro DJ chat: tap it and it turns into a 3-bar waveform that pulses with your voice, then transcribes what you said and auto-sends it to the DJ — no Google voice dialog
- Voice input uses the device's built-in speech recognition in the background (mic permission required) and falls back gracefully on devices without a recognizer

## Platform integration notes
- Android Auto: supported as a media app (browse, search, and playback through the media session). Android Auto renders its own templated UI, so Metrolist's custom Aura styling cannot be applied there
- Oxygen OS Fluid Cloud: not shipped as a separate APK/feature — Metrolist already surfaces playback through a standard media notification, which Oxygen OS can show in Fluid Cloud automatically when enabled on the device
- Oxygen OS Game Assistant sidebar: not implemented — OnePlus's game-assistant media panel is not open to third-party apps; Metrolist controls remain available in the notification shade and lock screen during gameplay

---v15.1.2
# What's new
- Metro DJ chat now pushes the conversation up when the composer is focused so all messages stay readable above the keyboard
- The chat popup is capped so it only extends up to the status bar, even with the keyboard open

---v15.1.1
# What's new
- Reverted the Metro DJ on-air pulse rendering change, restoring the previous animated dot look

---v15.1
# What's new
- Metro DJ now talks Spotify-style: a short intro once per block of songs, then stays silent while the block plays — no more speaking on every track

---v15.0.5
# What's new
- Metro DJ chat now keeps the latest message above the keyboard when the composer is focused
- Selected tab label reads white instead of green for clearer contrast
- Smoother Metro DJ chat: the on-air pulse animates in the draw phase instead of recomposing every frame

---v15.0.4
# What's new
- Tab switching is smoother: removed the redundant NavHost slide transition and a second nav-bar animation that competed for frames with the page slide

---v15.0.3
# What's new
- Home, Search, and Library pages stay pre-loaded in memory, so switching tabs with the bottom navigation bar is instant instead of laggy

---v15.0.2
# What's new
- Bottom navigation bubble now starts moving instantly on tab switches instead of lagging behind the page
- Search text is reset when leaving the Search tab, instead of carrying typed text across tab switches

---v15.0.1
# What's new
- Metro DJ chat sheet redesigned with a floating composer bar and a fixed, scrollable conversation area
- Search header collapses when the search field is focused, giving results more room
- Dismissing search now exits search mode instead of leaving an empty result list
- Metro DJ quick actions cleaned up: removed redundant "change vibe", "refresh block", and "categories" prompts
- Dislike controls and conservative Metro DJ filtering keep the radio on tracks you actually enjoy
- Listening taste import and chat surfaces stabilized
- Main tab transitions (Home / Library / Search) now animate smoothly with floating filter chips and search bars
- Fixed tab strip compositing issues that caused blank pages on some devices

---v13.6.11
# What's new
- Improved header transitions from Home to Library and Search pages with floating filter chips and search bars
- Library and Search now have consistent one-line headers with gradient-background floating elements for smoother transitions

---v13.6.10
# What's new
- Fixed the blank (black) page on the Search and Library tabs once and for all.
- The tab strip no longer slides one oversized row, which some devices failed to composite and left the page body empty. Pages now stack in place and move with per-page transforms, so the on-screen page is always rendered natively.
- Every page that can appear during a slide stays composed, so a Home ↔ Library travel never drags a black gap through the middle page.
- The bottom navigation bubble stays synchronized with the page slide (shared animation value).


---v13.6.1
# THE FUTURE OF METROLIST
The new Kotlin Multiplatform version of Metrolist is now in a good state, and we are aiming to release it within the next month. Until then, the current app will remain in maintenance mode and receive bug fixes and minor improvements.

# Major changes
- Improved playback reliability and recovery from YouTube player failures (@alltechdev @JASK625 @kairosci @mostafaalagamy @nyxiereal)
- Fixed black screens, startup crashes, and playback freezes (@kairosci @mostafaalagamy @nyxiereal)
- Improved Listen Together synchronization (@nyxiereal)

## Notable new features
- Added refreshed branding and an optional dynamic app icon (@mostafaalagamy)
- Added predictive back support and improved landscape scaling (@HansHolz09 @kairosci)

## Other improvements
- Fixed login, logout, and backup restoration issues (@mostafaalagamy @kairosci @nyxiereal)
- Fixed incorrect artist names, song durations, podcast metadata, and missing artwork (@alltechdev @kairosci @mostafaalagamy @nyxiereal)
- Fixed Android Auto freezes and playback delays (@kairosci)
- Fixed foreground service ANRs and multiple other crashes (@kairosci @mostafaalagamy)
- Fixed sleep timer crashes and restored saved defaults (@johannesbrauer @mostafaalagamy)
- Improved library sync performance and YouTube player compatibility (@mostafaalagamy @nyxiereal)

## New Contributors
* @HansHolz09 made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3837

**Full Changelog**: https://github.com/MetrolistGroup/Metrolist/compare/v13.6.0...v13.6.1

---v13.5.0
# MAINTENANCE MODE
Metrolist is currently in maintenance mode. This means we will only be fixing bugs and making minor improvements. Please do not submit PRs for new features or major changes, as they will not be accepted.

# Major changes
- Rewrote the Discord RPC integration again (@adrielGGmotion @nyxiereal)
- Fixed random playback issues and pauses (@DanielSchmerber @isotjs)
- Fixed liked songs, playlists, albums, search results, etc. not displaying properly (@adrielGGmotion @nyxiereal)

## Notable new features
- Added a toggle for automatic radio queue generation (@FireLion137)
- Added automatic tablet UI scaling (@kairosci)
- Toggle from repeat(1) to repeat(all) after song change (@sunjeetkajla)

## Other improvements
- Fixe covers not loading sometimes (@Arjuanto)
- Artist names are now split properly, and are now clickable (@kairosci)
- Fixed multiple crashes (@kairosci @nyxiereal)
- Improved audio normalization (@kairosci)
- Fixed search results not being combined properly (@kairosci)
- Fixed 'High quality' option not choosing the highest quality option (@kairosci)
- Fixed history sync not working (@kairosci)

## New Contributors
- @DanielSchmerber made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3777
- @Arjuanto made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3780

---v13.4.3
# MAINTENANCE MODE
Metrolist is currently in maintenance mode. This means we will only be fixing bugs and making minor improvements. Please do not submit PRs for new features or major changes, as they will not be accepted.

# Major changes
- Rewrote the Discord RPC integration (@adrielGGmotion)
- Improved the look of playlist screens (@adrielGGmotion)
- Added a new playlist widget (@David-2765 @AntonioDionisio05)

## Notable new features
- Added proper apple music lyrics support (@adrielGGmotion)
- Added a normalization level selector (@Jeff0945)
- Added the ability to hide monthly/weekly most playlists (@isotjs)

## Other improvements
- Improve overall performance and stability (@adrielGGmotion @nyxiereal)
- Fixed devnagari lyrics not being displayed properly (@cloud-zip)
- Improve lyrics fetching speed (@nyxiereal)
- Fixed crashes and some memory leaks (@nyxiereal)
- Fixed images being low resolution for some users (@adrielGGmotion)
- Handle playlist paging properly (@kairosci)
- Multiple smaller improvements by @kairosci <3

## New Contributors
- @Jeff0945 made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3358

---v13.4.2
# MAINTENANCE MODE
Metrolist is currently in maintenance mode. This means we will only be fixing bugs and making minor improvements. Please do not submit PRs for new features or major changes, as they will not be accepted.

# Major changes
- Fixed random crashes and some memory leaks (@nyxiereal)
- Fixed issues with uploading songs to YouTube (@kairosci)
- Fixed playback for uploaded songs (@punkscience)

## Notable new features
- EQ screen redesign and guided AutoEQ profile import (@ndellagrotte)
- Automatically create database backups before updates (@nyxiereal)

## Other improvements
- Improved support for Android Auto (@cmeka)
- Brought back the copy lyrics button for experimental lyrics (@nyxiereal)
- Fixed the re-sync button for experimental lyrics (@nyxiereal)
- Fixed listen together not working (@nyxiereal)
- Added back support for choosing an account upon login (@nyxiereal)
- Implemented concurrent fetching, fix lyrics fetch ordering, and optimize LyricsPlus server selection (@ibratabian17)
- Corrected the play-next shuffle order (@johannesbrauer)
- Improved the Android Auto icon (@ThatOneCalculator)

## New Contributors
- @ndellagrotte made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3487
- @cmeka made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3534
- @punkscience made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3517

---v13.4.1
# MAINTENANCE MODE
Metrolist is currently in maintenance mode. This means we will only be fixing bugs and making minor improvements. Please do not submit PRs for new features or major changes, as they will not be accepted.

# Major changes
- Fixed cached songs showing up in the downloads playlist (@nyxiereal)
- Fixed multiple playback issues and prepared for YouTube's player changes (@mostafaalagamy @nyxiereal)

## Notable new features
- Added the ability to paste URLs to the search to play them directly (@nyxiereal)
- Added a search bar to the Library screen (@isotjs)
- Added a setting to bind pitch and speed together (@sasha-melech)
- Added support for Gemini voice playback (@FireLion137)
- Added an option choose the highest possible audio quality (@nyxiereal @kairosci)
- Added a button to create a playlist from the Library screen (@SunjeetKajla)

## Other improvements
- Moved the resync button to the lyrics menu (@nyxiereal)
- Properly reset player on IO errors (@kairosci)
- Multiple improvements to lyrics fetching and parsing (@kairosci @nyxiereal @ibratabian17)
- Made autoplay disablable from the settings (@kairosci)
- Fixed foreground/background service crashes (@kairosci)
- Fixed Play next not working (@johannesbrauer)
- Properly handle database updates on download removal (@kairosci)
- Use lyricsplus caching to lower server load (@binimum)
- Performance optimizations (@stopper2408)
- Prefetch lyrics for the next song if currently viewing lyrics (@nyxiereal)
- Fixed multiple issues with Listen Together (@nyxiereal)
- Fixed multiple issues with the experimental lyrics (@nyxiereal)
- Fixed pause music on task clear not working (@nyxiereal)

## New Contributors
* @ibratabian17 made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3474
* @sasha-melech made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3301
* @FireLion137 made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3500
* @binimum made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3493
* @stopper2408 made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3506
* @SunjeetKajla made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3505

**Full Changelog**: https://github.com/MetrolistGroup/Metrolist/compare/v13.4.0...v13.4.1
---v13.4.0
# MAINTENANCE MODE
Metrolist is currently in maintenance mode. This means we will only be fixing bugs and making minor improvements. Please do not submit PRs for new features or major changes, as they will not be accepted.

No, this is not an April Fools joke, even though this update is being released on April 1st.

We are working on something big for the future of Metrolist - this is not the end of the project.

# Major changes
- Multiple playback fixes and reliability improvements (@alltechdev)
- Revamped the entire Lyrics engine, improving lyric accuracy and usability (@adrielGGmotion)
- Fixed multiple crash issues (@kairosci, @nyxiereal)
- Multiple improvements to Android Auto support (@andker87)
- Fixed multiple grammar and text inconsistency issues in the project (@TheRebo)

## Notable new features
- Added support for treating cached songs as offline songs (@kairosci)
- Added music alarm scheduling (@0xarchit)
- Added miniplayer styles (@johannesbrauer)
- Added a button to copy all song lyrics to the clipboard (@kairosci)
- Added a time transfer feature to move listening time between songs in the stats page (@finley-webber)
- Added customization support for the AI prompt used for translations (@nyxiereal)
- Added a notification-based music recognition for the QS tile shortcut (@isotjs)

## Other improvements
- Fixed incorrect artist order for multi-artist songs (@AntonioDionisio05)
- Fixed playtime in the stats page not being fully visible (@David-2765)
- Improved radio to start seamlessly when initiated from the currently playing track (@luigiwwmf)
- Improved the UI for tablets (@adrielGGmotion)
- Improved the About Screen layout (@adrielGGmotion)
- Fixed ghost adds on playlists (@johannesbrauer)
- Improved search focus and navigation behavior (@saivijaychandan)
- Added album navigation on song title click regardless of play source (@gergesh)
- Prevented UI state reset when switching apps (@mostafaalagamy)
- Restored the Daily Discover title in the Home screen (@mostafaalagamy)
- Fixed listen together audio choppiness (@nyxiereal)
- Redesigned romanization and account settings (@omardotdev)
- Improved the design of the sleep timer dialog (@johannesbrauer)
- Redesigned some components to use Material 3 Expressive (@johannesbrauer)
- Fixed links in the README (@Lolen10 @nyxiereal)

## New Contributors
* @AntonioDionisio05 made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3255
* @David-2765 made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3271
* @luigiwwmf made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3293
* @gergesh made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3300
* @Lolen10 made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3328

**Full Changelog**: https://github.com/MetrolistGroup/Metrolist/compare/v13.3.0...v13.3.1
---v13.3.0
# Major changes
- Implemented song upload and delete functionality (@alltechdev)
- Multiple playback fixes and reliability improvements (@alltechdev, @mostafaalagamy)
- Fixed proguard rules causing issues with Reproducible Builds (@nyxiereal)
- Fixed proguard rules removing Listen Together protobuf classes (@mostafaalagamy)
- Added a playlist export option to the playlist context menu (@nyxiereal)

## Notable new features
- Added a Play all action for the stats page (@isotjs)
- Added a quick settings tile for recognizing music (@nyxiereal)
- Added automatic sleep timer options and integrated fade-out volume handling (@isotjs)
- Added a profile search filter (@alltechdev)
- Added channel subscriptions for podcasts and artists (@alltechdev)

## Other improvements
- Fixed cached images not clearing properly and cached covers not showing when offline (@nyxiereal)
- Removed useless and stale strings from the codebase (@nyxiereal)
- Refined the song details view (@omardotdev)
- Added support for Mistral AI models (@nyxiereal)
- Redesigned the lastfm integration settings (@omardotdev)
- Fixed importing csv files crashing the app (@nyxiereal)
- Prevent guest playback while in listen together (@nyxiereal)
- Fixed podcasts not working for logged-out users (@alltechdev)
- Updated dependencies (@nyxiereal)

## New Contributors
* @isotjs made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3090

**Full Changelog**: https://github.com/MetrolistGroup/Metrolist/compare/v13.2.1...v13.3.0
---v13.2.1
>[!WARNING]
>Listen Together doesn't work in v13.2.1! Use v13.2.0 if you need it.

## Hot Fixes
- Fix interface lag issue
- Fix navigate local playlists pinned in speed dial
- Removed "cache songs only after playback has started" option

**Full Changelog**: https://github.com/MetrolistGroup/Metrolist/compare/v13.2.0...v13.2.1
---v13.2.0
# Major changes
- Fixed playback breaking due to YouTube's February 2026 n-transform changes (@alltechdev)
- Added full podcast library support (@mostafaalagamy & @alltechdev)
- Redesigned loading, Changelog, and About screens (@adrielGGmotion)
- Improved app startup time via parallelized home screen loading (@mostafaalagamy)

## Notable new features
- Added an option to cache songs only after playback has started (@kairosci)
- Added a music recognizer home screen widget (@mostafaalagamy)
- Rewrote music recognizer in pure Kotlin, removing NDK dependency and reducing APK size (@mostafaalagamy)
- Overhauled lyrics: added LyricsPlus provider, AI lyric fixes, untranslation support, and provider priority settings (@nyxiereal)
- Changed listen together to use protobuf, lowering latency and improving reliability (@nyxiereal)
- Added auto-approve setting for listen together song requests (@nyxiereal)
- Added an option to persist the sleep timer default value (@johannesbrauer)
- Added a dialog on logout to keep or clear library data (@alltechdev)

## Other improvements
- Fixed backup restore causing playback errors due to stale auth credentials (@alltechdev)
- The CSV import dialog is now scrollable (@kairosci)
- Fixed Android 15 foreground service crashes (@kairosci)
- Fixed a crash on the About screen on some devices (@mostafaalagamy)
- Fixed home screen playlist navigation routing to wrong screen (@mostafaalagamy)
- Fixed crash when creating local playlists (@mostafaalagamy)

## New Contributors
* @johannesbrauer made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/2991

**Full Changelog**: https://github.com/MetrolistGroup/Metrolist/compare/v13.1.1...v13.2.0