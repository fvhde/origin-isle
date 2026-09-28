# Origin Isle
<div align="center">
  
[![Release](https://img.shields.io/github/v/release/fvhde/origin-isle)](https://github.com/fvhde/origin-isle/releases)
[![Downloads](https://img.shields.io/github/downloads/fvhde/origin-isle/total)](https://github.com/fvhde/origin-isle/releases)

Brings the OriginOS **island** to *every* app: downloads, navigation, calls, payments, media and
live football scores, including from European and international apps.

**Credits** · OriginIsland api by [@theVakhovskeIsTaken](https://github.com/theVakhovskeIsTaken)
· Navigation card by [@Martimborgss](https://github.com/Martimborgss)

**Support** · [![Buy Me a Coffee](https://img.shields.io/badge/Buy%20me%20a%20coffee-FFDD00?logo=buymeacoffee&logoColor=black)](https://buymeacoffee.com/fvhde)

</div>

<table align="center">
  <tr>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/island-navigation.jpg" width="250"><br>
      <sub><b>Navigation</b></sub>
    </td>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/island-payment.jpg" width="250"><br>
      <sub><b>Payment</b></sub>
    </td>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/island-call.jpg" width="250"><br>
      <sub><b>Call</b></sub>
    </td>
  </tr>
  <tr>
    <td align="center" valign="top">
      <img src="docs/screenshots/media.jpg" width="250"><br>
      <sub><b>Media</b></sub>
    </td>
    <td align="center" valign="middle" colspan="2">
      <img src="docs/screenshots/island-football.jpg" width="520"><br>
      <sub><b>Live football</b></sub>
    </td>
  </tr>
</table>

## Features

- 🏝️ **Any notification** → island, with an allow/deny list per app
- 👆 **Tap a card** to open the original notification
- ⚽ **Live football scores** with team crests
- 💳 **Apple Pay–style payment animation** (Google Wallet, Revolut, PayPal, N26, Monzo…)
- 🎵 **Media controls** wired to the real app
- 📋 **Log tab** showing what was cast or skipped, and why

> [!WARNING]
> **vivo/iQOO + OriginOS 6 only.** Enable **OriginOS Navigation** first, and uninstall the real
> **AMap (高德地图)** app if you have it ([why?](docs/DEV.md)).

<details>
<summary>Where to enable OriginOS Navigation</summary>
<br>
<img src="docs/screenshots/pre-req.jpg" width="300">
</details>

## Install

1. Download the APK from **[Releases](https://github.com/fvhde/origin-isle/releases)** and install it.
2. Open Origin Isle and grant **notification access** (required).
3. Test with a sample card in the **Cast** tab.

> [!TIP]
> For reliable background running, also enable:
> **Battery → Unrestricted** · **Auto-start → Allow** · **Accessibility → Origin Isle keep-alive**

<details>
<summary><b>Verify your download</b></summary>

```sh
shasum -a 256 origin-isle-<version>.apk   # must match the .sha256 file
apksigner verify --print-certs origin-isle-<version>.apk
```
Expected certificate:
```
5D:2D:FA:7E:F6:A9:6B:95:4A:C2:43:61:A3:30:BA:9B:2C:EA:45:18:C5:B8:63:58:C9:F4:FF:B1:1D:79:E6:00
```
Doesn't match? **Don't install.**
</details>

<details>
<summary><b>App screenshots</b></summary>
<br>

| Cast | Apps | Log |
|:-:|:-:|:-:|
| <img src="docs/screenshots/cast.jpg" width="240"> | <img src="docs/screenshots/apps.jpg" width="240"> | <img src="docs/screenshots/log-tab.jpg" width="240"> |
</details>

<details>
<summary><b>Developers</b></summary>

Building, the AMap package spoof, and signing: [docs/DEV.md](docs/DEV.md)

> Switching between a self-built and an official APK requires uninstalling first (different
> signatures), so you'll need to re-grant permissions afterward.
</details>

---

**License** · Source-available, no redistribution. See [LICENSE](LICENSE)
