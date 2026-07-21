# План перехода на Next.js (SSR/ISR) — ревизия 2

**Дата:** 2026-07-20, ревизия 2026-07-21 (после двух ревью); 2.1 — сверка с
«Trivlu_SEO_структура_сайта_v3.docx» (добавлены: GSC-верификация домена, явный
disallow-список robots, nav/footer и destination-контекст на Ф3, правило
индексации категорий, карта перелинковки в Content)
**Статус:** дизайн утверждён (ревизия 2, включая §14.1 — SSR сначала на текущих URL), готов к реализации
**Ветка/PR:** `docs/nextjs-migration-plan`

> Документ — план миграции для передачи в работу. Исполнитель может работать по нему
> автономно. Ссылки на файлы проверены по состоянию на 2026-07-21.
> Ревизия 2: миграция разделена на независимые проекты (SSR-миграция ≠ URL v3 ≠
> бэкенд-слаги ≠ порт SPA); cutover не блокируется портом admin/vote/payment;
> бэкенд-работы сняты с критического пути.

---

## 1. Зачем это делаем (проблема)

Сейчас фронт — Create React App (CRA), чистый client-side rendering. `trivlu.com`
на любой публичный URL отдаёт **пустой SPA-шелл**: в исходном HTML только
`<div id="root"></div>` и `<noscript>You need to enable JavaScript…</noscript>`.
Проверено на проде — все маршруты (`/`, `/blog`, `/destination/...`) возвращают
**побайтово одинаковый** шелл, без текста, заголовков, ссылок; метатеги (`title`,
`description`, OG) есть, но **одинаковые генерик-значения на всех страницах**
(уникальные ставятся в рантайме через `react-helmet-async`).

Следствие: для краулера страницы фактически пустые. Контент-план на 50 постов
блога и гео-лендинги не дадут SEO-эффекта, пока публичные страницы не отдают
готовый HTML с контентом и уникальными метатегами в исходном ответе.

Дополнительно найдено (проверено на живом сайте 2026-07-21):

- `/api/sitemap.xml` на публичном домене отдаёт SPA-шелл, а не XML (статик-хост
  перехватывает путь; бэкендовый `SitemapController` не проксируется).
- **Cloudflare полностью подменяет живой `robots.txt`** своим managed-блоком
  «content signals»: директивы из закоммиченного `myhive-react-app/public/robots.txt:3-5`
  (`Disallow: /admin/`, `Sitemap:`) в живом ответе **отсутствуют вовсе** — блок
  замещает файл, а не дополняется к нему. Пока эта настройка Cloudflare не
  отключена/перенастроена, любой `robots.ts` из Next тоже не будет виден.
- Apex `trivlu.com` отдаёт 301 на `www.trivlu.com` — де-факто канонический хост
  сейчас **www**, при этом `Sitemap:` в закоммиченном robots.txt указывает на apex.

**Почему не react-snap:** react-snap — build-time снимок клиентского приложения.
Он закрывает только «пустой HTML» и только для известных на билде URL, застывшими
снапшотами (новый пост в админке виден лишь после ре-деплоя), не чинит sitemap,
soft-404 и не меняет хостинг. Для динамического, ведущегося из админки и растущего
по городам контента — не подходит. Настоящее решение — SSR/SSG.

## 2. Принятые решения

| Развилка | Решение | Обоснование |
|---|---|---|
| Стратегия | **Настоящий strangler**: cutover сразу после SSR публичных страниц; admin/vote/payment остаются легаси-SPA внутри Next (client-only обёртка) | SEO-эффект не ждёт порта админки; наименьший blast radius на шаг |
| URL v3 вместе с миграцией? | **Нет: сначала SSR на текущих URL**, v3 — отдельным шагом после cutover (Ф3). Утверждено 2026-07-21 | Смена фреймворка при неизменных URL — не «смена URL»; v3 позже всё равно даёт **один** набор 301, а SEO-эффекты шагов не перемешиваются |
| Хостинг | **Render Node web service** | Остаёмся у текущего провайдера рядом с бэкендом; знакомый деплой/биллинг |
| Роутинг Next | **App Router** | Актуальный дефолт, RSC/серверный фетч, `generateMetadata` |
| Режим публичных страниц | **ISR по таймеру** на запуске; on-demand revalidate вебхуком — после cutover | Готовый HTML сразу; вебхук — полировка, не блокер |
| Язык | **Гибридный TypeScript**: новый Next-код — TS, легаси-компоненты — JS (`allowJs: true`, `checkJs: false`) | TS не обязателен для Next; массовое переименование не нужно; без критерия «100% typed» |
| Auth | **Auth0 остаётся клиентским**; весь легаси-поддерево — через `next/dynamic` c `ssr: false` | `"use client"` недостаточно: client-компоненты всё равно пререндерятся на сервере, а `AuthContext.js:12,17` обращается к `window`/`localStorage` на уровне модуля |
| Бэкенд-слаги (составная уникальность) | **Отложены** до второго города (см. §6) | URL уже содержит город — отложка не ломает опубликованные URL; `ddl-auto=update` делает смену констрейнта рискованнее, чем казалось |
| Sitemap/robots | **Единственный владелец — Next** (`app/sitemap.ts`, `app/robots.ts`); `SitemapController` ретайрится на cutover | Не поддерживать две реализации |
| Размещение кода | **Новый модуль `myhive-next/`** рядом с `myhive-react-app/` | Сосуществуют во время перехода; CRA деплоибелен для отката |

## 3. Конечная архитектура

- Один **Next.js (App Router)** в `myhive-next/`, хостится **Render Node web service**,
  на cutover заменяет текущий static-site.
- **Spring-бэкенд в роли не меняется.** Server Components фетчат его напрямую по
  серверному абсолютному `BACKEND_URL`; браузерный код ходит same-origin в `/api/*`
  через Next rewrite (см. §8, env-контракт).
- **Публичные SEO-страницы** — Server Components, **SSR/ISR** → HTML с контентом,
  метатегами, schema в исходнике; клиентские «острова» для корзины/форм.
- **Админка / vote / оплата** — до отдельного порта живут как легаси React-Router-SPA
  внутри Next: client-обёртка (`"use client"`-шим), которая грузит легаси-приложение
  через `next/dynamic({ ssr: false })`. `react-router-dom` остаётся зависимостью на
  время перехода. Это сохраняет vote-флоу, завязанный на `location.state`
  (`useStartGroupVote.js:24-28`, `QuizPage.js:13`, `CuratePage.js:18-35`) — в Next
  navigation прямого эквивалента нет, порт = переработка флоу, делаем позже.

## 4. Режим рендеринга по типам страниц

| Страницы | Режим |
|---|---|
| `/`, `/about`, `/contact`, `/terms`, `/privacy-policy`, `/cookie-policy`, `/refund-policy` | SSG (или ISR с длинным revalidate) |
| `/destination/[slug]`, карточки активностей и пакетов, `/blog`, `/blog/[slug]` | **ISR по таймеру** (интервал — §14.2); on-demand revalidate — после cutover |
| admin, vote, payment | Легаси-SPA через client-only обёртку (`ssr: false`) |

Все динамические страницы обязаны отдавать **настоящий HTTP 404** через `notFound()`
на неизвестный slug/город/пост — сейчас SPA отдаёт soft-404 (200 с пустым шеллом).

## 5. Структура URL

**На запуске — текущие URL** (маршруты из `myhive-react-app/src/components/Layout.js:36-56`):

```
app/(public)/page.tsx                                       → главная
app/(public)/destination/[slug]/page.tsx                    → гео-лендинг
app/(public)/destination/[slug]/activity/[aslug]/page.tsx   → карточка активности
app/(public)/destination/[slug]/package/[pslug]/page.tsx    → карточка пакета
app/(public)/blog/page.tsx, blog/[slug]/page.tsx
app/(public)/about, contact, terms, privacy-policy, cookie-policy, refund-policy
app/(legacy)/admin/[[...slug]]/page.tsx     → client-шим → next/dynamic(ssr:false) → легаси-SPA
app/(legacy)/vote/[[...slug]]/page.tsx      → то же
app/(legacy)/payment/[[...slug]]/page.tsx   → то же
app/sitemap.ts, app/robots.ts               → единственная реализация
```

**URL v3** (`/destinations/[city]/activities/[slug]`, хаб `/destinations`, каталоги,
категории) — отдельный шаг после cutover (§9, Ф3): переименования + новые страницы +
один набор 301. Для категорий — **явный префикс** `/destinations/[city]/activities/category/[slug]`
(или отложить категорийные лендинги): плоский вариант «категория и карточка на одном
уровне» из v1 создаёт класс багов «категория молча затеняет одноимённую карточку» и
требует guard в генераторе слагов. Финальный выбор — на этапе Ф3 (§14.3).

## 6. Изменения на бэкенде (Spring) — сняты с критического пути

Модель сущностей не меняется (`Activity` привязана к городу, `destination_id NOT NULL` —
`entity/Activity.java:43`). **Для запуска SSR бэкенд-изменений не требуется.**

Интерим-схема для карточек: существующий глобальный lookup
(`ActivityController.java:63-65`, `getActivityBySlug`) + **проверка на стороне Next**,
что `destinationSlug` из ответа совпадает с городом в URL; при несовпадении —
`notFound()` (иначе одна карточка доступна под несколькими городами = дубли контента).

Отложенные задачи (триггер — **второй город**, до него не делаем):

| # | Задача | Файлы | Примечание |
|---|---|---|---|
| B1 | Составная уникальность slug `(destination_id + slug)` | `entity/Activity.java:46` | **Только явной миграцией БД.** Прод работает на `ddl-auto=update` (`application-prod.properties:9`): Hibernate добавит новый констрейнт, но **не удалит старый глобальный** — без ручной миграции останутся оба и кросс-городские дубли по-прежнему будут отбиваться |
| B2 | Генерация slug в пределах города + guard от коллизий slug категории ↔ slug карточки (если на Ф3 выбран плоский резолв) | `util/SlugUtils.java`, `service/SlugAssigner.java` | |
| B3 | Lookup `(city + slug)`: `GET /destinations/{city}/activities/{slug}` | `controller/ActivityController.java`, сервис, репозиторий | Заменяет интерим-схему |
| B8 | Юнит-тесты на B1–B3 | `src/test/...` | |

Прочее:

- **Sitemap**: `SitemapController` не дорабатывается — на cutover его заменяет
  `app/sitemap.ts` (фетчит те же данные из API). Одна реализация вместо двух.
- **EmailService** (`service/EmailService.java` — deep-links на `/destination/...` и
  `/vote/...`): **не переименовывать до cutover** — домен до переключения обслуживает
  CRA, и письма с новыми путями вели бы в 404. Старые ссылки в уже отправленных
  письмах после смены URL (Ф3) закрывают 301-редиректы; синхронно с Ф3 пути в
  письмах обновляются.
- Вебхук ревалидации (`POST /api/revalidate?path=…&secret=…` из admin-мутаций) —
  после cutover (Ф4).

Уже готово, менять не надо: каталог города (`getActivitiesByDestination`) и
категории в городе (`getActivitiesByDestinationAndCategorySlug`) —
`ActivityController.java:22-56`.

## 7. Работа на фронтенде (основной объём)

Зависимости: на публичных страницах `react-router-dom` → `next/navigation`+`next/link`,
`react-helmet-async` → Metadata API. **В легаси-поддереве обе остаются** до порта
(Ф4); удаление зависимостей — после него.

**SEO-разметка публичных страниц (сейчас у всех общие теги):**

- уникальные `title`/`description` на каждую страницу + `canonical` + один `H1`;
- OG/Twitter на каждую страницу;
- JSON-LD **минимальный набор на запуске**: Organization (главная), Article (посты),
  BreadcrumbList — крошки и Article почти бесплатны при постройке страниц и дороги
  в ретрофите. FAQPage, Product/Offer (`priceCurrency=EUR`) — после cutover;
- хлебные крошки (Home › Prague › Activity).

**Прочее на запуске:** существующие меню и футер как есть; клиентские острова для
Trip Builder/корзины/форм. Блог-кластеры (7 рубрик), новые страницы v3, WebP/CWV —
после cutover.

## 8. SSR-подводные камни и как их закрыть

- **~218 обращений к `window`/`document`/`localStorage` в 54 файлах.** Публичные
  страницы переписываются как Server Components и их не трогают; весь легаси-код
  живёт за `next/dynamic({ ssr: false })` и на сервере не исполняется. Нюанс Next 15:
  `ssr: false` допустим только внутри Client Component — обёртка = маленький
  `"use client"`-шим, который динамически импортирует легаси-приложение.
  `"use client"` сам по себе **не** защита: такие компоненты пререндерятся на
  сервере (пример краша: `AuthContext.js:12,17` — `window` на уровне модуля).
- **Env-контракт** (не переименовывать всё в `NEXT_PUBLIC_*` механически):
  - `BACKEND_URL` — серверная, без префикса, абсолютный URL бэкенда для Server
    Components и `sitemap.ts`;
  - браузерный код ходит в same-origin `/api/*` через rewrite в `next.config` —
    публичная переменная с URL API не нужна;
  - `NEXT_PUBLIC_*` — только реально нужное браузеру: OIDC-набор (`AUTHORITY`,
    `CLIENT_ID`, `REDIRECT_URI`, `AUDIENCE`, `ROLES_CLAIM`), `SITE_URL`,
    `TURNSTILE_SITE_KEY`. Итого 8 текущих `REACT_APP_*`-переменных раскладываются
    по этим двум корзинам; обновить конфиг на Render.
- GTM / CookieYes / Turnstile → через `next/script`; Consent Mode v2 сохранить.
- Auth0: добавить origin нового Render-сервиса (и preview-URL) в allowed redirect
  URIs; то же для CORS бэкенда и Stripe-доменов.
- Пиннинг версий Next/Node в `package.json`/Render.
- ISR-кэш у Next — на диске инстанса: на одном инстансе Render ок, при
  масштабировании >1 нужен shared cache handler (зафиксировать как ограничение).
- `generateStaticParams` из API делает **билд** зависимым от доступности бэкенда —
  либо принять явно, либо возвращать `[]` и полагаться на `dynamicParams` +
  заполнение по первому запросу.

## 9. Фазы (настоящий strangler)

- **Ф0. Каркас.** `myhive-next/` (App Router, гибридный TS), Render web-service,
  layout/header/footer, env-контракт из §8, rewrites `/api/*`, GTM/consent через
  `next/script`, client-обёртка легаси-SPA (admin/vote/payment работают внутри Next
  на preview-URL), Auth0/CORS/Stripe allowlists для preview. Домен пока на CRA.
- **Ф1. SEO-срез.** Публичные страницы на **текущих URL** как Server Components:
  SSR/ISR по таймеру, уникальные метатеги + canonical + OG, минимальный JSON-LD,
  крошки, `notFound()`-404, `app/sitemap.ts` + `app/robots.ts`. **Главный
  SEO-выигрыш — и он не ждёт порта админки.**
- **Ф2. Cutover.** Домен → Next-сервис; **разобраться с Cloudflare-подменой
  robots.txt** (managed-блок «content signals» замещает файл — отключить/настроить,
  иначе `robots.ts` невидим); зафиксировать канонический хост (сейчас де-факто www,
  §14.4) единообразно в canonical/OG/sitemap; отправка sitemap в Google Search
  Console; проверка **живых ответов** (не файлов): raw HTML, статусы, robots,
  sitemap; браузерные тесты booking/vote/payment; CRA держим деплоибельным для
  отката. **Верификация домена в Google Search Console** (DNS TXT через
  Cloudflare) — домен сейчас верифицирован только в Meta; сделать заранее, не
  ждать cutover. `robots.ts` — явный disallow-список: `/admin/`, `/vote/`,
  `/payment/` (сейчас в robots.txt только `/admin/`).
- **Ф3. URL v3**: переименования
  `destination`→`destinations`, `activity`→`activities`, новые страницы (хаб,
  каталог, категории с явным префиксом), **один набор 301** (Cloudflare/Render),
  синхронное обновление deep-links в письмах (`EmailService.java`), регенерация
  sitemap, обновление GSC. Дополнительно по SEO-структуре v3:
  - **Навигация/футер**: меню Destinations · Activities (пока город один — на
    каталог Праги) · Blog · About · Contact + CTA «Build Your Trip»; футер —
    популярные города, pillar-гайд, «Cookie settings». До Ф3 — меню как есть.
  - **Категории**: индексируем только категории со спросом (Nightlife, Czech
    beer, Extreme, Food & Drink, Shooting, Adult — нейтральный slug);
    Chillout/Transfer/All — фильтры в UI, не страницы.
  - **Destination-контекст с hostname на path**: SEO v3 — подпапки без
    поддоменов (prague.trivlu.com в DNS не существует, проверено 2026-07-21),
    а `DEFAULT_DESTINATION_SLUG` сейчас выводится из hostname
    (`services/config.js:37-40`, `resolveDestinationSlugFromHost`). На Ф3
    «Add to trip» и Trip Builder должны брать город из URL/props страницы,
    не из поддомена — иначе ломается атрибуция при нескольких городах.
- **Ф4. Позже, по мере надобности.** Порт admin/vote/payment на Next-роутинг
  (переработка vote-флоу с `location.state`); on-demand revalidate вебхук;
  B1–B3+B8 при втором городе (только явной миграцией БД); полный JSON-LD
  (FAQPage, Product/Offer); WebP/lazy/CWV; порт Jest/RTL; удаление
  `react-router`/`react-helmet`; блог-кластеры; опортунистическая конверсия в TS.

## 10. Разделение задач: Frontend / Edge / Content

- **Frontend (Next.js):** основной объём — Ф0–Ф2: каркас + обёртка легаси +
  публичные страницы с SEO-разметкой. Бэкенд до второго города не трогаем.
- **Edge / Infra:** Cloudflare robots-подмена, канонический хост, новый Render
  web-service, GSC; 301-редиректы — только на Ф3.
- **Content / Ops (не код, но блокирует SEO-эффект):** 50 постов + рубрики;
  **уникальные описания 100–200 слов на каждую комбинацию город × активность**
  (не шаблон — иначе клоны); нейтральные slug/title для adult-категорий; правило
  «город открывается только с готовым уникальным каталогом»; перелинковка постов
  по готовой карте (120 внутренних ссылок, документ «карта перелинковки») +
  ссылки из постов на коммерческие `/destinations/[city]` и категории.

## 11. Тестирование

- **SSR smoke-тесты (запуск-критичные):** `curl` каждого типа публичного маршрута →
  в **сыром HTML** есть контент + уникальные `title`/`description`/OG; неизвестный
  slug → HTTP 404.
- **Браузерные тесты revenue-флоу** до cutover: booking, vote (включая переходы с
  `location.state`), payment — внутри Next-обёртки.
- Гидрация: прогон публичных страниц на preview без hydration-mismatch в консоли.
- Порт Jest/RTL — Ф4; бэкенд юнит-тесты (B8) — вместе с B1–B3 (по CLAUDE.md
  бэкенд-тесты обязательны для нового/изменённого кода).

## 12. Риски и откат

- Cutover → митигируем preview-деплоем и параллельным прогоном до переключения
  DNS; CRA держим деплоибельным для быстрого отката.
- **Hydration-mismatch / краши браузерных API** → вся легаси за `ssr:false`,
  smoke-тесты на preview.
- **Cloudflare-подмена robots.txt** — блокер cutover-чеклиста, а не «полировка».
- Cold start на дешёвом Render-плане; Auth0 redirect URIs.
- Интерим-lookup карточек без проверки города → дубли контента (закрыто
  `notFound()`-правилом из §6).

## 13. Критерий готовности cutover (acceptance)

- View-source каждого типа публичной страницы = контент + уникальные
  `title`/`description`/OG + минимальный JSON-LD; ровно один `H1`; `canonical`
  на каноническом хосте.
- Неизвестный slug/город/пост → **HTTP 404**, не soft-404.
- **Живой** `robots.txt` (после разборки с Cloudflare) содержит `Sitemap:` и
  disallow служебных путей; **живой** `/sitemap.xml` = настоящий XML с текущими URL.
- Ноль регрессий в admin / vote / payment / booking-флоу (браузерные тесты).
- Единый канонический хост во всех canonical/OG/sitemap/redirect.

## 14. Открытые вопросы к согласованию

1. ~~**Последовательность URL v3**~~ — **решено 2026-07-21: сначала SSR на
   текущих URL, v3 отдельно после cutover** (Ф3). Отменяет решение v1
   «совместить»: смена фреймворка на тех же URL — не смена URL, набор 301 в
   любом случае один; плата — повторное касание файлов роутинга/метадаты
   (умеренная).
2. **Интервал ISR revalidate** по умолчанию (напр. 1 час).
3. **Категорийные маршруты на Ф3**: явный префикс `/activities/category/[slug]`
   (рекомендация) vs плоский резолв с guard от коллизий vs отложить категории.
4. **Канонический хост**: закрепить www (де-факто сейчас) или перейти на apex —
   решить до cutover, единообразно в canonical/OG/sitemap/301.
5. **Cloudflare**: где именно включена подмена robots.txt (managed content signals)
   и что с ней делать — отключить или совместить с нашим файлом.
