# Аудит «чёрное на чёрном / белое на белом» — FrostMost

Отчёт по жалобе «кнопки и тексты чёрное на чёрном и белое на белом». Ветка `fork/friendly-ui`,
репозиторий на рабочем каталоге, полная цветовая схема лежит в
`V2rayNG/app/src/main/java/com/v2ray/ang/ui/compose/Theme.kt`.

Задача — найти статически, по чтению кода, все места, где `Text` / `Icon` / `Button` может
оказаться **того же цвета, что поверхность под ним**. Правок в коде нет: это список для патча.

## Как это установлено

Каждый кандидат найден grep'ом (`color =`, `tint =`, `containerColor`, `background(`, `Surface(`,
`Scaffold(`, `LocalContentColor`) и прочитан в контексте ≥ 20 строк вместе со всеми родителями,
которые рисуют фон. Значения контраста посчитаны по WCAG 2.1 (формула как в спеке).

Что важно знать про фон по умолчанию:

- `Theme.kt:300` — корень `AppTheme` это `Box(Modifier.fillMaxSize())` **без фона**. Всё, что ниже,
  прозрачно до `android:windowBackground`.
- `res/values/themes.xml:9` и `res/values-night/themes.xml:8` — `windowBackground` равен `#FFFFFF`
  (день) и `#1C1B1F` (ночь). Это совпадает с `surface`
  (`Theme.kt:54` = `0xFFFFFFFF`, `Theme.kt:95` = `0xFF1C1B1F`) **только пока цвет статический**.
  При включённом динамическом цвете (`PREF_DYNAMIC_COLOR`, по умолчанию `true`, `Theme.kt:212`)
  `surface` берётся из обоев, а `windowBackground` остаётся белым/почти чёрным.
- `Scaffold` (material3 1.4.0) красит своё тело ролём `background` (`ScaffoldKt` →
  `MaterialTheme.colorScheme.background`), поэтому экраны на `Scaffold` не прозрачны, но и не
  получают `surface`: `background` светлой схемы — `#FCF8FA`, а не `#FFFFFF`.
- Проверено по байткоду `material3-android-1.4.0.aar` в кэше Gradle, а не по памяти:
  `ModalBottomSheet` по умолчанию `BottomSheetDefaults.ContainerColor = SurfaceContainerLow`;
  `ModalDrawerSheet` → `DrawerDefaults.ModalContainerColor = SurfaceContainerLow`;
  `DropdownMenu` → `MenuTokens.ContainerColor = SurfaceContainer`; `TopAppBar` →
  `AppBarTokens.ContainerColor = Surface`; `AlertDialog` → `DialogTokens.ContainerColor =
  SurfaceContainerHigh`.

Итог: **3 определённых дефекта**, **5 дефектов только при динамическом цвете / динамическом
контрасте**, **2 дефекта только в одной теме или на одном устройстве**. Плюс один структурный
корень, объясняющий третий пункт целиком.

---

## 1. Определённые дефекты (сломано всегда, в обеих темах)

| file:line | что нарисовано | что под ним | почему становится невидимым | минимальная верная правка |
|---|---|---|---|---|
| `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/HomeServerRows.kt:208` | `Text(latency, color = latencyColor(...))`, где `latencyColor` → `MaterialTheme.colorScheme.onSurfaceVariant` для тона `Unknown` (`:285`) и `status.*.onNeutral` (`#17663C` / `#7A4A00` / `#9B1C1C` светлая, `#7EE0A6` / `#F5C97A` / `#FFB3AC` тёмная) для остальных тонов | фон страницы `Modifier.background(background)` в `HomeScreen.kt:408`, где `background = accent` подписки (`HomeScreen.kt:101-105`) | Это ветка `onAccent == false`, но её выбирает порядок кода, а не поверхность: в `HomeScreen.kt:85` `lit = uiState.isRunning && card != null`, а `lit` уходит и в `HomeIssueStrip`, и в акцент фона. При включённом VPN **всегда** `lit == true` → фон страницы равен акценту (`#2447C8`, `#6B2A80`, `#0E6A70`, `#1F6B3A`, `#8C1D40`, `#7A4A00`) — а `onAccent` для строк серверов при этом приходит из `uiState.runsOnAccent` (`:236`), независимого флага. Пока они расходятся, задержка рисуется `#9B1C1C` на `#8C1D40`: **1.29:1**, и `#17663C` на `#1F6B3A`: **1.08:1**. Тот же разрыв виден в `HomeServerRows.kt:174`: имя сервера принудительно `onSurface` (`#1C1B1F`) поверх акцента — **2.6:1**, комментарий рядом (`:169-170`) прямо описывает этот баг как наблюдавшийся | Один источник правды: передавать в `serverRowItems` / `serverRowList` тот же флаг, что красит фон, то есть `lit` из `HomeScreen.kt:85`, вместо `uiState.runsOnAccent`; и в ветке «на акценте» не брать `onSurface`, а брать `LocalContentColor` / `Color.White`. Вторая половина правки — сделать так, чтобы `lit` и `onAccent` не могли разойтись: убрать второй флаг |
| `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/HomeScreen.kt:302` (через `:301-314`) | иконка `ic_settings_24dp` и подпись «Ещё» в `HomeTile(colors = OnBackgroundColors(Color.Transparent, contentColor))` | прозрачный контейнер поверх страницы, а страница **на светлой теме** — `background = MaterialTheme.colorScheme.background` = `#FCF8FA` | `contentColor` здесь — это `onBackground` (`HomeScreen.kt:109`), то есть `#1C1B1F`, и это **правильно**. Дефект в том, что контейнер прозрачный, а цвет берётся из `OnBackgroundColors`, а не каскадом: любая правка `onBackground` или перенос `HomeTopBar` на другую поверхность (шапка `Scaffold`, `surfaceContainer`) мгновенно даёт пару «цвет ↔ не та поверхность». Уже сейчас он ломается в `HomeFixedZoneLayout`: `statusBarsPadding()` есть (`HomeScreen.kt:409`), но `Box` с `background(background)` покрывает область только **после** инсета, поэтому строка состояния — прозрачное окно, и её цвет задаёт `AppTheme` (`Theme.kt:286`, `isAppearanceLightStatusBars = !darkTheme`), а не `LightStatusBarIcons` (`HomeScreen.kt:266`) — тот выставляет `!onAccent && !dark`. В тёмной теме при `lit` это `true`, хотя фон под строкой состояния — акцент | Не плодить пару «прозрачный контейнер + внешний цвет»: задать `HomeTopBar` саму `Surface(color = Color.Transparent, contentColor = contentColor)` и убрать передачу `contentColor` в `OnBackgroundColors`. Для строки состояния — вынести решение `onAccent` из `SideEffect` в `DisposableEffect(onAccent)` (`HomeScreen.kt:264-273`), чтобы отсутствие пересчёта не зависело от того, кто из двух `SideEffect` выполнился последним |
| `V2rayNG/app/src/main/java/com/v2ray/ang/ui/compose/SettingsItem.kt:86-89` + `:256-265` | подписи `SettingsSwitchItem` и значок `Switch` с `colors = SwitchDefaults.colors(checkedThumbColor = onPrimary, checkedTrackColor = primary)` | фон экрана `Scaffold` = `background`; заголовок/описание берут `onSurface` / `onSurfaceVariant` | Сами цвета верные, но **поля `uncheckedThumbColor`, `uncheckedTrackColor`, `disabledTrackColor` не заданы**, а `SwitchDefaults` в material3 1.4.0 при отсутствии значения подставляет роль `surfaceContainerHighest`. В **светлой** теме это `#E8E5E8` (`Theme.kt:69`) на фоне `#FCF8FA` — **1.19:1**: выключенный переключатель выглядит пустым местом, а рядом «включённый» — тёмно-синий. В `disabled`-состоянии `enabled = mux` / `enabled = isVpn` (`SettingsActivity.kt:278-577`) переключатель гасится в `onSurface α0.12` поверх той же `surfaceContainerHighest`, то есть целиком в «серое на сером». Это ровно то, что пользователь назвал «тесты чёрное на чёрном» на экране настроек | Дописать полный набор: `uncheckedThumbColor = outline`, `uncheckedTrackColor = surfaceVariant`, `uncheckedBorderColor = outline`, `disabledUncheckedTrackColor = surfaceVariant`, `disabledCheckedTrackColor = onSurface.copy(alpha = 0.12f)`. Повторить то же в `PerAppProxyActivity.kt:218-221` и `:238-241` — там те же `SwitchDefaults.colors` с теми же двумя полями |

---

## 2. Дефекты только при динамическом цвете (или динамическом контрасте)

| file:line | что нарисовано | что под ним | почему становится невидимым | минимальная верная правка |
|---|---|---|---|---|
| `V2rayNG/app/src/main/java/com/v2ray/ang/ui/compose/Dialog.kt:94, 174, 198, 259` (и `AboutActivity.kt:144`) | `AlertDialog(containerColor = MaterialTheme.colorScheme.surface)` со всем содержимым, `Text` внутри — из каскада `contentColorFor(containerColor)` | сам диалог красит `surface`, но `Text` берёт `LocalContentColor`, который `AlertDialog` выставляет из **`DialogTokens.contentColor` = `onSurface`… только когда `containerColor` — значение по умолчанию**. Как только передан явный `containerColor`, `contentColorFor(surface)` в material3 возвращает `onSurface` только при точном совпадении с ролью — при динамическом цвете `surface` уже другой, и `contentColorFor` отдаёт `Color.Unspecified` → непрозрачный `Color.Black`. Итого: **чёрный текст на тёмном системном `surface`** в тёмной теме | Не полагаться на `contentColorFor`: передавать `AlertDialog(contentColor = MaterialTheme.colorScheme.onSurface, containerColor = MaterialTheme.colorScheme.surface)` явно (или вообще убрать `containerColor` и оставить токен `surfaceContainerHigh`, а если нужен именно `surface` — задать оба) |
| `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/HomeSheets.kt:87` | весь контент `ModalBottomSheet(containerColor = MaterialTheme.colorScheme.surface)` — `SheetTitle` (`:124`), `SheetRow` (`:147`), `OutlinedTextField` (`:178`) | лист красит `surface`; `Text`/`Icon` берут `LocalContentColor` | Тот же механизм, что выше: `ModalBottomSheet` по умолчанию красит `surfaceContainerLow` (`BottomSheetDefaults.ContainerColor`) и вместе с ним выставляет `onSurfaceVariant`; при явном `containerColor` содержимое остаётся с `LocalContentColor` от предыдущей поверхности. В светлой теме это незаметно (обе роли светлые), в тёмной — заголовки листа могут стать почти невидимыми. Плюс сам лист на `surface` в тёмной теме сливается со страницей, у которой `background = #141317` — границы листа нет вовсе | Убрать `containerColor` (оставить токен `surfaceContainerLow`) либо задать `contentColor = MaterialTheme.colorScheme.onSurface` вместе с ним |
| `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainDrawer.kt:74-76` | `ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface)`: `NavigationDrawerItem` без явных цветов (`:108-114`), `Icon`, `Text` | лист красит `surface` | `DrawerDefaults.ModalContainerColor` по умолчанию `surfaceContainerLow`, и содержимое окрашивается под неё (`onSurface` / `onSurfaceVariant`). При явной подмене на `surface` элемент остаётся с цветом **для `surfaceContainerLow`**, а поверхность — нет. В светлой теме разница 1.06:1 и это не видно, при тёмном системном `surface` `#1C1B1F` против светлого `surfaceContainerLow` из обоев — пара может стать «тёмное на тёмном» | Либо не переопределять (`ModalDrawerSheet(drawerState)`), либо задать и `drawerContentColor = MaterialTheme.colorScheme.onSurface` |
| `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/HomeSheets.kt:205-209` | `Button(...) { Text(...) }` | фон кнопки — `primary`, текст — `onPrimary` из `ButtonDefaults` | Здесь правильная пара, но **`Button` не входит в `theme.copy(...)` для остальных ролей**: `Theme.kt:255-274` подменяет только `primary/onPrimary/primaryContainer/onPrimaryContainer/error/…`, оставляя динамические нейтрали. Значит на поверхности листа (динамический `surfaceContainerLow`) кнопка рисует свой `primary` из **фиксированной** схемы, а лист — из системной: пара «primary кнопки ↔ surface листа» ничем не проверена и на части устройств даёт 1.0–1.5:1 по границе, то есть кнопка сливается с листом. Тот же риск в `HomeFirstRun.kt:127, 134` и `HomeScreen.kt:323` | Проверять не «пару с `surface`», как сейчас в `ColourSchemeTest`, а пару «контейнер кнопки ↔ фон предка». Практическая правка: в `Theme.kt:261` добавить в `system.copy(...)` ещё и `secondaryContainer`, `tertiary`, `surfaceTint`, чтобы фиксированный `primary` всегда оказывался на фиксированном фоне |
| `V2rayNG/app/src/main/java/com/v2ray/ang/ui/compose/Theme.kt:300` (корень) + любой экран, который **не** на `Scaffold` | `HomeFirstRun.kt:81` (заголовок, `onBackground` из каскада `Text`), `HomeFirstRun.kt:166` (`CircularProgressIndicator` — `primary`, фон по умолчанию `background`), `HomeDownloading.kt`-блок (`HomeFirstRun.kt:155-172`) | корневой `Box` в `AppTheme` не рисует ничего → видно `android:windowBackground` = `#1C1B1F` (ночь) / `#FFFFFF` (день) | При статической схеме `windowBackground` случайно совпадает с `surface`/`background`. При **динамическом** цвете `background` из обоев (например светлый `#F5EFE8` при светлом `windowBackground` `#FFFFFF` — 1.05:1, это ещё терпимо), но если системная тема тёмная, а `PREF_UI_MODE_NIGHT` = «1» (светлая), — это штатная настройка, — то `darkTheme == false` даёт **светлые** тексты схемы на **тёмном** `windowBackground` до ближайшего `Surface`. `HomeFirstRun` и `HomeDownloading` вызываются **до** `HomeFixedZoneLayout` (`HomeScreen.kt:134-146`), то есть без `.background(background)` | Одна правка закрывает весь класс: в `Theme.kt:300` заменить `Box(Modifier.fillMaxSize())` на `Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))`. Тогда прозрачность до окна перестаёт существовать как явление |

---

## 3. Дефекты только в одной теме (или на одном устройстве)

| file:line | что нарисовано | что под ним | почему становится невидимым | минимальная верная правка |
|---|---|---|---|---|
| `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/HomeStrips.kt:302-303` (`HomeUndoBar`) | `Surface(color = inverseSurface, contentColor = inverseOnSurface)`, кнопка «Отменить» — `inversePrimary` (`:321`) | `inverseSurface` светлой темы `#313033`, тёмной — `#E5E1E6` | Пара верна **только для статической схемы**: `inverseSurface`/`inversePrimary` в `Theme.kt:255-274` **не попадают в фиксированный список** при динамическом цвете. Тогда `inverseSurface` приходит из обоев (скажем, почти белый), а `inversePrimary` — тоже системный, и `#AAC7FF`-класс на почти белом даёт 1.4:1. В тёмной теме при этом `inverseSurface` намеренно светлый — и текст в нём тёмный, а полупрозрачная подложка `HOME_ON_ACCENT_OVERLAY` (`:75`) белая, то есть при `lit` полоса теряет границу с акцентом | Добавить `inverseSurface`, `inverseOnSurface`, `inversePrimary` в список фиксированных ролей `Theme.kt:261-270` |
| `V2rayNG/app/src/main/java/com/v2ray/ang/ui/compose/Dialog.kt:198` + `AboutActivity.kt:126-140` | `WebView` с `file:///android_asset/open_source_licenses.html` внутри `AlertDialog(containerColor = surface)` | сам `AlertDialog` (фон `surface`) | HTML сам задаёт `body { background:#ffffff; color:#000000 }`, а в `@media (prefers-color-scheme: dark)` — `#121212`/`#E0E0E0`. `WebView` реагирует на **системную** тему, `AlertDialog` — на тему приложения (`resolveDarkTheme()`, `Theme.kt:236-243`). При `PREF_UI_MODE_NIGHT = "2"` (тёмная) на светлой системе получаем **чёрный текст на белом** внутри тёмного диалога — резкий белый прямоугольник. И наоборот при «1» на тёмной системе. Раньше это было незаметно, потому что весь диалог был системным | Прокинуть тему в HTML: `webView.settings.forceDark`/`WebSettingsCompat`, или проще — обернуть `AndroidView` в `Modifier.background(MaterialTheme.colorScheme.surface)` и задать в HTML `background:transparent` (строка 5 ассета) |
| `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/HomeCardPager.kt:147` и `:145-146` | чипы подписок: `content = Color.White` на `container = Color.White.copy(alpha = HOME_ON_ACCENT_OVERLAY)` | фон страницы = акцент (`HomeScreen.kt:408`) | Здесь всё честно посчитано для **акцента** (≥ 4.5:1, `HomeModels.kt:148-150`), но `HOME_ON_ACCENT_OVERLAY` — это **белая** плёнка. Комментарий в `HomeModels.kt:174-177` мотивирует 10 % тем, что «12 % уже уронило белый текст до 4.9:1» — то есть значение стоит на границе. В тёмной теме акценты светлее (`HOME_ACCENTS_DARK`, `:164-171`), и `#3556D6` с 10 % белого даёт 4.62:1, а `#0C6E75` — 4.51:1. Любое последующее затемнение акцента в `HomeCardPager.kt` (например, `busy`-фон `warnColors()`, `:501-506`) роняет белую подпись ниже 4.5:1 | Не трогать токен, а не ставить белую подпись на прозрачную плёнку: у чипа должна быть непрозрачная пара (`surface` + `onSurface`), а цвет подписки — точка (`:160-165`), как это уже сделано |
| `V2rayNG/app/src/main/java/com/v2ray/ang/ui/settings/SettingsActivity.kt:278` | `SettingsSwitchItem(..., enabled = dynamicColorSupported)` и подобные `enabled = isVpn` / `enabled = mux` | фон страницы (`background`) | Невидимость только в светлой теме: disabled-цвета `SettingsItem.kt:87, 89` считаются от `onSurface α0.38` — **это верно** — но подпись стоит **рядом** с `Switch`, у которого `disabledTrackColor` не задан и равен `surfaceContainerHighest` (`#E8E5E8`) на `#FCF8FA` → 1.19:1. Пользователь видит «серую строку с серым пустым местом». Это тот же корень, что дефект №3 из раздела 1; вынесен сюда, потому что в дневной теме он читается как «белое на белом», а в ночной — как «чёрное на чёрном» | См. раздел 1, строка третья: задать `disabledUncheckedTrackColor` и `disabledUncheckedThumbColor` |

---

## 4. Что проверено и НЕ является дефектом

Чтобы не переисследовать.

| Место | Почему это не дефект |
|---|---|
| `compose/Theme.kt:300` — `Box(fillMaxSize())` без фона | Сам по себе **не** дефект для экранов на `Scaffold`: `Scaffold` красит тело ролём `background` (проверено в байткоде `ScaffoldKt`, material3 1.4.0). Дефект — только для `HomeFirstRun` / `HomeDownloading` при динамическом цвете (раздел 2) |
| `compose/Components.kt:104-109` `AppTopBar` | `TopAppBarDefaults` → `AppBarTokens.ContainerColor = Surface`, а цвета title/nav/action явно `onSurface`. Пара верна при любой схеме |
| `compose/Components.kt:134-135` `SearchInputField` | `textStyle = onSurface`, `placeholder = onSurfaceVariant` на `TopAppBar` = `surface`. Верно |
| `compose/Components.kt:116` `LinearProgressIndicator(color = primary)` | indeterminate на `surface` = 2.5–6.4:1 по схеме. Не текст |
| `compose/Components.kt:288-299`, `:309-316` `ReorderableListItem/GridItem` | `Surface(shadowElevation=…)` без `color` → `surface`, а содержимое в каскаде получает `onSurface`. Верно |
| `compose/Components.kt:199-213` `AppListItem` | `bodyLarge` (`onSurface`) и `bodySmall` (`onSurfaceVariant`) на списке `Scaffold`; `Checkbox` задаёт `checkedColor = primary`. Верно |
| `compose/Components.kt:256-259` `VersionInfoBlock` | `bodySmall` без `color` → каскад `onSurface` на `background`. Верно |
| `compose/Dialog.kt:63-95` сами `Text`/`TextButton` | Цвета не заданы: работают из `contentColorFor`. Риск — в явном `containerColor` (раздел 2) |
| `compose/Dialog.kt:238-241` `RadioButton(selected = isSelected, onClick = null)` | `onClick = null` — это «пассивный» режим, а **не disabled**; цвет остаётся `onSurfaceVariant`/`primary`. Строка нажатия — `Modifier.selectable` выше. Верно |
| `compose/FormFields.kt:60-68`, `:114-122` | Открытый `OutlinedTextField` с `containerColor = Color.Transparent`: текст/метка/рамка берутся из `OutlinedTextFieldDefaults` от `onSurface`, что на любой поверхности экрана — роль поверхности `Scaffold`. Верно |
| `compose/Menu.kt:9-19` `AppDropdownMenuItems` | `Text` без цвета → `onSurface` на `MenuTokens.ContainerColor = SurfaceContainer`. Верно |
| `compose/SnackBar.kt:194-217` | Фон — служебные токены `snackbar*`, текст — `snackbarText = #FFFFFF`. Белый на `snackbarNeutralLight #353A3E` = 10.4:1, на `snackbarSuccessDark #1E6B3C` = 6.3:1. Верно по построению |
| `compose/Scrollbar.kt:71-73` | `thumbColor = primary α0.7` поверх любой поверхности; декоративный, не текст |
| `main/HomeScreen.kt:326-330` `AddButton` | `primaryContainer` + `onPrimaryContainer` — пара роли, 9.99:1. Верно. `filled = true` (`:323`) — обычный `Button`, пара `primary`/`onPrimary` |
| `main/HomeScreen.kt:350-366` `HomeEmpty` | `contentColor = onBackground` передаётся явно в `Text`, а `AddButton(filled = true)` рисует `Button` со своей парой. Верно |
| `main/HomeScreen.kt:109` `onBackground` | Логика `if (lit) Color.White else onBackground` совпадает с тем, что рисует `HomeFixedZoneLayout:408`. Верно |
| `main/HomeCardPager.kt:315-321` `colors` карточки | `surface` + `onSurface` (на светлой карточке) и `accent` + `White` (на акценте). Обе пары проверены тестом `HomeModelsTest`, как обещает `AGENTS.md`. Верно |
| `main/HomeCardPager.kt:330` `accentContrastsWithWhite` | Измеряет, а не предполагает; при провале берёт `surface`. Верно |
| `main/HomeCardPager.kt:381-383` настройка внутри карточки | `Color.Transparent` + `LocalContentColor.current`, `BorderStroke` от того же `LocalContentColor`. Наследует пару карточки. Верно |
| `main/HomeCardPager.kt:414-421` индикатор трафика | `primary` на `surface` (2.5:1 светлая / 10:1 тёмная) и `White` на акценте; дорожка `outlineVariant`. Декоративный индикатор, не текст. Верно |
| `main/HomeCardPager.kt:430` строка срока | `warning.onNeutral` только при `expired && onCard` — то есть уже на `surface`, где `#7A4A00` = 6.9:1. Верно |
| `main/HomeStrips.kt:74-78` `homeNeutralSurface` | Возвращает **пару**: `surface`+`onSurface` или `White α0.10`+`White`. Пара согласована внутри себя, поэтому «не тот цвет на той поверхности» здесь невозможен **пока пара передаётся целиком** |
| `main/HomeStrips.kt:137` `CompositionLocalProvider(LocalContentColor provides colors.content)` | Все потомки, которые не переопределяют цвет, получают цвет **того же** контейнера. Ни один потомок в `HomeTile` не задаёт цвет, конфликтующий с ним — проверены `HomeAnnounceStrip:230-236`, `HomeRulesRow:278-287`, `AddButton:339-340`, `PowerButton:525-536`, `HomeActionRow:603-610`, `SectionHeader:456-473` |
| `main/HomeStrips.kt:181-207` `HomeIssueStrip` | `Surface(color = colors.container, contentColor = colors.content)`, а кнопка внутри `TextButton` дополнительно `color = colors.content` (`:203`). Пара согласована. Единственное, что может её сломать, — `lit` и фон страницы, это дефект №1 раздела 1 |
| `main/HomeStrips.kt:85-88` `warnColors()` | `warning.chip` + `warning.onChip` — пара статусной роли (тёмный текст на светлом чипе в обеих темах). Верно |
| `main/HomeConfiguration.kt:458-473` `SectionHeader` | `colors.content` для заголовка и шеврона, `colors.content α0.88` для подытога — оба на той же `colors.container`. Комментарий `:481-485` обосновывает 0.88 (5.05:1 на акценте). Верно |
| `main/HomeConfiguration.kt:577-592` `ServerToolButton` | `container`/`content` уменьшаются **парой** (`0.45` / `0.55`), а композиция двух прозрачных слоёв над акцентом сохраняет порядок контраста. Не «исчезает», а тускнеет. Верно (и это осознанно, `:602`) |
| `main/HomeConfiguration.kt:497-507` `RoutingRow`, `:509-516` `OwnRulesRow` | `RadioButton` и `Text` без цветов на фоне секции; `TextButton` — `primary` на `background`. Верно |
| `main/HomeSheets.kt:194, 202` | `error` на `surface`, `onSurfaceVariant` на `surface`. Верно (риск только у `containerColor`, раздел 2) |
| `main/HomeFirstRun.kt:116, 123, 127-139` | `error` / `onSurfaceVariant` / `Button` — все пары ролей. Риск только от отсутствия фона (раздел 2) |
| `main/HomeFirstRun.kt:166` `CircularProgressIndicator()` | `primary` на `background`; не текст, но и пара верна |
| `main/MainDrawer.kt:80-107` `Surface` шапки ящика | `Surface` без `color` → `surface`, `Text` принудительно `onSurface`. Верно; `colorFilter` для логотипа в тёмной теме — отдельный вопрос, не цвет текста |
| `main/MainImportMenu.kt:76-96` `ShareMethodDialog` | `SelectListDialog` → `AlertDialog` с `onSurface`. Риск общий, раздел 2 |
| `ScannerActivity.kt:251-277` `ScannerIdlePlaceholder` | `.background(MaterialTheme.colorScheme.surface)` + `onSurface` / `onSurfaceVariant` / `primary`. Явно заданный фон, пара верна |
| `ScannerActivity.kt:326-334` подпись сканера | `color = snackbarText` (`#FFFFFF`) на `scannerScrim α0.50` над **живым кадром камеры**, а не над `surface`. Комментарий `:327-329` это фиксирует. Кадр не тема, поэтому «белое на белом» возможно только если камера смотрит в белый лист — это не дефект кода |
| `ScannerActivity.kt:290-300` `ScannerOverlay` Canvas | `drawRect(scannerScrim)` + `BlendMode.Clear` — вырезает окно, а не красит текст. Верно |
| `perappproxy/PerAppProxyActivity.kt:198-256` | `Surface(color = surface)` + `onSurface` / `onSurfaceVariant`. Пара верна. Отдельно — незаданные disabled/unchecked цвета `Switch` (см. раздел 1, №3) |
| `settings/SettingsActivity.kt:221-231` | `Scaffold` + `CollapsiblePreferenceGroupHeader(color = primary)` на `background`. `primary` светлой темы `#0B57D0` на `#FCF8FA` = 6.3:1. Верно |
| `routing/RoutingSettingActivity.kt:340-374` | `onSurfaceVariant` для подсказки, `colorConfigTypeLight/Dark` (`#0A6E4A` / `#7EE0A6`) для метки типа — на `background` это 6.2:1 / 10.7:1. Верно |
| `routing/RoutingEditActivity.kt:238` | `onSurfaceVariant` на `background`. Верно |
| `routing/RoutingEditActivity.kt:257` `enabled = canUseProcess` | `FormTextField` → `OutlinedTextField(enabled = false)`; material3 сам гасит текст/рамку долями `onSurface`, а фон прозрачный — экранный. Не «тот же цвет», а приглушение. Верно |
| `server/ServerCustomConfigActivity.kt:371, 416, 440, 456-460` | Номера строк `onSurfaceVariant α0.5`, текст `onSurface`, плейсхолдер `onSurface α0.38` — все на теле `Scaffold`. Явное приглушение, не совпадение с фоном (хотя `α0.38` на `background` даёт ≈ 2.4:1 — это вопрос доступности, не «невидимости») |
| `server/*Activity.kt` — `ServerEditorScaffold` (`BaseServerActivity.kt:440-478`), `ServerGroupActivity.kt:240-320`, `ServerProxyChainActivity.kt:224-270`, `ServerCustomConfigActivity.kt:313` | Все на `Scaffold`, все компоненты — `FormTextField` / `FormDropdownField` / `SettingsSwitchItem` / `Text`, у каждого пара ролей. Единственный общий риск — `Switch` (раздел 1, №3) |
| `subscription/SubEditActivity.kt:161-283`, `SubSettingActivity.kt:129-210` | `ReOrderableListItem` → `Surface` = `surface`, текст в каскаде `onSurface`, `onSurfaceVariant` явно. Верно |
| `userasset/UserAssetActivity.kt:271-345, 401`, `UserAssetUrlActivity.kt:146-200` | `Scaffold` + `onSurfaceVariant` на `background`. Верно |
| `TranslatorsActivity.kt:59-107` | `Card(containerColor = surfaceContainerLow)`: `#F9F5F8` светлая / `#19181C` тёмная. `onSurface` на ней = 16.4:1 / 12.8:1. `primary` для ссылок = 6.5:1 светлая / 9.8:1 тёмная. Верно |
| `TranslatorsActivity.kt:103` `HorizontalDivider(color = outlineVariant)` | Декоративная линия по контракту токена (`Theme.kt:59` «decorative, 1.70:1»). Не текст |
| `logcat/LogcatActivity.kt:143-235` | `Scaffold` + `LogcatItem` `bodySmall` в каскаде `onSurface`. Верно |
| `backup/BackupActivity.kt:205-230` | `Scaffold` + `SettingsMenuItem`. Верно |
| `checkupdate/CheckUpdateActivity.kt:68-110` | `Scaffold` + `SettingsSwitchItem` (`Switch` — см. раздел 1, №3) |
| `apppicker/AppPickerActivity.kt:130-190` | `Scaffold` + `AppListItem`. Верно |
| `shortcut/Sc*Activity.kt`, `shortcut/TaskerActivity.kt:128-180` | `Sc*` не рисуют ничего (сразу `finish()`), `Tasker` — `Scaffold` + `RadioButton`/`Text` в каскаде. Верно |
| `main/HomeCardPager.kt:141-148` — прозрачная плёнка | Пара `White`/`White` на **акценте** посчитана в `HomeModelsTest`. Не дефект по контрасту; см. раздел 3 про запас |
| `res/layout/widget_switch.xml:38` `android:textColor="#FFFFFF"` | Это **не** Compose и не экран приложения: RemoteViews на домашнем экране, где `windowBackground` темы не применяется. Белый текст и белая иконка (`ic_play/ic_stop` с `fillColor="#FFFFFFFF"`) лежат на **непрозрачных** `ic_rounded_corner_inactive` (`#A0374151`) / `ic_rounded_corner_active` (градиент `#FB923C→#EA580C`) — 6.5:1 и 2.1:1 к градиенту. Фон не тематизируется, значит «белое на белом» невозможно **по построению**. Тёмная тема ничего не перекрашивает: `WidgetProvider.kt:46-55` меняет только `setImageResource`/`setBackgroundResource` и никогда не трогает `TextView` |
| `res/drawable/ic_outline_filter_alt_24.xml:3` `android:tint="#000000"` | Запечённый чёрный tint — ловушка, но **ни одного использования** в коде и разметке нет (grep по `main/`). Мёртвый ассет |
| `res/drawable/ic_play_24dp.xml`, `ic_stop_24dp.xml`, `ic_action_done.xml`, `ic_refresh_24dp.xml`, `ic_qu_start_24dp.xml`, `ic_settings_24dp.xml` | `fillColor` чёрный или белый, **но** один-два `path` на файл, поэтому Compose `Icon` всегда применяет `tint` (по умолчанию `LocalContentColor`) и стирает запечённый цвет. Запечённый `android:tint` есть только у неиспользуемого `ic_outline_filter_alt_24` |
| `ToastExt.kt:33` `Toast.makeText(...)` | Системный тост, вне Compose и вне темы. Пока нет `AppSnackbarHost`, цвет выбирает платформа — это не наша пара |
| `main/HomeConfiguration.kt:135-165` `LazyColumn` в режиме `ScrollAll` | Один владелец прокрутки (обосновано `:117-124` и `:135-141`). Не цвет |

---

## 5. Что это означает по существу

Самый крупный класс — **не «неверный цвет», а «цвет из одной пары на поверхности из другой»**.
Схема в `Theme.kt` считает контрасты только против `surface` (`:33`, `:55`), а экраны ниже оперируют
четырьмя разными поверхностями: `background` (`Scaffold`, светлая `#FCF8FA`), `surface` (`#FFFFFF`),
`surfaceContainerLow` (полоса, ящик), акцент подписки (`HOME_ACCENTS`, до `#8C1D40`) и
`android:windowBackground` (белый/`#1C1B1F`). Отсюда все три «определённых» дефекта и большая часть
динамических: роль `onX` подбирается по имени, а не по фактическому предку.

Вторая по важности причина — **два независимых флага «страница на акценте»**: `lit`
(`HomeScreen.kt:85`, красит фон) и `uiState.runsOnAccent` (`HomeScreen.kt:236`, выбирает цвет строк
серверов). Пока их два, существует состояние, где текст рисуется цветом для нейтральной поверхности
поверх акцента — это ровно «чёрное на чёрном» на главном экране при включённом VPN.

Третья — **неполные пары аргументов Material**: `containerColor` без `contentColor`
(`Dialog.kt`, `HomeSheets.kt`, `MainDrawer.kt`) и `SwitchDefaults.colors` без
`unchecked*`/`disabled*` (`SettingsItem.kt`, `PerAppProxyActivity.kt`). Material3 подставляет
остальное из ролей, и при динамическом цвете или в disabled-состоянии подставленное совпадает
с фоном.
