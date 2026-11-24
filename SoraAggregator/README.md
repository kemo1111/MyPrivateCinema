# SoraAggregator (work in progress)

A StreamPlay-style multi-source CloudStream plugin, built from the GPLv3 SoraStream module of hexated/cloudstream-extensions-hexated (commit d540c07). See LICENSE.md and NOTICE.md.

STATUS: UNFINISHED SOURCE PROGRESS. Not a release. No artifact is published from this branch. Playback has not been tested on a device and parity with StreamPlay is not claimed.

Done: ported the 2024 code to the current CloudStream API (compiles). Runtime TMDB key stored in app settings (no key is embedded in the build); settings dialog shows the TMDB logo and notice.
Not done: automated tests, visual check of the settings dialog, source-by-source testing. All providers are kept as untested candidates; several hosts did not answer one connectivity check, which does not prove they are broken.
Providers that needed private keys (Ghostx, CinemaTV, SFMovies, Zshow, Dump, Crunchyroll) have blank values and are expected to return nothing.

This product uses TMDB and the TMDB APIs but is not endorsed, certified, or otherwise approved by TMDB.
