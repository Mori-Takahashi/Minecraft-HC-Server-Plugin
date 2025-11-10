# DeathCounter Fabric

Ein Fabric-Server‑Mod für Minecraft 1.21.8, der Spieler nach jedem Tod für eine wachsende Dauer sperrt, Todesdaten speichert und komfortable Admin-/Spieler-Commands bereitstellt.

## Features
- Progressiver Death-Cooldown: nach jedem Tod werden Spieler automatisch in den Zuschauermodus versetzt und erst nach Ablauf der Sperrzeit wieder spielbar.
- Speicherung von Todeszeitpunkten, Deathcount, Spawn- und Todespositionen in `config/deathcounter_data.json`, sodass Server-Neustarts kein Problem sind.
- Automatische Teleports zurück zum letzten Spawn oder Todesort, sobald ein Spieler wiederbelebt wird bzw. wieder einloggt.
- Broadcasts an alle Spieler mit Infos über neue Tode und verbleibende Sperrzeiten.
- Admin-/Spieler-Commands zur Einsicht und Bearbeitung der Daten.

### Sperrzeiten je Tod

| Tod | Dauer |
| --- | ----- |
| 1   | 10 Sekunden |
| 2   | 20 Sekunden |
| 3   | 30 Sekunden |
| 4   | 30 Minuten |
| 5   | 1 Stunde |
| 6   | 2 Stunden |
| 7   | 3 Stunden |
| 8+  | 24 Stunden |

## Commands

| Command | Beschreibung | Rechte |
| ------- | ------------ | ------ |
| `/deathinfo [spieler]` | Zeigt, wie oft ein Spieler bereits gestorben ist, wie lange der aktuelle Bann noch dauert und wie lange der nächste Tod sperren würde. Ohne Argument funktioniert der Befehl nur für den ausführenden Spieler. | Alle Spieler |
| `/revive <spieler> [death_counter]` | Hebt die aktuelle Sperre eines Spielers auf. Optional kann der Deathcounter direkt auf einen Wert (0–8) gesetzt werden, um die nächste Sperrzeit zu beeinflussen. | Permission-Level ≥ 2 |

## Installation

1. Stelle sicher, dass dein Server auf **Minecraft 1.21.8** läuft und **Fabric Loader 0.17.3** inkl. passender **Fabric API** installiert ist.
2. Lade das gebaute `deatcounter-fabric-*.jar` aus `build/libs`.
3. Lege die Datei in den `mods/` Ordner deines Servers.
4. Starte den Server neu. Beim ersten Start wird `config/deathcounter_data.json` automatisch angelegt.

## Entwicklung & Build

Voraussetzungen:
- Java Development Kit (JDK) 21
- Gradle Wrapper (liegt bereits bei)

Build:

```bash
.\gradlew build
```

Die kompilierten Artefakte findest du anschließend unter `build/libs/`.

Für die Arbeit im IDE empfiehlt sich das Importieren des Projekts als Gradle-Projekt. Loom erzeugt automatisch getrennte Source-Sets für Client/Server.

## Daten & Anpassung

Alle Death-Daten landen in `config/deathcounter_data.json`. Die Datei wird bei Server-Stop oder in regelmäßigen Abständen aktualisiert. Manuelle Änderungen sollten nur im ausgeschalteten Zustand des Servers erfolgen.

Für weitergehende Anpassungen (z. B. andere Sperrzeiten) können die Konstanten in `Bond007.java` angepasst und anschließend neu gebaut werden.

## Lizenz

Dieses Projekt steht unter der [MIT License](LICENSE.txt). Du darfst den Code frei nutzen, ändern und verbreiten, solange die Lizenz beibehalten wird.
