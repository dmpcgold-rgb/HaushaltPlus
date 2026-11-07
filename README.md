# Haushalt+ — Android-Haushaltsmanager (MVP)

**Status:** Android-Quellprojekt mit lokalem Modus und Firebase-Spark-Anbindung. Kein signierter Release-Build. Firebase Authentication, Firestore und die aktuellen Zugriffsregeln müssen in der Console aktiviert werden.

## Bereits implementiert

- Übersicht: offene Einkäufe, Aufgaben, fehlende Vorräte, Einkaufsfortschritt
- Einkauf: mehrere frei benennbare Listen, Artikel, Menge, Einheit, Kategorie, Preisrichtwert, Haken, „erledigt von“, Löschen
- Aufgaben: Zuständigkeit, Fälligkeit, Erledigtstatus, Wiederholungsrhythmus mit automatischer Folgeaufgabe (lokal oder im Android-Cloud-Client), Löschen
- Vorrat: Mengen verwalten, Mindestbestand, auf Einkaufsliste setzen
- Lokaler Modus: dauerhaft auf dem Gerät gespeicherte Daten, **keine** Familie-/Cloud-Funktion
- Cloud-Modus: E-Mail/Passwort-Konten, Haushalt gründen, per 20-stelligem Code beitreten, Firestore-Echtzeitsync, Firestore-Zugriffsregeln
- Kostenloser Spark-Modus: Aktivitäten in Firestore, lokale Handy-Benachrichtigungen bei aktiver App und Hintergrundkontrolle ab etwa 15 Minuten. Wiederkehrende Aufgaben werden direkt im Android-Client angelegt.
- GitHub Actions zum Bauen einer **Debug-APK**

## Für einen offline nutzbaren Test

Projekt mit Android Studio öffnen und die App als Debug-Build bauen. Ohne `app/google-services.json` läuft Haushalt+ automatisch im lokalen Modus. Die Daten bleiben nach App-Neustart erhalten; andere Handys sehen sie **nicht**.

Falls du eine Debug-APK über GitHub Actions bauen möchtest: neues Repository anlegen, diesen Projektordner hochladen, Workflow `Android Debug APK` starten und das ZIP-Artefakt mit der APK herunterladen. Der Workflow braucht **kein** Firebase-Projekt für den lokalen Modus.

## Echte Familienfreigabe aktivieren

1. Separates Firebase-Projekt für Haushalt+ erstellen (nicht das eines anderen Apps wiederverwenden).
2. In Firebase Console eine Android-App mit Package-ID `at.familie.haushaltplus` registrieren.
3. Die heruntergeladene `google-services.json` **lokal** unter `app/google-services.json` ablegen, **nicht** öffentlich committen.
4. Authentifizierung → **E-Mail/Passwort** aktivieren. Firestore-Datenbank in EU-Region erstellen.
5. `firebase deploy --only firestore:rules` aus dem Projektordner ausführen.
6. Auf dem **kostenlosen Spark-Tarif** bleiben. **Keine Cloud Functions installieren oder deployen.** Die Android-App erzeugt Folgeaufgaben und kostenlose Benachrichtigungsereignisse direkt.
7. Bei GitHub Actions: Repository Secret `FIREBASE_GOOGLE_SERVICES_JSON` mit dem **vollen JSON-Inhalt** setzen, damit der Debug-APK-Workflow einen Cloud-Build erzeugt.
8. Auf mehreren Handys die Cloud-APK installieren, für jede Person ein eigenes Konto anlegen und mit dem 20-stelligen Einladungscode beitreten. Android-Benachrichtigungen erlauben.

### Sicherheits- und Datenschutzverhalten

- Listen sind nur für Mitglieder sichtbar (Firestore Security Rules).
- Einladungscodes sind zufällig, 20 Zeichen lang und 30 Tage gültig. Wer einen gültigen Code kennt und ein Konto hat, kann beitreten; Code wie ein Passwort behandeln.
- Kostenloser Benachrichtigungsmodus ohne Server: Firestore-Ereignisse werden von anderen Geräten gelesen und als lokale Android-Meldung angezeigt. Bei geschlossener App kann die Zustellung deutlich verzögert sein. **Kein sofortiger FCM-Remote-Push**.
- Der Cloud-Modus hat noch keine Konto-Wiederherstellung/Passwort-zurücksetzen-UI, Admin-Mitgliederverwaltung, echte Mehrgeräte-Push-Zustellung oder Datenmigration von lokal in Cloud. Diese Punkte gehören auf die Roadmap.
- `google-services.json`, Signaturschlüssel und Service-Account-Zugangsdaten niemals ins öffentliche Repository stellen.

## Geplante Erweiterungen

- Einkaufslisten umbenennen, archivieren und gezielt mit einzelnen Personen teilen (aktuell für den ganzen Haushalt sichtbar)
- Barcode-Scan und Produktvorschläge
- Foto-/Kassenbon-Erfassung, tatsächliche Einkaufskosten und Budgetwarnungen
- Erinnerungen vor Fälligkeit, Wochenplan, Müllabfuhrtermine
- Vollständige Offline-Konfliktbehandlung, Änderungsverlauf und papierloser Haushaltskalender
- Benachrichtigungseinstellungen, Mehrgeräte-Token, Personen aus Haushalt entfernen, Einladung widerrufen
- UI-Tests, Firestore-Emulator-/Regeltests, signiertes Release-Build, interne Verteilung


## Nur kostenloser Spark-Tarif
Haushalt+ ist für Spark vorgesehen: **Keine kostenpflichtigen Cloud Functions deployen und keinen Blaze-Plan aktivieren.** Die noch vorhandenen Dateien unter `functions/` sind ein nicht verwendeter Prototyp; `firebase.json` deklariert ausschließlich Firestore-Regeln.

Die neue kostenlose Benachrichtigung verwendet Firestore-Aktivitäten mit Android-WorkManager. Die Zustellung bei geöffneter App ist zeitnah. Hintergrundchecks können aufgrund von Android-Energiesparmechanismen über 15 Minuten dauern. Es ist **kein sofortiges serverseitiges FCM-Push**. Für die Aktivitätsereignisse müssen die aktuellen Firestore-Regeln veröffentlicht werden.

Benötigte Firebase-Dienste auf Spark: Firestore-Datenbank und E-Mail/Passwort-Anmeldung. Firebase-Quoten beachten. Siehe [FIREBASE_SETUP.md](FIREBASE_SETUP.md).
