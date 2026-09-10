# Origin Isle

**Bring the OriginOS island to every app.**

Origin Isle brings OriginIsland supports to all your apps. 
It watches your notifications and re-casts them onto the same island, so downloads,
turn-by-turn navigation, calls, payments, media playback and live football scores from
your everyday apps — including European and international ones — show up there too.

This is a hobby project, not an official vivo/iQOO/OriginOS product.

## Screenshots

| Cast tab                               | Apps tab (allow/deny list)             | Log tab                                  |
|----------------------------------------|----------------------------------------|------------------------------------------|
| <img src="docs/screenshots/cast.jpg" width="300"> | <img src="docs/screenshots/apps.jpg" width="300"> | <img src="docs/screenshots/log-tab.jpg" width="300"> |


| Island — navigation | Island — payment | Island — call |
| --- | --- | --- |
| ![Navigation card on the island](docs/screenshots/island-navigation.jpg) | ![Payment card on the island](docs/screenshots/island-payment.jpg) | ![Call card on the island](docs/screenshots/island-call.jpg) |

| Island — football score                                                       |
|-------------------------------------------------------------------------------|
| ![Compact football score on the island](docs/screenshots/island-football.jpg) |

| Island - mimic OriginPlayer                 |
|---------------------------------------------|
| <img src="docs/screenshots/media.jpg" width="300"> |

## What it does

- **Casts any notification** to the island
- **You choose which apps get to use it.**
- **A log you can check.** The Log tab shows every notification the app saw, whether it was cast to
  the island or skipped (and why).
- **Tap a card to jump straight to that notification** in the original app.
- **Live football scores** with real team crests, laid out to fit whatever room is left on the
  island.
- **Apple-Pay-style payment cards** — a short "Processing… → Paid ✓" animation for Google Wallet/Pay,
  Revolut, PayPal, N26, Monzo, Starling and similar apps.
- **Media controls on the island** — previous/play-pause/next for whatever's playing, wired back to
  the real app.

## Before you install:

- **This only works on vivo/iQOO phones running OriginOS 6.** .
- **Enable OriginOS Navigation first.**
  
  <img src="docs/screenshots/pre-req.jpg" width="300">
- **It can't be installed alongside the real AMap (高德地图) app.** See [docs/DEV.md](docs/DEV.md) for the full explanation.
- **It reads your notifications**, so ONLY install it from this repo's Releases page.

## Installing

1. Go to this repo's **[Releases](https://github.com/fvhde/origin-isle/releases)** page and download the latest
   `origin-isle-<version>.apk` **and** its matching `.sha256` file.
2. **Check the checksum** (this confirms the file wasn't corrupted or swapped for something else):
   - On a Mac or Linux computer, open Terminal in the download folder and run:
     ```
     shasum -a 256 origin-isle-<version>.apk
     ```
   - Compare the result to the contents of the `.sha256` file you downloaded — they should match
     exactly.
3. **On your phone**, allow installing apps from this source when prompted (Android will ask the
   first time), then open the downloaded APK to install it.
4. **Open Origin Isle.** The onboarding screen walks you through the permissions it needs.
   Notification access is the only one that's required to continue — the rest are optional but make
   things work better (see below).
5. Try a sample card in the **Cast** tab first, to confirm the island lights up, before relying on
   real notifications.

### A few settings worth turning on

These aren't required, but each one fixes a real OriginOS quirk:

- **Battery → Unrestricted** for Origin Isle, so OriginOS doesn't kill it in the background to save
  power.
- **Accessibility → turn on "Origin Isle keep-alive"** — this is how the app runs without putting an
  icon in your status bar.
- **Settings → Battery → Auto-start → allow Origin Isle**, or it may get shut down whenever your
  screen turns off.

> If you ever switch from a version you built yourself (in Android Studio) to an official release
> APK, or the other way around, Android will make you uninstall the old one first — the signatures
> don't match. That also clears your notification-access and accessibility permissions, so just
> re-grant them after reinstalling.

## Verifying you have a genuine build

Because this app reads your notifications, it's worth confirming a downloaded APK actually came from
the maintainer rather than somewhere else. Every official release is signed with one private key that
never leaves the maintainer's machine. Besides matching the checksum (above), you can check the
signing certificate itself:

```
apksigner verify --print-certs origin-isle-<version>.apk
```

The output should show this fingerprint:

```
SHA-256: 5D:2D:FA:7E:F6:A9:6B:95:4A:C2:43:61:A3:30:BA:9B:2C:EA:45:18:C5:B8:63:58:C9:F4:FF:B1:1D:79:E6:00
```

(`apksigner` ships with the Android SDK build-tools — this check is really only practical if you
already have Android tooling installed. The checksum comparison above is the check anyone can do.)

If either the checksum or the certificate fingerprint doesn't match, **don't install the file** — it
isn't an official build.

## For developers

Want to build it yourself, understand the AMap package-name spoof, or cut a signed release? See
[docs/DEV.md](docs/DEV.md).

## Contributors

- [Martim Borges](https://github.com/Martimborgss) — the driving-navigation card (vivo's template 9)
  and the fix for Maps showing a bare progress ring instead of the distance/ETA.

## Support

If this is useful to you, you can [buy me a coffee](https://buymeacoffee.com/fvhde).

## License

See [LICENSE](LICENSE) — a **source-available, no-redistribution** license. In short: you can read
the code and build it for your own devices, but you can't redistribute the source, modified versions,
or any build of it (signed or not) to anyone else.
