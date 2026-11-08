# FrostMost — субагентное исполнение задач после пересборки

**Дата:** 2026-11-08
**Источник:** `2026-11-08-post-rebuild-tasks.md` (принято владельцем).
**Режим:** субагентное исполнение в трёх непересекающихся потоках; прогон на устройстве и TalkBack
**не выполняются** в этот раз.

## Разделение файлов (write scopes)

| Поток | Задачи из бэклога | Пишет в |
| --- | --- | --- |
| A — тональность | P1 «Убрать рамки» | `ui/compose/FrostComponents.kt`, `ui/compose/FrostStructure.kt`, `ui/compose/Components.kt`, `ui/compose/FormFields.kt`, `ui/compose/SettingsItem.kt`, `ui/server/ServerCustomConfigActivity.kt`, `ui/server/ServerFormSections.kt` |
| B — экран | P1 «Карточка-toggle» + P2 «Акцент всего экрана» (делаются одним изменением) | `ui/main/HeroConnectionCard.kt`, `ui/main/HeroPowerControl.kt`, `ui/main/HomeScreen.kt`, при необходимости `ui/main/HomeModels.kt` + `app/src/test/java/com/v2ray/ang/ui/main/` |
| C — секция серверов | P1 «Убрать галочку» + P2 «Убрать Перезапустить, Проверить в заголовок» | `ui/main/HomeServerRows.kt`, `ui/main/HomeConfiguration.kt`, `app/src/main/res/values/strings.xml`, `values-ru/strings.xml` |

`ui/main/HomeModels.kt` правит только поток B; `ui/compose/*` — только поток A;
`ui/main/HomeServerRows.kt` и `HomeConfiguration.kt` — только поток C.

## Отложено (одна задача, требует явного решения владельца)

- P1 «Заменить бордовый акцент по умолчанию» — меняет цвета всей карточки. Поток A работает с той
  же палитрой контрастами; одновременная правка даёт конфликт по смыслу. Ставится отдельным
  потоком после приёмки A/B/C.

## Проверки

Каждый поток: `git diff --check`, затем (из `V2rayNG/`, кэш в `.tmp/gradle-home`)

```sh
JAVA_HOME=/home/frostmonster/fork-toolchain/jdk21 \
GRADLE_USER_HOME=/home/frostmonster/Projects/Personal/v2rayNG/.tmp/gradle-home \
./gradlew :app:testPlaystoreDebugUnitTest :app:compilePlaystoreDebugKotlin --offline
```

и `:app:assemblePlaystoreDebug`, если поток трогает ресурсы (поток C).

**Not run:** прогон на устройстве, TalkBack, проверка фокуса/активации D-pad — по указанию владельца;
в отчётах эти пункты помечаются как `Not run` с причиной.
