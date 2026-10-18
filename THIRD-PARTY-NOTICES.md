# Third-party notices

Block Cinema itself is MIT licensed ([LICENSE](LICENSE)). The mod jar also contains the
following unmodified third-party software.

## hls.js

- What: the HLS video player library our video page uses for Jellyfin streams and `.m3u8` links,
  in `assets/blockcinema/web/hls.min.js`
- Version: 1.7.3, the `dist/hls.min.js` file of the [npm package](https://www.npmjs.com/package/hls.js/v/1.7.3)
- Copyright (c) 2017 Dailymotion (http://www.dailymotion.com)
- License: Apache License 2.0 ([LICENSE-Apache-2.0.txt](LICENSE-Apache-2.0.txt))
- Source code: https://github.com/video-dev/hls.js (exact version: tag `v1.7.3`)

## Not included

Block Cinema needs [MCEF](https://github.com/CinemaMod/mcef) (LGPL-2.1), which players install
as a separate mod. MCEF downloads Chromium (CEF) itself on first start. Nothing from MCEF or
Chromium is in this mod's jar.
