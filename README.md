# Haushalt+ v0.2 – Android-Familienhaushalt

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
