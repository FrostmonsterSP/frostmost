# FrostMost — аудит графического стека и контракт дизайн-системы

**Дата:** 2026-11-08
**Статус:** аудит (что есть) + контракт (что обязано появиться). Раздел 2 — обязывающий контракт
для двух параллельных исполнителей; разделы 1 и 3–7 описывают фактическое состояние ветки
`fork/friendly-ui`.

Документ отвечает на два вопроса: каким графическим стеком форк располагает фактически (по
разрешённым артефактам на диске, а не по ожиданиям) и как выглядит контракт `Motion.kt`, которого
ждут два заблокированных агента.

Метод: `:app:dependencies --configuration playstoreDebugCompileClasspath --offline`, распаковка
`classes.jar` из кэша Gradle и `javap` по байткоду. Всё, что не удалось подтвердить, помечено явно.

---

## 1. Что фактически лежит в стеке

### 1.1 Разрешённые версии

Проверено задачей `:app:dependencies`, не по каталогу версий: каталог задаёт только BOM, а реальные
версии выбирает BOM и транзитивные ограничения.

| Артефакт | Разрешённая версия | Откуда |
|---|---|---|
| `androidx.compose:compose-bom` | `2026.09.00` | `libs.versions.toml`, `composeBom` |
| `androidx.compose.ui:ui` | `1.12.1` | BOM |
| `androidx.compose.foundation:foundation` | `1.12.1` | BOM |
| `androidx.compose.runtime:runtime` | `1.12.1` | BOM |
| `androidx.compose.material3:material3` | `1.4.0` | BOM |
| `androidx.compose.animation:animation` | `1.12.1` | транзитивно, см. 1.2 |
| `io.coil-kt.coil3:coil-compose` | `3.6.3` | `libs.versions.toml`, `coil` |

`compileSdk = 37`, `minSdk = 24`, `targetSdk = 37` — из `V2rayNG/app/build.gradle.kts`.

### 1.2 Вопрос об артефакте анимации — закрыт

**`androidx.compose.animation` не объявлен в `app/build.gradle.kts` явно.** В блоке зависимостей
есть только `ui`, `foundation`, `material3`, `ui-tooling-preview`. Тем не менее артефакт
**присутствует на compile classpath и на диске**, и импортировать его можно.

Почему: `foundation-android:1.12.1` запрашивает `androidx.compose.animation:animation:1.12.1`
напрямую, а BOM `2026.09.00` фиксирует версию всех модулей семейства через ограничения `(c)`.
В дереве зависимостей это видно как:

```text
+--- androidx.compose.foundation:foundation -> 1.12.1
|    \--- androidx.compose.foundation:foundation-android:1.12.1
|         +--- androidx.compose.animation:animation:1.12.1
|         |    \--- androidx.compose.animation:animation-android:1.12.1
|         |         +--- androidx.compose.animation:animation-core:1.12.1
```

Строки `androidx.compose.animation:animation:1.12.1 (c)` — это ограничения BOM, а не запросы; сам
запрос идёт от `foundation`. На диске лежат настоящие AAR, а не только POM:

```text
caches/modules-2/files-2.1/androidx.compose.animation/animation-android/1.12.1/.../animation.aar
caches/modules-2/files-2.1/androidx.compose.animation/animation-core-android/1.12.1/.../animation-core.aar
```

Вывод: писать `import androidx.compose.animation.core.tween` и `animateContentSize(...)` можно без
правки Gradle — это уже транзитивно разрешено, и код в `ui/main/HomeConfiguration.kt`,
`ui/compose/Components.kt`, `ui/compose/Scrollbar.kt` это подтверждает на практике.

**Отдельно, чтобы не путать:** в дереве есть также `org.jetbrains.compose.animation:animation:1.12.0`
(и `org.jetbrains.compose.*:1.12.0` для foundation/runtime). Это androidx-артефакты, подменённые
Compose Multiplatform, и они **не** попадают в APK Android-сборки: `androidx.compose.animation:*`
разрешается в `1.12.1`, а `org.jetbrains.compose.*` в дереве остаётся как источник ограничений.
Импортировать нужно `androidx.compose.animation.*`, иначе модуль соберётся, но классы не те.

### 1.3 Как Compose учитывает системный «убрать анимации»

Это главный вопрос аудита, и ответ проверен по байткоду, а не по документации.

Механизм — `androidx.compose.ui.MotionDurationScale`, элемент `CoroutineContext` с одним членом
`getScaleFactor(): Float`. Он живёт в `ui-android:1.12.1`:

| Класс | Роль |
|---|---|
| `androidx.compose.ui.MotionDurationScale` | интерфейс, `CoroutineContext.Element` + `Key` |
| `androidx.compose.ui.platform.MotionDurationScaleImpl` | реализация; хранит `_scaleFactor` в snapshot-state |
| `WindowRecomposer_androidKt.readAnimationScale` | читает системное значение |
| `WindowRecomposer_androidKt.getAnimationScaleFlowFor` | `StateFlow` + `ContentObserver` на изменение настройки |
| `androidx.compose.ui.scrollcapture.DisableAnimationMotionDurationScale` | локальный «выключить» для захвата прокрутки |

Что именно читается — видно из констант в `WindowRecomposer_androidKt`:

```text
readAnimationScale
animator_duration_scale
android/provider/Settings$Global
```

То есть **ровно та настройка, которую меняет «Убрать анимации» / «Масштаб длительности анимации»**
(Developer options → Animator duration scale). Значение не читается один раз: `getAnimationScaleFlowFor`
подписывается через `ContentObserver` на URI настройки и обновляет `scaleFactor` в snapshot-state,
так что смена настройки подхватывается без перезапуска процесса.

Как масштаб применяется — `animation-core:1.12.1`, `SuspendAnimationKt`:

```text
public static final float getDurationScale(CoroutineContext):
    MotionDurationScale element = context.get(MotionDurationScale.Key)
    scale = element != null ? element.getScaleFactor() : 1f
    check(scale >= 0f) { "negative scale factor" }
    return scale
```

и в `doAnimationFrameWithScale(scope, frameTimeNanos, durationScale, animation, state, onFrame)`:

```text
if (durationScale == 0f) playTimeNanos = animation.getDurationNanos()
else                     playTimeNanos = (frameTimeNanos - scope.startTimeNanos) / durationScale
```

**Практическое следствие, важное для проектирования.** При масштабе `0f` анимация не «зависает» и не
растягивается бесконечно: `playTimeNanos` сразу подставляется равным полной длительности, поэтому
анимация **мгновенно переходит в конечное значение за один кадр**. Это корректное поведение
«убрать анимации»: конечное состояние достигается сразу, промежуточных кадров нет.

Что из этого следует для нас:

- Все API, идущие через `SuspendAnimationKt` — `tween`, `spring`, `keyframes`, `animate*AsState`,
  `Animatable.animateTo`, `Transition`, `animateContentSize`, `AnimatedVisibility` — honour настройку
  автоматически. Своего кода для этого не нужно.
- **`infiniteRepeatable` не входит** в эту гарантию: `InfiniteTransition` не завершается и конечного
  значения не имеет, поэтому масштаб `0f` для него осмысленного «мгновенного финала» не даёт.
  Бесконечных декоративных анимаций в home-компонентах нет, и контракт их не вводит.
- Диалоги, шторки и системные переходы (`ModalBottomSheet`, `Dialog`) анимируются собственными
  механизмами Compose; они тоже идут через тот же `MotionDurationScale`, но это утверждение для них
  я **не проверял по байткоду** — проверены только пути `animation-core`.

Чего я **не** проверял и не утверждаю: поведение на конкретных прошивках, где `animator_duration_scale`
эмулируется не полностью (некоторые vendor-сборки). Проверка на устройстве в этом аудите не
выполнялась — см. раздел 7.

---

## 2. Контракт `Motion.kt` (обязывающий)

Раздел адресован исполнителю, который создаёт файл, и потребителю, который импортирует константы.
Значения не обсуждаются: их нужно воспроизвести буквально.

### 2.1 Расположение

| Что | Значение |
|---|---|
| Пакет | `com.v2ray.ang.ui.compose` |
| Путь файла | `V2rayNG/app/src/main/java/com/v2ray/ang/ui/compose/Motion.kt` |
| Видимость объявлений | `internal` (рядом с `Theme.kt`, `Components.kt`) |
| Импорты, которые понадобятся | `androidx.compose.animation.core.FastOutSlowInEasing`, `FiniteAnimationSpec`, `Spring`, `SpringSpec`, `TweenSpec`, `spring`, `tween`; `androidx.compose.ui.graphics.Color`; `androidx.compose.ui.unit.IntSize` |

Файл не должен содержать ничего, кроме объявлений ниже. Ни `@Composable`-функций, ни состояния, ни
работы с настройками: это чистый набор спецификаций, поэтому он не требует ни `@OptIn`, ни
экспериментальных API.

### 2.2 Первая объявленная сущность — её уже ждёт код

`ui/main/HomeConfiguration.kt` (строки 52 и 140) **уже импортирует и использует**
`com.v2ray.ang.ui.compose.HomeMotionEnterSpec`. Сейчас ветка не компилируется:

```text
e: HomeConfiguration.kt:52:33 Unresolved reference 'HomeMotionEnterSpec'.
e: HomeConfiguration.kt:140:53 Unresolved reference 'HomeMotionEnterSpec'.
```

Поэтому первое объявление в файле обязано иметь **точно** это имя и этот тип.

### 2.3 Точные объявления

**Этот листинг проверен компилятором** — произведён `BUILD SUCCESSFUL` для
`:app:compilePlaystoreDebugKotlin` вместе с реальными потребителями (`HomeConfiguration.kt`,
`HomeStrips.kt`, `HomeCardPager.kt`). Воспроизводить его нужно буквально, включая типы.

**Состояние на момент публикации документа:** файл уже создан исполнителем, содержимое совпадает с
листингом побайтно, и `:app:compilePlaystoreDebugKotlin` проходит (`BUILD SUCCESSFUL`). Раздел
оставлен как обязывающий контракт и как запись проверенных типов, а не как запрос на создание.

```kotlin
package com.v2ray.ang.ui.compose

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize

/**
 * The motion vocabulary of the fork: four durations and four specs, nothing else.
 *
 * Every spec goes through androidx.compose.animation.core, so the system "remove animations"
 * setting (Settings.Global.animator_duration_scale, surfaced to Compose as MotionDurationScale)
 * collapses each of them to its final value in a single frame. No spec here needs its own guard.
 */

/** 90 ms: a colour or alpha change that must not read as a transition. */
internal const val HOME_MOTION_INSTANT_MS: Int = 90

/** 180 ms: the standard length of a structural change, e.g. a section opening. */
internal const val HOME_MOTION_STANDARD_MS: Int = 180

/** 240 ms: a change that moves something the user is looking at. */
internal const val HOME_MOTION_EMPHASIS_MS: Int = 240

/** 320 ms: the longest length allowed; reserved for the connection card switching. */
internal const val HOME_MOTION_MAX_MS: Int = 320

/** Size and position changes of a section as it opens or closes; fed to `animateContentSize`. */
internal val HomeMotionEnterSpec: FiniteAnimationSpec<IntSize> =
    tween(durationMillis = HOME_MOTION_STANDARD_MS, easing = FastOutSlowInEasing)

/** Colour and alpha changes, including the lit/unlit screen background. */
internal val HomeMotionFadeSpec: TweenSpec<Color> =
    tween(durationMillis = HOME_MOTION_INSTANT_MS, easing = FastOutSlowInEasing)

/** Slow settle of something the user is dragging or that has just landed. */
internal val HomeMotionEmphasisSpec: TweenSpec<Float> =
    tween(durationMillis = HOME_MOTION_EMPHASIS_MS, easing = FastOutSlowInEasing)

/**
 * The press scale of a tile, as a spec rather than a raw spring: no bounce, so a finger that rests
 * on a tile does not make it wobble. Fed to `animateFloatAsState`, so it is typed on `Float`.
 */
internal fun homeMotionSpringSpec(): SpringSpec<Float> =
    spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
```

### 2.3.1 Три ловушки типов, стоившие компиляции

Эти три места невозможно угадать: они выглядят очевидно и при этом не компилируются. Зафиксировано
по фактическим ошибкам компилятора.

1. **`HomeMotionEnterSpec` не может быть `TweenSpec<Float>`.** `Modifier.animateContentSize`
   принимает `FiniteAnimationSpec<IntSize>`, потому что анимирует размер, а не скаляр:

   ```text
   Argument type mismatch: actual type is 'TweenSpec<Float>', but 'FiniteAnimationSpec<IntSize>' was expected.
   ```

   Правильный тип — `FiniteAnimationSpec<IntSize>` (проверено).

2. **`HomeMotionFadeSpec` не может быть `TweenSpec<Float>`.** `animateColorAsState` ждёт спецификацию
   на `Color`:

   ```text
   Argument type mismatch: actual type is 'TweenSpec<Float>', but 'AnimationSpec<Color>' was expected.
   ```

   Правильный тип — `TweenSpec<Color>` (проверено: `tween(...)` выводит `TweenSpec<Color>` из
   целевого типа объявления).

3. **`Spring` — не generic.** В `animation-core:1.12.1` `androidx.compose.animation.core.Spring` — это
   объект-держатель констант (`StiffnessMediumLow`, `DampingRatioNoBouncy`, …), а фабрика `spring(...)`
   возвращает `SpringSpec<T>`:

   ```text
   Spring: public final class androidx.compose.animation.core.Spring { … INSTANCE; StiffnessMediumLow; … }
   spring: public static final <T> SpringSpec<T> spring(float, float, T);
   ```

   Поэтому `Spring<T>` в качестве возвращаемого типа — ошибка (`No type arguments expected for
   'object Spring'`), а правильный возвращаемый тип — `SpringSpec<Float>`.

Дополнительно: `homeMotionSpringSpec()` **обязателен**, а не опционален. `ui/main/HomeStrips.kt`
уже импортирует его и применяет к масштабу нажатия плитки:

```kotlin
val scale by animateFloatAsState(
    targetValue = if (enabled && pressed) HOME_TILE_PRESS_SCALE else 1f,
    animationSpec = homeMotionSpringSpec(),
    label = "homeTilePress",
)
```

где `HOME_TILE_PRESS_SCALE = 0.97f`. Отсюда и тип `Float`: спецификация питает `animateFloatAsState`.

### 2.3.2 Цветовые спецификации: `HomeMotionFadeSpec` и `HomeMotionEmphasisColorSpec`

Промежуточная редакция этой ветки собирала цветовую спецификацию карты локальным `tween(...)` в
`HomeCardPager.kt`, потому что `HomeMotionFadeSpec` был `TweenSpec<Float>` и `animateColorAsState` его
не принимал. Причина снята: спецификации теперь `TweenSpec<Color>`, и оба нужных значения объявлены
в `Motion.kt`:

```kotlin
internal val HomeMotionFadeSpec: TweenSpec<Color>          // 90 мс, тихое состояние
internal val HomeMotionEmphasisColorSpec: TweenSpec<Color> // 240 мс, смена статуса на глазах
```

`HomeCardPager.kt` берёт их как есть, без локальных чисел:

```kotlin
val settled = status == HomeStatus.DISCONNECTED || status == HomeStatus.CONNECTED
val statusColor by animateColorAsState(
    targetValue = statusColorTarget,
    animationSpec = if (settled) HomeMotionFadeSpec else HomeMotionEmphasisColorSpec,
    label = "homeStatusColor",
)
```

Правило на будущее: цветовая анимация берётся из `Motion.kt`, а не собирается на месте, чтобы
длительности не разъезжались по файлам.

### 2.4 Что именно обязательно, а что — оформление

Если исполнитель сокращает файл, обязательны к сохранению:

| Имя | Тип | Почему обязательно |
|---|---|---|
| `HomeMotionEnterSpec` | `FiniteAnimationSpec<IntSize>` | импортируется в `HomeConfiguration.kt`; `animateContentSize` требует именно `IntSize`, см. 2.3.1 |
| `HomeMotionFadeSpec` | `TweenSpec<Color>` | `animateColorAsState` ждёт `AnimationSpec<Color>`, см. 2.3.1 |
| `HomeMotionEmphasisSpec` | `TweenSpec<Float>` | зарезервирован под масштаб и отступы |
| `homeMotionSpringSpec()` | `fun (): SpringSpec<Float>` | уже вызывается в `HomeStrips.kt` для масштаба нажатия |
| `HOME_MOTION_INSTANT_MS` | `Int` | 90 |
| `HOME_MOTION_STANDARD_MS` | `Int` | 180 |
| `HOME_MOTION_EMPHASIS_MS` | `Int` | 240 |
| `HOME_MOTION_MAX_MS` | `Int` | 320 |

Обязательные требования к значениям:

1. **`HomeMotionEnterSpec` — `tween` на 180 мс с `FastOutSlowInEasing`.** Не `spring`: он уже стоит в
   `animateContentSize` у `LazyListScope`-элемента секции, а пружину в этом месте нельзя измерить
   тестом и она расходится с остальной сеткой. Не длиннее `HOME_MOTION_STANDARD_MS`.
2. **`HOME_MOTION_INSTANT_MS` (90, в спецификации — `HomeMotionFadeSpec`) — для цвета и прозрачности**,
   чтобы фон экрана и подсветки не «переезжали» заметно.
3. **`HOME_MOTION_MAX_MS` (320) — верхняя граница.** Ни одна спецификация в форке не может быть
   длиннее: 320 мс при `animator_duration_scale = 1` — предел, после которого смена подписки
   воспринимается как задержка.
4. **Только `internal`.** Ни одно объявление не публикуется наружу модуля.

### 2.5 Как потребителю подключать

```kotlin
import com.v2ray.ang.ui.compose.HomeMotionEnterSpec
import com.v2ray.ang.ui.compose.HomeMotionFadeSpec

// размер/раскрытие
Modifier.animateContentSize(animationSpec = HomeMotionEnterSpec)

// цвет/прозрачность
val background by animateColorAsState(
    targetValue = ...,
    animationSpec = HomeMotionFadeSpec,
    label = "homeBackground",
)
```

`label` в `animate*AsState` обязателен: он попадает в трассировку и без него отладка анимаций
невозможна. Соглашение о метках в форке — `camelCase` от того, что меняется
(`"homeBackground"`, `"ReorderableElevation"`).

### 2.6 Чего контракт не разрешает

- Никаких `infiniteRepeatable` и `rememberInfiniteTransition` — они не сворачиваются в конечное
  состояние при `animator_duration_scale = 0`.
- Никаких сырых `tween(400)` и подобных чисел в вызовах: только константы из этого файла.
- Никаких `tween(0)` как «отключить анимацию»: при масштабе `1` это рывок, при масштабе `0` всё
  равно один кадр. Роль «мгновенно» играет `HOME_MOTION_INSTANT_MS`.
- Никаких новых зависимостей в Gradle: `androidx.compose.animation` уже транзитивно разрешён
  (раздел 1.2).

---

## 3. Тема: что есть и чего нет

`ui/compose/Theme.kt` — единственный владелец темы. В нём:

- `LightColor` и `DarkColor` — полные схемы, включая `surfaceContainerLow..Highest`;
- семантические литералы (`colorPing`, `colorConfigType`, `toast*`, `dividerColor*`);
- `ThemeManager` — `MmkvManager`-состояние режима ночи и динамического цвета;
- `resolveDarkTheme()`, `LocalDarkTheme`;
- `AppTheme(...)` → `MaterialTheme(colorScheme = colorScheme)`.

**Чего в нём нет и что поэтому не существует как токенов:**

| Ожидаемо | Фактически |
|---|---|
| `Typography` | нет; используется `MaterialTheme.typography` по умолчанию, веса переопределяются на месте (`SemiBold`, `Bold` в вызовах) |
| `Shapes` | нет; формы задаются локальными `private val` в файлах-потребителях |
| `Dimensions.kt` | нет; все отступы — литеральные `.dp` в местах использования |
| `Motion.kt` | нет; см. раздел 2 |

Это не дефект и не приглашение к рефакторингу: правка темы за пределами задачи запрещена правилами
репозитория. Фиксирую как факт, чтобы следующий агент не искал несуществующие файлы.

### 3.1 Фактическая шкала размеров

Собрано по литералам в `ui/main/` и `ui/compose/`.

| Роль | Значение | Где |
|---|---|---|
| Шаг сетки | `8.dp` | почти везде; исключения — `3`, `4`, `6`, `7`, `10`, `11`, `12`, `14` |
| Боковой отступ экрана | `16.dp` | главный экран |
| Радиус карты | `24.dp` | `HomeCardPager.CARD_SHAPE` |
| Радиус кнопки питания | `26.dp` | `HomeCardPager.POWER_SHAPE` |
| Радиус плитки/полосы | `14.dp` | `HomeCardPager.ACTION_SHAPE`, `HomeStrips.STRIP_SHAPE` |
| Радиус инструмента списка | `12.dp` | `HomeConfiguration.SERVER_TOOL_SHAPE` |
| Радиус полосы трафика | `3.dp` | `HomeCardPager.kt:357` |
| Минимальная высота строки/секции | `56.dp` | `HomeServerRows`, `HomeSheets`, `HomeScreen` (топ-бар), `HomeConfiguration` (заголовок) |
| Минимальная высота строки правил | `48.dp` | `HomeConfiguration.kt:289`, `HomeStrips` (полоса проблемы) |
| Минимальная высота карты | `216.dp` | `HomeCardPager.kt:306` |
| Минимальная высота полосы правил | `52.dp` | `HomeStrips.kt:242` |
| Минимальная высота ряда действий | `58.dp` | `HomeCardPager.kt:498` |
| Кнопка питания | `84.dp` | `HomeCardPager.kt:425` |
| Кнопка «Ещё» | `48.dp`, радиус `14.dp` | `HomeScreen.kt:301` |
| Пилюля «Добавить» | высота `40.dp` | `HomeScreen.kt:328` |

**Наблюдение для будущего шрифта токенов:** четыре радиуса (`12`, `14`, `24`, `26`) и шесть
минимальных высот (`48`, `52`, `56`, `58`, `216`, `84`) — это ровно тот набор, который просится в
`Dimensions.kt`. Пока их владельцы — локальные `private val` в четырёх файлах, и это осознанно:
каждый радиус принадлежит одному компоненту. Сводить их в один файл — отдельная задача, не эта.

---

## 4. Владельцы цветовых решений на главном экране

`ui/main/HomeStrips.kt` — владелец нейтральной пары и предупреждающей пары. Здесь же `HomeTile`.

| Объявление | Тип | Смысл |
|---|---|---|
| `OnBackgroundColors` | `internal data class (container: Color, content: Color)` | пара цветов блока, лежащего на фоне экрана |
| `homeNeutralSurface(lit: Boolean)` | `@Composable internal fun` | `lit=false` → `surface`/`onSurface`; `lit=true` → белый `HOME_ON_ACCENT_OVERLAY` / белый |
| `warnColors()` | `@Composable internal fun` | фиксированная оранжевая пара, не зависит от динамического цвета |
| `HomeTile(...)` | `@Composable internal fun` | плитка: `clip(shape)`, фон, опциональная обводка, `selected`, слияние семантики |

`HomeTile` — единственный правильный способ нарисовать «плитку на фоне»: он сам ставит
`LocalContentColor` из пары и сам сливает потомков в одну семантическую ноду с `Role.Button`. Любой
новый блок того же вида обязан идти через него, иначе разъедется и цвет, и TalkBack.

Правило акцента (повторено из `AGENTS.md` и карты): акцент принадлежит **только** карте подписки и
экрану подключённого состояния. Всё остальное — `homeNeutralSurface(lit)`. Модальная шторка на
акценте — запрещена.

Контраст белого на акценте проверяется тестами, а не глазами:
`HomeModelsTest.accentsAreStableAndReadableWithWhite`, `whiteTextReadableOnOverlayOverEveryAccent`,
`bothPalettesKeepWhiteReadable` — порог `4.5`, включая белую подложку `0.10` поверх цвета.

---

## 5. Уже существующая анимация: инвентарь

Чтобы контракт из раздела 2 не плодил дубли, вот всё, что уже анимируется, и чем.

| Место | API | Спецификация сейчас |
|---|---|---|
| `ui/compose/Components.kt:111` | `AnimatedVisibility` + `expandVertically()` / `shrinkVertically()` | по умолчанию (spring) |
| `ui/compose/Components.kt:265` | `animateDpAsState` | по умолчанию (spring), `label = "ReorderableElevation"` |
| `ui/compose/Scrollbar.kt:51–57` | `Animatable` + `tween(fadeAnimDurationMs)` | `300` мс по умолчанию, `fadeOutDurationMs` рядом |
| `ui/main/HomeScreen.kt:102` | `animateColorAsState` | по умолчанию, `label = "homeBackground"` |
| `ui/main/HomeConfiguration.kt` | `animateContentSize(HomeMotionEnterSpec)` | 180 мс, раскрытие секции маршрутизации и серверов |
| `ui/main/HomeStrips.kt` | `animateFloatAsState(homeMotionSpringSpec())` | пружина без отскока, `label = "homeTilePress"`, масштаб `0.97f` |
| `ui/main/HomeCardPager.kt:320` | цвет статуса: `HomeMotionFadeSpec` / `HomeMotionEmphasisColorSpec` | 90 / 240 мс из `Motion.kt` |
| `ui/main/HomeCardPager.kt:422–424` | `AnimatedContent` статуса | `fadeIn(180)` + `fadeOut(90)` + `SizeTransform` |
| `ui/server/ServerProxyChainActivity.kt:299` | `animateDpAsState` | по умолчанию |

Читается так: весь главный экран уже идёт через `Motion.kt`; три места вне него (дерево компонентов,
скроллбар, редактор цепочки) остаются на дефолтных пружинах Compose.

**Важно:** контракт `Motion.kt` писался не «на будущее» — три потребителя уже его требуют. Поэтому
типы в разделе 2 обязаны совпадать с их ожиданиями, и проверены они компиляцией.

Отдельно про `Scrollbar`: его `fadeAnimDurationMs = 300` — собственный параметр конфигурации, а не
токен дизайн-системы. Он остаётся как есть; `HOME_MOTION_MAX_MS` его не покрывает.

---

## 6. Coil 3

`io.coil-kt.coil3:coil-compose:3.6.3` — уже зависимость (`libs.coil.compose`), в коде
используется один раз: `ui/compose/Components.kt` (`AsyncImage`, `ImageRequest`).

Практические выводы для будущих экранов:

- Загрузка картинки и так вне главного потока внутри Coil; своего `Dispatchers.IO` для `AsyncImage`
  не нужно.
- Coil 3 требует `ImageLoader` в `Application`-скоупе для дискового кэша; в проекте отдельного
  `ImageLoader` не заводится, работает дефолтный синглтон. Отдельный загрузчик — вне этой задачи.
- `AsyncImage` рисует в Compose-ноду, поэтому не пересекается с `Motion.kt`.

---

## 7. Что этим аудитом не проверено

Честно, чтобы не выдать аудит за приёмку:

| Проверка | Статус | Причина / результат |
|---|---|---|
| `git diff --check` по этому файлу | **run** | чисто |
| Компиляция листинга из 2.3 с реальными потребителями | **verified** | `:app:compilePlaystoreDebugKotlin --offline` → `BUILD SUCCESSFUL in 1m 6s`. Спецификация клалась в `ui/compose/Motion.kt` на время проверки, затем удалена; `HomeConfiguration.kt`, `HomeStrips.kt`, `HomeCardPager.kt` не правились |
| `:app:testPlaystoreDebugUnitTest` | **Not run** | документация не требует; логика не менялась |
| `:app:assemblePlaystoreDebug` | **Not run** | Gradle и зависимости не менялись |
| Поведение при `animator_duration_scale = 0` на устройстве | **Not run** | нужен эмулятор/устройство; вывод раздела 1.3 получен из байткода `animation-core`/`ui` |
| Поведение при `animator_duration_scale = 0` на vendor-прошивках | **Not run** | требуются конкретные устройства |
| Поведение анимации `AnimatedContent` в карте подписки | **Not run** | только компиляция; визуальная проверка не выполнялась |

Важная оговорка к строке «verified». Компиляция подтверждает **типы и разрешимость имён**, то есть
что контракт из 2.3 действительно собирается вместе с существующими потребителями. Она **не**
подтверждает, что выбранные длительности и изинги выглядят хорошо — это решается глазами на
устройстве и в этот аудит не входит.

Побочное ограничение среды: в лог каждой сборки попадает
`FileSystemException: .../kotlin/daemon/...tmp: Файловая система доступна только для чтения` —
`sandbox` запрещает Kotlin-демону писать в `$HOME/.local/share/kotlin`. На результат это не влияет
(сборка завершается успешно), но строку не нужно принимать за ошибку компиляции.

---

## 8. Сводка фактов и опровержений

Подтверждено:

1. BOM `2026.09.00` разрешается в Compose UI **1.12.1**, Material3 **1.4.0**.
2. `androidx.compose.animation:animation:1.12.1` доступен транзитивно через `foundation`; правка
   Gradle не нужна. Строки `(c)` в дереве — ограничения BOM, а не запросы.
3. Compose читает `Settings.Global.animator_duration_scale` через `ContentObserver`
   (`WindowRecomposer_androidKt.getAnimationScaleFlowFor`) и передаёт его как `MotionDurationScale`.
4. При масштабе `0f` `SuspendAnimationKt.doAnimationFrameWithScale` подставляет полную длительность
   анимации, поэтому анимация **мгновенно достигает конечного значения за один кадр**.
5. `ui/compose/Theme.kt` — единственный владелец темы; `Typography`/`Shapes`/`Dimensions`/`Motion`
   отсутствуют.
6. `HomeStrips.kt` владеет `OnBackgroundColors`, `homeNeutralSurface`, `warnColors`, `HomeTile`.
7. Coil 3 в зависимостях и используется один раз в `Components.kt`.
8. `HomeModelsTest` держит порог `4.5` для белого на обоих палитрах и на подложке `0.10`.

Опровергнуто / уточнено относительно исходного брифа:

9. **`ui/main/HomeServersSheet.kt` больше не существует.** Файл переименован в
   `ui/main/HomeDialogs.kt` параллельной работой (в `git status` это
   `RM HomeServersSheet.kt -> HomeDialogs.kt`). В `HomeDialogs.kt` 75 строк, **9** импортов, все
   использованы; «~40 неиспользуемых импортов» там уже нет. Переименование сделано другим агентом,
   не этим — фиксирую как факт состояния ветки, а не как свою правку.
10. **Ветка не компилировалась** из-за отсутствующего `Motion.kt` (раздел 2.2). Раздел 2 это
    закрывает: листинг из 2.3 собран компилятором вместе с потребителями.
11. **Три типа спецификаций невозможно угадать** (2.3.1): `IntSize` для `animateContentSize`, `Color`
    для `animateColorAsState`, `SpringSpec<Float>` вместо `Spring<T>`. Ранняя версия контракта была
    именно такой неверной — её и поймала компиляция, а не ревью.
12. **`HomeCardPager.kt` больше не обходит контракт.** Локальный `tween(...)` заменён на
    `HomeMotionFadeSpec` и `HomeMotionEmphasisColorSpec` из `Motion.kt`; неиспользуемые после этого
    импорты (`FastOutSlowInEasing`, `HOME_MOTION_EMPHASIS_MS`) удалены (2.3.2).
13. Потребителей у контракта не один, а три: `HomeConfiguration.kt` (`animateContentSize`),
    `HomeStrips.kt` (масштаб нажатия плитки через `animateFloatAsState`), `HomeCardPager.kt`
    (цвет статуса и `AnimatedContent` карты).

---

## 9. Следующий шаг

Сделано в этой же ветке:

1. `ui/compose/Motion.kt` создан по разделу 2.3 и принят компилятором вместе с потребителями.
2. `HomeCardPager.kt` переведён на `HomeMotionFadeSpec` / `HomeMotionEmphasisColorSpec` (2.3.2).
3. Плитка `HomeTile` получила единый отклик на нажатие — масштаб на `graphicsLayer`, одна точка на
   все девять вызовов.

Осталось:

4. Перевести `Components.kt` и `HomeScreen.kt` с дефолтных пружин на спецификации из `Motion.kt`.
5. Проверить на устройстве с «Убрать анимации» = вкл, что раскрытие секций, смена фона и цвет
   статуса доходят до конечного состояния без промежуточных кадров.
6. Свести четыре радиуса и шесть минимальных высот из 3.1 в общий файл размеров (`Dimensions.kt`),
   когда появится вторая область, которая их переиспользует; сейчас у каждого радиуса один владелец,
   и сводить их ради самого факта свода не нужно.
