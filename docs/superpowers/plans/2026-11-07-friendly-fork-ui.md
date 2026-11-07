# Форк v2rayNG с человеколюбивым интерфейсом: план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Собрать форк, в котором человек со ссылкой проходит путь «нажал → увидел подписку → включил» без объяснений, а весь функционал v2rayNG сохранён.

**Architecture:** Новый интерфейс лежит рядом со старым: новые файлы `Home*.kt` в `ui/main`, а `MainActivity.ScreenContent` вызывает `HomeScreen` вместо `MainScreen`. Состояние остаётся в `MainViewModel` и `MainAction`, чистая логика вынесена в отдельные файлы с JVM-тестами. Старые композаблы не удаляются, чтобы слияния с v2rayNG проходили без конфликтов.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, coroutines, MMKV, JUnit 5, Mockito. Версии брать из `V2rayNG/gradle/libs.versions.toml`.

**Spec:** `docs/superpowers/specs/2026-11-07-friendly-fork-ui-design.md`. Живой макет рядом с ней: `2026-11-07-friendly-fork-ui-mockup.html`. Он определяет вид и текст экранов.

## Global Constraints

- Читать `AGENTS.md` и `V2rayNG/app/src/main/java/com/v2ray/ang/ui/AGENTS.md` до правок; `service/AGENTS.md` до правки `MainActivity`-вызовов `LauncherManager`.
- Пакет и `namespace` `com.v2ray.ang` не переименовывать. Правки ядра, хранилища и манифеста минимальны.
- Новые поля хранимых данных только добавлять, с тестом чтения старого формата.
- Весь видимый и доступный текст в ресурсах, ключи в `values/strings.xml` (en) и `values-ru/strings.xml`. Русский текст берётся из спеки и макета.
- Идентичность подписок и серверов только по GUID, не по индексу.
- Никогда не логировать адреса подписок.
- Коммиты без упоминания Claude и без `Co-Authored-By`: так требует личный CLAUDE.md автора.
- Перед каждым коммитом выполнять проверку инструкций из `AGENTS.md`. Индексировать только перечисленные файлы, не `git add .`.
- Gradle запускать из `V2rayNG/`. Базовая проверка задачи: `./gradlew :app:testPlaystoreDebugUnitTest :app:compilePlaystoreDebugKotlin`.
- Push не делать без отдельного разрешения автора. `origin` указывает на форк автора `FrostmonsterSP/frostmost`, `upstream` на `2dust/v2rayNG`.

## Review Focus

1. Удалена последняя или активная подписка: приложение возвращается к первому запуску, не падает на пустом списке. Тест в задаче 4.
2. Обновление подписки заменило GUID серверов: запомненный сервер исчез, берётся первый сервер группы. Тест в задаче 4.
3. Ссылка без параметра `url` или с адресом не http(s): показывается ошибка, ничего не сохраняется. Тест в задаче 3.
4. Одна ссылка нажата повторно: открывается существующая карта, дубля и ошибки нет. Тест в задаче 2.
5. Новый свайп или выключение VPN во время окна отмены: старое отложенное переключение не срабатывает. Тест в задаче 5.

---

### Task 1: Ветка, сборка и личность форка

**Files:**
- Modify: `V2rayNG/gradle.properties`, `V2rayNG/app/build.gradle.kts`, `V2rayNG/app/src/main/AndroidManifest.xml`, `V2rayNG/app/src/main/java/com/v2ray/ang/AppConfig.kt`
- Modify: `V2rayNG/app/src/main/res/values/strings.xml`, `values-ru/strings.xml`
- Add to commit: спека, макет и план в `docs/superpowers/`

**Interfaces:**
- Produces: `BuildConfig.LINK_SCHEME: String`; manifest placeholder `linkScheme`; свойства `fork.applicationId`, `fork.appName`, `fork.linkScheme`.

- [ ] **Step 1: Создать ветку и локальные настройки**

```bash
git switch -c fork/friendly-ui
echo '.superpowers/' >> .git/info/exclude
git fetch upstream master
```

Expected: `git remote get-url upstream` печатает ровно `https://github.com/2dust/v2rayNG.git`. `origin` не менять.

- [ ] **Step 2: Подготовить сборку чистого checkout**

Инициализировать подмодули рекурсивно и воспроизвести шаги HEV и AAR из `.github/workflows/build.yml`. Каталога `V2rayNG/app/libs` сейчас нет. Ревизия AAR должна совпасть с `git ls-tree HEAD AndroidLibXrayLite`. Эти файлы не коммитить.

Run: `./gradlew :app:compilePlaystoreDebugKotlin`
Expected: `BUILD SUCCESSFUL` до любых правок. Если сборка невозможна, остановиться и сообщить автору причину.

- [ ] **Step 3: Задать личность в сборке**

В `gradle.properties` добавить значения: `fork.applicationId=pro.depworks.frostmost`, `fork.appName=FrostMost`, `fork.linkScheme=fmost`. Значения утвердил автор. Регистр важен: заглавная M показывает «мост».

В `build.gradle.kts`: `applicationId` читать из свойства, `namespace` не менять; `buildConfigField("String", "LINK_SCHEME", ...)`, `manifestPlaceholders["linkScheme"]`, `resValue("string", "app_name", ...)`. Убрать `app_name` из `values/strings.xml` и `values-ru/strings.xml`, из остальных локалей только если сборка сообщит о дубликате. `localeFilters` сократить до `"en", "ru"`, папки других переводов не удалять.

В манифесте заменить `android:scheme="v2rayng"` на `${linkScheme}`.

- [ ] **Step 4: Заменить адреса 2dust**

В `AppConfig.kt`: добавить `fork.repository=FrostmonsterSP/frostmost` в `gradle.properties` и строить из него через `BuildConfig` адреса `APP_URL`, `APP_API_URL`. `APP_PRIVACY_POLICY` направить на `CR.md` этого репозитория. Telegram-канал и `APP_PROMOTION_URL` оставить в коде: их пункты не показываются в новом интерфейсе. `ANDROID_PACKAGE_NAME_LIST_URL` не менять.

- [ ] **Step 5: Проверить сборку и установку**

Run: `./gradlew :app:assemblePlaystoreDebug`
Expected: `BUILD SUCCESSFUL`; `aapt dump badging` показывает новый `package` и только локали `en`, `ru`.

- [ ] **Step 6: Commit**

```bash
git add docs/superpowers V2rayNG/gradle.properties V2rayNG/app/build.gradle.kts V2rayNG/app/src/main/AndroidManifest.xml V2rayNG/app/src/main/java/com/v2ray/ang/AppConfig.kt V2rayNG/app/src/main/res/values/strings.xml V2rayNG/app/src/main/res/values-ru/strings.xml
git commit -m "build: give the fork its own identity and limit locales to en and ru"
```

### Task 2: Данные подписки

**Files:**
- Modify: `dto/entities/SubscriptionUsageInfo.kt`, `dto/entities/SubscriptionItem.kt`, `dto/SubscriptionUpdateResult.kt`, `handler/SubscriptionInfoParser.kt`, `handler/AngConfigManager.kt` (все пути от `V2rayNG/app/src/main/java/com/v2ray/ang/`)
- Create: `dto/SubscriptionImportResult.kt`
- Test: `V2rayNG/app/src/test/java/com/v2ray/ang/handler/SubscriptionImportTest.kt`, существующий тест `SubscriptionInfoParser`

**Interfaces:**
- Produces:
  - `SubscriptionUsageInfo.announce: String? = null`
  - `SubscriptionItem.selectedGuid: String? = null`
  - `enum class SubscriptionFailure { DOWNLOAD, NO_SERVERS }`, поле `SubscriptionUpdateResult.failure: SubscriptionFailure? = null`
  - `sealed interface SubscriptionImportResult { Added(guid: String), AlreadyExists(guid: String), DownloadFailed, NoServers }`
  - `AngConfigManager.importSubscriptionUrl(url: String): SubscriptionImportResult`
  - `AngConfigManager.previewSubscriptionTitle(url: String): String?`
  - `internal fun resolveImportedRemarks(current: String, title: String?): String`

- [ ] **Step 1: Написать падающие тесты**

```kotlin
@Test fun parsesPlainAndBase64Announce() {
    val plain = SubscriptionInfoParser.parse(null, null, null, null, announce = "Работы")
    val encoded = SubscriptionInfoParser.parse(null, null, null, null, announce = "base64:0KDQsNCx0L7RgtGL")
    assertEquals("Работы", plain?.announce)
    assertEquals("Работы", encoded?.announce)
}
@Test fun readsSubscriptionItemWithoutNewFields() {
    val item = JsonUtil.fromJson("{\"remarks\":\"a\",\"url\":\"https://x\"}", SubscriptionItem::class.java)
    assertNull(item.selectedGuid)
}
@Test fun titleReplacesOnlyAutomaticName() {
    assertEquals("Наш VPN", resolveImportedRemarks("import sub", "Наш VPN"))
    assertEquals("Моё имя", resolveImportedRemarks("Моё имя", "Наш VPN"))
    assertEquals("import sub", resolveImportedRemarks("import sub", " "))
}
```

Для `importSubscriptionUrl` тестируем выделенную чистую функцию решения: `decideImport(existingGuid = "g", update = null)` даёт `AlreadyExists("g")`, `update.failure == DOWNLOAD` даёт `DownloadFailed`, `NO_SERVERS` даёт `NoServers`, успех даёт `Added`.

- [ ] **Step 2: Запустить и увидеть падение**

Run: `./gradlew :app:testPlaystoreDebugUnitTest --tests '*SubscriptionImportTest' --tests '*SubscriptionInfoParser*'`
Expected: ошибка компиляции, новых символов нет.

- [ ] **Step 3: Реализовать**

- `SubscriptionInfoParser.parse`: параметр `announce: String? = null` перед `decodeBase64`, разбор тем же способом, что `decodeTitle`. Заголовок читать как `headers["Announce"]`.
- `updateConfigViaSub`: при успехе `remarks = resolveImportedRemarks(remarks, usageInfo?.title)`; пустой ответ даёт `failure = DOWNLOAD`, ноль серверов даёт `failure = NO_SERVERS`. Константа `"import sub"` общая с `importUrlAsSubscription`.
- `importSubscriptionUrl`: существующий адрес даёт `AlreadyExists`; иначе создаёт подписку, обновляет только её, при неудаче удаляет созданную запись (`MmkvManager.removeSubscription`). Для этого `MmkvManager.encodeSubscription` начинает возвращать ключ: `String` вместо `Unit`.
- `previewSubscriptionTitle`: один GET через тот же запрос, что в `updateConfigViaSub`, возвращает `Profile-Title`. Запрос вынести в приватную функцию, чтобы не дублировать.
- Удалить `LogUtil.i(AppConfig.TAG, url)` в `updateConfigViaSub`.

- [ ] **Step 4: Запустить тесты**

Run: команда из шага 2.
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

Сообщение: `feat: keep subscription titles, announcements and failure reasons`.

### Task 3: Распознавание ввода и пресет правил

**Files:**
- Create: `handler/ImportClassifier.kt`, `handler/RoutingPresetMatcher.kt`
- Modify: `handler/SettingsManager.kt`
- Test: `handler/ImportClassifierTest.kt`, `handler/RoutingPresetMatcherTest.kt`

**Interfaces:**
- Produces:
  - `sealed interface ImportKind { Subscription(url: String), Config, Routing, Invalid }`
  - `ImportClassifier.classify(text: String): ImportKind`
  - `data class DeepLinkRequest(val url: String)`, `ImportClassifier.parseDeepLink(host: String?, urlParam: String?): DeepLinkRequest?`
  - `RoutingPresetMatcher.match(current: List<RulesetItem>, presets: Map<RoutingType, List<RulesetItem>>): RoutingType?`
  - `SettingsManager.currentRoutingPreset(context: Context): RoutingType?`

- [ ] **Step 1: Написать падающие тесты**

```kotlin
@Test fun classifiesInput() {
    assertEquals(ImportKind.Subscription("https://a.b/sub"), ImportClassifier.classify(" https://a.b/sub "))
    assertEquals(ImportKind.Config, ImportClassifier.classify("vless://id@h:443#n"))
    assertEquals(ImportKind.Routing, ImportClassifier.classify("[{\"outboundTag\":\"direct\",\"domain\":[\"a\"]}]"))
    assertEquals(ImportKind.Config, ImportClassifier.classify("{\"outbounds\":[]}"))
    assertEquals(ImportKind.Invalid, ImportClassifier.classify("привет, вот мой пароль"))
    assertEquals(ImportKind.Invalid, ImportClassifier.classify(""))
}
@Test fun deepLinkRejectsBadInput() {
    assertNull(ImportClassifier.parseDeepLink("install-sub", null))
    assertNull(ImportClassifier.parseDeepLink("install-sub", "javascript:alert(1)"))
    assertNull(ImportClassifier.parseDeepLink("other", "https://a.b"))
    assertEquals("https://a.b", ImportClassifier.parseDeepLink("install-config", "https://a.b")?.url)
}
@Test fun presetMatchIgnoresLockedRules() {
    val preset = listOf(RulesetItem(remarks = "a"))
    val locked = RulesetItem(remarks = "mine", locked = true)
    val presets = mapOf(RoutingType.GLOBAL to preset)
    assertEquals(RoutingType.GLOBAL, RoutingPresetMatcher.match(preset + locked, presets))
    assertNull(RoutingPresetMatcher.match(listOf(RulesetItem(remarks = "b")), presets))
}
```

- [ ] **Step 2: Запустить и увидеть падение**

Run: `./gradlew :app:testPlaystoreDebugUnitTest --tests '*ImportClassifierTest' --tests '*RoutingPresetMatcherTest'`
Expected: ошибка компиляции.

- [ ] **Step 3: Реализовать**

Выбранный пресет не хранится, а вычисляется сравнением текущих незамкнутых правил с пятью пресетами: так ручная правка на любом экране сразу превращается в `null`, то есть «Свои правила», а `RoutingSettingActivity` не меняется. `classify` проверяет base64 через `Utils.decode`, как `importBatchConfig`. `parseDeepLink` принимает хосты `install-sub`, `install-config` и адреса с именем протокола или `http(s)`.

- [ ] **Step 4: Запустить тесты**

Run: команда из шага 2.
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

Сообщение: `feat: classify imported input and detect the active routing preset`.

### Task 4: Модель главного экрана

**Files:**
- Create: `ui/main/HomeModels.kt`
- Test: `V2rayNG/app/src/test/java/com/v2ray/ang/ui/main/HomeModelsTest.kt`

**Interfaces:**
- Consumes: `buildSubscriptionInfoUiModel`, `GroupMapItem`, `SubscriptionUsageInfo.announce`.
- Produces:
  - `data class HomeCard(id, name, info: SubscriptionInfoUiModel?, isManual: Boolean, announce: String?, accent: Int)`
  - `enum class HomeAction { UPDATE, CABINET, SUPPORT }`
  - `sealed interface HomeIssue { UpdateFailed(lastUpdated: Long), ServerUnreachable, Expired, TrafficExhausted, VpnPermissionDenied }`
  - `fun buildHomeCards(groups: List<GroupMapItem>, urls: Map<String, String>, nowMillis: Long): List<HomeCard>`
  - `fun homeActions(card: HomeCard): List<HomeAction>`
  - `fun resolveHomeIssue(card: HomeCard, transient: HomeIssue?): HomeIssue?`
  - `fun highlightedAction(card: HomeCard, issue: HomeIssue?): HomeAction?`
  - `fun resolveServerForSubscription(remembered: String?, groupGuids: List<String>): String?`
  - `fun shouldShowFirstRun(cards: List<HomeCard>, serverCount: Int): Boolean`
  - `val HOME_ACCENTS: List<Long>`, `fun accentFor(id: String): Int`

- [ ] **Step 1: Написать падающие тесты**

```kotlin
@Test fun hidesActionsWithoutData() {
    assertEquals(listOf(HomeAction.UPDATE, HomeAction.SUPPORT), homeActions(card(support = "s")))
    assertEquals(emptyList<HomeAction>(), homeActions(card(isManual = true)))
}
@Test fun issuePriorityAndHighlightFallback() {
    assertEquals(HomeIssue.ServerUnreachable, resolveHomeIssue(expired(), HomeIssue.ServerUnreachable))
    assertEquals(HomeIssue.Expired, resolveHomeIssue(expired(), null))
    assertEquals(HomeAction.CABINET, highlightedAction(card(web = "w", support = "s"), HomeIssue.Expired))
    assertEquals(HomeAction.SUPPORT, highlightedAction(card(support = "s"), HomeIssue.Expired))
    assertNull(highlightedAction(card(), HomeIssue.Expired))
    assertEquals(HomeAction.UPDATE, highlightedAction(card(), HomeIssue.UpdateFailed(1L)))
}
@Test fun rememberedServerFallsBackToFirst() {
    assertEquals("b", resolveServerForSubscription("b", listOf("a", "b")))
    assertEquals("a", resolveServerForSubscription("gone", listOf("a", "b")))
    assertNull(resolveServerForSubscription("gone", emptyList()))
}
@Test fun firstRunOnlyWhenNothingToConnect() {
    assertTrue(shouldShowFirstRun(emptyList(), 0))
    assertTrue(shouldShowFirstRun(listOf(card(isManual = true)), 0))
    assertFalse(shouldShowFirstRun(listOf(card(isManual = true)), 1))
    assertFalse(shouldShowFirstRun(listOf(card()), 0))
}
@Test fun accentsAreStableAndReadableWithWhite() {
    assertEquals(accentFor("x"), accentFor("x"))
    HOME_ACCENTS.forEach { assertTrue(contrastWithWhite(it) >= 4.5) }
}
```

`card`, `expired` собрать как локальные помощники теста; `contrastWithWhite` считает контраст по WCAG.

- [ ] **Step 2: Запустить и увидеть падение**

Run: `./gradlew :app:testPlaystoreDebugUnitTest --tests '*HomeModelsTest'`
Expected: ошибка компиляции.

- [ ] **Step 3: Реализовать в `ui/main/HomeModels.kt`**

Значения: `HOME_ACCENTS` = `0xFF2447C8, 0xFF6B2A80, 0xFF0E6A70, 0xFF1F6B3A, 0xFF8C1D40, 0xFF7A4A00`. `accentFor` через `Math.floorMod(id.hashCode(), size)`. Псевдогруппу «Все» с `id == ""` исключить из карт. Ручная группа: пустой `url`. Приоритет ошибок: переданная `transient`, затем `Expired`, затем `TrafficExhausted`; они вычисляются из `SubscriptionInfoUiModel`. `Expired` раньше, потому что при истёкшем сроке трафик не поможет.

- [ ] **Step 4: Запустить тесты**

Run: команда из шага 2.
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

Сообщение: `feat: add pure home screen model`.

### Task 5: Переключение подписок и ошибки во ViewModel

**Files:**
- Create: `ui/main/PendingSwitch.kt`
- Modify: `ui/main/MainContract.kt`, `ui/main/MainViewModel.kt`, `ui/main/MainDataSource.kt`, `ui/main/MainRepository.kt`, `ui/main/MainActivity.kt`, `V2rayNG/gradle/libs.versions.toml`, `V2rayNG/app/build.gradle.kts`
- Test: `ui/main/PendingSwitchTest.kt`

**Interfaces:**
- Consumes: `resolveServerForSubscription`, `HomeIssue`, `SubscriptionFailure`, `SubscriptionItem.selectedGuid`.
- Produces:
  - `class PendingSwitch(scope: CoroutineScope, delayMillis: Long = 3_000, onCommit: (String) -> Unit)` с `start(targetId: String)`, `cancel()`, `pendingId: StateFlow<String?>`
  - `MainUiState`: `undoTargetId: String? = null`, `homeIssue: HomeIssue? = null`, `isConnecting: Boolean = false`
  - `MainAction.SwitchSubscription(groupId: String)`, `MainAction.UndoSwitch`, `MainAction.VpnPermissionDenied`, `MainAction.ClearHomeIssue`
  - `MainViewModel.homeEffects: SharedFlow<HomeEffect>`, `sealed interface HomeEffect { RestartService }`
  - `MainDataSource.rememberServer(groupId: String, guid: String)`

- [ ] **Step 1: Добавить `kotlinx-coroutines-test` и написать падающие тесты**

В каталог добавить библиотеку с `version.ref = "kotlinxCoroutines"`, в `build.gradle.kts` как `testImplementation`.

```kotlin
@Test fun commitsAfterDelay() = runTest {
    val done = mutableListOf<String>()
    val s = PendingSwitch(backgroundScope, 3_000) { done += it }
    s.start("b"); advanceTimeBy(2_999); assertTrue(done.isEmpty())
    advanceTimeBy(2); assertEquals(listOf("b"), done)
}
@Test fun cancelStopsCommit() = runTest {
    val done = mutableListOf<String>()
    val s = PendingSwitch(backgroundScope, 3_000) { done += it }
    s.start("b"); s.cancel(); advanceUntilIdle()
    assertTrue(done.isEmpty()); assertNull(s.pendingId.value)
}
@Test fun newStartReplacesPrevious() = runTest {
    val done = mutableListOf<String>()
    val s = PendingSwitch(backgroundScope, 3_000) { done += it }
    s.start("b"); advanceTimeBy(2_000); s.start("c"); advanceTimeBy(3_001)
    assertEquals(listOf("c"), done)
}
```

- [ ] **Step 2: Запустить и увидеть падение**

Run: `./gradlew :app:testPlaystoreDebugUnitTest --tests '*PendingSwitchTest'`
Expected: ошибка компиляции.

- [ ] **Step 3: Реализовать `PendingSwitch` и запустить тест**

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Встроить в `MainViewModel`**

Поведение `SwitchSubscription(id)` для свайпа и ряда подписок:

| Состояние | Что делает |
|---|---|
| VPN выключен | `subscriptionIdChanged(id)`, выбрать сервер через `resolveServerForSubscription`; ничего не запускать |
| VPN включён | То же сразу, запомнить прежнюю группу и сервер, `PendingSwitch.start(id)`; коммит шлёт `HomeEffect.RestartService` |
| `UndoSwitch` | `cancel()`, вернуть прежние группу и сервер |
| Выключение VPN | `cancel()`, новая группа остаётся выбранной |

- `SelectServer`: дополнительно `rememberServer(selectedGroupId, guid)`.
- `StateStartSuccess` после включения пользователем: снять `isConnecting`, запустить `testCurrentServerRealPing()`.
- `MeasureDelayResult` с `delayMillis <= 0` или непустым `errorMessage`: `homeIssue = ServerUnreachable`. VPN не выключается: сбой мог быть разовым, а «Другой сервер» переподключит.
- `StateStartFailure`: `homeIssue = ServerUnreachable`.
- `importConfigViaSub`: `failure != null` даёт `UpdateFailed(lastUpdated)`, успех снимает ошибку.
- Новые действия добавить в исчерпывающий `when` в `onAction`.

В `MainActivity`: `requestVpnPermission` при отказе шлёт `VpnPermissionDenied`, эффект `RestartService` вызывает `LauncherManager.restartService(this)`, собирать через `repeatOnLifecycle`.

- [ ] **Step 5: Полная проверка и Commit**

Run: `./gradlew :app:testPlaystoreDebugUnitTest :app:compilePlaystoreDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

Сообщение: `feat: switch subscriptions with an undo window and report home issues`.

### Task 6: Главный экран

**Files:**
- Create: `ui/main/HomeScreen.kt`, `ui/main/HomeCardPager.kt`, `ui/main/HomeStrips.kt`
- Modify: `ui/main/MainActivity.kt`, `res/values/strings.xml`, `res/values-ru/strings.xml`
- Test: нет новой чистой логики; проверка на устройстве в задаче 9

**Interfaces:**
- Consumes: всё из задач 4 и 5, `SubscriptionInfoUiModel`, `GroupPagerPage`, `MainMoreMenuAction`, `MoreMenuContent`, `MainDestination`.
- Produces: `@Composable fun HomeScreen(mainViewModel: MainViewModel, onAction: (MainAction) -> Unit, onNavigate: (MainDestination) -> Unit, onEditSubscription: (String) -> Unit)`; цвет подписки передаётся дочерним композаблам параметром `accent: Color`.

- [ ] **Step 1: Собрать экран по макету**

Сверху вниз, как в таблице спеки. Панель: `app_name`, «Добавить», «Ещё». Ряд подписок: `LazyRow` с ключом `id`, активная кнопка видна целиком, скрыт при одной карте. Карта: `HorizontalPager` с ключом `id`. При смене страницы жестом `MainAction.SwitchSubscription`, при программной смене не слать повторно. Ряд действий по `homeActions`, подсветка по `highlightedAction`. Полоса ошибки, сообщение провайдера (две строки, раскрывается через `rememberSaveable`), строка правил.

Цвет: при `isRunning` фон экрана равен `HOME_ACCENTS[accent]`, карта `surface`; статус-бар через `enableEdgeToEdge` не трогать, цвет иконок выставить светлым на акценте. В тёмной теме карта использует тёмный `surface`.

- [ ] **Step 2: Собрать шторку серверов**

`BottomSheetScaffold` со свёрнутым заголовком: «Серверы», текущий сервер, «Проверить» (`TestRealAllServers`), «Перезапустить» (`RestartService`, неактивно при `!isRunning`). Внутри `GroupPagerPage` для `selectedGroupId` и меню `MoreMenuContent` для сортировки, поиска, удаления и экспорта: это сохраняет весь функционал строки сервера без нового кода.

- [ ] **Step 3: Подключить в `MainActivity`**

В `ScreenContent` заменить `MainScreen(...)` на `HomeScreen(...)` с тем же `when`. `onEditSubscription` открывает `SubEditActivity` с GUID подписки. В первой версии «Настроить» использует этот существующий экран, а не окно снизу. `MainScreen` не удалять.

- [ ] **Step 4: Добавить строки**

Все тексты макета в `values` (en) и `values-ru`. Каждая иконка действия с `contentDescription`, иконка без действия с `null`.

- [ ] **Step 5: Проверить и Commit**

Run: `./gradlew :app:testPlaystoreDebugUnitTest :app:assemblePlaystoreDebug`
Expected: `BUILD SUCCESSFUL`.

Сообщение: `feat: add the new home screen`.

### Task 7: Окна «Добавить», «Правила» и «Ещё»

**Files:**
- Create: `ui/main/HomeSheets.kt`
- Modify: `ui/main/HomeScreen.kt`, `ui/main/MainActivity.kt`, `ui/main/MainViewModel.kt`, `ui/main/MainContract.kt`, строки
- Test: `ui/main/HomeModelsTest.kt` (пункты «Ещё»)

**Interfaces:**
- Consumes: `ImportClassifier.classify`, `SettingsManager.currentRoutingPreset`, `SettingsManager.resetRoutingRulesetsFromPresets`, `SettingsManager.resetRoutingRulesets`.
- Produces:
  - `MainAction.ApplyImport(text: String)`, `MainAction.ApplyRoutingPreset(type: RoutingType)`
  - `MainUiState.routingPreset: RoutingType?`
  - `val HOME_MORE_ITEMS: List<MainDestination>`

- [ ] **Step 1: Написать падающий тест**

```kotlin
@Test fun moreKeepsEverythingExceptPromotion() {
    assertEquals(MainDestination.entries.toSet() - MainDestination.Promotion, HOME_MORE_ITEMS.toSet())
}
```

- [ ] **Step 2: Запустить и увидеть падение**

Run: `./gradlew :app:testPlaystoreDebugUnitTest --tests '*HomeModelsTest'`
Expected: ошибка компиляции.

- [ ] **Step 3: Реализовать окна**

- **«Добавить»:** из буфера, QR, файл (`ImportClipboard`, `ImportQRcode`, `ImportConfigLocal`) и «Другие способы» (`ImportMenuContent`). Перед добавлением показывать распознанный тип. `ApplyImport` по типу: подписка `importSubscriptionUrl`, конфигурация `importBatchConfig`, правила `resetRoutingRulesets` после предупреждения `routing_settings_import_rulesets_tip`.
- **«Правила»:** пять `RoutingType`, активный из `routingPreset`; `null` показывает «Свои правила». Выбор идёт через то же предупреждение. После применения при включённом VPN `HomeEffect.RestartService`.
- **«Ещё»:** `HOME_MORE_ITEMS` через `onNavigate`, плюс «Обновить все подписки» (`UpdateGroupSubscription("")`).
- Импорт из буфера и файла в `MainActivity` уже зовёт `ImportBatchConfig`: заменить на `ApplyImport`, чтобы правила распознавались единообразно.

- [ ] **Step 4: Проверить и Commit**

Run: `./gradlew :app:testPlaystoreDebugUnitTest :app:assemblePlaystoreDebug`
Expected: `BUILD SUCCESSFUL`.

Сообщение: `feat: unify import, routing presets and rarely used settings`.

### Task 8: Первый запуск и вход по ссылке

**Files:**
- Create: `ui/main/HomeFirstRun.kt`, `ui/main/HomeImportState.kt`
- Modify: `ui/UrlSchemeActivity.kt`, `ui/main/MainActivity.kt`, `ui/main/MainViewModel.kt`, `ui/main/MainContract.kt`, `ui/main/HomeScreen.kt`, строки
- Test: `ui/main/HomeImportStateTest.kt`

**Interfaces:**
- Consumes: `ImportClassifier`, `AngConfigManager.importSubscriptionUrl`, `previewSubscriptionTitle`, `shouldShowFirstRun`.
- Produces:
  - `sealed interface HomeImportState { Idle, Downloading(url), Confirm(url, title), Failed(url, reason: SubscriptionFailure) }`
  - `fun nextImportState(url: String, hasSubscriptions: Boolean, title: String?): HomeImportState`
  - `MainAction.OpenLink(url: String)`, `MainAction.ConfirmLink`, `MainAction.DismissLink`, `MainAction.RetryImport`
  - `const val EXTRA_IMPORT_LINK` в `MainActivity`

- [ ] **Step 1: Написать падающий тест**

```kotlin
@Test fun firstSubscriptionSkipsConfirmation() {
    assertEquals(HomeImportState.Downloading("u"), nextImportState("u", hasSubscriptions = false, title = "T"))
}
@Test fun laterLinkAsksWithTitleOrHost() {
    assertEquals(HomeImportState.Confirm("https://a.b/s", "T"), nextImportState("https://a.b/s", true, "T"))
    assertEquals(HomeImportState.Confirm("https://a.b/s", "a.b"), nextImportState("https://a.b/s", true, null))
}
```

- [ ] **Step 2: Запустить и увидеть падение**

Run: `./gradlew :app:testPlaystoreDebugUnitTest --tests '*HomeImportStateTest'`
Expected: ошибка компиляции.

- [ ] **Step 3: Реализовать логику и экран**

`HomeFirstRun` по макету: заголовок, строка с «Вставить», подсказка, тихая ссылка «Другие способы». Клавиатура сама не открывается. Форма в прокручиваемом контейнере с `imePadding`, текст строки в `rememberSaveable`. Кнопка «Добавить» только при `classify != Invalid`, ошибки по таблице спеки, правила применяются с предупреждением, экран остаётся.

`HomeScreen` показывает `HomeFirstRun`, когда `shouldShowFirstRun` и нет `Downloading`.

- [ ] **Step 4: Переделать `UrlSchemeActivity`**

Разбор через `ImportClassifier.parseDeepLink`, запуск `MainActivity` с `EXTRA_IMPORT_LINK`, при `null` тоже запускает `MainActivity` и показывает ошибку «Это не похоже на ссылку подписки». Импорт из самой активности и все `LogUtil.i` с адресом удалить. `ACTION_SEND` идёт тем же путём. `MainActivity` обрабатывает extra в `onCreate` и `onNewIntent`, затем очищает extra, чтобы пересоздание не повторяло импорт.

Поведение: новая подписка становится выбранной через `subscriptionIdChanged`, VPN сам не включается и не переключается. `AlreadyExists` просто выбирает её.

- [ ] **Step 5: Проверить и Commit**

Run: `./gradlew :app:testPlaystoreDebugUnitTest :app:assemblePlaystoreDebug`
Expected: `BUILD SUCCESSFUL`.

Сообщение: `feat: add first run import and confirmed deep links`.

### Task 9: Проверка на устройстве и релиз

**Files:**
- Modify: `.github/workflows/build.yml` только если нужно под форк

- [ ] **Step 1: Прогнать сценарии на подключённом устройстве**

Эмулятора в системе нет, `adb` есть. Если устройства нет, записать каждый пункт под меткой `Not run`.

| Сценарий | Ожидается |
|---|---|
| Установка рядом с v2rayNG | Оба приложения работают |
| Подпись под иконкой в лаунчере устройства | `FrostMost` виден целиком, без обрезки. Если обрезается, сообщить автору: 9 знаков приняты при этом условии |
| Ссылка на первом запуске | Карта с названием из `Profile-Title`, VPN выключен |
| Ссылка при включённом VPN | Окно подтверждения, VPN не переключён |
| Свайп при включённом VPN, отмена, без отмены | Возврат назад; переподключение через 3 с |
| Отказ в разрешении VPN | Полоса и кнопка «Разрешить» |
| Мёртвый сервер | Полоса «Сервер не отвечает» |
| Режимы только прокси и root | Работают как раньше |
| Виджет и плитка | Работают как раньше |
| TalkBack, клавиатура, D-pad | Ряд подписок заменяет свайп, порядок логичен |
| Поворот и пересоздание во время отмены | Состояние по GUID сохранено |
| Светлая, тёмная, dynamic color; IME на первом запуске | Текст читаем, «Добавить» доступна при открытой клавиатуре |

- [ ] **Step 2: Запустить линт**

Run: `./gradlew :app:lintPlaystoreDebug`
Expected: нет новых ошибок.

- [ ] **Step 3: Проверить кликабельность ссылки в Telegram**

Отправить `<схема>://install-sub?url=...` в чат. Результат записать в спеку. Если не кликается, раздавать через страницу автора или https-адрес.

- [ ] **Step 4: Подготовить релиз**

Настроить секреты подписи по `.github/workflows/build.yml`. Push и первый релиз только с разрешения автора.

- [ ] **Step 5: Завершить ветку**

Использовать `superpowers:finishing-a-development-branch`. В отчёте перечислить все `Not run`.
