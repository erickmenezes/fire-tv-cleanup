# Examples

One folder per TV model, each with a `debloat.txt` listing the packages that are safe to disable
on that firmware, grouped and commented. These are starting points: comment out anything you use.

| Folder | TV | OS | Notes |
|---|---|---|---|
| `xiaomi-mi-tv-4a/` | Xiaomi Mi TV 4A (MiTV-MOOQ1) | Android TV 10 | PatchWall, Mi analytics, SmartShare, MiLink |
| `hisense-fire-tv-AFTOLCBC82/` | Hisense Fire TV Edition (AFTOLCBC82) | Fire OS 8 (Android 11) | Ads and telemetry only |
| `fire-tv-stick-AFTSS/` | Fire TV Stick 3rd gen / Lite (AFTSS), an HDMI stick | Fire OS 7.7 (Android 9) | Ads, telemetry, Alexa, unused Amazon apps, Live TV |

To add your TV, see [CONTRIBUTING.md](../CONTRIBUTING.md). The quickest way to get a list is to
run the prompt in [PROMPT.md](../PROMPT.md) and copy the packages your assistant disabled.
