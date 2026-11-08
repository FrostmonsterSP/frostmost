# FrostMost — пересборка интерфейса с нуля

**Дата:** 2026-11-08 · **Ветка:** fork/friendly-ui
**Решение владельца:** пересобрать интерфейс полностью. Прежний UI уничтожается, а не правится.

## Направление (выбрано владельцем)

- **Визуальный язык:** мягкий «финансовый» — крупные числа трафика, выраженные скругления,
  настоящая глубина (тень только у главного действия и hero-карты), карточки с внутренними панелями.
- **Типографика:** собственный шрифт **Manrope** (OFL, кириллица проверена) — 4 веса в `res/font/`.
- **Объём:** главный экран + все Compose-экраны (настройки, редакторы серверов, роутинг).

## Фундамент (готов, компилируется)

| Файл | Что даёт |
|---|---|
| `ui/compose/FrostTypography.kt` | Manrope, шкала из 8 ступеней, трекинг, `LineHeightStyle` |
| `ui/compose/FrostShapes.kt` | `FrostRadius` (chip 12 → tile 18 → inner 20 → card 28 → hero 34 → pill), `FrostElevation` |
| `ui/compose/FrostSpacing.kt` | шкала 4/8/12/16/24/32/48, отступы экрана/карты/ряда, `FrostSurfaceLevel` |
| `ui/compose/FrostSurface.kt` | **`FrostSurfaceColors(container, content)`** + `FrostSurface` — пара цветов создаётся вместе и раздаётся через `LocalContentColor`; «чёрное на чёрном» становится невозможным |
| `ui/compose/FrostComponents.kt` | `FrostPrimaryButton` / `FrostSecondaryButton` / `FrostTextButton` / `FrostCircleAction` / `FrostChip` |
| `ui/main/HeroConnectionCard.kt` | новая hero-карта: крупный трафик, статус-панель, акцент только здесь |
| `ui/main/HeroPowerControl.kt` | круглый power-контрол + `ConfigureChip` |

## Железные правила для всех исполнителей

1. **Ни одного цвета литералом.** Только `FrostSurfaceColors`, `MaterialTheme.colorScheme`,
   `LocalStatusColors` или именованный токен `Theme.kt`.
2. **Никакого `HomeTile`, `homeNeutralSurface`, `warnColors`, `OnBackgroundColors`** в новом коде —
   эти типы удаляются вместе со старым интерфейсом.
3. Текст на акценте — только `Color.White` / `Color.White.copy(alpha = 0.78f)`.
4. Отступы и радиусы — только из `FrostSpacing` / `FrostRadius`.
5. Поведение (VPN, подписки, серверы, роутинг, импорт) не меняется.

## Разбиение по субагентам

| # | Задача | Файлы (эксклюзивно) |
|---|---|---|
| S1 | Главный экран: каркас, секции, список серверов на новом фундаменте; удаление `HomeStrips`/`HomeCardPager` | `ui/main/HomeScreen.kt`, `HomeSections*`, `HomeServerRows.kt`, `HomeConfiguration.kt`, `HomeModels.kt` |
| S2 | Настройки + per-app proxy на новых компонентах | `ui/settings/*`, `ui/perappproxy/*` |
| S3 | Редакторы серверов (11 экранов) на новом фундаменте | `ui/server/*` |
| S4 | Роутинг, подписки, бэкап, apppicker, userasset, logcat, checkupdate, about | остальные `ui/**` |
| S5 | Удаление старых компонентов + проверка на устройстве | `ui/compose/Components.kt`, `HomeSheets.kt` |
