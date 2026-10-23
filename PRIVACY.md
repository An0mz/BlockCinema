# Block Cinema privacy policy

Last updated: 2026-10-22

Block Cinema is a Minecraft mod made by Anomz. This policy explains what information Block Cinema handles
and where it goes. Block Cinema's author doesn't run any server for the mod and doesn't collect, receive or
sell any information about you.

## YouTube

Block Cinema uses **YouTube API Services**: YouTube videos play in YouTube's official embedded player. When a
TV plays YouTube, YouTube's player loads from YouTube and collects some basic information, as any YouTube
embed on a website does (for example your IP address, the video watched and playback events). Block Cinema
uses YouTube's privacy-enhanced mode (`youtube-nocookie.com`), which limits what the player stores.

What YouTube and Google do with that information is described in the
[Google Privacy Policy](http://www.google.com/policies/privacy). You are asked to agree to this policy, the
[YouTube Terms of Service](https://www.youtube.com/t/terms) and Block Cinema's [terms](TERMS.md) before
YouTube plays for you; you can withdraw that agreement at any time from a TV's menu (Privacy & terms).

YouTube's player can show ads and set cookies or similar storage in the mod's browser; Block Cinema doesn't
add, change or block ads.

## Other services

TVs only connect to the service you or another player picked:

- **Twitch**: Twitch's official embedded player, under the [Twitch Privacy Notice](https://www.twitch.tv/p/legal/privacy-notice/).
- **Jellyfin**: your own Jellyfin server (or one you have an account on), at the address you enter.
- **Video links**: the website the link points to.

Each of them sees the requests your game makes to it, like any website you visit.

## What stays on your computer

- `config/blockcinema/jellyfin.json`: your Jellyfin server address, user name, access token and a device ID.
  The token is never sent to the Minecraft server or other players; a Jellyfin TV only shares the
  server's ID and the video's ID.
- `config/blockcinema/positions.json`: where you stopped YouTube videos and video links, to resume them.
- `config/blockcinema/agreements.json`: whether you agreed to YouTube's terms.
- The browser's cache and cookies, kept by MCEF (the mod that provides the browser) in its own folder.

Delete these files to forget them. Logging out of Jellyfin in the TV menu deletes the token.

## Multiplayer

The Minecraft server you play on stores what each TV plays (the service, video IDs, channel name or link,
and who started it) so everyone near the TV watches the same thing. It sees your Minecraft name next to
what you start. It never receives video, logins or tokens.

## Update check

When the game starts, NeoForge checks whether a newer Block Cinema is out by reading a small file from GitHub
(`raw.githubusercontent.com/An0mz/BlockCinema/main/update.json`), like it does for other mods that offer this.
GitHub sees that request. It can be turned off with `versionCheck` in `config/fml.toml`.

## MCEF

The browser comes from [MCEF](https://github.com/CinemaMod/mcef), a separate mod. On first start it
downloads Chromium from its own download server.

## Children

Block Cinema is a general-audience mod and isn't directed at children.

## Contact

Questions about this policy: open an issue at <https://github.com/An0mz/BlockCinema/issues>.
