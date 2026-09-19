# Forge

A hypertrophy training app for Android that plans the block, picks the loads, and runs the
clock, so the only thing left to do in the gym is lift.

Built because Hevy is a logbook: it faithfully records what you decide, and decides nothing.
Every session you re-derive what you lifted last time, what to add, how many sets you are
supposed to be doing this week, and when to deload — and it costs you battery and a warm
phone to do it.

## What it actually automates

**Loads and reps, every set.** The last session's performance and RIR drive a double
progression inside each exercise's rep range. Top the range with reps to spare and it adds
two increments; top it exactly and it adds one and resets to the floor; land inside it and
it holds the load and adds a rep; miss the floor badly and it walks the weight back. Every
suggestion rounds to an increment the equipment can actually be loaded to — a 5 kg pin stack
is never told to make 82.5 kg.

**The whole mesocycle.** Pick a split, days per week and block length; the generator
allocates each muscle's weekly volume from its landmarks *first*, then chooses exercises to
carry it. Doing it in that order is what stops the usual accident of thirty sets of chest
and four of hamstrings. Compounds get ordered before isolations, and A/B days get different
exercise selections.

**Volume, week to week.** Volume starts at MEV and ramps toward MRV, with the last week a
deload. The ramp is not fixed: three questions after each session (pump, soreness, workload)
move next week's set count up or down. Still sore going in means it pulls a set rather than
adding one.

**Rest and flow.** The timer starts itself when a set is logged, the list follows you to the
next set, and the phone can stay in your pocket.

## Your gym, and the unit it is marked in

Programs are generated from what is actually on the floor, not from a generic commercial gym.

A gym profile carries its equipment (fifteen categories — a pin stack is not a plate-loaded
machine, a pull-up bar is not a lat pulldown), free-text detail on any of them, and a list of
**stations**: named machines with your own notes and an explicit list of what each one can do.
A station grants its exercises even when its equipment category is switched off, which is how
a sparse gym says "no machines, except the shoulder press and pec deck on that one combo unit"
without lying in either direction. Availability resolves most-specific-first: a per-exercise
yes/no beats a station, which beats the broad category.

Station names are yours to write. The app has no way to verify a manufacturer's model number
and does not invent one — what it actually needs is the exercise list.

If the gym cannot train a muscle at all, the generator says so instead of quietly dropping it.

**Units belong to the gym, not to you.** An Australian gym's plates are marked in kg and step
in 2.5; a US gym's step in 5 lb. That is not a display preference — rounding a suggestion in
kilograms and converting it afterwards yields numbers nobody can load ("220.5 lb"), which
forces exactly the manual override this app exists to remove. So the progression maths runs in
the gym's own unit and only converts back to kilograms for storage. Fly between gyms, flip the
toggle, and every suggestion lands on plates that exist in the room you are standing in.

Every prescription carries a plain-English reason, reachable from the ⓘ on any exercise. A
number you cannot interrogate is a number you stop trusting.

## Why it should not cook your phone

The usual way these apps drain a battery is a countdown that re-composes the UI sixty times
a second while holding a wake lock. Forge does none of that:

- The rest timer stores **one absolute deadline** and schedules **one OS alarm**. No ticking
  coroutine, no wake lock, no foreground service.
- The on-screen countdown sleeps until the *next second boundary*, so a three-minute rest
  costs 180 wake-ups rather than ~10,800 — and it is wrapped in `repeatOnLifecycle`, so
  backgrounding the app cancels it outright. The alarm is what guarantees the buzz.
- The countdown ring animates from a single `Animatable` read inside a draw lambda: redrawn
  per frame on the GPU, never re-composed, never re-laid-out. Press feedback works the same
  way via `graphicsLayer`.
- Dark-first with a true-black option — on OLED a black pixel is an off pixel.
- Numbers that change in place use tabular figures, so digits don't shuffle sideways as they
  tick.

## Bringing your Hevy history over

Settings → Import from Hevy, and pick the CSV from Hevy's *Settings → Export Data*. Sessions,
sets and RPE all come across (RPE is converted to reps-in-reserve), PRs and estimated 1RMs are
recalculated as it goes, and re-importing the same file will not duplicate anything.

The CSV parser is RFC 4180-proper because Hevy's exports contain free-text notes with commas,
quotes and newlines in them — splitting on `,` silently shifts every later column. Exercises
not in the library are created and classified by name; anything the classifier isn't confident
about is listed for you to correct rather than filed silently.

## Build and install

Needs JDK 17+ and the Android SDK. Plug your phone in with USB debugging on:

```bash
./gradlew installDebug          # build and install straight to the device
./gradlew assembleDebug         # just the APK -> app/build/outputs/apk/debug/
./gradlew test                  # unit tests
```

For a release build, add a signing config to `app/build.gradle.kts` — `assembleRelease`
currently produces an unsigned APK (~2.7 MB after R8).

Minimum Android 11 (API 30), targets API 35.

## How it is put together

Single-module, single-activity Compose. Kotlin 2.0, Room, DataStore, Navigation Compose.

```
domain/          the parts with no Android in them, and the parts worth testing
  progression/   ProgressionEngine (loads and reps), VolumeAutoregulator (set counts)
  volume/        per-muscle MV/MEV/MAV/MRV landmarks and the weekly ramp
  gym/           which exercises a given gym can actually perform
  model/Load     unit conversion and per-implement, per-unit load increments
  mesocycle/     MesocycleGenerator — split layout, volume allocation, exercise selection
  model/         enums and the 1RM maths
data/
  db/            Room entities, DAOs, converters
  seed/          147 seeded exercises, plus gym presets
  importer/      RFC 4180 CSV reader, Hevy importer, name classifier
  repo/          repositories; WorkoutRepository.prescribe() is where a day becomes targets
ui/              theme and motion system, shared components, one package per screen
timer/           the rest timer, its alarm receiver and notification
```

Dependencies are wired by hand in `AppContainer` rather than by a DI framework — for a
single-user app it is less machinery, and it keeps the build to one annotation processor.

## Not done yet

- **Wear OS companion.** The real zero-tap story: log a set from your wrist. The data model
  is ready for it; the module is not written.
- **Editing a generated program** from the UI (swap or add an exercise). The repository
  methods exist, the screen does not.
- **Health Connect** export of sessions.
- **Plate calculator**, supersets, myo-reps and drop sets. `SetType` already carries them;
  nothing logs them yet.
- **Personal landmark learning.** `PersonalLandmarkEntity` is read everywhere it should be,
  but nothing writes to it yet, so landmarks are still the population defaults.
- **Bodyweight logging UI** — repository and table exist, no screen.
- **Light theme.**
- **Per-exercise gym notes.** The schema carries notes against an exercise at a gym ("this
  leg press starts at 60"), and the session screen does not surface them yet.
- **Baseline profile.** `profileinstaller` ships, but no profile is generated yet.

## Tests

71 unit tests over the progression engine, the unit/loading model, volume ramp and
autoregulator, the generator, gym availability, the CSV parser and the Hevy importer's date
handling. `./gradlew test`.

Several exist because they caught real bugs: a classifier rule order that filed every imported
leg curl as a biceps exercise (and, later, pike push-ups as chest and reverse Nordics as
hamstrings), and a 1RM blend that ran the wrong way and inflated high-rep sets.

Database migrations are checked separately, without a device:

```bash
python3 tools/verify_migration.py 1 2
```

It rebuilds the old schema in real SQLite, applies the migration exactly as the Kotlin does,
and diffs columns, primary keys, foreign keys and indices against Room's own exported schema
for the new version — plus the data rewrites. Room validates the schema when it opens the
database, so a migration that merely looks right is one column-order mismatch away from an
app that will not launch.
