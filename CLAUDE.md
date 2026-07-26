# CLAUDE.md

autopomodoro — an Android app (Kotlin, Jetpack Compose, Room) for cycle-based recurring
reminders.

## Never use sudo or root

Do not run `sudo`, do not probe for sudo access, and do not act as root, unless the user has
explicitly said to in that session. This covers package installs (`apt install`), writes
outside the user's home, and service management. If a task appears to require root, stop and
ask the user what to do next.

## Building and testing

The system JVM is a **JRE only** — it has no `javac`, and Gradle fails with
"does not provide the required capabilities: [JAVA_COMPILER]". Use the JDK bundled with
Android Studio:

```bash
JAVA_HOME=/home/tjcelaya/bin/android-studio/jbr ./gradlew testDebugUnitTest --offline
```

`--offline` works — the Gradle cache under `~/.gradle` is fully populated. Prefer it, since it
keeps builds to a few seconds. `assembleDebug` also works offline if you need to confirm the
app still packages.

`local.properties` (which points Gradle at the SDK) is gitignored, so a fresh clone needs it
copied in before the first build.

## Where the logic lives

`scheduler/CycleCalculator.kt` is a pure, stateless object with no Android dependencies. It is
the only part of the app covered by fast JVM unit tests (`app/src/test/`, run via
`testDebugUnitTest`, no emulator). Put new scheduling logic there and test it there.

Everything else — `AlarmReceiver`, `AlarmSchedulerService`, the Compose UI — needs an emulator
to exercise, so keep decision-making out of those classes and delegate to `CycleCalculator`.

## Room

`AppDatabase` is at `version = 1` with `exportSchema = false`. Any change to the
`PomodoroSchedule` entity needs a matching migration (or an explicit fallback), or existing
installs will crash on open.
