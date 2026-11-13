# Block Cinema

Watch YouTube, Twitch, your own Jellyfin server and video links on TV blocks inside Minecraft, alone or together with your friends. Put TVs side by side for a screen up to 16x9 blocks.

TVs only show these services through their official players. There is no address bar and no web browsing.

![Build](https://github.com/An0mz/BlockCinema/actions/workflows/build.yml/badge.svg)

## Features

- **TV** block: right-click it to pick what to watch. TVs facing the same way in a filled rectangle (up to 16 wide and 9 tall) join into one seamless screen; the picture is 16:9 (9:16 on screens taller than wide).
- Screens on walls, **floors** and **ceilings**: look steeply down or up while placing a TV. The top of the picture points the way you face, and new floor or ceiling TVs line up with the ones next to them. The rotate button in the TV menu turns the picture a quarter turn.
- **Diagonal TV**: a thin screen across the block at 45°. Diagonal TVs in a `\` or `/` line (and stacked up) join into one screen too.
- **TV Remote**: sneak + right-click a TV to link it, then right-click anywhere within 48 blocks to open its menu.
- **YouTube**: paste a video or playlist link. It plays in YouTube's official embedded player.
- **Twitch**: type a channel name, or paste a channel, past video (VOD) or clip link. It plays in Twitch's official embedded player. Past videos seek, resume and stay in step like any video. Clips play in Twitch's clip player, which can't be controlled from outside: a clip is heard at Twitch's own volume when you are close and not at all further away, can't be paused or kept in step, and doesn't move the queue on when it ends.
- **Jellyfin**: log in to your own Jellyfin server with Quick Connect (approve a code in your Jellyfin app; no password is typed into Minecraft), browse your libraries and play, with a choice of audio track and subtitles. Videos resume where you left them, in any Jellyfin app.
- **Video links**: any `https://` (or `http://`) video file or `.m3u8` stream. MP4 (H.264), WebM and `.m3u8` play; AVI, WMV, FLV and MPEG files don't, and the TV says so. Jellyfin converts those for you.
- **Your own video files**: put videos in the `blockcinema-videos` folder of your game and play them from Video link > My video files. Players who have the same file watch along; see [Playing your own video files](#playing-your-own-video-files).
- **Favorites and history**: the star in the TV menu's header lists your favorites and the last videos you played, to play or queue again without the link. The star beside the controls keeps what is on now. Kept on your computer; Jellyfin has its own library.
- **Glow**: a screen gives off light while something is on.
- **Fullscreen**: a control bar below the picture (seek, play/pause, ±10 s, previous/next, volume). Shorts links open in a vertical 9:16 view with the buttons beside the picture. The wheel and up/down change the volume. Any video can switch between the wide and the vertical view. In a YouTube playlist, next/previous move through the playlist for everyone; otherwise next plays the queued video and previous goes back to the one before.
- **A Shorts feed**: a channel link with its ID (`youtube.com/channel/UC…/shorts`) plays the channel's Shorts as a playlist. (`@name` links can't be used: finding the channel ID behind them would mean loading YouTube's site.)
- **Watch in the world**: the eye button in the TV menu locks your view onto the TV with the HUD hidden; scroll for the volume, click to pause, Esc to leave.
- **Portrait screens**: a wall of TVs taller than wide shows a vertical 9:16 picture, so Shorts fill it.
- **Queue**: while a TV plays, add more videos to its queue from the link screen or the Jellyfin library; they play one after another for everyone, and the TV menu shows what's up next (remove entries or skip).
- **Redstone**: a signal on any TV of a screen pauses it for everyone; when the signal stops, it plays on (a video paused by hand stays paused).
- Media controls in the TV menu: play/pause, a seek bar, -10s/+10s, previous/next, the TV's own volume and mute, and fullscreen (where Space, Left/Right, Up/Down and Esc work too).
- Sharp picture: big screens and fullscreen play in 1080p.
- Sound fades with distance, following your Master and Jukebox/Note Blocks volume. With `directionalSound` on, video links and Jellyfin are also heard from the side the TV is on.
- YouTube and video links resume where you left them.
- Multiplayer: everyone near a TV watches the same thing at the same moment. Pausing, jumping and turning off are shared. Video never goes through the server: each player's game plays it.
- Jellyfin in multiplayer: players logged in to the same Jellyfin server watch along with their own login; others see who is watching.
- English and French.

## Requirements

- Minecraft 1.21.1 with NeoForge 21.1.x (Java 21)
- Install Block Cinema on **both** the client and the server.
- Players also need [MCEF](https://www.curseforge.com/minecraft/mc-mods/mcef) (2.1.6 for 1.21.1) on their client: it plays the video. The server doesn't need it.
- On first start, MCEF downloads Chromium for your system (Windows, Linux or macOS) from its own server. The TVs work once that's done.

## Getting started

1. Craft a few TVs and place them facing you, side by side for a big screen.
2. Right-click a TV with an empty hand (or a remote) and pick YouTube, Twitch, Jellyfin or Video link.
3. Right-click the TV again for the controls, or to switch to something else.

### Recipe

| Item | Recipe |
|---|---|
| TV (x2) | `IGI / GRG / III` (iron ingot, black stained glass pane, redstone) |
| TV Remote | Shapeless: iron ingot + redstone + any button + black dye |
| Diagonal TV | A TV on its own (and a Diagonal TV on its own back to a TV) |

## Playing your own video files

Video never goes through the Minecraft server: every player's game plays it itself. So a file on your computer can be played in three ways, depending on who should see it.

**The `blockcinema-videos` folder (simplest).**

1. Right-click a TV, pick **Video link**, then **My video files**.
2. **Open folder** opens the `blockcinema-videos` folder of your game folder. Put your videos in it; they show up in the list.
3. Pick a file and press **Play** (or **Add to queue**).

Only the file's name is shared with the server and the other players, never the file. A player who has a file with the same name in their own `blockcinema-videos` folder watches along, in step with you. Everyone else sees who is playing which file, and what to do to watch along. So for a film night, send the file to your friends first. Server owners can turn this off with `enableLocalFiles`.

Only files right in that folder can be played, by name: a TV can't reach anything else on your computer, and a path like `C:\Videos\film.mp4` or a `file://` link typed as a video link is still refused.

**Jellyfin (plays every format, nothing to send around).** [Jellyfin](https://jellyfin.org) is a free media server you install on your computer.

1. Install Jellyfin, add the folder with your videos as a library.
2. In the TV menu pick **Jellyfin**, type your server's address (`http://localhost:8096` on the same computer) and approve the Quick Connect code in Jellyfin.
3. Browse your library and play. Jellyfin converts any format the TV can't play by itself (MKV, AVI, HEVC...), with a choice of audio track and subtitles.

Other players watch along if they can reach your Jellyfin server and have a login on it.

**A video link.** Put the file somewhere that gives a direct link to it (your own web server, a file host that serves the file itself and not a download page), pick **Video link** in the TV menu and paste the `https://` link. Everyone near the TV needs to be able to open that link.

| Format | Plays from your folder or as a video link? |
|---|---|
| MP4 with H.264 video and AAC audio | Yes |
| WebM (VP8, VP9) | Yes |
| `.m3u8` streams (HLS) | Yes, as a video link |
| AVI, WMV, FLV, MPEG, VOB, RM | No: it is refused. Use Jellyfin |
| MP4 or MKV with HEVC (H.265) video | No: the TV says the format can't be played. Use Jellyfin |

## Player settings

`config/blockcinema-client.toml`, also in Mods > Block Cinema > Config, for each player:

| Setting | Default | What it does |
|---|---|---|
| `playVideos` | `true` | Turn all video off (for slow computers); TVs then say what is on. |
| `onlyTvYouLookAt` | `false` | Only the TV you look at plays for you; it keeps playing until you look at another. |
| `maxScreens` | `4` | Most TVs playing at once; the closest play. |
| `keepPlayingOutOfSight` | `false` | Keep TVs playing when you aren't looking at them or are far away, as long as their area is loaded. Off: TVs stop past 96 blocks. On costs more CPU. A YouTube TV still pauses a few seconds after you look away. |
| `quality` | `AUTO` | `AUTO` (1080p for big screens and fullscreen), `P720` or `P1080`. |
| `startVolume` | `100` | The volume a TV starts at, in percent. It follows the last volume you set on a TV, so your volume is remembered (muting isn't). |
| `showYoutube`, `showTwitch`, `showJellyfin`, `showVideoLinks` | `true` | Which services the TV menu offers you. A hidden one still plays when someone else puts it on a TV. |
| `directionalSound` | `false` | Hear a TV from the left or right, where it is. Video links and Jellyfin only: YouTube and Twitch don't allow changing their sound. |
| `youtubeCaptions` | `false` | Show YouTube captions, in the game's language when the video has them. Off hides them even when YouTube would turn them on by itself. |

## Server settings

`config/blockcinema-server.toml` (or per world in `serverconfig/`), also editable in singleplayer from Mods > Block Cinema > Config:

| Setting | Default | What it does |
|---|---|---|
| `enableYoutube`, `enableTwitch`, `enableJellyfin`, `enableDirectLinks` | `true` | Turn each service on or off. Turned-off services are greyed out in the TV menu. |
| `directLinkHosts` | empty | If set, video links may only come from these sites (and their subdomains). |
| `enableLocalFiles` | `true` | Let players play video files from their own `blockcinema-videos` folder. Only the file's name is shared. Needs `enableDirectLinks`. |
| `screenGlow` | `true` | TVs give off light (level 10) while something is on. A change shows the next time a screen starts or stops. |
| `anyoneCanControl` | `true` | If `false`, only the player who started a TV and operators can change or control it. |

The server checks every request: the service is on, the link has the right form, the player is near the TV and isn't sending too many requests.

## Privacy

The full [privacy policy](PRIVACY.md) and [terms of use](TERMS.md) are in this repository. In short:


- Nothing is sent anywhere you didn't choose: the TV only contacts the service you picked (YouTube, Twitch, your Jellyfin server or the link you entered), plus MCEF's first-start download.
- Your Jellyfin login token is saved on your computer only (`config/blockcinema/jellyfin.json`) and is never sent to the Minecraft server or other players. A Jellyfin TV shares only the server's ID and the video's ID.
- Where you stopped YouTube videos and links is saved in `config/blockcinema/positions.json`, and your favorites and history in `config/blockcinema/saved.json`, on your computer.

## Notes on services

- YouTube plays through YouTube API Services, under [YouTube's API Services terms](https://developers.google.com/youtube/terms/api-services-terms-of-service): the official player in privacy-enhanced mode, never covered or changed, ads aren't blocked, and a YouTube TV pauses when it has been out of sight for a few seconds and continues when you look at it again. Before YouTube plays for you, the game asks you to agree to the [YouTube Terms of Service](https://www.youtube.com/t/terms), the [Google Privacy Policy](http://www.google.com/policies/privacy) and Block Cinema's [privacy policy](PRIVACY.md) and [terms](TERMS.md).
- Netflix, Disney+, Prime Video and other services with DRM aren't supported and can't be: they need a licensed, certified browser.
- Block Cinema isn't affiliated with YouTube, Twitch, Jellyfin or any other service. Their names are trademarks of their owners.

## Building

```bash
./gradlew build
```

The mod jar is in `build/libs/`. `./gradlew runClient` starts a test client.

## License

Block Cinema is MIT licensed, see [LICENSE](LICENSE). It includes [hls.js](https://github.com/video-dev/hls.js) (Apache-2.0); see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
