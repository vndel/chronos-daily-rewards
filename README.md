# chronos-daily-rewards

![Java](https://img.shields.io/badge/Java_21-ED8B00?logo=openjdk&logoColor=white) ![Paper](https://img.shields.io/badge/Paper_1.20%2B-0D7E84?logo=minecraft&logoColor=white) ![Maven](https://img.shields.io/badge/Maven-C71A36?logo=apachemaven&logoColor=white) ![License](https://img.shields.io/badge/License-MIT-green)

> Streak rewards with correct timezone handling and offline UUID resolution.

## The actual hard part

Not the rewards — the day boundary.

The common implementation is `System.currentTimeMillis() / 86_400_000`, which
rolls over at **UTC midnight**. For a regional community that lands in the
middle of prime time, so players claim, log off, and claim again two hours later.

This resolves day boundaries in a configured `ZoneId`:

```java
LocalDate today = LocalDate.now(zone);
boolean consecutive = streak.lastClaim().plusDays(1).equals(today);
```

Using `LocalDate` rather than a timestamp also means daylight saving transitions
are handled by `java.time` instead of by arithmetic, and that stored state does
not silently change meaning if the server's zone changes.

## Streak rules

| Situation | Result |
|---|---|
| Claimed today already | Rejected, shows time to local midnight |
| Last claim was yesterday | Streak continues |
| Gap within `grace-days` | Streak continues (one missed day does not erase a month) |
| Larger gap | Streak resets to 1, best streak preserved |

Tiers are `streak >= N` thresholds and the **highest** match wins, so a 30-day
streak never silently falls back to the day-1 reward.

## Persistence

YAML, written only when state actually changed, on an async autosave task plus a
synchronous flush on disable. Malformed UUIDs and dates are logged and skipped
rather than aborting the whole load.

## Configuration

```yaml
timezone: 'Asia/Riyadh'      # any IANA zone id
streak:
  grace-days: 1
tiers:
  7:
    name: 'Weekly Dedication'
    commands:
      - 'give %player% diamond 3'
      - 'ecoadmin give %player% 2500'
```

Placeholders: `%player%`, `%uuid%`, `%tier%`.

## Measured impact

| Scenario | Metric | Before | After |
|---|---|---|---|
| Day rollover | boundary | UTC midnight | configured local midnight |
| `/daily` call | file I/O | per claim | none (in-memory + autosave) |
| Offline inspect | resolution | online only | offline UUID lookup |

Reward commands are always dispatched on the main thread, since command
execution touches world and inventory state.

## Build

```bash
mvn clean package
# target/chronos-daily-rewards-1.0.0.jar
```

Drop the jar in `plugins/`, start the server once to generate `config.yml`,
then adjust and run `/chronos reload` where supported.

## Verification

```bash
./verify.sh          # builds, runs tests, checks bytecode version
```

**7 unit tests** cover the streak transitions and the date arithmetic that the
plugin exists to get right:

- A UTC day boundary and an `Asia/Riyadh` boundary genuinely differ at 22:30Z —
  the test asserts both, which is the whole reason a `ZoneId` is configurable.
- Month end, year end and the 2024 leap day all advance by exactly one day.
- A DST spring-forward resolves rather than producing an invalid local time.
- A broken streak resets to 1 while preserving the previous best.

## License

MIT — see [LICENSE](LICENSE).
