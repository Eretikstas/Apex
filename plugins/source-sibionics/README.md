# Native Sibionics source (work in progress)

`src/main/kotlin/tk/glucodata/` and `src/test/` hold code taken unchanged from JugglucoNG
(https://github.com/ctqvva/JugglucoNG, commit 75ff85a, `Common/src/main/java/tk/glucodata/drivers/sibionics`),
licensed GPL-3.0 (see `LICENSE-JugglucoNG-GPL-3.0.txt`). The package name is kept so an update from JugglucoNG is a plain copy.
Not taken: the Bluetooth manager and the Juggluco storage, registry, notification and migration code, which depend on Juggluco
itself. Only change: `tk/glucodata/Log.kt` is a stand-in for Juggluco's logger.

The AAPS side (BG source plugin, Bluetooth link, calibration inside the source) will live under `app/aaps/plugins/source/sibionics`.
