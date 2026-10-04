# HunReader privacy

* HunReader has no publisher backend, app-update checks, Firebase analytics or
  performance reporting. Crash reports and diagnostic logs stay on the device
  unless you explicitly export or share them.
* Local books, settings and imported service configurations are stored on the
  device. Storage access is used for books, cache, exports and backups.
  Android automatic cloud backup is disabled; manual backup remains available.
* Book/RSS sources, online TTS (including Azure), dictionaries, WebDAV and upload
  services are optional and have no bundled online presets. Requests go to the
  endpoints you configure. Online TTS sends the text being read to your provider.
* WebDAV requires your own server address and credentials. Backups can contain
  private service settings: protect them and exported TTS/source files.
* Cronet is optional and off by default. Enabling it downloads its native
  networking library from Google's Chromium storage. On older Android versions,
  an already-installed Google Play Services TLS provider may be used locally.
  These are networking components, not HunReader analytics or accounts.
* The optional local web server is off by default. The external reader content
  provider is disabled. Android's file-sharing provider remains available for
  files you explicitly share.
* Imported sources can execute JavaScript and make network requests. Only import
  configurations you trust. Third-party services have their own privacy terms.
* HunReader is an independent GPLv3 fork of Legado/Legado-E, not an official
  upstream release. There is no automatic migration from upstream.