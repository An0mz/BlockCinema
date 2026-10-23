# Changelog

All notable changes to Block Cinema. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.1.0] - Unreleased

First release, for Minecraft 1.21.1 with NeoForge.

### Added

- **TV** block. TVs facing the same way in a filled rectangle (up to 16×9) join into one screen; the picture is
  16:9, or 9:16 on screens taller than wide.
- TVs on walls, floors and ceilings, with a button to turn the picture a quarter turn.
- **Diagonal TV**: a thin screen across the block at 45°, joining into screens in `\` and `/` lines.
- **TV Remote**: link it to a TV, then open the TV's menu from up to 48 blocks away.
- **YouTube** videos, playlists and Shorts, in YouTube's official embedded player (privacy-enhanced mode),
  after a one-time agreement to YouTube's terms. Channel links with an ID play the channel's Shorts or videos.
- **Twitch** channels, in Twitch's official embedded player.
- **Jellyfin**: log in to your own server with Quick Connect, browse your libraries, pick audio and subtitles,
  and resume where you left off. Other players on the same server watch along with their own login.
- **Video links**: `https://` video files and `.m3u8` streams.
- Multiplayer sync: everyone near a TV sees the same moment; pausing, jumping and turning off are shared.
- **Queue**: line up videos to play one after another; previous and next buttons.
- **Fullscreen** with a control bar, and a vertical 9:16 view for Shorts.
- **Watch in the world**: lock your view onto a TV with the HUD hidden.
- **Redstone**: a signal pauses a screen for everyone.
- Messages on the TV when a video can't play, and why.
- Sound that fades with distance; each TV has its own volume for each player.
- Sharp 1080p picture on big screens and fullscreen.
- Player settings: turn videos off, only play the TV you look at, most TVs at once, picture quality, starting
  volume, YouTube captions, and which services the TV menu shows.
- Server settings: which services are allowed, which sites video links may come from, and who can control TVs.
- Update check: the mods list says when a new version is out.
- Block Cinema creative tab, English and French.
