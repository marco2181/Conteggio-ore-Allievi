# Conteggio Ore Allievi — App Android

App Android nativa (Kotlin + Jetpack Compose + Room) che sostituisce la versione Windows.
Vedi le issue GitHub etichettate `android` per la roadmap.

## Stato: Fase 0 — scheletro
- Navigazione bottom bar: Dashboard, Presenze, Allievi, Report, Altro
- **Corsi** (in "Altro"): crea/modifica (nome + ore, preset 20/90/150/300), archivia/ripristina
- **Allievi**: lista con ricerca (nome + etichetta), crea/modifica (nome, corso o ore individuali, etichetta per omonimi)
- Room DB `conteggio_ore.db`
- Dashboard / Presenze / Report sono placeholder (fasi successive)

## Come compilare
Serve **Android Studio** (scarica da solo l'SDK) oppure JDK 17 + Android SDK.

1. Apri la cartella `android/` in Android Studio.
2. Lascia sincronizzare Gradle (scarica dipendenze).
3. Collega un telefono (Android 10+) con debug USB, oppure usa un emulatore.
4. Premi **Run**.

Da riga di comando (con SDK configurato in `local.properties`):
```
./gradlew assembleDebug
# APK in app/build/outputs/apk/debug/app-debug.apk
```

> `local.properties`, `.gradle/` e i build output sono ignorati da git.
> Il Gradle wrapper (`gradlew`) viene generato da Android Studio al primo sync,
> oppure con `gradle wrapper` se hai Gradle installato.
