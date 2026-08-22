# P0: ручные действия после деплоя

Кому адресован документ: тому, кто делает деплой пакета P0 (измерение +
OG-превью) и держит доступ к прод-админке / Render / GA4 / Meta Events
Manager. Это чек-лист действий вне кода — то, что не попадает в релиз
автоматически. Полное ТЗ — `fixes/ТЗ_Аналитика_trivlu_v1.6.md`; настройка
GTM/Meta/GA4 в веб-интерфейсах — `docs/ops/gtm-meta-setup.md` (не дублируется
здесь).

## 1. Прод-админка: переименовать «Stag Premiumtest»

На проде (не в коде и не в локальной dev-БД) существует пакет с названием
«Stag Premiumtest» на `/destination/prague`. Нужно:

1. Открыть админку → Destinations → Prague → Packages, найти пакет
   «Stag Premiumtest».
2. Переименовать название и slug на нормальные (без «test» в названии/slug).
3. После деплоя округления цены пакетов (см. ниже) проверить, что цена этого
   пакета отображается как **€445** (целое число евро) — в списке пакетов, на
   странице пакета и в корзине. До деплоя цена могла показываться как
   `€444.62` — это ожидаемо для старого кода, не баг для отката.

Округление цены пакета (сумма активностей минус `discount_pct`, HALF_UP до
целых евро) теперь считается в коде на бэкенде (`PackageService`) — после
деплоя работает автоматически для всех пакетов, без ручных действий, кроме
самого переименования «Stag Premiumtest».

## 2. Backend env vars (Render)

Задать в окружении бэкенда:

| Переменная | Значение |
| :--- | :--- |
| `META_PIXEL_ID` | `1482052533162342` |
| `META_CAPI_ACCESS_TOKEN` | сгенерировать в Events Manager → датасет «Trivlu data» → Settings → Conversions API → Generate access token (шаги — `docs/ops/gtm-meta-setup.md`, раздел 4) |
| `META_CAPI_TEST_EVENT_CODE` | опционально, только для проверки на стейджинге (вкладка Test Events в Events Manager); на проде не задавать |

Без `META_PIXEL_ID`/`META_CAPI_ACCESS_TOKEN` CAPI-сервис молча отключён —
деплой без токена ничего не ломает, просто серверные события (`Lead`,
`Purchase`, `start_group_vote`) не отправляются в Meta.

## 3. Прогон OG-превью

После деплоя пройти **Meta Sharing Debugger**
(https://developers.facebook.com/tools/debug/) по следующим URL и для каждого
нажать **Scrape Again** (кэш Facebook держит старые метатеги/картинку иначе):

1. Главная страница (`/`).
2. `/destination/prague`.
3. 2–3 страницы активностей (любые из каталога Праги).
4. **Свежая vote-ссылка** — создать тестовое голосование (`/vote/{token}`) и
   проверить именно её: заголовок должен содержать имя жениха (если оно было
   указано при создании голосования) или общий фолбэк без имени, картинка —
   динамический коллаж 1200×630 (`/vote/{token}/opengraph-image`) из фото
   выбранных активностей + бейдж числа проголосовавших + брендинг Trivlu; если
   у сессии нет фото — статичный брендированный фолбэк.

Для каждого URL в Debugger проверить:
- `og:title`, `og:description`, `og:image` подтягиваются верно;
- `twitter:card` = `summary_large_image` (не `summary`);
- размер og-изображения 1200×630 (не старые 1000×1000).

После прохода в Debugger — **реальная проверка рендера в мессенджере**:
отправить те же ссылки (включая vote-ссылку) себе в WhatsApp на iOS и на
Android, визуально сверить превью (картинка, заголовок, домен) — Debugger не
всегда совпадает 1:1 с тем, что реально показывает клиент WhatsApp.

## 4. GA4: custom dimensions

Зарегистрировать в GA4 (Admin → Custom definitions → Create custom
dimensions, все — **event-scoped**):

- `nights`
- `vote_id`
- `source_campaign`
- `group_size`
- `activities_count`
- `trip_id`
- `user_role`

Полный список событий/параметров и key events — `docs/ops/gtm-meta-setup.md`
(раздел 5).

## 5. Выгрузки: CSV-экспорты из админки

Два новых admin-эндпоинта для отчётности (нужна активная админская сессия —
запрос через браузер, залогиненный в админку, или с JWT админа/менеджера в
заголовке `Authorization`):

- **`GET /admin/votes/export`** — только роль **ADMIN**. По каждой сессии
  голосования: id, groom_name, created_at, opened_count, voted_count,
  booking_id, booking_created_at, paid_at (пусто, если брони нет).
- **`GET /admin/bookings/first-touch-report`** — роль **ADMIN или MANAGER**.
  По оплаченным броням: first_touch_at, created_at, paid_at, число дней
  first-touch → paid, utm_source/medium/campaign, ref, vote_session_id.

Оба отдают `text/csv` с заголовком `Content-Disposition: attachment` — при
открытии в браузере скачаются файлом, а не отрисуются на странице.

## Итоговый чек-лист

- [ ] «Stag Premiumtest» переименован (название + slug) в прод-админке.
- [ ] Цена пакета на `/destination/prague` показывает €445 после деплоя.
- [ ] `META_PIXEL_ID`, `META_CAPI_ACCESS_TOKEN` заданы на Render; при
      необходимости `META_CAPI_TEST_EVENT_CODE` — только на стейджинге.
- [ ] Sharing Debugger + Scrape Again пройден для главной, `/destination/prague`,
      2–3 активностей и свежей vote-ссылки.
- [ ] Реальный рендер превью проверен в WhatsApp на iOS и Android.
- [ ] 7 custom dimensions зарегистрированы в GA4.
- [ ] `/admin/votes/export` и `/admin/bookings/first-touch-report` проверены
      из админской сессии (файлы скачиваются, содержимое корректно).
