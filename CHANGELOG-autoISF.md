# Changelog: AAPS 4.0.0 + AutoISF 3.2.1

Testversion. Nur mit virtueller Pumpe oder im Open Loop testen.
⚠ = diese Änderung kann die Dosierung beeinflussen.

---

## 2026-11-17 (42) – duISF-Linie in Blau

- **AutoISF-Graph:** die **duISF-Linie** ist jetzt **blau** statt cyan. Cyan war im hellen Design auf Weiß zu blass.
  Blau ist gut zu sehen, auf dunklem und hellem Hintergrund, und keine andere Linie dieses Graphen ist blau.

## 2026-11-17 (40) – BZ-Alter: Sekunden nur unter 2 Minuten

- **Hauptbildschirm:** das Alter des letzten BZ-Werts steht nur noch **unter 2 Minuten in Sekunden**
  („Vor 119s“), ab 2 Minuten in Minuten („Vor 2m“, „Vor 3m“ …). Vorher zählte es bis 10 Minuten in Sekunden.
  Ein 1-Minuten-Sensor zeigt so weiter immer Sekunden, bei einem 5-Minuten-Sensor ist weniger Bewegung.
  Nur Anzeige.

## 2026-11-16 (38) – Normale Version: Oberfläche, SMB-Dreieck, duISF-Farbe, Basalrate

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

## 2026-11-12 (34) – Activity Classifier entfernt

- ⚠ **Der Activity Classifier ist ganz entfernt** – Code, die Einstellungen „Use ActivityClassifier“,
  „activity target offset“, „ActivityClassifier sensitivity“ und „Resting heart rate“ sowie die
  Aktivitätserkennung des Handys (Gehen/Laufen/Rad), die nur er genutzt hat. Er gehörte nicht zu AutoISF 3.4
  (kam mit der AAPS-4-Basis) und hat Ziel und SMB-Größe verändert.
- Der **Activity Monitor** (Schritte, Original AutoISF) bleibt unverändert, ebenso der Schrittzähler des Handys
  und die Uhr-Schritte.
- Der letzte Stand des Classifiers liegt weiter auf GitHub im Zweig `fix/activity-classifier-steps`,
  wird aber in keine Version eingebaut.

## 2026-11-12 (33) – Normale Version: Classifier-Fix, Schritt-Automation, Uhr-Schritte

Aus dev übernommen (dort getestet):

- ⚠ **Activity Classifier** (standardmäßig aus): wenn eingeschaltet, zählt er die Schritte der ganzen letzten
  15 Minuten statt eines 5-Minuten-Blocks; Empfindlichkeit wählbar: hoch 450/90, **mittel 600/45**, niedrig 750/30.
- **Automation „Schritte“ (AAPS #5499 von Milos):** liest die neueste Zeile, löst also auch mit Handy-Schritten aus;
  von der Wear-Uhr sind 10 und 15 min jetzt Summen. **Eigene Automationen mit Schritten bitte prüfen.**
- ⚠ **Activity Monitor mit Uhr-Schritten:** neue Einstellung **„Steps from“** – Phone / Watch (Garmin, Wear OS) /
  **Automatic** (Standard: Uhr, wenn sie in den letzten 10 Minuten Schritte geschickt hat, sonst Handy). Es zählt
  immer nur die neueste Uhr-Zeile, nichts doppelt. Die Monitor-Logik bleibt wie im Original AutoISF.
  Garmin nur mit einem Watchface, das die Schritte mitschickt. Ohne Uhr bleibt alles wie bisher.

## 2026-11-12 (32) – Gemeldete Fehler: AutoISF-Tab, BZ-Anzeige, iobTH-Legende, Classifier aus, Logdateien

- **AutoISF-Tab** wie in AutoISF 3.4.2.6: zuerst das Ergebnis (Script Debug, Result, Request – offen), dann die
  Eingaben (zugeklappt, außer Glucose Status). Der **Glucose Status zeigt alle AutoISF-Werte** (duraISF, Parabel
  a0–a2, corrSqu, deltaPl/deltaPn, bgAcceleration …), das **Profil die Schritte** (recent_steps 5–60, phone_moved …).
- **Log:** nur noch `FitUsesRaw=…` statt `rawFit=…; smoothedFit=…`.
- **Einheitlicher BZ-Wert:** Benachrichtigung/Statusleiste, Wear-Uhr, SMS und Tizen zeigen denselben, neuesten Wert
  wie Hauptbildschirm und Loop (vorher bei 1-Minuten-Sensoren bis zu 4 Minuten alt). Nur Anzeige.
- **IOB-Graph:** Legende für IOB und iobTH, wenn iobTH eingeschaltet ist – iobTH gestrichelt wie seine Linie und
  hellviolett. iobTH ist nur noch im IOB-Graph wählbar, nicht mehr in den Graphen 2–5.
- ⚠ **Activity Classifier ist jetzt standardmäßig aus.** Er gehört nicht zu AutoISF 3.4 (kam mit der AAPS-4-Basis)
  und ändert Ziel und SMB-Größe – das soll niemand ungefragt haben. Wer ihn will, schaltet ihn in den
  Activity-Monitor-Einstellungen ein; wer ihn schon selbst eingeschaltet hatte, behält ihn.
- **Logdateien** liegen jetzt auch in der normalen Version in `Documents/aapsLogs` (stündliches Zip, 30 Tage),
  wie bei AutoISF 3.4 – damit die AutoISF-Emulator-Werkzeuge sie lesen können.

## 2026-11-11 (29) – Update direkt von AutoISF 3.4.2.6 möglich

Bisher ist die App beim Update über **AAPS 3.4.2.6 + AutoISF 3.2.1** bei jedem Start abgestürzt.

- **Datenbank-Version 32 doppelt belegt:** AutoISF 3.4 nutzt Version 32 für seine Tabelle `autoIsfValues`,
  AAPS 4 für die Spalte `carbInsulin` in den Tagesdosen (TDD). **Fix:** Fehlt die Spalte, wird der TDD-Cache
  geleert (wird neu berechnet) und die Spalte angelegt; die alte Tabelle `autoIsfValues` wird gelöscht.
  Bei einer normalen AAPS-4-Datenbank passiert nichts.
- **Unbekannte Einträge im Benutzerprotokoll** (`AUTOISF_ENABLED`, `AUTOISF_DISABLED`, `IOB_TH_SET`,
  `ACCE_WEIGHT_SET`) werden zu „UNKNOWN“, und jeder unbekannte Eintrag wird künftig als „UNKNOWN“ gelesen
  statt abzustürzen.
- Die übrige Historie bleibt erhalten. Vorher trotzdem **Einstellungen exportieren**; gleicher
  Signaturschlüssel nötig. Zurück auf 3.4 geht nicht ohne Verlust der Datenbank.
- Am besten zuerst auf einem **Zweithandy** testen.

## 2026-11-11 (26) – Doku: Version 4.0.0 genannt, echter Screenshot

- README und Anleitung nennen jetzt genau **AAPS 4.0.0** statt nur „AAPS 4“.
- Die gezeichneten Beispielbilder sind entfernt. Stattdessen gibt es einen **echten Screenshot** des
  Hauptbildschirms mit geöffnetem AutoISF-Chip.
- Nur Doku, keine Änderung an der App.

## 2026-11-11 (23) – Farbige BZ-Punkte jede Minute, Versionsprüfung aus

- **Farbige BZ-Punkte jede Minute** (in dev getestet): Mit 1-Minuten-CGM und UKF-Glättung zeigt der
  BZ-Graph jede Minute einen farbigen Punkt (geglätteter Wert), nicht nur alle 5 Minuten. Gilt mit jedem
  APS. Reine Anzeige, die Berechnung bleibt auf dem 5-Minuten-Raster.
- **Versionsprüfung aus:** Diese Version ist keine offizielle AAPS-Version. Die Prüfung hätte sie mit den
  offiziellen Versionen verglichen und einige Zeit nach einer neuen offiziellen Version Max IOB auf 0
  gesetzt („Anwendung abgelaufen“) – das hätte den geschlossenen Loop beendet.

## 2026-11-10 (22) – Normale Version: AutoISF-Chip, pinke Parabel, langes Drücken

In der Testversion geprüft und jetzt in der normalen Version (und in sport):

- **AutoISF-Chip** auf der Hauptseite, zum Aufklappen, mit anheftbaren Werten (siehe 16–18).
- **Pinke BG-Parabel** im BZ-Graph wie in AutoISF 3.2.1 (siehe 19).
- **Langes Drücken im BZ-Graph** zeigt den farbigen, geglätteten Wert in seiner Farbe und dahinter
  grau in Klammern den Rohwert, z. B. `147 (raw 151)`. In der normalen Version gibt es die farbigen
  Punkte nur alle 5 Minuten; zwischen ihnen zeigt das lange Drücken den Rohwert allein.
- Die farbigen BZ-Punkte jede Minute bleiben vorerst in der Testversion.
- Reine Anzeige, keine Änderung an der Dosierung.

## 2026-11-10 (19) – Pinke BG-Parabel im BZ-Graph

- Wie in AutoISF 3.2.1: Die Parabel, die AutoISF für acce_ISF an die BZ-Werte legt, wird als
  **pinke Linie** im BZ-Graph gezeichnet. **Durchgezogen** über die Minuten, auf die sie gelegt wurde,
  **gestrichelt** 20 Minuten in die Zukunft (nur wenn die Vorhersagen eingeschaltet sind).
- **Glatt:** ein Punkt pro Minute, bei 1-Minuten-CGM also an jedem Wert. Die Linie wird **bei jedem
  neuen Wert** neu berechnet, unabhängig vom Loop-Intervall. Sie zeigt die Parabel, die AutoISF
  wirklich benutzt (roh oder geglättet, je nach Schalter).
- Ein-/ausschaltbar mit dem Stift am BZ-Graph („BG parabola (AutoISF)“), nur mit AutoISF. Bei neuer
  Einstellung ist sie an; wer seine Graph-Einstellung schon gespeichert hat, schaltet sie dort ein.
- Reine Anzeige, keine Änderung an der Dosierung.

## 2026-11-10 (18) – AutoISF-Chip: Abstände

- Über und unter dem AutoISF-Chip ist jetzt derselbe kleine Abstand (2 dp) wie zwischen IOB/COB
  und Sensitivity. Ohne AutoISF bleibt die Hauptseite wie vorher.
- Reine Anzeige.

## 2026-11-10 (17) – AutoISF-Chip zum Aufklappen

- Der AutoISF-Chip ist jetzt **zugeklappt einzeilig**: AutoISF-Symbol und die angehefteten Werte,
  rechtsbündig. **Tippen** klappt ihn auf und zu, **lang drücken** öffnet den AutoISF-Bildschirm.
- **Aufgeklappt** stehen alle Werte (acce, bg, pp, dura, final, iobTH) in einem Raster.
  **Antippen** eines Wertes heftet ihn an die zugeklappte Zeile an oder löst ihn (höchstens 4,
  Standard: final und iobTH). Auswahl und auf/zu werden gespeichert und kommen mit dem Export mit.
- Farben: Faktor über 1 (stärker, mehr Insulin) orange/gelb, unter 1 (schwächer) grün.
- Nur sichtbar, wenn AutoISF das aktive APS ist. Reine Anzeige, keine Änderung an der Dosierung.

## 2026-11-10 (16) – AutoISF-Chip auf der Hauptseite

- Die AutoISF-Werte des letzten Loop-Laufs (acce, bg, pp, dura, final, iobTH) stehen jetzt in einem
  **eigenen Chip** über die ganze Breite, direkt unter BZ-Wert und Sensitivity-Chip. Gleiche Farbe
  und Höhe wie der Sensitivity-Chip, Text rechtsbündig.
- **Klick oder langer Klick** auf den Chip öffnet den AutoISF-Bildschirm (APS-Tab) mit der Berechnung.
- Nur sichtbar, wenn AutoISF das aktive APS ist.
- Die **graue Statuszeile** (letzter Loop, letzter SMB, TBR) ist wieder entfernt.
- Reine Anzeige, keine Änderung an der Dosierung.

## 2026-11-10 (15) – README zeigt die neuesten Änderungen

- Unten in der README stehen jetzt die 5 neuesten Changelog-Einträge, darunter der Link auf den
  ganzen Changelog.
- Keine Änderung am Code.

## 2026-11-10 (14) – README: Übersicht der Zweige

- Die README erklärt jetzt die drei Zweige (normal, dev, sport) mit Links und wie neue Features
  hineinkommen.
- Der Parabel-Vergleich roh/geglättet ist in der README als „nur dev“ markiert.
- Keine Änderung am Code.

## 2026-11-10 (13) – Automation-States nach dem Import

- **States werden jetzt immer frisch gelesen.** Nach einem Einstellungs-Import gelten die importierten
  States sofort, ohne App-Neustart. Vorher konnte die nächste Änderung die alten States wieder über
  den Import schreiben.
- Der **States-Bildschirm aktualisiert sich live**, wenn sich ein State ändert (auch durch eine
  Automation oder einen Import).
- Bewusst **kein Import-Filter**: Ein Import stellt den Stand genau so wieder her, wie er exportiert
  wurde.
- In allen drei Zweigen (normal, dev, sport).

## 2026-11-10 (12) – Zweige: normal, dev, sport

- **`aaps4-autoisf` (normal):** ohne den Parabel-Vergleich (Anzeige roh/geglättet auf der Hauptseite,
  im APS-Tab und das Diagramm „acc raw / acc smooth“). Schalter und Regler für die Parabel bleiben;
  ist der Schalter an, steht im APS-Tab, welche Werte die Parabel benutzt.
- **`aaps4-autoisf-dev` (Testversion):** normal plus Test-Features, zur Zeit der Parabel-Vergleich.
- **`aaps4-autoisf-sport`:** Grundlage für eine spätere Sport-Version.
- Neue Features entstehen auf eigenen Feature-Zweigen vom normalen Zweig, werden in dev getestet und
  erst danach in den normalen Zweig übernommen.

## 2026-11-10 (11) – AutoISF in Szenen und Automationen, CGM-Korrektur

- ⚠ **Szenen:** Im Szenen-Assistenten (Seite „SMB“) gibt es den Bereich **„AutoISF“**. Eine Szene kann
  für ihre Dauer AutoISF an/aus schalten, das acce-Gewicht (0–1) und iobTH (10–100 %) setzen.
  Am Ende der Szene (von Hand oder nach Ablauf) kommen die vorherigen Werte automatisch zurück,
  auch nach einem App-Neustart.
- ⚠ **Automationen** wie in AutoISF 3.2.1: Aktionen „AutoISF aktivieren/deaktivieren“,
  „acce-Gewicht setzen“, „iobTH setzen“, Auslöser „acce-Gewicht“ und „iobTH“. Regeln aus
  AAPS 3.4.2.6 + AutoISF lassen sich lesen.
- ⚠ **CGM-Korrektur: nur Slope und Offset – keine Kalibrierung, keine zusätzliche Glättung.**
  Jeder Wert, der über xDrip (auch Juggluco) ankommt, wird als `Wert × Slope + Offset` gespeichert
  (Slope 0,5–1,5, Offset −50 bis +50 mg/dl, nie unter 40 mg/dl). Standard 1,00 / 0 = keine
  Änderung. Einstellbar in den xDrip-Einstellungen und im AutoISF-Bildschirm. Wirkt immer, egal
  welches APS-Plugin aktiv ist, und wird so auch an Nightscout geschickt. Die Glättung macht weiter
  der UKF.
- **Ohne AutoISF ausgeblendet:** AutoISF-Diagrammlinien (Faktoren, iobTH, Parabel), der
  AutoISF-Bereich im Szenen-Assistenten, die AutoISF-Automationen und die AutoISF-Zeilen auf der
  Hauptseite, solange AutoISF nicht das aktive APS-Plugin ist. Gespeicherte Einstellungen bleiben
  erhalten.
- **Korrektur:** iobTH lässt sich jetzt wirklich im IOB-Diagramm einschalten (Stift-Symbol am
  IOB-Diagramm → IOBth). Vorher wurde es dort nicht angeboten.

## 2026-11-10 (10) – Aufräumen nach Code-Review, Inaktivität (Issue #2)

- ⚠ **AutoISF holt Autosens nur einmal pro Lauf.** Ein doppelter, toter Abfrage-Block wurde entfernt.
  Er konnte im Sonderfall „keine Autosens-Daten“ den reinen Profil-ISF zurückgeben, ohne
  Aktivitäts- und Temp-Target-Anpassung.
- ⚠ **Aktivitätswerte werden direkt weitergegeben** statt über die Einstellungen. So kann nie ein
  Wert aus einem früheren Lauf benutzt werden. Das Ergebnis bleibt gleich.
- Kleine Aufräumarbeiten (doppelte Bedingungen, eine überflüssige Prüfung).
- BZ-Anzeige, Widgets und Trendpfeil lesen nur noch die neuesten Werte statt jedes Mal die ganze
  Liste zu kopieren.
- ⚠ **Inaktivität überstimmt keinen „empfindlicher“-Autosens mehr** (Issue #2, Option C): Liegt Autosens
  unter 1, gilt Autosens. Sonst gilt der größere Wert von Autosens und Inaktivität, höchstens
  autosens_max. Ist Autosens ausgeschaltet (Faktor 1,0), wirkt die Inaktivitätserkennung wie bisher.
- Drei Tests von JetFoxy (PR #1) übernommen: AutoISF fragt Autosens nie ein zweites Mal ab.
- **Automation-Zustände** (z. B. „Schlafen“) liegen jetzt im gemeinsamen Code und funktionieren damit
  auf allen Plattformen, nicht nur auf Android. Für Android ändert sich nichts.

## 2026-11-10 (9) – Parabel: Schalter und Regler, AutoISF-Wertezeile

- ⚠ **Schalter „Parabola on smoothed values“** (AutoISF-Einstellungen, Standard **aus** = Original):
  Parabel (acce_ISF) und dura_ISF rechnen mit den UKF-geglätteten 1-Minuten-Werten statt mit den
  Rohwerten. Ruhiger, aber später. Braucht UKF-Glättung und einen 1-Minuten-Sensor, sonst Rohwerte.
- ⚠ **Regler „Shortest parabola“** 5–20 min (Standard **10** = Original): wie viele Minuten die Parabel
  mindestens abdecken muss (nur 1-Minuten-Sensoren). Kürzer = schneller, aber nervöser; länger =
  ruhiger, aber später.
- Mit beiden Standardwerten rechnet AutoISF exakt wie bisher (per Test gesichert).
- Hauptseite: die von AutoISF benutzte Parabel ist **fett**. APS-Tab: Zeile mit Quelle und Mindestdauer.
- Diagramm „acc raw / acc smooth“ zeigt weiter beide, egal welche benutzt wird.
- **Neue Zeile mit den AutoISF-Werten** des letzten Loop-Laufs: acce, bg, pp, dura, final, iobTH.
- Offen: die r²-Schwelle 0,9 filtert bei geglätteten Werten kaum, noch nicht einstellbar.

## 2026-11-10 (8) – Parabel-Vergleich als Diagramm

- **Neues Diagramm „Parabola acceleration“:** zwei Linien über die Zeit, die Beschleunigung, die
  AutoISF benutzt hat (**acc raw**, grün), und dieselbe Parabel auf den UKF-geglätteten Werten
  (**acc smooth**, pink, dicker). Gleiche Achse, 0 in der Mitte (0 = BZ wird weder schneller noch
  langsamer).
  - Einschalten: „+ Diagramm hinzufügen“ → **acc raw** und **acc smooth** wählen.
  - Die Werte werden ab dieser Version bei jedem Loop-Lauf gespeichert; ältere Läufe haben keine.
  - Nur Anzeige, AutoISF rechnet unverändert mit den Rohwerten.

## 2026-11-09 (7) – Parabel-Vergleich roh / geglättet (nur Anzeige)

- **Vergleich des Parabel-Fits:** Bei 1-Minuten-Sensoren wird die AutoISF-Parabel zusätzlich mit den
  UKF-geglätteten 1-Minuten-Werten gerechnet. Auf der Hauptseite (unter der Loop-Statuszeile) und im
  APS-Tab stehen beide Ergebnisse nebeneinander: Beschleunigung, r² und Dauer.
  - Grün = r² mindestens 0,9, also der Wert, ab dem AutoISF die Parabel für acce_ISF nutzt.
  - **AutoISF rechnet weiter nur mit den Rohwerten**, wie im Original. Der geglättete Fit ist nur zum
    Vergleichen. Ein Test stellt sicher, dass der angezeigte Roh-Fit genau dem von AutoISF entspricht.
  - Sichtbar nur, wenn AutoISF aktiv ist und die Glättung **UKF** gewählt ist (Konfiguration → Glättung).

## 2026-11-09 (6) – iobTH im IOB-Diagramm

- **iobTH im IOB-Diagramm:** Im IOB-Diagramm kann iobTH als zweite Linie dazu. Beide liegen auf
  derselben Achse in IE, gleich skaliert, und die Basal-Leiste oben bleibt (vorher verschwand sie).
  Einschalten: Stift-Symbol am IOB-Diagramm → iobTH hinzufügen. Nur Anzeige.

## 2026-11-09 (5) – Bolus-Rechner mit neuestem BZ

- ⚠ **Bolus-Rechner nimmt den neuesten Messwert.** Bisher kam der BZ im Rechner aus dem
  5-Minuten-Raster und konnte bei 1-Minuten-Sensoren bis zu 4 Minuten alt sein. Jetzt ist es derselbe
  Wert wie oben in der Anzeige und im Loop. Die Rechnung selbst ist unverändert.

## 2026-11-09 (4) – Neuester BZ oben, Statuszeile Loop / SMB / TBR

- **BZ-Anzeige zeigt den neuesten Messwert.** Bisher kam der Wert oben aus dem festen 5-Minuten-Raster
  und konnte bei 1-Minuten-Sensoren bis zu 4 Minuten alt sein („vor 228 s“), obwohl schon ein neuer
  Wert da war. Jetzt zeigen BZ, Trendpfeil und Widgets denselben Wert, mit dem auch der Loop rechnet.
- **Neue Statuszeile** unter dem BZ: „Loop vor 35 s · SMB 0,30 IE vor 4 min · TBR 1,20 IE/h vor 3 min“
  (oder „Keine TBR“). Zählt jede Sekunde mit. Ist der letzte Loop-Lauf älter als 15 Minuten, wird
  „Loop“ rot. Nur auf dem Master-Handy, nur Anzeige.
- AutoISF selbst ist unverändert wie im Original 3.2.1.

## 2026-11-09 (3) – Loop bei jedem Wert, BZ-Alter in Sekunden

- ⚠ **Loop interval 1 = Loop bei jedem neuen Wert.** Bisher ließ der Loop einen Lauf aus, solange das
  SMB-Intervall noch lief. Jetzt nur noch, wenn das SMB-Intervall vor dem nächsten regulären Lauf
  endet (sonst käme der SMB unnötig spät). Bei 1 Minute läuft der Loop damit bei jedem Wert.
  Das SMB-Intervall gilt unverändert: Ein SMB kommt nie öfter als dort eingestellt.
- **BZ-Alter in Sekunden:** Oben steht jetzt „vor 47 s“ statt „vor 1 min“, jede Sekunde aktualisiert.
  Ab 10 Minuten wieder in Minuten.
- **Diagramm:** fISF hat eine eigene Farbe (weiß im dunklen Design), weil das alte Gold zu ähnlich zu
  bgISF (orange) war.

## 2026-11-09 (2) – Loop für 1-Minuten-Werte

- ⚠ **Frische Werte:** Der Loop rechnet mit dem neuesten Messwert und nicht mehr mit dem
  5-Minuten-Wert davor. (Von JetFoxy übernommen, Upstream-Vorschlag #5148.)
- ⚠ **1-Minuten-Werte im Filter:** Bei Libre 2/3, Sibionics, AiDex und Syai/Ottai bekommt der
  UKF-Filter die echten Minutenwerte. Ein zweiter Durchlauf über dieselben Werte stört das Lernen
  des Filters nicht mehr. (Von JetFoxy übernommen.)
- ⚠ **AutoISF-Beschleunigung mit 1-Minuten-Werten:** Die Parabel für acce_ISF nutzt die echten
  Messwerte, wenn der Sensor schneller als alle 4 Minuten sendet. (Von JetFoxy übernommen.)
- ⚠ **Neue Einstellung „Loop interval“** (1–10 min, Standard 5). Sie steht bei den SMB-Einstellungen
  von OpenAPS SMB und AutoISF und legt fest, wie oft der Loop höchstens läuft.
  - Weniger als 5 min hilft nur mit einem Sensor, der jede Minute sendet.
  - Mehr als 5 min: Der Loop reagiert später, auch bei Unterzucker.
  - Das **SMB-Intervall** (1–10 min) gilt weiter: Ein SMB kommt höchstens so oft, wie dort
    eingestellt ist. Ist das SMB-Intervall noch nicht vorbei, lässt der Loop Zwischenläufe aus.
    Spätestens nach 5 min läuft er aber immer (außer das Loop-Intervall ist größer als 5).
- Mit dem Standardwert 5 verhält sich der Loop wie bisher.

**Welche Werte nutzen die AutoISF-Faktoren jetzt?**

| Faktor | Was er braucht | Woher es kommt |
|---|---|---|
| acce_ISF | Beschleunigung (Parabel) | echte 1-Minuten-Messwerte |
| dura_ISF | wie lange der BZ schon flach und hoch ist | echte 1-Minuten-Messwerte |
| bg_ISF | aktueller BZ | neuester Messwert (früher: der 5-Minuten-Wert davor) |
| pp_ISF | Delta (Anstieg) | ab dem neuesten Messwert (früher: ab dem 5-Minuten-Wert davor) |

- Das Delta bleibt „Anstieg pro 5 Minuten“: neuester Wert minus Wert 5 Minuten vorher. Darauf sind
  die pp_ISF-Gewichte eingestellt. Ein Delta über 1 Minute wäre etwa fünfmal kleiner.
- acce_ISF und dura_ISF nutzen die echten 1-Minuten-Werte nur, wenn AAPS den Sensor erkennt (Libre 2,
  Libre 2 Native, Libre 3, Sibionics, AiDex, Syai/Ottai oder Dexcom) **und** etwa jede Minute ein Wert
  kommt. Sonst rechnen sie wie bisher mit dem 5-Minuten-Raster. bg_ISF und pp_ISF sind in jedem Fall
  frisch.

## 2026-11-09 (1) – Erster Stand

- AutoISF 3.2.1 auf AAPS 4.0.0, Basis: JetFoxys Branch `autoisf-3.2.1`.
- AutoISF lässt sich ohne Engineering Mode einschalten.
- ⚠ Prüfung ungültiger Profilwerte (ISF, KH-Faktor) wie in Upstream: AutoISF bricht dann ab.
- ⚠ Aktivitätserkennung (Activity Monitor und ActivityClassifier) wieder aktiv:
  - Bewegung gibt nie mehr Insulin als Autosens allein.
  - Inaktivität höchstens bis zur Autosens-Obergrenze.
  - Bei temporärem Ziel ist der ActivityClassifier aus.
- Libre-Kalibrierung entfernt (Kalibrieren in Juggluco).
- AutoISF-Werte (acce, bg, pp, dura, final ISF, iobTH) werden im Loop-Ergebnis gespeichert und als
  Diagramm angezeigt. Die Achse hat Rand, damit eine Linie bei 1,0 sichtbar ist.
- Datenbank wie Upstream 4.0.0 (keine eigene Tabelle).
