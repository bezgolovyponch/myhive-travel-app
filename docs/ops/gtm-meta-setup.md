# GTM + Meta Pixel/CAPI — руководство по настройке

Кому адресован документ: маркетингу и разработчику, настраивающим Google Tag
Manager и Meta Events Manager для trivlu.com. Это чек-лист действий, не
объяснение теории — контекст и полное ТЗ см. `fixes/ТЗ_Аналитика_trivlu_v1.6.md`
(разделы 3, 5, 6, 8, 11).

Код (dataLayer-события, CAPI на бэкенде) уже реализован — этот документ только
про настройку GTM/Meta/GA4 в веб-интерфейсах, без релизов.

## 0. Идентификаторы (готовы, см. ТЗ §11)

| Актив | Значение |
| :---- | :---- |
| Meta Pixel / Dataset («Trivlu data») | `1482052533162342` |
| GTM контейнер | `GTM-KB7BJLDS` |
| CookieYes website key | см. ТЗ §4 |
| Microsoft Clarity Project ID | см. ТЗ §7 |
| Backend env vars (CAPI) | `META_PIXEL_ID`, `META_CAPI_ACCESS_TOKEN`, `META_CAPI_TEST_EVENT_CODE` (опционально) |

## 1. Meta Pixel — базовый тег в GTM

1. GTM → Tags → New → **Meta Pixel** (шаблон из Community Template Gallery, не
   хардкодить `fbq()` в код сайта — см. ТЗ §3, §5).
2. Pixel ID: `1482052533162342`.
3. Trigger: **All Pages** (событие PageView).
4. Consent Settings тега → категория **«маркетинговые»** (CookieYes) — тег не
   должен грузиться до согласия пользователя (ТЗ §4). Consent Mode v2 уже
   дефолтно `denied` для `ad_storage`/`ad_user_data`/`ad_personalization`.
5. Опубликовать контейнер `GTM-KB7BJLDS` только после проверки в Preview-режиме.

## 2. Таблица маппинга событий (dataLayer → Meta / GA4)

Источник — ТЗ §8. Имена dataLayer-событий и параметров зафиксированы в коде,
не менять. Каждое событие GTM триггерит по **Custom Event** с именем из левой
колонки.

| dataLayer event | Meta тег (GTM) | Meta событие | Ключевые параметры из dataLayer | GA4 событие |
| :---- | :---- | :---- | :---- | :---- |
| `cta_click` | Custom Event тег | (custom `CTAClick`) | `cta_label` | `cta_click` |
| `contact_click` | Standard Event тег | `Contact` | `channel` | `contact_click` |
| `tb_start` | Standard Event тег | `ViewContent` | `ref` | `tb_start` |
| `tb_group_submitted` | Standard Event тег + Advanced Matching | `CompleteRegistration` | `destination`, `group_size`, `has_budget`, **`user_email`** → Advanced Matching (email) | `tb_group_submitted` (key event) |
| `quiz_completed` | Custom Event тег | (custom) | `q_daytime`, `q_adrenaline`, `q_food`, `q_classy` | `quiz_completed` |
| `shortlist_completed` | Custom Event тег | (custom) | `selected_count` | `shortlist_completed` |
| `vote_launched` | Custom Event тег | **`start_group_vote`** (custom conversion, см. §3) | `selected_count`, `trip_id`, `nights`, `vote_id`, `group_size`, `activities_count`, `source_campaign`, **`event_id`** | `vote_launched` |
| `vote_skipped` | Custom Event тег | (custom) | `selected_count` | `vote_skipped` |
| `vote_opened` | — | — | `user_role=participant` | `vote_opened` |
| `vote_completed` | — | — | `user_role=participant` | `vote_completed` |
| `checkout_viewed` | Standard Event тег | `InitiateCheckout` | `items_count`, `value`, `currency=EUR` | `checkout_viewed` |
| `booking_form_viewed` | Custom Event тег | (custom) | `value`, `currency` | `booking_form_viewed` |
| `booking_submitted` | Standard Event тег | `Lead` (главная конверсия) | `value`, `currency=EUR`, `activities_count`, `destination`, `group_size`, `nights`, `vote_id`, `source_campaign`, `utm_*`, **`event_id`**, **`user_email`** → Advanced Matching | `generate_lead` (key event) |
| `payment_page_viewed` | Custom Event тег | (custom) | `user_role`, `share_value`, `currency` | `payment_page_viewed` |
| `payment_completed` | Standard Event тег | `Purchase` | `value`, `currency=EUR`, `payment_index`, `user_role`, **`event_id`** | `purchase` (key event) |
| `trip_fully_paid` | — (сервер, фаза 2) | — (CAPI, фаза 2) | `value`, `currency`, `participants_paid` | фаза 2, Measurement Protocol |

Все новые параметры (`event_id`, `nights`, `vote_id`, `source_campaign`,
`group_size`, `activities_count`) уже пушатся в dataLayer кодом — в GTM для
каждого нужна **Data Layer Variable** с точным именем параметра (snake_case).

## 3. `vote_launched` → custom conversion `start_group_vote`

Это единственное событие с раздельной браузерной (Pixel через GTM) и серверной
(CAPI на бэкенде) отправкой уже на старте — обе стороны используют
`event_id = trip_id` (shareToken поездки), не uuid.

1. В GTM тег для `vote_launched` отправляет **custom event** с именем
   `start_group_vote` (не стандартное Meta-событие) и обязательно передаёт
   `event_id` = переменная dataLayer `trip_id`.
2. Events Manager → **Custom Conversions** → Create → источник: custom event
   `start_group_vote` → сохранить как конверсию (для использования в
   оптимизации/отчётах отдельно от Lead).
3. Так как event_id совпадает на клиенте и сервере, Meta должен показывать пару
   событий как **Deduplicated** — проверка в §6.

## 4. CAPI — токен и переменные бэкенда

1. Events Manager → датасет **«Trivlu data»** → Settings → **Conversions API**
   → Generate access token.
2. Задать на бэкенде (Render/окружение):
   - `META_PIXEL_ID=1482052533162342`
   - `META_CAPI_ACCESS_TOKEN=<сгенерированный токен>`
   - `META_CAPI_TEST_EVENT_CODE=<код из вкладки Test Events>` — опционально,
     только для проверки на стейджинге, на проде не задавать.
3. Без этих переменных CAPI-сервис молча выключен (проверено кодом) — деплой
   без токена ничего не ломает, просто CAPI не шлётся.
4. CAPI уже отправляет три события с бэкенда: `start_group_vote` (при создании
   сессии голосования, event_id = shareToken), `Lead` (при сабмите заявки,
   event_id = клиентский `event_id` либо id заявки), `Purchase` (при успешной
   оплате доли, event_id = `UUID.nameUUIDFromBytes("purchase:"+shareId)` —
   идентичен тому, что бэкенд подставляет в Stripe `success_url`, поэтому
   браузерный Purchase и серверный Purchase дедуплицируются).

## 5. GA4 — custom dimensions и key events

1. Admin → Custom definitions → Create custom dimensions (**event-scoped**),
   по одной на каждый параметр:
   - `trip_id`
   - `user_role`
   - `nights`
   - `vote_id`
   - `source_campaign`
   - `group_size`
   - `activities_count`
2. Admin → Events → пометить как **key events**: `generate_lead`
   (booking_submitted), `tb_group_submitted`, `purchase`.
3. Тег GA4 Configuration — только через GTM (ТЗ §6), не через встроенное
   предложение GA4 «установить тег Google».

## 6. Проверка дедупликации в Events Manager → Test Events

1. Открыть Events Manager → датасет «Trivlu data» → **Test Events**, вставить
   URL сайта/тестовый `META_CAPI_TEST_EVENT_CODE` при стейджинге.
2. Пройти сценарий: запустить голосование (`vote_launched`) → отправить заявку
   (`booking_submitted`) → оплатить долю (`payment_completed`).
3. Для каждой пары браузер/сервер (`start_group_vote`, `Lead`, `Purchase`)
   в Test Events должен появиться статус **«Deduplicated»** (или парный ивент
   с одинаковым `event_id`), без ошибок валидации параметров.
4. У `Lead` и `Purchase` обязательно присутствуют `value` и `currency`.
5. Проверить расширением **Meta Pixel Helper** в браузере — тот же набор
   событий без предупреждений о дублях/пропущенных параметрах.

## 7. Итоговый чек-лист приёмки

- [ ] Meta Pixel тег в GTM — Pixel ID `1482052533162342`, триггер All Pages,
      Consent Settings = «маркетинговые».
- [ ] Все события из таблицы §2 настроены в GTM (Custom Event триггер + Data
      Layer Variables на каждый параметр).
- [ ] `tb_group_submitted` и `booking_submitted` передают `user_email` в
      Advanced Matching.
- [ ] Custom Conversion `start_group_vote` создана в Events Manager.
- [ ] `META_PIXEL_ID` / `META_CAPI_ACCESS_TOKEN` заданы на бэкенде (без них
      CAPI молча выключен — это ожидаемо для окружений без кредов).
- [ ] GA4: 7 event-scoped custom dimensions зарегистрированы (`trip_id`,
      `user_role`, `nights`, `vote_id`, `source_campaign`, `group_size`,
      `activities_count`).
- [ ] GA4 key events: `generate_lead`, `tb_group_submitted`, `purchase`.
- [ ] Test Events показывает `start_group_vote` / `Lead` / `Purchase` как
      Deduplicated при параллельном браузер+CAPI запуске, без ошибок.
- [ ] Контейнер `GTM-KB7BJLDS` опубликован после проверки в Preview-режиме.
