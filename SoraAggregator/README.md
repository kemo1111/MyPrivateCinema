# SoraAggregator (work in progress)

A StreamPlay-style multi-source CloudStream plugin, built from the GPLv3 SoraStream module of hexated/cloudstream-extensions-hexated (commit d540c07). See LICENSE.md and NOTICE.md.

STATUS: UNFINISHED SOURCE PROGRESS. Not a release. No artifact is published from this branch. Playback has not been tested on a device and parity with StreamPlay is not claimed.

Done: ported the 2024 code to the current CloudStream API (compiles). Runtime TMDB key stored in app settings (no key is embedded in the build); settings dialog shows the TMDB logo and notice.
Not done: automated tests, visual check of the settings dialog, source-by-source testing. All providers are kept as untested candidates; several hosts did not answer one connectivity check, which does not prove they are broken.
Providers that needed private keys (Ghostx, CinemaTV, SFMovies, Zshow, Dump, Crunchyroll) have blank values and are expected to return nothing.

This product uses TMDB and the TMDB APIs but is not endorsed, certified, or otherwise approved by TMDB.

## Source probe (one run, 2026-11-24, one sandbox IP, fixture: The Matrix tmdb 603 / tt0133093)
Run through the plugin's own extractor code (LiveProbeTest, off by default). Results are one snapshot, not a ranking.
- moflix: returned 1 HLS link (HTTP 200 on the playlist URL). Playback not tested.
- vidsrc.to, 2embed, watchsomuch: reached the site, returned no link (scraping logic may be out of date).
- vidsrc.me: parse error (empty iframe URL). zoechip: connection dropped.
- allmovieland, showflix, dahmer: host name did not resolve. nowtv, uhdmovies: connect timeout.
All stay in the code as candidates.
