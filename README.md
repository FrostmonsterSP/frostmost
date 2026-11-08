# FrostMost

**FrostMost is a graphical-only fork of [v2rayNG](https://github.com/2dust/v2rayNG).**
Only the user interface, typography, icons and branding differ. The V2Ray/Xray core, the networking
engine, the protocol and routing code, subscription handling, and the import/export formats are
untouched upstream code.

**FrostMost — исключительно графический форк [v2rayNG](https://github.com/2dust/v2rayNG).**
Отличаются только интерфейс, типографика, иконки и брендинг. Ядро V2Ray/Xray, сетевой движок,
протоколы и маршрутизация, работа с подписками и форматы импорта/экспорта — неизменённый
апстримный код.

---

## What this fork changes / Чем этот форк отличается

### Changed / Изменено

- **Interface.** The screens are rebuilt on a shared design system (`ui/compose/Frost*.kt`): surfaces
  that always pair a background with its foreground, one typography scale, closed spacing, radius and
  elevation scales. Text can no longer end up invisible or black-on-black.
- **Branding.** Own app name (`FrostMost`), own launcher icon, own notification and quick-settings
  glyph, own brand mark in the top bar.
- **Application identity.** A different application id, so FrostMost installs **alongside** v2rayNG
  instead of replacing it. Deep links use its own scheme.
- **Locales.** Only English and Russian are built in.
- **Home screen.** Subscription cards with traffic and expiry rails, swiping between subscriptions,
  and in-place server and routing configuration.

### Not changed / Не изменено

- **Core.** The native library, the tunnel, the protocols and the routing engine are the upstream
  ones. A bug in the proxy core is an upstream bug, and so is its fix.
- **Data model.** Servers, groups, subscriptions and routing are stored and imported/exported in the
  upstream formats. FrostMost is not a new client with its own storage.
- **Direction of work.** This fork follows upstream. The point of the fork is the look, not a
  different product.

### Please / Пожалуйста

- **Report interface bugs here**, to this repository.
- **Report protocol, connectivity or core bugs [upstream](https://github.com/2dust/v2rayNG/issues)** —
  they are not introduced here and are not fixed here.
- **Do not treat a FrostMost build as official v2rayNG.** It is not released by, endorsed by, or
  signed with the keys of the v2rayNG project.

---

## Download / Скачать

FrostMost releases / Релизы FrostMost:

**https://github.com/FrostmonsterSP/frostmost/releases**

Pick the APK matching your device ABI (`arm64-v8a` for almost every phone). The `universal` APK runs
anywhere but is larger. Files with `-fdroid` in the name are the F-Droid-flavoured build; the in-app
updater selects the right one automatically.

Выбирайте APK под архитектуру устройства (`arm64-v8a` подходит почти любому телефону). `universal`
работает везде, но больше по размеру. Файлы с `-fdroid` в имени — сборка варианта F-Droid; встроенный
апдейтер выбирает нужный сам.

> [!IMPORTANT]
> FrostMost has its own signing keys and is **not** signed with an upstream v2rayNG key.
> A FrostMost APK does not upgrade an installed v2rayNG, and a v2rayNG APK does not upgrade an
> installed FrostMost: they are separate applications with separate application ids.

> [!IMPORTANT]
> У FrostMost собственные ключи подписи, и он **не** подписан ключом апстрима v2rayNG.
> APK FrostMost не обновляет установленный v2rayNG, и наоборот — это разные приложения с разными
> application id.

For the upstream client and its releases, see [https://github.com/2dust/v2rayNG](https://github.com/2dust/v2rayNG).

Апстримный клиент и его релизы — здесь: [https://github.com/2dust/v2rayNG](https://github.com/2dust/v2rayNG).

---

## GPG Verification / Проверка подписи GPG

FrostMost release files are signed with a GPG key owned by this fork, to verify authenticity and
integrity and to protect against mirror, ISP or CDN tampering. It is **not** the upstream v2rayNG
key: a signature published here means "this file came from FrostMost" and nothing more.

Релизные файлы FrostMost подписаны GPG-ключом этого форка — для проверки подлинности и целостности
и для защиты от подмены на зеркале, у провайдера или в CDN. Это **не** ключ апстрима v2rayNG: подпись
здесь означает «файл выпущен FrostMost» и ничего больше.

### Fingerprint / Отпечаток ключа

```text
1CCA 33F5 E8A6 7CC3 62E2 14EC 77A5 DACA
C461 9451
```

Key: `FrostMost Releases <frostmost@depworks.pro>`, Ed25519, no expiry.

Ключ: `FrostMost Releases <frostmost@depworks.pro>`, Ed25519, без срока действия.

> [!CAUTION]
> This key signs release files only. Verify that the fingerprint printed by `gpg --verify` matches
> the block above character for character. The fork also has a **separate** Android signing key for
> the APK itself, and neither of them is the upstream v2rayNG key.
>
> Этот ключ подписывает только релизные файлы. Сверьте отпечаток, который печатает `gpg --verify`,
> с блоком выше посимвольно. У форка есть ещё **отдельный** ключ подписи Android для самого APK, и
> ни один из них не является ключом апстрима v2rayNG.

### Verifying a release / Как проверить релиз

Every APK is published next to a detached signature (`<apk>.sig`) and the fork's public key
(`frostmost-public-key.asc`).

Каждый APK публикуется вместе с отсоединённой подписью (`<apk>.sig`) и открытым ключом форка
(`frostmost-public-key.asc`).

```sh
gpg --import frostmost-public-key.asc
gpg --verify frostmost_0.9.0rc_arm64-v8a.apk.sig frostmost_0.9.0rc_arm64-v8a.apk
```

A good result prints `Good signature` and the fingerprint above. If the fingerprint differs, stop:
the file is not a FrostMost release.

Хороший результат печатает `Good signature` и отпечаток выше. Если отпечаток отличается —
остановитесь: файл не является релизом FrostMost.

This GPG signature is separate from the APK signing scheme. FrostMost APKs also carry the fork's own
Android signing key, which is why FrostMost cannot be installed over v2rayNG or updated by a v2rayNG
build.

Подпись GPG — отдельная от подписи APK. APK FrostMost подписаны ещё и собственным ключом Android
форка, поэтому FrostMost нельзя установить поверх v2rayNG или обновить сборкой v2rayNG.

---

## Geoip and Geosite / Geoip и Geosite

- geoip.dat and geosite.dat files are in `Android/data/pro.depworks.frostmost/files/assets` (the path may differ on some Android devices)
- the in-app download fetches the enhanced version from [this repo](https://github.com/Loyalsoldier/v2ray-rules-dat) (needs a working proxy)
- the latest official [domain list](https://github.com/Loyalsoldier/v2ray-rules-dat) and [IP list](https://github.com/Loyalsoldier/geoip) can be imported manually
- a third-party dat file can be placed in the same folder, for example [h2y](https://guide.v2fly.org/routing/sitedata.html#%E5%A4%96%E7%BD%AE%E7%9A%84%E5%9F%9F%E5%90%8D%E6%96%87%E4%BB%B6)

- Файлы geoip.dat и geosite.dat лежат в `Android/data/pro.depworks.frostmost/files/assets` (на некоторых устройствах путь может отличаться)
- Загрузка в приложении берёт расширенную версию из [этого репозитория](https://github.com/Loyalsoldier/v2ray-rules-dat) (нужен рабочий прокси)
- Актуальные официальные [список доменов](https://github.com/Loyalsoldier/v2ray-rules-dat) и [список IP](https://github.com/Loyalsoldier/geoip) можно импортировать вручную
- В ту же папку можно положить сторонний dat-файл, например [h2y](https://guide.v2fly.org/routing/sitedata.html#%E5%A4%96%E7%BD%AE%E7%9A%84%E5%9F%9F%E5%90%8D%E6%96%87%E4%BB%B6)

Upstream documentation lives in the [upstream wiki](https://github.com/2dust/v2rayNG/wiki).

Документация апстрима — в [вики апстрима](https://github.com/2dust/v2rayNG/wiki).

---

## Development guide / Разработка

- The Android project is under `V2rayNG/` and builds with the Gradle wrapper or Android Studio.
- `V2rayNG/app/libs/libv2ray.aar` is built from
  [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) and is not modified by this fork.
  For a quick start on rebuilding it, see [Go Mobile](https://github.com/golang/go/wiki/Mobile) and
  [Makefiles for Go Developers](https://tutorialedge.net/golang/makefiles-for-go-developers/).
- `V2rayNG/app/libs` (the HEV tunnel libraries) is generated by `compile-hevtun.sh` from the
  `hev-socks5-tunnel` submodule. Run it before building a clean checkout.
- FrostMost runs on Android emulators. For WSA, grant the VPN permission with
  `appops set pro.depworks.frostmost ACTIVATE_VPN allow`.

- Android-проект находится в `V2rayNG/` и собирается через Gradle wrapper или Android Studio.
- `V2rayNG/app/libs/libv2ray.aar` собирается из
  [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) и этим форком не изменяется.
  Быстрый старт по его пересборке — [Go Mobile](https://github.com/golang/go/wiki/Mobile) и
  [Makefiles for Go Developers](https://tutorialedge.net/golang/makefiles-for-go-developers/).
- `V2rayNG/app/libs` (библиотеки туннеля HEV) генерируется скриптом `compile-hevtun.sh` из
  сабмодуля `hev-socks5-tunnel`. Запустите его перед сборкой чистого клона.
- FrostMost запускается на эмуляторах Android. Для WSA выдайте разрешение VPN командой
  `appops set pro.depworks.frostmost ACTIVATE_VPN allow`.

---

## Community / Сообщество

FrostMost follows upstream, so protocol and core discussion belongs upstream.

FrostMost следует за апстримом, поэтому обсуждение протоколов и ядра — на стороне апстрима.

- Telegram Group: [https://t.me/v2rayN](https://t.me/v2rayN)
- Telegram Channel: [https://t.me/github_2dust](https://t.me/github_2dust)

---

## License / Лицензия

FrostMost is a fork of v2rayNG and keeps its license (GPL-3.0, see [LICENSE](LICENSE)). Copyright in
the upstream code remains with the v2rayNG project and its contributors.

FrostMost — форк v2rayNG и сохраняет его лицензию (GPL-3.0, см. [LICENSE](LICENSE)). Авторские права
на апстримный код остаются у проекта v2rayNG и его участников.
