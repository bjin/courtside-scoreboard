# Contributor and agent rules

Courtside is a landscape Android badminton scoreboard with touch and standard phone-media
controls. Read [README.md](README.md) for user behavior and [DEVELOPMENT.md](DEVELOPMENT.md)
for architecture, builds, tests, signing, versioning, releases, and debugging. Device support
claims must follow the evidence in [docs/watch-compatibility.md](docs/watch-compatibility.md).

## Working rules

- Keep changes focused and reuse existing patterns. Put scoring/input logic in JVM-testable
  `core/` code; keep Android glue thin. Update affected tests and documentation together.
- Preserve user changes. Never commit, tag, push, publish a release, or change repository
  visibility without explicit authorization. Commit only verified, buildable snapshots.
- Keep credentials and temporary artifacts out of the repository and logs. Never expose
  signing secrets to pull-request code. Follow the release process in DEVELOPMENT.md.
- First-party code and documentation use MIT, with copyright holder
  `Bin Jin <bjin@protonmail.com>`. Preserve B612's OFL and all third-party notices.

## App invariants

- No network access, accounts, analytics, ads, or additional runtime permissions.
- Scores cap at 99. History belongs to teams, survives restarts, and follows teams when ends
  swap. All state changes go through the controller on the main thread.
- Only deliberate, clean single-finger taps score; preserve edge, control, message, drag,
  duration, and multi-touch protections.
- Reset clears undo history: require a hold on screen or two separate guarded remote inputs.
  Bursts and held keys must not accidentally confirm reset.
- Keep physical left/right semantics, equal digit scale, cutout/corner safety, serving tint,
  immediate score metadata updates, and guarded exit behavior.
- Preserve direct media-button handling, session lifecycle, and foreground/background volume
  distinctions. Remote reset must not leave the phone's real media volume changed.
- Keep system fonts for UI text, the bundled B612 subset for digits, and locale/accessibility
  behavior consistent. Do not claim untested hardware compatibility as fact.
