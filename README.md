# Haushalt+ v0.3.0 – Android-Familienhaushalt

Native Kotlin-/Jetpack-Compose-App mit RAUS0-inspiriertem **dunklem Neon-Design** (Schwarz/Carbon, Orange, Pink, Türkis und Blau). Android-Paketname: `at.familie.haushaltplus`. Quellcode ausschließlich im Gold-Repository `dmpcgold-rgb/HaushaltPlus`.

## Funktionen von v0.2

1. **Intelligente Einkaufslisten:** Mehrere Listen, Umbenennen, Suchen, Artikel/Menge/Preis bearbeiten, häufig gekaufte Artikel als Schnellwahl und Zusammenlegen numerischer Doppeleinträge in derselben Liste.
2. **Gemeinsamer Haushalt:** Firebase-Authentifizierung, 20-stelliger Einladungscode, Firestore-Echtzeitübertragung und Androids standardmäßig aktivierter Offline-Cache. Die App zeigt den Cloud-Synchronisierungsstatus; gleichzeitige Änderungen am selben Feld unterliegen der Firestore-Konfliktauflösung (last write wins).
3. **Benachrichtigungen ohne Serverkosten:** Firestore-Aktivitäten mit lokalen Benachrichtigungen, je Kategorie abschaltbar; fällige Aufgaben werden höchstens einmal je Tag erinnert. Geöffnete App: zeitnah. Geschlossene App: WorkManager prüft frühestens ungefähr alle 15 Minuten; Android kann deutlich verzögern. **Nicht mit sofortigem FCM-Remote-Push verwechseln.**
4. **Familienverwaltung:** Mitgliedsliste, Einladungen erstellen/teilen und widerrufen, Haushaltsmitglieder entfernen, Besitzverwaltung an ein anderes Mitglied übertragen.
5. **Haushaltsplan:** Aufgaben bearbeiten, delegieren, Fälligkeit/Serienrhythmus einstellen, Zuständigkeit bei Wiederholung rotieren. Wiederholungen entstehen auf dem Gerät beim Erledigen.
6. **Vorratsverwaltung:** Bestand und Mindestmenge bearbeiten; beim Unterschreiten wird ein benötigter Artikel auf die Einkaufsliste gesetzt; auch manuell als Sammelaktion möglich.
7. **Monatsbudget:** Monatslimit und Übersicht gebuchter Einkäufe. Tatsächliche oder geplante Gesamtpreise werden über Artikel bearbeiten eingegeben. Ein automatischer Kassenbon-Scanner ist nicht enthalten.
8. **Barcode-Scanner:** Google Code Scanner (Google Play Services, keine Kamera-Appberechtigung) und einzelne, vom Nutzer ausgelöste Produktnamensabfragen bei Open Food Facts. Offline/bei unbekannten Produkten bleibt der Code als bearbeitbarer Name stehen.
9. **Essensplan:** Mahlzeiten für die kommenden sieben Tage; Zutaten aus kommagetrennten Rezeptlisten auf die Einkaufsliste übernehmen.
10. **Daten und Kontosicherheit:** JSON-Export über Android-Dateiauswahl, bestätigter Import fehlender Einträge, Passwort-Zurücksetzen per E-Mail, Konto-Löschung für Nicht-Besitzer nach Reauthentifizierung; Besitzer übertragen vorher die Verwaltung.

## Neu in v0.3.0: 15 Funktions- und Designverbesserungen

1. Einkaufsmodus mit großen, einhändig bedienbaren Abhakflächen, Live-Fortschritt und Ausblendung erledigter Positionen.
2. Deutsche Spracherkennung über das Android-System und lokale Mengen-/Einheitenanalyse. Vor dem Speichern werden alle erkannten Artikel angezeigt und bestätigt.
3. Geteilte Einkaufsvorlagen speichern und auf eine ausgewählte Liste anwenden.
4. Verbrauchsprotokoll für Vorräte und eine nachvollziehbare Schätzung der verbleibenden Tage auf Basis der letzten 14 Tage.
5. Einkaufsbelege als manuelle Kassenbeträge, Geschäft und Datum speichern; grafische monatliche Auswertung je Geschäft.
6. Gemeinsame Rezepte und Zutatenmengen pro Portion speichern; Zutaten für eine frei gewählte Personenzahl umrechnen und auf die Liste setzen.
7. Familienkalender mit Wochen- und Monatsansicht der Aufgaben und geplanten Mahlzeiten.
8. Geräteeinstellungen für Ruhezeiten, Sammelmeldungen, Vorab-Erinnerungen bis sieben Tage und direktes Öffnen des Aufgabenreiters.
9. Firestore-Offlinespeicherung für Cloud-Daten und atomare Bestandsänderungen per `FieldValue.increment`, die auch offline vorgemerkt werden. Konfliktbedingte Unterschreitungen von Null werden nach Bereitstellung der neuen Firestore-Regeln zurückgewiesen und als Fehler angezeigt.
10. Gemeinsamer Aktivitätsverlauf aus Firestore-Ereignissen; unmittelbares Rückgängig beim Abhaken von Einkaufsartikeln. Lokal werden Aktivitäten auf dem Gerät gespeichert.
11. Personalisierbare Startseite mit 7 Kacheltypen, deren Reihenfolge und Sichtbarkeit sich pro Gerät einstellen lassen.
12. Kompakte Navigation mit direkter Schnellwahl für neue Artikel, Aufgaben und Vorräte.
13. Kompakte, anpassungsfähige Listen und ausklappbare Kategorie-Filter.
14. Einheitliche dunkle RAUS0-inspirierte Neon-Farbwelt mit Orange für Aktionen, Türkis für Erfolg, Pink für Warnungen, Blau für Informationen und zentralen Form-Radien.
15. Sanft animierte Karten/Filter, haptisches Feedback und eine Einstellung für reduzierte Vibrationen.

Weitere Änderungen: JSON-Sicherungen können auch Rezepte, Einkaufsvorlagen und Belege ergänzend exportieren/importieren; alte lokale Daten bleiben lesbar. Die Version bleibt **Spark-only**.

**Vor dem Cloud-Einsatz zwingend:** Die neue `firestore.rules` in Firebase Console veröffentlichen, da sie die Collections `templates`, `recipes`, `receipts`, `stockUsage` freigibt und negative Bestände verhindert. Keine offenen Testregeln!

**Einschränkungen:** Kein automatisches Kassenbon-OCR oder synchronisiertes Fotoarchiv, da die Belege absichtlich nur als manuelle Beträge/Notizen verarbeitet werden. Die Sprachfunktion benötigt verfügbare Android-Spracherkennung. Hintergrundbenachrichtigungen erfolgen best effort via WorkManager und sind nicht sofortiges Remote-FCM. Die Bestandsprognose ist eine lineare Schätzung, keine präzise Verbrauchsvorhersage. Optimistische, offline gespeicherte Änderungen können nach einer echten Konfliktauflösung abgewiesen werden; eine vollständige manuelle Konfliktmerge-UI gibt es noch nicht. Ein Echtgerätetest mit zwei Personen steht noch aus.

## Neu in v0.2.2

- **Abgeschlossene Einkäufe archivieren**: pro Einkaufsliste, nach Bestätigung; im Archiv wieder sichtbar machen. Die bestehenden Kaufdatensätze bleiben erhalten, sodass Monatsausgaben nicht beim Aufräumen verschwinden.
- **Kategoriefilter** für Einkaufslisten; Kombination mit der bisherigen Produktsuche und dem Erledigt-Filter.
- **Startseite zeigt nur aktive Einkäufe** im Fortschrittsbalken und hebt heute fällige bzw. überfällige Aufgaben hervor.
- Gemeinsamer **TaskPlanner** für tägliche, wöchentliche und monatliche Folgen inklusive Zuständigkeitswechsel; lokale Aufgaben werden nur aus dem aktuellen gespeicherten Status fortgeschrieben.
- Firebase-Zweitklickschutz gegen doppelte Aufgabenänderungen, solange eine Änderung bestätigt wird.
- Kotlin-JVM-Tests für den TaskPlanner in der Android-Firebase-Buildpipeline. Der Build gilt nur als erfolgreich, wenn Tests und APK-Kompilierung durchlaufen.

Bestehende Listen, Budgetdaten, Firebase-Mitglieder und die kostenlose Spark-Konfiguration bleiben erhalten. Firestore-Sicherheitsregeln müssen für diese Erweiterungen nicht erneut geändert werden.

## Neu in v0.2.1

- Shoppingliste als Text teilen (z. B. über Messenger), ohne anderen Apps direkten Cloud-Zugriff zu geben.
- Erledigte Artikel über das Optionsmenü erneut auf die Liste setzen, ohne den ursprünglichen Einkauf aus der Monatsstatistik zu löschen.
- Aufgaben nach **Offen**, **Heute**, **Meine** und **Alle** filtern; weniger störende Aktionssymbole in der Liste.
- Bessere Auth-Profilinitialisierung und Bereinigung der gespeicherten Haushalts-ID beim Kontowechsel.
- Android erinnert auch nach nachträglich erteilter Benachrichtigungsberechtigung an fällige Aufgaben.
- Die Berechtigung für Handy-Benachrichtigungen wird erst im Einstellungsbereich angefragt, nicht beim ersten Start.
- Hintergrundabfragen laden nach der ersten Synchronisierung nur **neue** Aktivitäten nach und prüfen fällige Aufgaben höchstens einmal pro Tag. Das reduziert Firestore-Lesevorgänge im Spark-Tarif.
- Budget zeigt Ausgaben nach Kategorie und macht Artikel ohne Preis transparent.
- JSON-Export meldet Fehler, wenn der gewählte Speicherort nicht beschreibbar ist, und begrenzt zu große Importdateien.
- Niedrige Vorräte werden auch beim Erstellen oder manuellen Bearbeiten auf die Einkaufsliste gesetzt, sofern nicht schon ein offener Eintrag vorhanden ist.

Bekannte Grenzen: Firestore lässt in dieser Version Änderungen offline in der Regel vormerken; **Vorratsänderungen, die eine Cloud-Transaktion benötigen**, können offline fehlschlagen. Hintergrundmeldungen sind keine garantierten sofortigen Push-Nachrichten. Ein praxistauglicher signierter Release-Schlüssel wird gesondert benötigt.

## Kostenloser Spark-Tarif

Das Projekt ist fest auf Firebase **Spark (kostenlos)** ohne Abrechnung ausgelegt. `firebase.json` enthält ausschließlich Firestore-Regeln. Der ältere `functions/`-Prototyp wird **nicht deployt**. Auf **Blaze** darf ohne ausdrückliche neue Freigabe nicht gewechselt werden. Bei Überschreiten der freien Kontingente können Funktionen bis zum Quoten-Reset ausfallen.

- Firebase-Projekt: `haushaltplus-50075` (separates Google-Konto ist erlaubt)
- GitHub: `dmpcgold-rgb/HaushaltPlus`
- Firebase-Android-Konfiguration: über Secret `FIREBASE_GOOGLE_SERVICES_JSON` in GitHub Actions
- Firestore-Regeln: `firestore.rules` (nach jeder Änderung **erneut in Firebase Console veröffentlichen**)

## Android-Builds

Die GitHub-Actions-Workflows `Android Debug APK` (rein lokal ohne Konfiguration) und `Android Firebase APK` (mit Secret) erzeugen **Debug-Test-APKs**. Die Builds sind keine stabil signierten Release-Versionen. APKs aus verschiedenen Runnern können inkompatible Debug-Signaturen verwenden: vorhandene lokale Daten vor einer Deinstallation separat sichern.

Ausführliche Konfigurationsschritte stehen in [FIREBASE_SETUP.md](FIREBASE_SETUP.md).

## Bewusste Grenzen

- Neue v0.2-Features sind implementiert und werden mit GitHub Actions kompiliert; ein echter Test mit zwei Handys, Kamera-Scanner und Berechtigungen muss noch erfolgen.
- Ohne erneute Veröffentlichung der aktualisierten Regeln funktionieren die neuen `meals`- und `activity`-Felder sowie Mitglieder-/Besitzerfunktionen ggf. nicht.
- Import ist **ergänzend**, kein pixelgenauer Restore: Herkunfts-Listen-IDs, Erledigtstatus und der Eigentümer werden bei Import in einen neuen Haushalt nicht überschrieben.
- Lokaler und Cloud-Modus migrieren nicht automatisch zwischen sich.
- Sofortige serverseitige Push-Benachrichtigungen, automatische Kassenbon-OCR, geteilte Einzelberechtigungen je Liste und vollständige Konfliktauflösung sind nicht implementiert.
- Bitte keine echten Haushaltsdaten laden, bevor Firestore-Regeln und Trennung zwischen Haushaltsmitgliedern geprüft wurden.

## Datenschutz

Keine Passwörter oder Admin-Zugangsschlüssel im Repository speichern. `app/google-services.json` und Signierschlüssel sind durch `.gitignore` ausgeschlossen; Cloud-Config wird nur während des Builds aus dem GitHub-Secret erzeugt. Das Repository ist bei GitHub aktuell öffentlich; vor persönlichen Nutzerdaten empfiehlt sich ein privates Repository. 
