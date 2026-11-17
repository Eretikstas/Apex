# AAPS 4.0.0 + AutoISF 3.2.1 (test version)

---

## ⚠️ READ THIS FIRST – SAFETY NOTICE

**This software can control an insulin pump. Wrong insulin doses can cause severe hypoglycemia,
diabetic ketoacidosis, loss of consciousness or death.**

- **This is an experimental test version.** It is NOT an official AndroidAPS release and is NOT
  supported, reviewed or approved by the AndroidAPS team or the AutoISF authors.
- **This is not a medical device.** It is not approved by any health authority (FDA, CE, MDR or
  any other). It is not medical advice.
- **You use it entirely at your own risk.** You alone are responsible for building it, installing
  it, choosing every setting and every insulin dose that results from it.
- **No warranty of any kind.** The software is provided "as is". The authors and contributors
  accept no liability for any harm, injury, damage or loss, direct or indirect, that results from
  using, building or sharing it (see the license, sections 15 and 16).
- **Test first, without risk:** use the virtual pump or **Open Loop** for several days. Only move
  to Closed Loop when every result has made sense to you – and then start with careful limits
  (max IOB, max SMB, AutoISF min/max).
- **Changes marked ⚠️** in the changelog can change how much insulin is given. Read them before
  you update.
- **Always keep a backup plan:** a blood glucose meter, fast carbs, insulin pen and a way to stop
  the loop at any time.
- **Do not use it** if you do not understand how AndroidAPS, OpenAPS/SMB and AutoISF work, or if
  you cannot check and correct what the loop is doing.
- **Talk to your diabetes team** before you change your therapy.

**If you do not accept these terms, do not build, install or use this software.**

---

## What this is

This fork brings **AutoISF 3.2.1** to **AndroidAPS 4.0.0** and adds features for CGM sensors
that send a value every minute (for example Libre 3 through Juggluco).

- Based on AndroidAPS 4.0.0 and JetFoxy's branch `autoisf-3.2.1` – many thanks to JetFoxy!
- Many thanks also to [ga-zelle](https://github.com/ga-zelle) (AutoISF) and [swissalpine](https://github.com/swissalpine)
  for their work, ideas and tests!
- Goal: AutoISF stays **as close to the original as possible**.
- No version expiry: this is not an official AAPS version, so the official version check is off.
  It would otherwise set max IOB to 0 some time after a new official release.
- The version name shows the branch: `4.0.0-aisf3.2.1` (normal), `…-dev`, `…-sport`.
- All changes in detail: [CHANGELOG-autoISF.md](CHANGELOG-autoISF.md) (German)

## Branches – which one is for me?

| Branch | What it is | For whom |
|---|---|---|
| ⭐ [`aaps4-autoisf`](https://github.com/blaqone/AAPS-autoISF/tree/aaps4-autoisf) | **Normal version – recommended.** AutoISF 3.2.1 on AAPS 4.0.0, as close to the original as possible. Only features that were tested. | **Everyone who wants to use AutoISF. Start here.** |
| [`aaps4-autoisf-dev`](https://github.com/blaqone/AAPS-autoISF/tree/aaps4-autoisf-dev) | **Test version.** The normal version plus features that are still being tested. Changes often. | Only for testers. |
| [`aaps4-autoisf-sport`](https://github.com/blaqone/AAPS-autoISF/tree/aaps4-autoisf-sport) | **Sport version – my personal mode.** The normal version plus my changes for sport: steps from a Garmin watch for the activity monitor, SMB threshold, ketoacidosis protection. More will follow. | Mainly for me. Use it only if you know what these changes do. |

How new things get in:

1. A new feature or fix is made on its own branch (`feature/...` or `fix/...`), starting from the
   normal version.
2. It is tested in `aaps4-autoisf-dev`.
3. Only when it works well, it goes into `aaps4-autoisf` (and `aaps4-autoisf-sport`).

Changes for sport are made only in `aaps4-autoisf-sport`. They do not go into the normal or the dev version.

The dev version is never merged into the normal version as a whole, so the normal version only
gets features that were tested.

## Guide

**New to AutoISF on AAPS 4.0.0?** Read the guide: [docs/AutoISF-v4-Guide.md](docs/AutoISF-v4-Guide.md) –
what is different from AutoISF on AAPS 3.x, where the settings are and how to use the new parts.

For AutoISF itself see the original documents: the AutoISF Quick Guide and BerNie's guide for Full Closed
Loop in [ga-zelle/autoISF](https://github.com/ga-zelle/autoISF), and the official AutoISF branch
[T-o-b-i-a-s/AndroidAPS 3.4.2.6+aisf3.2.1](https://github.com/T-o-b-i-a-s/AndroidAPS/tree/3.4.2.6+aisf3.2.1).

<img src="docs/images/main-screen.png" alt="Main screen with the AutoISF chip opened" width="400">

*Screenshot from the app, AutoISF chip opened. The two parabola lines at the bottom of the chip (raw and
smoothed) are only in the dev version.*

## Features

### AutoISF 3.2.1
- acce_ISF, bg_ISF, pp_ISF, dura_ISF, iobTH – as in the original
- can be enabled without engineering mode
- activity monitor (steps, as in the original) with safety limits: activity never gives more
  insulin than autosens, inactivity never overrules an autosens that says "more sensitive"
- steps from the phone or from a watch (Wear OS, Garmin with a watch face that sends steps):
  setting **"Steps from"** – Phone / Watch / Automatic (watch when it sent steps in the last 10
  minutes, else phone)
- update straight from AutoISF 3.4.2.6 (AAPS 3.x) keeps the database
- AutoISF values are stored in the loop result, no own database table

### One-minute values and loop interval
- the loop uses the **newest reading**, not the 5-minute grid
- UKF filter with the real one-minute readings
- **loop interval 1–10 minutes** (default 5) – at 1 the loop runs on every new value
- the **SMB interval** stays a separate setting
- BG display, trend arrow, widgets and bolus wizard use the newest value

### Parabola (acce_ISF)
- default as in the original: parabola fit on raw values
- switch: parabola on **UKF-smoothed values** (calmer, but later)
- slider: **shortest parabola** 5–20 minutes (default 10)
- when the switch is on, the APS tab shows which values the parabola uses

### CGM correction
- **Only slope and offset – no calibration, no extra smoothing**
- `BG = value × slope + offset` for every value that comes in through xDrip (also Juggluco)
- example: the sensor always reads 30 mg/dL too low → offset +30
- set in the xDrip source settings or in the AutoISF settings; it always applies and is also
  stored and uploaded (Nightscout)

### Scenes and automations
- scenes can change **AutoISF on/off, acce weight and iobTH** while they run –
  the old values come back automatically at the end
- automation actions: enable/disable AutoISF, set acce weight, set iobTH
- automation triggers: acce weight, iobTH (compatible with rules from AutoISF 3.2.1)

### Display
- BG age in seconds
- AutoISF chip on the main screen, under the BG: closed one line with the values you pinned, a tap
  opens all values (pin up to 4), a long press opens the AutoISF screen with the calculation;
  under the open values: AutoISF version and how many values are pinned
- the ISF row names "AutoISF" on the right while AutoISF runs
- **SMB triangle** at the loop chip turns **yellow** when SMB is on but the last AutoISF run did not
  allow it (odd target, max IOB 0, IOB above iobTH …) – display only
- **basal rate as a number** in the temp basal chip: profile rate in grey, a temp basal with its percent
- the COB chip is hidden at 0 g when no carbs are required
- settings sheets scroll, so the last rows are reachable also with a large font
- pink BG parabola on the BG graph, as in AutoISF 3.2.1: solid over the fitted minutes, dashed
  20 minutes ahead
- holding a point of the BG graph shows the smoothed value and the raw value in brackets
- coloured BG dots every minute with a 1 minute CGM and UKF smoothing (display only)
- graphs for the AutoISF factors and iobTH (also in the IOB graph); duISF is blue, so it is not mixed
  up with the green acISF
- German texts for everything this fork added

Everything that only concerns AutoISF (graph lines, scene section, automations, main screen
lines) is hidden when AutoISF is not the active APS plugin.

## Building

1. Install Android Studio, clone this repository, check out the branch you want –
   **normally `aaps4-autoisf`**
   (see [Branches](#branches--which-one-is-for-me))
2. Build `fullDebug` (for testing) or a signed `fullRelease` with your own keystore
3. Copy the APK to the phone and install it

No ready-made APK is provided.

## Before your first test

- export the settings of your current app
- best use a second phone or the virtual pump
- start in **Open Loop**; move to Closed Loop only after several days that made sense
- choose careful limits (max IOB, max SMB, AutoISF min/max)

## Feedback

Bugs and ideas are welcome as an [issue](https://github.com/blaqone/AAPS-autoISF/issues) –
ideally with a screenshot of the APS tab and the time.

## License

Same as AndroidAPS: GNU Affero General Public License v3.0 (AGPL-3.0).
This program comes with ABSOLUTELY NO WARRANTY, to the extent permitted by applicable law.

---

<!-- changelog:start -->
## Changelog (German)

The newest changes. ⚠ = this change can affect insulin dosing.

### 2026-11-17 (42) – duISF-Linie in Blau

- **AutoISF-Graph:** die **duISF-Linie** ist jetzt **blau** statt cyan. Cyan war im hellen Design auf Weiß zu blass.
  Blau ist gut zu sehen, auf dunklem und hellem Hintergrund, und keine andere Linie dieses Graphen ist blau.

### 2026-11-17 (40) – BZ-Alter: Sekunden nur unter 2 Minuten

- **Hauptbildschirm:** das Alter des letzten BZ-Werts steht nur noch **unter 2 Minuten in Sekunden**
  („Vor 119s“), ab 2 Minuten in Minuten („Vor 2m“, „Vor 3m“ …). Vorher zählte es bis 10 Minuten in Sekunden.
  Ein 1-Minuten-Sensor zeigt so weiter immer Sekunden, bei einem 5-Minuten-Sensor ist weniger Bewegung.
  Nur Anzeige.

### 2026-11-16 (38) – Normale Version: Oberfläche, SMB-Dreieck, duISF-Farbe, Basalrate

Aus dev übernommen:

- **AutoISF-Chip:** kein Titel mehr in der geschlossenen Zeile, die Werte verteilen sich über die Breite (der letzte
  wurde vorher abgeschnitten). Unter den offenen Werten steht „AutoISF v3.2.1 · 3 von 4 angeheftet“.
- **ISF-Zeile:** rechts steht „AutoISF“, wenn AutoISF läuft.
- **COB-Chip** fällt weg bei 0 g und wenn keine Carbs nötig sind.
- **Einstellungs-Fenster scrollen** (AAPS #5564): die letzten Zeilen sind auch bei großer Schrift erreichbar.
- **SMB-Dreieck gelb** am Loop-Chip, wenn SMB eingeschaltet ist, der letzte AutoISF-Lauf aber kein SMB erlaubt hat
  (ungerades Ziel, max IOB 0, IOB über iobTH …). Nur Anzeige.
- **duISF-Linie cyan** statt mintgrün (Issue #4).
- **Basal-Chip zeigt die Basalrate als Zahl:** ohne TBR die Profilrate grau, mit TBR die Rate mit Prozent.
  Nur Anzeige.
- **Deutsche Übersetzung** für alle Texte, die AutoISF4 selbst eingebaut hat.

### 2026-11-12 (34) – Activity Classifier entfernt

- ⚠ **Der Activity Classifier ist ganz entfernt** – Code, die Einstellungen „Use ActivityClassifier“,
  „activity target offset“, „ActivityClassifier sensitivity“ und „Resting heart rate“ sowie die
  Aktivitätserkennung des Handys (Gehen/Laufen/Rad), die nur er genutzt hat. Er gehörte nicht zu AutoISF 3.4
  (kam mit der AAPS-4-Basis) und hat Ziel und SMB-Größe verändert.
- Der **Activity Monitor** (Schritte, Original AutoISF) bleibt unverändert, ebenso der Schrittzähler des Handys
  und die Uhr-Schritte.
- Der letzte Stand des Classifiers liegt weiter auf GitHub im Zweig `fix/activity-classifier-steps`,
  wird aber in keine Version eingebaut.

### 2026-11-12 (33) – Normale Version: Classifier-Fix, Schritt-Automation, Uhr-Schritte

Aus dev übernommen (dort getestet):

- ⚠ **Activity Classifier** (standardmäßig aus): wenn eingeschaltet, zählt er die Schritte der ganzen letzten
  15 Minuten statt eines 5-Minuten-Blocks; Empfindlichkeit wählbar: hoch 450/90, **mittel 600/45**, niedrig 750/30.
- **Automation „Schritte“ (AAPS #5499 von Milos):** liest die neueste Zeile, löst also auch mit Handy-Schritten aus;
  von der Wear-Uhr sind 10 und 15 min jetzt Summen. **Eigene Automationen mit Schritten bitte prüfen.**
- ⚠ **Activity Monitor mit Uhr-Schritten:** neue Einstellung **„Steps from“** – Phone / Watch (Garmin, Wear OS) /
  **Automatic** (Standard: Uhr, wenn sie in den letzten 10 Minuten Schritte geschickt hat, sonst Handy). Es zählt
  immer nur die neueste Uhr-Zeile, nichts doppelt. Die Monitor-Logik bleibt wie im Original AutoISF.
  Garmin nur mit einem Watchface, das die Schritte mitschickt. Ohne Uhr bleibt alles wie bisher.

All changes: [CHANGELOG-autoISF.md](CHANGELOG-autoISF.md)
<!-- changelog:end -->
