# FrostMost: справочники по цветовой системе

Собрано для схемы FrostMost: Jetpack Compose + Material 3, светлая и тёмная темы, акцент на подписку,
статусы connected / connecting / error / latency. Только ограничения и свидетельства, без итоговой палитры.
Всё, что не удалось подтвердить первичным источником, помечено **не проверено**.

## 1 Гайдлайны

### 1.1 Material 3

**26 ролей, шесть групп.** primary, secondary, tertiary, error, surface, outline. «Primary, Secondary,
Tertiary – Accent color roles»; роли бывают `container` — «used as a fill color… should not be used for text
or icons» — и `on-` — «a color for text or icons on top of its paired parent color»
([m3 roles](https://m3.material.io/styles/color/roles)).

**Как M3 велит выводить статус/error.** Роль error статична: «Error is an example of a static color (it doesn't
change even in dynamic color schemes). Error color roles are made static by default with any dynamic color
scheme. They still adapt to light and dark theme»
([m3 roles](https://m3.material.io/styles/color/roles)). То есть ошибка — фиксированная семантика, которая
получает разные значения per-theme, а не оттенок акцента. Отдельного рецепта для success/warning/latency в M3
**нет** — **не проверено**, что M3 вообще покрывает эти статусы.

**Тональная модель.** Источник порождает пять ключевых цветов (primary, secondary, tertiary, neutral, neutral
variant) → тональные палитры с тонами 0–100 → «The algorithm uses accessible color relationships to assign
specific tones to the 26 color roles in both light and dark theme»; пример — primary40 на primary, primary100
на on primary. Контраст задаётся расстоянием тонов: тоны 50/98 дают 3:1, тоны 30/98 дают 7:1
([m3 how the system works](https://m3.material.io/styles/color/system/how-the-system-works)).

**Три уровня контраста.** Standard / Medium / High; medium «provides a minimum contrast ratio of 3:1», high —
«7:1 contrast ratio»; уровни применяются автоматически к обеим темам, и кастомные компоненты поддерживают их,
если используют правильную роль (`primary container` + `on primary container`)
([m3 how the system works](https://m3.material.io/styles/color/system/how-the-system-works)).

**Surface tint / tonal elevation — отменены.** «Surface tint color is deprecated. Use elevation level tokens
(0–5) instead» ([m3 elevation tokens](https://m3.material.io/styles/elevation/tokens)). Историю замены
описывает сам Material: раньше тинт давался через opacity-слой при поднятии elevation; «The update introduces
dedicated surface color roles that are no longer tied to elevation»; `surface at elevation +1 → surface container
low`, `+2 → surface container`, `+3 → surface container high`, `+4/+5` deprecated в пользу
`surface container highest`; поверх этого — light-surface с тона 99 на 98, chroma нейтральной палитры 4→6,
surface в тёмной теме чуть темнее ([m3 tone-based surfaces](https://m3.material.io/blog/tone-based-surface-color-m3)).
Текущее состояние guidance: тинт не поддерживается, вместо него пять `surface container`-ролей
([m3 roles](https://m3.material.io/styles/color/roles)).

**Парность и слоистость.** «Apply colors only in the intended pairs or layering orders»; пример неверной
разводки — `primary` рядом с `primary container`, фоном `secondary container` и текстом `on surface` —
«become illegible as the contrast level changes». Внутри системы пары держат «minimum 3:1 contrast», но
`on surface variant` описан как «lower-emphasis color», а `outline variant` — «decorative elements… and when
other elements provide 4.5:1 contrast» ([m3 roles](https://m3.material.io/styles/color/roles)).

**Fixed-акценты — ловушка для контраста.** `primary fixed` и т.п. «maintain the same tone in light and dark
themes», и прямо: «Fixed colors don't change based on light or dark theme, so they're likely to cause contrast
issues. Avoid using them where contrast is necessary» ([m3 roles](https://m3.material.io/styles/color/roles)).

**Контраст в общей формулировке M3:** «Large text (at 14 pt bold/18 pt regular and up) and graphics — at least
3:1… Small text — at least 4.5:1»; «Disabled states do not need to meet contrast requirements»; кроме того,
сгруппированные элементы требуют 3:1 между контейнером и фоном, а одиночные (FAB) — нет
([m3 color & contrast](https://m3.material.io/foundations/designing/color-contrast)).

### 1.2 Apple HIG

**Акцент.** Бренд-цвет и семантические цвета разведены: system colors «are semantically defined by its purpose,
rather than its appearance or color values», и «Avoid redefining the semantic meanings of dynamic system colors»
([HIG Color](https://developer.apple.com/design/human-interface-guidelines/color)). Для Liquid Glass правило
про акцент: «To emphasize primary actions, apply color to the background rather than to symbols or text»,
красить надо фон, а не текст ([там же](https://developer.apple.com/design/human-interface-guidelines/color)).

**Прямое попадание в нашу задачу.** «Avoid using similar colors in control labels if your app has a colorful
background… If your app features colorful backgrounds or visually rich content, prefer a monochromatic
appearance for toolbars and tab bars, or choose an accent color with sufficient visual differentiation»
([там же](https://developer.apple.com/design/human-interface-guidelines/color)).

**Один цвет — одно значение.** «Avoid using the same color to mean different things. Use color consistently
throughout your interface, especially when you use it to help communicate information like status or
interactivity» ([там же](https://developer.apple.com/design/human-interface-guidelines/color)).

**Не только цветом.** «Avoid relying solely on color to differentiate between objects, indicate interactivity,
or communicate essential information… you can use text labels or glyph shapes to identify objects or states»
([там же](https://developer.apple.com/design/human-interface-guidelines/color)).

**Обязательность вариантов.** «Make sure all your app's colors work well in light, dark, and increased contrast
contexts… If you define a custom color, make sure to supply light and dark variants, and an increased contrast
option for each variant»; и «Colors that work well in one appearance can look wrong in the other»
([там же](https://developer.apple.com/design/human-interface-guidelines/color),
[HIG Dark Mode](https://developer.apple.com/design/human-interface-guidelines/dark-mode)).

**Культурная зависимость цвета статуса.** «Red communicates danger in some cultures, but has positive
connotations in other cultures» — тот же system red используется для роста в Stocks в CN
([HIG Color](https://developer.apple.com/design/human-interface-guidelines/color)).

### 1.3 IBM Carbon

**Support-роли существуют как отдельная группа.** В таблице токенов группа `Support` описана как
«Notification elements and status indicators» — то есть это выделенная роль, а не переиспользование
акцентных ([Carbon color overview](https://carbondesignsystem.com/building-blocks/foundations/color/overview)).

**Значения support-токенов различаются по темам — это проверено в самих токенах.** На White (light):

| Роль | Значение |
|---|---|
| `$support-error` | Red 60 `#da1e28` |
| `$support-success` | Green 50 `#24a148` |
| `$support-warning` | Yellow 30 `#f1c21b` |
| `$support-info` | Blue 70 `#0043ce` |
| `$support-caution-major` | Orange 40 `#ff832b` |

([Carbon tokens](https://carbondesignsystem.com/building-blocks/foundations/color/tokens))

На Gray 100 (dark) те же роли сдвинуты на **более светлый grade**:

| Роль | Light | Dark (Gray 100) |
|---|---|---|
| `$support-error` | Red 60 `#da1e28` | Red 50 `#fa4d56` |
| `$support-success` | Green 50 `#24a148` | Green 40 `#42be65` |
| `$support-info` | Blue 70 `#0043ce` | Blue 50 `#4589ff` |
| `$support-warning` | Yellow 30 `#f1c21b` | Yellow 30 (без сдвига) |
| `$support-caution-undefined` | Purple 60 `#8a3ffc` | Purple 50 `#a56eff` |

([Carbon tokens](https://carbondesignsystem.com/building-blocks/foundations/color/tokens) — переключение темы
White / Gray 10 / Gray 90 / Gray 100 на той же странице). Наблюдение: сдвиг всегда к светлому и к меньшему
номеру grade (60→50, 50→40, 70→50); warning Yellow 30 не меняется, потому что уже светлый. Развёрнутого
текстового обоснования «почему мы не берём тот же hue» в документации нет — **не проверено**, но само
поведение токенов проверяемо.

**Правило «роль одна, значение от темы».** «A role is the systematic usage of a color assigned to a token.
Roles cannot be changed between themes»; «Color token names and roles are the same across themes, only the
assigned value will change with the theme» — на светлых темах White/Gray 10, на тёмных Gray 100/Gray 90
([Carbon color overview](https://carbondesignsystem.com/building-blocks/foundations/color/overview)).

**Grayscale-радиус контраста.** «Small text is any size below 24px and requires a 4.5:1 contrast ratio. Large
text is anything above 24px and requires a 3:1 contrast ratio. Graphical elements… also require a 3:1», плюс
таблица ступеней палитры, нужных для достижения 4.5:1 / 3:1 между любой парой цветов — прямо применимо к
проверке «наш акцент против цвета статуса»
([Carbon color overview](https://carbondesignsystem.com/building-blocks/foundations/color/overview)).

**Hard rule для dark.** «Do not apply components that are darker than the background unless using high-contrast
mode» ([Carbon color overview](https://carbondesignsystem.com/building-blocks/foundations/color/overview)).

**Статус не только цветом — измеримое правило.** «For WCAG compliance, at least three of these elements must be
present» из набора {symbols, shapes, colors, type}; «Icon indicators… require an icon, a shape, a meaningful
color, and a descriptive inline label». Плюс: «Avoid using status indicators when no user action is required…
Instead, use plain text», и «more than five or six indicators can overwhelm users»
([Carbon status indicators](https://carbondesignsystem.com/building-blocks/core/patterns/status-indicators)).

**Статусная палитра — не брендовая.** Extended status palettes (Yellow/Orange 10–100) — «only for added contrast
accessibility when using yellows and oranges… Do not use this palette in any other context or layout»
([Carbon status indicators](https://carbondesignsystem.com/building-blocks/core/patterns/status-indicators)).

**Форма как канал.** Shape indicator = «a shape, color, and descriptive label… without relying on a symbol»;
формы несут культурные ассоциации (hexagon = stop, upside triangle = yield) и «using shapes incorrectly can
disturb learned recognition patterns»
([Carbon status indicators](https://carbondesignsystem.com/building-blocks/core/patterns/status-indicators)).

### 1.4 Atlassian Design System

**Цветовые роли с явной семантикой.** `information` («UI that communicates something is in progress»),
`success`, `warning`, `danger`, `discovery`, `neutral`, и отдельно `accent` — «Use for colors that don't have
any specific meaning tied to them. You should be able to exchange one accent color for another, and the
experience would remain unchanged». С прямым запретом: «Don't use an accent when the color has semantic
meaning» ([Atlassian color](https://atlassian.design/foundations/color)). Для FrostMost это формальное
разделение: цвет подписки — `accent`, статус — `success`/`warning`/`danger`/`information`.

**Цветной текст на насыщенном фоне решён отдельными токенами.** `color.text.warning.inverse` —
«Use for text when on bold warning backgrounds»; и вводная фраза страницы: «For bold warning backgrounds, which
are yellow, there are special warning.inverse tokens designed to pass WCAG AA contrast requirements»
([Atlassian color](https://atlassian.design/foundations/color),
[Atlassian tokens](https://atlassian.design/components/tokens/all-tokens)). Рядом — семейство `.bolder`:
`color.text.success.bolder` — «Use for text on top of success semantic labels to ensure accessibility and
desired visual appearance» (Lime900 / Lime200); `danger.bolder` (Red900 / Red200); `warning.bolder`
(Orange900 / Orange200); `discovery.bolder` (Purple900 / Purple200). Это ровно наша задача — текст статуса на
цветной плашке — и у ADS это **отдельный токен на каждый статус**, а не общий цвет.
([Atlassian tokens](https://atlassian.design/components/tokens/all-tokens))

**Фоновые статусные роли тоже по уровням.** `color.background.success.bold` (Lime700 / Lime400),
`success.subtler` (Lime300 / Lime800), а также `danger.bold` (Red700 / Red400) — «vibrant background option»;
light и dark значения различаются ([Atlassian tokens](https://atlassian.design/components/tokens/all-tokens)).

**Альфа-цвета как механизм адаптации.** «Alpha colors have varying levels of transparency or opacity.
Transparency helps UI adapt to different background colors and elevations»
([Atlassian color](https://atlassian.design/foundations/color)).

**Порог контраста.** «Must pass 3:1… Any UI essential to understanding the experience and text 24px or larger
(WCAG 1.4.11)»; «Must pass 4.5:1: Text smaller than 24px (WCAG 1.4.3)»
([Atlassian color](https://atlassian.design/foundations/color)).

**Лозенж как компонент статуса.** «Lozenges use semantic colors to indicate meanings that people can learn and
recognize across apps»; нейтральный лозенж — для статуса без семантики («default», «standard», «inactive»,
«draft») ([Atlassian Lozenge](https://atlassian.design/components/lozenge/examples)).

**Не только цветом для выделения.** «Pair selected colors with another visual cue, such as a checkmark,
underline, border, or change in shape. This helps people identify selection without relying on color alone»
([Atlassian color](https://atlassian.design/foundations/color)).

### 1.5 WCAG (первичный текст)

**AA (1.4.3).** «Text… has a contrast ratio of at least 4.5:1», исключение «Large Text… at least 3:1»; large =
«at least 18 point or 14 point bold» (≈24px / ≈18.5px). Порог не округляется: «4.499:1 would not meet the
4.5:1 threshold». Логотипы и неактивные компоненты исключены; «insufficient contrast due to corporate identity
… is not exempted» ([W3C 1.4.3](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html)).

**AAA (1.4.6).** «At least 7:1», для large text — 4.5:1. Обоснование порогов: 4.5:1 ≈ потеря контрастной
чувствительности при 20/40, 7:1 — при 20/80 ([W3C 1.4.6](https://www.w3.org/WAI/WCAG22/Understanding/contrast-enhanced.html)).
Практический вывод для FrostMost: 4.5:1 — минимум, 7:1 — достижимая цель для мелкого статусного текста,
и именно так CCS задаёт свои «high contrast» уровни (7:1)
([m3 how the system works](https://m3.material.io/styles/color/system/how-the-system-works)).

**Non-text (1.4.11, AA).** «Contrast ratio of at least 3:1 against adjacent color(s)» для визуальной
информации, необходимой для опознания компонента **и его состояний**; 2.999:1 не проходит. Важно для нас:
«If an indicator is partly inside and partly outside the component, either part of the indicator could provide
contrast» — то есть обводка/кольцо вокруг цветной точки засчитывается как носитель контраста
([W3C 1.4.11](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html)).

**Цвет — не единственный канал (1.4.1).** Пример прямо про наши индикаторы: 1–5 звёзд, где full = жёлтый,
empty = белый, «is likely to fail the Use of color criterion»; проходной вариант — «either a solid fill… that
has contrast, or a thicker border as well as yellow fill»; и жёлтый `#FFF000` на белом = 1.2:1 — провал 1.4.11
([W3C 1.4.11](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html)).

**Чего WCAG не говорит.** Готового рецепта «транслюцентный скрим под цветной плашкой» в WCAG нет — есть
только требование считать контраст против фактического фона: «contrast is measured with respect to the
specified background over which the text is rendered», и «it is a failure if no background color is specified
when the text color is specified» ([W3C 1.4.3](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html)).
Скрим легален только если даёт ≥4.5:1.

## 2 Приложения

Первичных дизайн-разборов или замеренных скриншотов VPN-приложений найти почти не удалось; всё, что ниже —
прочитанный исходный код, что проверяемо лучше маркетинговой страницы.

### 2.1 Mullvad — проверено по исходникам

**Статус соединения — цветной `text`-label с явным `role="status"`, и в нём всего два «цветных» состояния.**
`ConnectionStatus.tsx`: `connected → colors.green`, `connecting / disconnecting → colors.white`,
`disconnected → lockedDown ? white : red`, `error → blockingError ? red : white`. Текст — `largeText`,
`minHeight: 24px`
([ConnectionStatus.tsx](https://github.com/mullvad/mullvadvpn-app/blob/main/desktop/packages/mullvad-vpn/src/renderer/components/views/main/components/connection-panel/components/connection-status/ConnectionStatus.tsx)).
Вывод, применимый к нам: «подключение» и нейтральные ошибки — **белый/нейтральный**, а не третий оттенок;
цвет только на крайних состояниях.

**Индикатор сервера — форма + цвет, не только цвет.** `RelayStatusIndicator`: круг 16×16, `borderRadius: 8px`,
`backgroundColor: active ? green : red`; у выбранного вместо цвета — `checkmark` иконка
([RelayStatusIndicator.tsx](https://github.com/mullvad/mullvadvpn-app/blob/main/desktop/packages/mullvad-vpn/src/renderer/components/RelayStatusIndicator.tsx)).
Второй канал — геометрия/иконка, ровно как требует Carbon.

**Статусные цвета живут в одной палитре с брендом, без отдельной шкалы качества.** `red rgb(227,64,57)`,
`green rgb(68,173,77)`, `yellow rgb(255,213,36)`, плюс затемнённые варианты `red80/green80/red40/green40` и
альфы `greenAlpha40` / `redAlpha40`
([color-tokens.ts](https://github.com/mullvad/mullvadvpn-app/blob/main/desktop/packages/mullvad-vpn/src/renderer/lib/foundations/tokens/color-tokens.ts)).
Шкалы «latency: отлично/хорошо/плохо» по цвету в коде **нет** — **не проверено**, что она есть в UI.

**Android-приложение Mullvad — фактически только тёмная тема.** `ColorLightTokens.kt` целиком состоит из
`PaletteTokens.Black` с комментарием «This is experimental and currently not used»
([ColorLightTokens.kt](https://github.com/mullvad/mullvadvpn-app/blob/main/android/lib/ui/theme/src/main/kotlin/net/mullvad/mullvadvpn/lib/ui/theme/color/ColorLightTokens.kt),
[ColorDarkTokens.kt](https://github.com/mullvad/mullvadvpn-app/blob/main/android/lib/ui/theme/src/main/kotlin/net/mullvad/mullvadvpn/lib/ui/theme/color/ColorDarkTokens.kt)).
Значит Mullvad **не** даёт нам примера пары light/dark — **не проверено** как образец для двух тем.

**Elevation в dark = светлее, а не тень — буквальные значения.** `Surface = DarkBlue #192E45`, а контейнеры
растущей высоты: `SurfaceContainerLow #1C344E` → `SurfaceContainer #1F3A57` → `SurfaceContainerHigh #234161`
→ `SurfaceContainerHighest = Blue #294D73`
([ColorDarkTokens.kt](https://github.com/mullvad/mullvadvpn-app/blob/main/android/lib/ui/theme/src/main/kotlin/net/mullvad/mullvadvpn/lib/ui/theme/color/ColorDarkTokens.kt)).
Кроме того `SurfaceTint` и `SurfaceDim` помечены «Currently not in use» — то есть тинт по elevation
действительно не применяется, что совпадает с M3
([m3 elevation tokens](https://m3.material.io/styles/elevation/tokens)).

**Текст на цветной поверхности — белый, а не оттенок фона.** `OnPrimary = MullvadWhite #F8F7F1`,
`OnSurfaceVariant = WhiteOnDarkBlue60 #A3ABB5` для понижённого акцента
([PaletteTokens.kt](https://github.com/mullvad/mullvadvpn-app/blob/main/android/lib/ui/theme/src/main/kotlin/net/mullvad/mullvadvpn/lib/ui/theme/color/PaletteTokens.kt),
[OpacityTokens.kt](https://github.com/mullvad/mullvadvpn-app/blob/main/android/lib/ui/theme/src/main/kotlin/net/mullvad/mullvadvpn/lib/ui/theme/color/OpacityTokens.kt)).

**Обводка как несущая контраста на цветной карте.** Маркеры карты: `ringBorderColor = Color.White`,
`perimeterColors = centerColor.copy(alpha = 0.4f)`, `shadowColor = Black @ 0.55`
([LocationMarkerColors.kt](https://github.com/mullvad/mullvadvpn-app/blob/main/android/lib/map/src/main/kotlin/net/mullvad/mullvadvpn/lib/map/data/LocationMarkerColors.kt)).
Это ровно техника «кольцо/обводка вокруг цветного элемента на насыщенном фоне», которую WCAG 1.4.11
разрешает считать носителем контраста
([W3C 1.4.11](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html)).

### 2.2 Tailscale — проверено частично

В `Color.kt` Android-клиента ровно один цвет: `ts_color_light_blue = Color(0xFF4B70CC)` с комментарием
«TODO: replace references to these with references to material theme»
([Color.kt](https://github.com/tailscale/tailscale-android/blob/main/android/src/main/java/com/tailscale/ipn/ui/theme/Color.kt)).
Статусной цветовой системы для сравнения там нет — **не проверено** как источник паттернов.

### 2.3 Остальные кандидаты — не проверены

- **Signal** — репозиторий открыт, но это мессенджер без VPN-статусов; цветовые ресурсы — селекторы
  состояний кнопок, к задаче не относятся. **Не проверено** как образец.
- **WireGuard, IVPN, Proton VPN, 1.1.1.1, Surfshark, AdGuard** — публичного исходного кода UI или дизайн-
  разбора не нашёл; страницы продуктов маркетинговые. **Не проверено.** Отдельно: страница
  [Cloudflare One Client](https://community.cloudflare.com/t/introducing-the-new-cloudflare-one-client-interface/901362)
  отдала 403 за Cloudflare-челленджем — содержимое **не проверено**.
- Утверждения вида «Proton использует зелёный для connected» **не проверены** — не нашёл первичного
  подтверждения.

## 3 Проблема «цветной статус на акценте»

Задача: статусный/латентностный label читается на насыщенном акцентном фоне подписки. Собранные техники:

**3.1 Пара `on-` вместо подбора «похожего» оттенка.** Основной документированный путь M3: текст на цветном
fill берётся из `on`-роли, а не из отдельного статусного цвета. «The two buttons mapped with (1) primary,
(2) on primary… stay legible as the contrast level changes»
([m3 roles](https://m3.material.io/styles/color/roles)). Следствие: статусная семантика уходит в **форму,
иконку и текст**, а не в hue ([Carbon](https://carbondesignsystem.com/building-blocks/core/patterns/status-indicators),
[HIG](https://developer.apple.com/design/human-interface-guidelines/color)).

**3.2 Отдельные `.bolder` / `.inverse` токены на текст статуса внутри цветной плашки — самый прямой ответ.**
ADS держит `color.text.success.bolder`, `danger.bolder`, `warning.bolder`, `discovery.bolder` именно как
«text on top of … semantic labels to ensure accessibility», с отдельными значениями light (900-grade) и
dark (200-grade), и отдельно `color.text.warning.inverse` «for text when on bold warning backgrounds» —
«designed to pass WCAG AA contrast requirements»
([Atlassian tokens](https://atlassian.design/components/tokens/all-tokens),
[Atlassian color](https://atlassian.design/foundations/color)). То есть рекомендуемая техника — **не
прозрачный скрим, а именованный токен «текст-на-статусном-фоне» per status per theme**.

**3.3 Чип со собственным фоном (subtle-вариант) вместо насыщенного.** ADS: `color.background.success.bold`
(Lime700/Lime400) против `success.subtler` (Lime300/Lime800) и `success.subtle` — «Background colour for
non-interactive elements communicating success (e.g. semantic badges, status pills, callout chips)»
([Atlassian tokens](https://atlassian.design/components/tokens/all-tokens)). Чип со своим фоном переводит
задачу в обычный контраст «текст на своём чипе», полностью убирая зависимость от акцента.

**3.4 Увод статуса с акцентной поверхности.** Apple прямо рекомендует монохромность контролов на цветном
фоне: «prefer a monochromatic appearance for toolbars and tab bars, or choose an accent color with sufficient
visual differentiation»; и «apply color to the background rather than to symbols or text»
([HIG Color](https://developer.apple.com/design/human-interface-guidelines/color)). Это техника «статус живёт
на нейтральной поверхности, а акцент — только фон».

**3.5 Обводка/кольцо как носитель контраста.** WCAG 1.4.11 засчитывает контраст обводки: «a narrow border
around the letter would be used as the letter», и «if an indicator is partly inside and partly outside the
component, either part of the indicator could provide contrast»
([W3C 1.4.11](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html)). Практика подтверждена
Mullvad: белое кольцо вокруг маркера на цветной карте
([LocationMarkerColors.kt](https://github.com/mullvad/mullvadvpn-app/blob/main/android/lib/map/src/main/kotlin/net/mullvad/mullvadvpn/lib/map/data/LocationMarkerColors.kt)).

**3.6 Транслюцентный скрим/подложка — легален, но не документирован как рекомендация.** Ни M3, ни Carbon,
ни ADS, ни WCAG не описывают «scrim под label» как предписанную технику — **не проверено**. Прозрачные
альфа-цвета в ADS есть как механизм адаптации («Transparency helps UI adapt to different background colors
and elevations», [Atlassian color](https://atlassian.design/foundations/color)), и Mullvad использует
`centerColor.copy(alpha = 0.4f)` для периферии маркера. Но при применении скрима контраст надо считать
против **итогового** скомпонированного цвета, и WCAG требует, чтобы фон был специфицирован явно
([W3C 1.4.3](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html)).

**3.7 Латентность без опоры на цвет.** Документированного «градационного» паттерна нет ни у одного
источника в этой выборке — **не проверено**. Ближайшие проверяемые требования: для любого индикатора нужно
≥3 элемента из {символ, форма, цвет, текст}
([Carbon](https://carbondesignsystem.com/building-blocks/core/patterns/status-indicators)); не полагаться
на hue для различения ([W3C 1.4.1 в 1.4.11](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html),
[HIG](https://developer.apple.com/design/human-interface-guidelines/color)); и если число индикаторов
превышает «five or six», они перегружают интерфейс
([Carbon](https://carbondesignsystem.com/building-blocks/core/patterns/status-indicators)).

## 4 Тёмная тема

**4.1 Почему насыщенный цвет, прошедший на белом, проваливается на тёмно-сером.** Контраст считается как
`(L1 + 0.05) / (L2 + 0.05)` по относительной яркости; на белом фоне `L2` велико, поэтому нужен довольно
тёмный текст; на тёмно-сером `L2 ≈ 0`, знаменатель падает, и **тот же** цвет даёт другое отношение —
из-за нормировки на `+0.05` именно мелкие `L2` дают самые большие скачки
([W3C 1.4.3, формулы relative luminance / contrast ratio](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html)).
Дополнительно: WCAG не округляет порог, «4.499:1 would not meet the 4.5:1 threshold»
([там же](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html)), поэтому перенос значения
«впритык» почти всегда ломает порог. Прямое следствие: пару «статус/фон» надо пересчитывать **в каждой
теме отдельно**, а не переносить hex.

**4.2 Рекомендуемый фикс — сдвинуть к светлому и снизить насыщенность.** Это не блог-совет, а поведение
токенов: Carbon на Gray 100 берёт Red 50 `#fa4d56` вместо light-Red 60 `#da1e28`, Green 40 `#42be65` вместо
Green 50 `#24a148`, Blue 50 `#4589ff` вместо Blue 70 `#0043ce` — во всех случаях **светлее и на меньший
номер grade**
([Carbon tokens](https://carbondesignsystem.com/building-blocks/foundations/color/tokens)). ADS делает то же
в `.bolder`: light 900-grade, dark 200-grade (`Red900`/`Red200`, `Orange900`/`Orange200`,
`Lime900`/`Lime200`) ([Atlassian tokens](https://atlassian.design/components/tokens/all-tokens)). Apple
формулирует это словами «dimmer background colors and brighter foreground colors»
([HIG Dark Mode](https://developer.apple.com/design/human-interface-guidelines/dark-mode)); M3 — «slightly
darkened surface roles in dark theme» при неизменных ролях
([m3 tone-based surfaces](https://m3.material.io/blog/tone-based-surface-color-m3)).

**4.3 Насыщенные акценты в dark не совпадают с light.** Это проверяемо у ADS (Blue700 / Blue400 в
`background.brand.bold`, Purple800 / Purple300 в `text.discovery`)
([Atlassian tokens](https://atlassian.design/components/tokens/all-tokens)) и у Carbon (Blue 60 / Blue 50 в
`$interactive`) ([Carbon tokens](https://carbondesignsystem.com/building-blocks/foundations/color/tokens)).
Обратное — M3 fixed-цвета, которые «maintain the same tone in light and dark themes», с предупреждением
«likely to cause contrast issues; avoid using them where contrast is necessary»
([m3 roles](https://m3.material.io/styles/color/roles)). Для FrostMost: акцент на подписку — это fixed по
смыслу (индекс цвета), но контраст белого на нём обязателен, значит акцент **обязан** иметь два значения.

**4.4 Прозрачности как замена второй палитры.** ADS: «Alpha colors have varying levels of transparency…
helps UI adapt to different background colors and elevations»
([Atlassian color](https://atlassian.design/foundations/color)). Mullvad реализует это численно —
`WhiteOnDarkBlue60 #A3ABB5` как «понижённый» текст, и весь набор `BlueOnDarkBlue10…80`
([OpacityTokens.kt](https://github.com/mullvad/mullvadvpn-app/blob/main/android/lib/ui/theme/src/main/kotlin/net/mullvad/mullvadvpn/lib/ui/theme/color/OpacityTokens.kt)).
Замечание: в коде там **не** реальные альфы, а уже вычисленные непрозрачные hex — то есть палитра
предвычислена под конкретный тёмный фон, а не смешивается в рантайме.

**4.5 «Поднятый» = светлее серый, не тень.** Правило M3: surface-контейнеры идут `lowest → low → default
→ high → highest` по возрастанию акцента
([m3 roles](https://m3.material.io/styles/color/roles)), и этот градиент получается повышением тона светлого
конца палитры в dark. Буквальные значения у Mullvad: `#1C344E → #1F3A57 → #234161 → #294D73` при фоне
`#192E45` ([ColorDarkTokens.kt](https://github.com/mullvad/mullvadvpn-app/blob/main/android/lib/ui/theme/src/main/kotlin/net/mullvad/mullvadvpn/lib/ui/theme/color/ColorDarkTokens.kt)).
Carbon запрещает обратное: «Do not apply components that are darker than the background unless using
high-contrast mode» ([Carbon color overview](https://carbondesignsystem.com/building-blocks/foundations/color/overview)).
Тинт по elevation при этом не использовать — он deprecated
([m3 elevation tokens](https://m3.material.io/styles/elevation/tokens)).

**4.6 Не только контраст: тот же цвет значит не то же самое.** В dark светлый оттенок читается как «более
яркое, поэтому более важное», а в light тот же hue уводит в «мягкое, второстепенное». Отсюда требование Apple
о варианте для **каждой** темы и для increased contrast
([HIG Color](https://developer.apple.com/design/human-interface-guidelines/color)).

**4.7 Красный на чёрном — отдельный риск.** WCAG отмечает «the use of predominantly long wavelength colors
against darker colors (generally appearing black) for those with protanopia» и даёт advisory-технику
«avoiding red on black» ([W3C 1.4.3](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html)).
Практически: ошибку на тёмном акценте нельзя кодировать одним насыщенным красным.

## 5 Что это значит для FrostMost

Ограничения, которым схема обязана удовлетворять (не решения):

1. Акцент подписки — роль с парной `on-accent`; текст и иконки на акценте берутся только из неё, не из
   статусных цветов ([m3 roles](https://m3.material.io/styles/color/roles)).
2. Акцент — «accent» в смысле ADS: взаимозаменяемый цвет без семантики. Статус — только из
   success/warning/danger/information; «Don't use an accent when the color has semantic meaning»
   ([Atlassian color](https://atlassian.design/foundations/color)).
3. Акцент обязан иметь **два значения** (light/dark): M3 предупреждает, что неизменный цвет «likely to cause
   contrast issues» ([m3 roles](https://m3.material.io/styles/color/roles)); Apple требует варианты для
   light, dark и increased contrast ([HIG Color](https://developer.apple.com/design/human-interface-guidelines/color)).
4. Пара accent/on-accent проверяется в каждой теме отдельно; порог не округляется (4.499:1 — провал)
   ([W3C 1.4.3](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html)).
5. Мелкий статусный текст — 4.5:1 минимум, 7:1 — цель; крупный (≥18pt/≥14pt bold) — 3:1
   ([W3C 1.4.3](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html),
   [W3C 1.4.6](https://www.w3.org/WAI/WCAG22/Understanding/contrast-enhanced.html)). Решить заранее, какой
   размер и вес у статуса: от этого зависит допустимый порог.
6. Визуальная информация о **состоянии** компонента — 3:1 против соседнего цвета; 2.999:1 не проходит
   ([W3C 1.4.11](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html)).
7. Цветной статус на насыщенном акценте решается одним из: именованный токен «текст-на-статусном-фоне» per
   theme ([Atlassian tokens](https://atlassian.design/components/tokens/all-tokens)); чип со своим фоном
   (subtle/subtler) ([там же](https://atlassian.design/components/tokens/all-tokens)); увод статуса на
   нейтральную поверхность ([HIG](https://developer.apple.com/design/human-interface-guidelines/color));
   обводка/кольцо как носитель контраста
   ([W3C 1.4.11](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html)). Скрим допустим, но
   контраст считается против итогового смешанного цвета и ни одним из источников не рекомендован —
   **не проверено** как предписанная практика.
8. Статус передаётся минимум тремя каналами из {символ, форма, цвет, текст}
   ([Carbon status](https://carbondesignsystem.com/building-blocks/core/patterns/status-indicators)); одно
   только различие hue — провал ([W3C 1.4.1](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html),
   [HIG](https://developer.apple.com/design/human-interface-guidelines/color)).
9. Латентность нуждается в нецветовом носителе (число, длина, позиция, подпись) — градационного паттерна в
   источниках нет, **не проверено**; ограничение по числу одновременно видимых индикаторов — ≲5–6
   ([Carbon status](https://carbondesignsystem.com/building-blocks/core/patterns/status-indicators)).
10. Значения статусных цветов задаются отдельно для light и dark, сдвигом к светлому и на меньший grade
    (проверено на токенах Carbon и ADS)
    ([Carbon tokens](https://carbondesignsystem.com/building-blocks/foundations/color/tokens),
    [Atlassian tokens](https://atlassian.design/components/tokens/all-tokens)).
11. Error — статическая роль, адаптирующаяся по теме, но не производная акцента
    ([m3 roles](https://m3.material.io/styles/color/roles)). Красный на тёмном фоне не должен быть
    единственным каналом ошибки ([W3C 1.4.3](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html)).
12. Глубина — светлее контейнер, не тень; контейнер не темнее фона; тинт по elevation не используется
    ([Carbon](https://carbondesignsystem.com/building-blocks/foundations/color/overview),
    [m3 elevation tokens](https://m3.material.io/styles/elevation/tokens)).
13. Роль одна, значение от темы: «Roles cannot be changed between themes»
    ([Carbon](https://carbondesignsystem.com/building-blocks/foundations/color/overview)) — имена статусных
    токенов не расходятся между темами.
14. Один цвет — одно значение: цвет подписки не должен означать статус в другой части экрана
    ([HIG Color](https://developer.apple.com/design/human-interface-guidelines/color)).
15. Динамический цвет/пользовательский контраст не должен менять семантику статуса; M3 держит error
    статическим именно для этого ([m3 roles](https://m3.material.io/styles/color/roles)).
