# HunReader help

HunReader is a personal fork of Legado/Legado-E. It has no app-update service,
publisher account, analytics or bundled online sources.

## Local reading

Use the bookshelf menu to add local TXT or EPUB books. Grant file access when
requested. Built-in chapter-detection rules, themes and reading layouts remain
available. The app can coexist with upstream; it does not migrate upstream data.

## Your online TTS configuration

Open a book and the read-aloud settings, then open the engine list. Use **Add**
to configure your HTTP TTS endpoint or **Import local** to load an existing JSON
configuration (including your own Azure/Foundry-backed configuration). Select
that engine to read aloud. System TTS is also available.

The online-engine list starts empty and no default engines are restored on
upgrades. Keep endpoint credentials in your own configuration, not in source code
or a pull request. Online TTS sends the text being read to the configured service.

## Optional networking

You can import your own book/RSS sources and dictionary rules. Only import
trusted rules: they can run JavaScript and make network requests.

WebDAV requires your own URL, account and password. Its default subdirectory is
`hunreader`. File-link uploads also require your own endpoint and response rule.
Neither uses a bundled public service.

The local web server is off by default. Cronet is optional and off by default;
enabling it downloads a native library from Google's Chromium storage. Normal
reading and HTTP TTS do not require Cronet.

## Backups and diagnostics

Choose a backup directory in settings. Exported sources, TTS configurations and
backups can contain credentials; protect them. Crash logs stay local unless you
explicitly export/share them. There are no automatic application updates.

The About screen includes version information, attribution, privacy and the
GPLv3 license. HunReader is not an official upstream release.
