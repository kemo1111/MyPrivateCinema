# StreamPlayPort (experimental)

Derived from the StreamPlay module of phisher98/cloudstream-extensions-phisher, version 653, commit f72ea88 (2026-07-04, author Phisher98), taken from the public fork https://github.com/b4byhuey/cloudstream-extensions-phisher (history preserved). Upstream authors credited in the source: Phisher98 and Hexated. Other contributors in that history: Salman Ilyas / salman731, Luna712, rockhero1234, Saurabh Kaperwan, doGior.

License basis: the upstream repository README states its extensions are distributed under the GNU General Public License version 3 or later (README "License" section; no LICENSE file exists upstream). The full GPLv3 text is in LICENSE.md. The source files carry no per-file headers.

Changes: build secrets (TMDB_API, ZSHOW_API, ANICHI_API, KissKh, SUPERSTREAM_*, MOVIEBOX_*, KAI*, VideasyDEC, YFX*, NuvFeb, PROXYAPI, ANICHI_APP) are blank strings in this build. Sources that need them are expected to return nothing. Ported to this repository's CloudStream stubs.

Not an official StreamPlay release and not the v715 binary. This product uses TMDB and the TMDB APIs but is not endorsed, certified, or otherwise approved by TMDB.
