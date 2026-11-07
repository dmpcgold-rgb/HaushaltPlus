# Firebase für Haushalt+ einrichten

## Zuordnung

- GitHub: `dmpcgold-rgb/HaushaltPlus` (Gold-Konto)
- Firebase: `haushaltplus-50075` (darf mit einem anderen Google-Konto verwaltet werden)
- Android applicationId: `at.familie.haushaltplus`
- Firebase-Datei: `google-services.json` (nicht ins Repository committen)

## 1. Firebase-Android-Konfiguration als GitHub-Secret hinterlegen

Die Firebase-Konfiguration wird für den Build über ein Repository-Secret geladen.

1. Im Repository **Settings** öffnen.
2. Unter **Secrets and variables → Actions → New repository secret** wählen.
3. **Name**: `FIREBASE_GOOGLE_SERVICES_JSON`
4. **Secret**: Den **gesamten Inhalt** der originalen Firebase-Datei `google-services.json` einfügen, inklusive öffnender und schließender geschweifter Klammer. Keine Screenshots und keine Dateipfade einsetzen.
5. **Add secret** drücken.

**Achtung:** Nicht als normale Datei in GitHub hochladen, auch nicht in Issues, README oder Actions-Logs. In `.gitignore` ist `app/google-services.json` bereits ausgeschlossen.

## 2. Firebase-Dienste in der Console aktivieren

Im Projekt `haushaltplus-50075`:

- **Authentication → Sign-in method → Email/Password** aktivieren (Google-Login ist derzeit nicht programmiert).
- **Firestore Database** erstellen und eine EU-Region auswählen, z. B. `europe-west3` (falls angeboten und zur Datenhaltung passend).
- Bei Firestore zunächst keine dauerhaft offenen Testregeln verwenden. Die Regeln aus `firestore.rules` im Repository müssen vor echter Nutzung bereitgestellt werden.

## 3. Firestore-Regeln bereitstellen

Das geht über die Firebase CLI von einem autorisierten Rechner:

```bash
npm install -g firebase-tools
firebase login
firebase use haushaltplus-50075
firebase deploy --only firestore:rules
```

Alternativ kann man den Inhalt der Datei `firestore.rules` in Firebase Console → Firestore Database → **Rules** prüfen und veröffentlichen. Änderungen an Regeln sollten erst nach einem Test mit mehreren Nutzerkonten freigegeben werden.

## 4. Android-APK mit Firebase bauen

In GitHub → **Actions → Android Firebase APK → Run workflow** auswählen.

Der Workflow:
- stoppt, wenn das Secret fehlt,
- prüft Projekt-ID und Android-Paketnamen, ohne die Konfiguration auszudrucken,
- erstellt einen Firebase-fähigen **Debug-Build**,
- bietet ihn als Artefakt `HaushaltPlus-Firebase-debug-APK` an.

Die alte Workflow-Datei `android-debug.yml` bleibt für Offline-Tests bestehen.

## 5. Benachrichtigungen kostenlos auf Spark

Spark beibehalten, keine Zahlungsmethode hinzufügen, keine Cloud Functions deployen und nicht zu Blaze wechseln.

Die App erzeugt ein Mitgliedsereignis in `households/{homeId}/activity`. Auf anderen Handys wird daraus eine lokale Android-Benachrichtigung: bei aktiver App via Firestore-Listener, bei geschlossener App mit Android-WorkManager-Hintergrundabfrage (normalerweise nicht öfter als etwa 15 Minuten, ggf. deutlich später). **Keine echten sofortigen Remote-Push-Nachrichten.**

Die neuen `firestore.rules` für `activity` müssen veröffentlicht werden. Die Prototyp-Funktionen im Ordner `functions/` werden nicht verwendet und sind auch nicht mehr in `firebase.json` konfiguriert.

## 6. Verhalten überprüfen

1. APK auf zwei Android-Handys installieren.
2. Auf jedem Handy ein eigenes E-Mail/Passwort-Konto in Haushalt+ anlegen.
3. Erstes Konto gründet den Haushalt; zweites Konto tritt per Einladungscode bei.
4. Artikel auf dem ersten Gerät hinzufügen: Er muss auf dem zweiten erscheinen.
5. Artikel abhaken: Erledigtstatus und Name müssen auf dem anderen Gerät sichtbar werden.
6. Bei geöffneter App die lokale Android-Benachrichtigung testen; im Hintergrund kann sie deutlich verspätet eintreffen.

Nicht mit echten Haushaltsdaten starten, bevor die Firestore-Regeln und der Zugriff durch Nicht-Mitglieder getestet wurden.

## Bekannte Einschränkungen

- Cloud-Modus und Offline-Modus sind bisher getrennt; kein automatischer Import lokaler Daten.
- Pro Benutzer ist derzeit nur ein gespeicherter FCM-Gerätetoken vorgesehen.
- Debug-APK ist noch kein signierter Release-Build.
- Für Konten ist noch keine Passwort-zurücksetzen-Oberfläche vorhanden.
