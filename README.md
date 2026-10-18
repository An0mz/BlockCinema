# Block Cinema

Watch YouTube, Twitch, your own Jellyfin server and video links on TV blocks inside Minecraft, alone or together with your friends. Put TVs side by side for a screen up to 16x9 blocks.

TVs only show these services through their official players. There is no address bar and no web browsing.

![Build](https://github.com/An0mz/BlockCinema/actions/workflows/build.yml/badge.svg)

## Features

- **TV** block: right-click it to pick what to watch. TVs facing the same way in a filled rectangle (up to 16 wide and 9 tall) join into one seamless screen; the picture is always 16:9.
- **YouTube**: paste a video or playlist link. It plays in YouTube's official embedded player.
- **Twitch**: type a channel name or paste its link. It plays in Twitch's official embedded player.
- **Jellyfin**: log in to your own Jellyfin server with Quick Connect (approve a code in your Jellyfin app; no password is typed into Minecraft), browse your libraries and play. Videos resume where you left them, in any Jellyfin app.
- **Video links**: any `https://` (or `http://`) video file or `.m3u8` stream.
- Controls in the TV menu: play/pause, a time bar, -10s/+10s, the TV's own volume, and fullscreen (Space, Left/Right, Up/Down, Esc).
- Sharp picture: big screens and fullscreen play in 1080p.
- Sound fades with distance, following your Master and Jukebox/Note Blocks volume.
- YouTube and video links resume where you left them.
- Multiplayer: everyone near a TV watches the same thing at the same moment. Pausing, jumping and turning off are shared. Video never goes through the server: each player's game plays it.
- Jellyfin in multiplayer: players logged in to the same Jellyfin server watch along with their own login; others see who is watching.
- English and French.

## Requirements

- Minecraft 1.21.1 with NeoForge 21.1.x (Java 21)
- Install Block Cinema on **both** the client and the server.
- Players also need [MCEF](https://modrinth.com/mod/mcef) (2.1.6 for 1.21.1) on their client: it plays the video. The server doesn't need it.
- On first start, MCEF downloads Chromium for your system (Windows, Linux or macOS) from its own server. The TVs work once that's done.

## Getting started

1. Craft a few TVs and place them facing you, side by side for a big screen.
2. Right-click a TV with an empty hand and pick YouTube, Twitch, Jellyfin or Video link.
3. Right-click the TV again for the controls, or to switch to something else.

### Recipe

| Item | Recipe |
|---|---|
| TV (x2) | `IGI / GRG / III` (iron ingot, black stained glass pane, redstone) |

## Server settings

`config/blockcinema-server.toml` (or per world in `serverconfig/`), also editable in singleplayer from Mods > Block Cinema > Config:

| Setting | Default | What it does |
|---|---|---|
| `enableYoutube`, `enableTwitch`, `enableJellyfin`, `enableDirectLinks` | `true` | Turn each service on or off. Turned-off services are greyed out in the TV menu. |
| `directLinkHosts` | empty | If set, video links may only come from these sites (and their subdomains). |
| `anyoneCanControl` | `true` | If `false`, only the player who started a TV and operators can change or control it. |

The server checks every request: the service is on, the link has the right form, the player is near the TV and isn't sending too many requests.

## Privacy

- Nothing is sent anywhere you didn't choose: the TV only contacts the service you picked (YouTube, Twitch, your Jellyfin server or the link you entered), plus MCEF's first-start download.
- Your Jellyfin login token is saved on your computer only (`config/blockcinema/jellyfin.json`) and is never sent to the Minecraft server or other players. A Jellyfin TV shares only the server's ID and the video's ID.
- Where you stopped YouTube videos and links is saved in `config/blockcinema/positions.json`, on your computer.

## Notes on services

- YouTube plays under [YouTube's API Services terms](https://developers.google.com/youtube/terms/api-services-terms-of-service): the official player is never covered or changed, ads aren't blocked, and a YouTube TV pauses when it has been out of sight for a few seconds and continues when you look at it again.
- Netflix, Disney+, Prime Video and other services with DRM aren't supported and can't be: they need a licensed, certified browser.
- Block Cinema isn't affiliated with YouTube, Twitch, Jellyfin or any other service. Their names are trademarks of their owners.

## Building

```bash
./gradlew build
```

The mod jar is in `build/libs/`. `./gradlew runClient` starts a test client.

## License

Block Cinema is MIT licensed, see [LICENSE](LICENSE). It includes [hls.js](https://github.com/video-dev/hls.js) (Apache-2.0); see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
