# Аудит цветов вне схемы — FrostMost

Аудит ветки `fork/friendly-ui` против
`docs/superpowers/specs/2026-11-08-frostmost-colour-scheme.md` (разделы 2, 3, 5, 6, 8).
Источник токенов — `ui/compose/Theme.kt`. Правок в коде нет: это только список для патча.

Вне схемы считается цвет, пришедший не из `MaterialTheme.colorScheme`, не из `LocalStatusColors`
и не из именованного служебного токена `Theme.kt` (`dividerColor*`, `snackbar*`, `scannerFrame*`,
`scannerScrim`, `colorConfigType*`), а также литерал, рождённый в среде, где тема всё равно известна.

Найдено **6 настоящих дефектов** (все — экран подключённого состояния главного экрана) и
**1 вне темы по построению** (снекбар в тёмной теме). Остальное — либо корректно по схеме, либо
остатки мёртвого кода. Значения контраста посчитаны по WCAG 2.1, формула как в спеке.

## Дефекты

### main/HomeCardPager.kt

| Файл:строка | Что сейчас | Во что заменить | Почему |
|---|---|---|---|
| `HomeCardPager.kt:307` | `val okColor = if (dark) Color(0xFF62D9AE) else Color(0xFF0A7455)` | `LocalStatusColors.current.success.onNeutral` | Два приватных hex вместо статусной роли. `#0A7455` — ровно тот цвет, который спека (п. 1.1–1.2) признала провальным (3.22:1 в тёмной теме), здесь он задан заново. `#62D9AE` вообще отсутствует в схеме: тёмный `onNeutral` — это `#7EE0A6`. Контраст на светлом акценте «вино» `#8C1D40` — **1.54:1**: текста не видно. |
| `HomeCardPager.kt:308` | `val busyColor = if (dark) Color(0xFFFFC07A) else Color(0xFF8F4C08)` | `LocalStatusColors.current.warning.onNeutral` | То же: `#8F4C08` и `#FFC07A` — литералы, которых в схеме нет (warning `onNeutral` = `#7A4A00` / `#F5C97A`). На тёмном бронзовом акценте `#8F5600` даёт **1.09:1**. |
| `HomeCardPager.kt:310` | `!onCard -> Color.White` | `MaterialTheme.colorScheme.primaryContainer` + `onPrimaryContainer` (пара для чипа), либо `LocalStatusColors.current.<роль>` | Статус по правилу 5.1 рисуется только чипом. `Color.White` буквально на акценте даёт 1.0:1 против белой карточки предпросмотра (`:129–130`) — статус карточки, которая не показана, не читается вообще. |
| `HomeCardPager.kt:315` | `else -> if (dark) Color(0xFF1C1B1F) else MaterialTheme.colorScheme.onSurface` | `MaterialTheme.colorScheme.onSurface` (или `onBackground`, если карточка на `background`) | Литерал `#1C1B1F` дублирует роль `onSurface` в светлой теме, но это `surface`, а не `on*`; плюс комментарий прямо признаёт расхождение с темой. В светлой теме `onSurface` совпадает случайно, в динамическом цвете — разойдётся. |
| `HomeCardPager.kt:390` | `color = if (onCard) (if (dark) MaterialTheme.colorScheme.onSurface else accent) else Color.White` | `if (onCard) MaterialTheme.colorScheme.primary else LocalContentColor.current` | Три разные семантики в одном выражении: `onSurface`, акцент подписки и белый литерал. Акцент подписки не должен красить индикатор трафика (п. 2: акцент означает «эта подписка», а не «прогресс»). Белый — литерал. |
| `HomeCardPager.kt:391` | `trackColor = if (onCard) MaterialTheme.colorScheme.surfaceVariant else Color.White.copy(alpha = 0.3f)` | `if (onCard) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onPrimaryContainer` | `surfaceVariant` как дорожка — 1.18:1 (светлая) и 1.41:1 (тёмная) к `surface`: дорожка невидима, индикатор читается как сломанный. `Color.White.copy(alpha = 0.3f)` — литерал, а прозрачность 0.3 в схеме не объявлена (есть только `HOME_ON_ACCENT_OVERLAY = 0.10`). |

### compose/SnackBar.kt

| Файл:строка | Что сейчас | Во что заменить | Почему |
|---|---|---|---|
| `SnackBar.kt:182–185` | `ToastType.NORMAL -> if (isDark) snackbarNeutralDark else snackbarNeutralLight` (и так для SUCCESS/ERROR/INFO) | `LocalDarkTheme.current` оставить, но проверять рендер: `snackbarNeutralDark = #4A4F54` даёт **2.07:1** к `surface` тёмной темы `#1C1B1F` | Это не литерал, а служебный токен — формально не нарушение п. 1. Но дефект схемы: в тёмной теме нейтральный снекбар темнее поверхности, то есть тонет в ней. Нужен более светлый `snackbarNeutralDark` (кандидат — `surfaceContainerHighest`-класс тона, ~`#3F4147`, расчёт даёт ≥ 8:1). Правка внутри `Theme.kt`, не в экране. |

## Корректно по схеме — не трогать

Осознанные литералы и роли, которые совпадают с таблицей ролей. В патч не включать.

| Файл:строка | Что | Почему верно |
|---|---|---|
| `main/HomeCardPager.kt:129–131` | `Color.White.copy(alpha = HOME_ON_ACCENT_OVERLAY)`, `Color.White` | Пара `on-accent` / `on-accent-overlay` из п. 4.2, значение берётся из токена, а не из головы. |
| `main/HomeCardPager.kt:304, 474–475` | `OnBackgroundColors(accent, Color.White)`, `OnBackgroundColors(Color.White, accent)` | Белый текст на акцентной плашке — п. 4.2 (`on-accent` = `#FFFFFF`, минимум 6.00:1). Комментарий это называет, контраст проверяется тестом. |
| `main/HomeCardPager.kt:360` | `OnBackgroundColors(Color.Transparent, …)` | Прозрачный контейнер плитки «Настроить»; цвет даёт `LocalContentColor`. Ripple/фон не нужен. |
| `main/HomeStrips.kt:74` | `homeNeutralSurface`: тот же оверлей и белый | Единственная нейтральная пара главного экрана, п. 3 правила применения. |
| `main/HomeScreen.kt:109, 327` | `if (lit) Color.White`, оверлей + белый | Подписи на акценте — `on-accent`. |
| `main/HomeScreen.kt:302` | `OnBackgroundColors(Color.Transparent, contentColor)` | Прозрачная кнопка «Ещё»; цвет текста уже из схемы. |
| `ScannerActivity.kt:287` | `Color(0x80000000)` | `scannerScrim` из п. 6 — но токен объявлен в `Theme.kt:199` и не используется. См. «Нужен новый токен / мёртвый токен». |
| `ScannerActivity.kt:289` | `Color.Transparent` + `BlendMode.Clear` | Не цвет отрисовки, а «вырезать окно» в маске оверлея. Литерала цвета здесь нет. |
| `ScannerActivity.kt:324` | `color = Color.White` | Белая подпись поверх затемнённой камеры — единственный читаемый вариант на любом кадре. Схема не покрывает слой поверх видео; ближайший токен — `snackbarText` (`#FFFFFF`), можно взять его, чтобы литерал из Compose-функции ушёл (правило 8.1). Это стилистика, не контрастный дефект. |
| `compose/Components.kt:137–140`, `compose/Dialog.kt:155–156`, `compose/FormFields.kt:61–62, 115–116` | `Color.Transparent` в `OutlinedTextFieldDefaults.colors` | Прозрачность как отсутствие фона; поля стоят на поверхности, которую задаёт родитель. Тинт не нужен. |
| `compose/Scrollbar.kt:36, 94, 119` | `Color.Transparent` как значение по умолчанию и как признак «дорожки нет» | Не отрисовка: сравнение-маркер `trackColor != Color.Transparent`. Сам ползунок — `MaterialTheme.colorScheme.secondary` (`:69`), все вызовы идут с конфигом по умолчанию. |
| `main/HomeConfiguration.kt:462, 573–574` | `colors.content.copy(alpha = SUMMARY_ALPHA)` и т.д. | Альфа от цвета схемы, а не литерал. Замечание: `SUMMARY_ALPHA = 0.75f` — ровно та величина, которую п. 4.2 заменил на `on-accent-muted` (88%, 5.05:1). На акцентном фоне подпись секции даёт ~4.71:1. Не дефект цвета, но кандидат на замену `0.75f → 0.88f`. |
| `main/MainDrawer.kt:98` | `ColorFilter.tint(Color.White, BlendMode.SrcIn)` | Перекраска растра-логотипа во все белые пиксели для тёмной темы. К `MaterialTheme` не относится; «белый как трафарет», не как цвет роли. |
| Иконки с `android:fillColor="@android:color/white"` (`ic_arrow_back_24dp`, `ic_expand_more_24dp`, `ic_flash_on/off_24dp`, `ic_outline_filter_alt_24, ic_search_24dp, license_24px`) | Белая заливка в векторе | Заливка перебивается `Icon`/`tint` из темы (`AppTopBar` → `actionIconContentColor`, `SettingsItem` → `onSurface`, `ServerToolButton` → `colors.content`). Фактический цвет — из схемы. Не дефект. |
| `res/values/themes.xml:2–3` | `window_background_light` / `window_background_dark` | `#FFFFFFFF` и `#FF1C1B1F` совпадают с `surface` светлой и тёмной темы. Используются как `android:windowBackground` до первого кадра Compose — тут XML обязан знать цвет, темы ещё нет. |
| `res/layout/widget_switch.xml:34, 39` | `textColor="#FFFFFF"`, `shadowColor="#C0000000"` | Виджет рисуется вне Compose, `MaterialTheme` к нему не приходит; белый с тенью — принятый приём для подписи поверх системного виджета. Токена для этого слоя в схеме нет. |
| `res/drawable/ic_rounded_corner_*.xml` | `solid #A0374151`, `gradient #FB923C→#EA580C`, `stroke #FFEDD5` | Тоже слой виджета (`widget_switch.xml` — единственный потребитель). Не Compose-экран. |

## Мёртвый код и мусор — не дефекты выбора цвета

| Файл:строка | Что | Что сделать |
|---|---|---|
| `ScannerActivity.kt:278` | `val cornerColor = Color(0xFF4CAF50)` — литерал в Compose-функции, ровно тот `#4CAF50`, который п. 6 заменяет на `scannerFrame` | **Достать токен**, а не ждать замены цвета: заменить на `if (LocalDarkTheme.current) scannerFrameDark else scannerFrameLight`. Это единственный оставшийся литерал на экране сканера с реальным значением из схемы. Но: `ScannerOverlay` вызывается из `ScannedResultCompose`/превью — см. ниже, в текущем потоке он не рисуется (`CameraXViewfinder` занимает весь экран). Патчить всё равно нужно: функция публичная. |
| `res/drawable-night/ic_fab_check.xml:7` | `android:fillColor="#FFF"` (в `drawable/` — `#000`) | Мусор: иконка перекрашивается `Icon` из темы в 7 местах (`BaseServerActivity:464`, `ServerGroupActivity:263`, `ServerProxyChainActivity:245`, `TaskerActivity:136`, `SubEditActivity:191`, `RoutingEditActivity:205`, `ServerCustomConfigActivity:338`, `UserAssetUrlActivity:174`). Цвет не влияет. Привести к `#FF000000` для единообразия с остальными 48 иконками, необязательно. |
| `res/drawable/ic_play_24dp.xml` и `drawable-night/ic_play_24dp.xml` | Обе версии `#FFFFFFFF`; светлая не парная остальным | Перекрашивается `Icon` (`HomeCardPager.kt:496`). Безвредно, асимметрию можно убрать. |
| `res/drawable/ic_qu_scan/start/stop/switch_24dp.xml` | Два цвета: белый круг + чёрный глиф | Не парная иконка для `ServerToolButton` (`HomeConfiguration.kt:535`) и не перекрашивается корректно: `tint = content` красит оба пути в один цвет, круг и глиф сливаются. Реальный дефект отрисовки: нужен одноцветный вектор. |
| `res/drawable/ic_menu_24dp.xml` | Нет ни одного потребителя в `app/src` | Удалить или оставить как есть; цвета не касается. |

## Нужен новый токен (или правка схемы)

1. **Акцентный текст статуса.** `HomeCardPager.kt:309–316` красит «Подключаюсь…» / «Переключено»
   (`statusWarning.onNeutral`) прямо на светлой карточке (`surface`) в тёмной теме. Сегодня это
   `#F5C97A` на `#1C1B1F` — 9.7:1, формально проходит. Но роль `onNeutral` по п. 5.2 определена как
   «цветной текст прямо на **нейтральной** поверхности», а карточка подключённого состояния — это
   `surface` светлой карточки в тёмной теме, то есть контейнер, а не страница. Схема этого случая не
   разводит. Вариантов два, выбрать надо явно:
   - красить статус только `onSurface` / `onSurfaceVariant` (нейтрально, по образцу Mullvad из п. 10.2);
   - либо завести `status*OnCard` для текста на светлом контейнере в тёмной теме.
   Сейчас схема не даёт ответа — это не «подогнать под токен», а решить.
2. **Слой поверх камеры.** `ScannerActivity.kt:287, 289, 324` (скрим, вырез, подпись). `scannerScrim`
   в схеме есть, `scannerFrame*` есть, а белой подписи токена нет. Либо взять `snackbarText`
   (значение совпадает), либо добавить `scannerCaption` в п. 6, чтобы `Color.White` ушёл из Compose.
3. **Тёмный снекбар.** `snackbarNeutralDark = #4A4F54` даёт 2.07:1 к `surface` тёмной темы — см.
   дефект `SnackBar.kt:182–185`. Нужен пересчёт значения, новый токен не требуется.
4. **`scannerFrameLight/Dark`, `scannerScrim` не имеют потребителя** (`Theme.kt:197–199`).
   Схема в п. 6 говорит, что токен без потребителя — удаляется. Либо патч `ScannerActivity.kt:278`
   и вычитка сканера (п. 1 выше), либо токены удалить. Сейчас они — ловушка.

## Сводка по механическому поиску

- `Color(0x…)`, `Color.White/Black/Red/…` под `ui/` вне `Theme.kt` — 33 попадания, из них
  **6 дефектов** в `HomeCardPager.kt`, 1 общий (`ScannerActivity.kt:278`), остальные прозрачности,
  пара `on-accent` и прозрачные контейнеры полей.
- Литеральных hex-строк в Kotlin вне `Theme.kt` и `HomeModels.kt` нет.
- `android:fillColor`/`android:tint` с литералом в `res/drawable*` — только чёрный/белый
  (плюс `@android:color/white`); цветных иконок не осталось. Единственный не-монохромный —
  `ic_qu_*` (белый круг + чёрный глиф).
- Вне `ui/` цветовых литералов нет вовсе. `handler/MmkvManager.kt` — единственный файл вне `ui/`,
  тянущий Compose, но только `androidx.compose.runtime.*` (состояние настроек); `graphics.Color`
  там не импортируется.
