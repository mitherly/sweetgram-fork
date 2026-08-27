# sweetgram

розовый форк телеги на базе [margelet](https://github.com/margelet/DrKLO) с собственным брендом и системой верификации.

![sweetgram](logo.svg)

## что внутри
- розовая тема оформления (фоны, акценты, названия, заголовки, счётчики, уведомления, иконки микрофона/камеры в звонках);
- кастомный фон чата по умолчанию (нежно-розовый);
- бренд-звук уведомлений и розовый сплеш при запуске;
- система верификации: админка выдаёт галочку, у верифицированных она рисуется в списке диалогов, в чате (рядом с именем и мягкой розовой обводкой сообщения) и в профиле;
- закреплённый канал `@SweetGramOfficial` в списке диалогов;
- кнопка поддержки в меню чата.

## сборка
сборка идёт через github actions (`.github/workflows/build.yml`): релизный apk `Sweetgram-v12.10.0.apk` собирается автоматически на пуше в ветку `rewrite`.

реальный `google-services.json` (firebase-проект sweetgram-60662) подставляется в ci из секрета
`GOOGLE_SERVICES_JSON`, в репозитории лежит только плейсхолдер — ключи не светятся.

локально:
```
./gradlew :TMessagesProj_App:assembleAfatRelease
```
нужны android sdk, ndk `27.2.12479018`, build-tools `35.0.0`, jdk 17.

## верификация
галочка выдаётся через админку (`SweetgramAdminActivity`) и пишется в firebase
(`verified_users/<id>` + `verified_meta/<id>/k` с секретом). чтение публично, запись
закрыта правилами базы.

## ссылки
- канал: [@SweetGramOfficial](https://t.me/SweetGramOfficial)
- исходник: github.com/mitherly/sweetgram-fork
