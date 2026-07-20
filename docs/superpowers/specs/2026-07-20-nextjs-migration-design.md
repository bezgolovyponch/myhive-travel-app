# План перехода на Next.js (SSR/ISR) + реструктуризация URL v3

**Дата:** 2026-07-20
**Статус:** дизайн утверждён, готов к реализации
**Ветка/PR:** `docs/nextjs-migration-plan`

> Документ — план миграции для передачи в работу. Пишется так, чтобы исполнитель мог
> работать по нему автономно. Ссылки на файлы даны по состоянию на 2026-07-20.

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

Дополнительно найдено: `/sitemap.xml` на публичном домене отдаёт SPA-шелл, а не
XML (статик-хост перехватывает путь; бэкендовый `SitemapController` под `/api` не
проксируется); в `robots.txt` нет директивы `Sitemap:`.

**Почему не react-snap:** react-snap — build-time снимок клиентского приложения.
Он закрывает только «пустой HTML» и только для известных на билде URL, застывшими
снапшотами (новый пост в админке виден лишь после ре-деплоя), не чинит sitemap,
soft-404 и не меняет хостинг. Для динамического, ведущегося из админки и растущего
по городам контента — не подходит. Настоящее решение — SSR/SSG.

## 2. Принятые решения

| Развилка | Решение | Обоснование |
|---|---|---|
| Стратегия | **Incremental strangler**, один Next.js-проект | Публичные SEO-страницы первыми → ранний эффект; админка/vote/оплата остаются client-компонентами и переносятся постепенно; минимальный риск |
| Совмещать с URL-структурой v3? | **Да** | Публичные страницы всё равно перестраиваются в Next — v3-структуру делаем сразу; не трогаем URL дважды, один набор 301 |
| Хостинг | **Render Node web service** | Остаёмся у текущего провайдера рядом с бэкендом; знакомый деплой/биллинг |
| Роутинг Next | **App Router** | Актуальный дефолт, RSC/серверный фетч, `generateMetadata` |
| Режим публичных страниц | **ISR + on-demand revalidate** | Готовый HTML + свежесть по вебхуку из админки (закрывает слабость react-snap) |
| Auth | **Auth0 остаётся клиентским** (localStorage, silent renew) | Админка не нуждается в SEO; серверные сессии — лишний риск и объём |
| Размещение кода | **Новый модуль `myhive-next/`** рядом с `myhive-react-app/` | Сосуществуют во время перехода; CRA деплоибелен для отката; ретайрим на cutover |

## 3. Конечная архитектура

- Один **Next.js (App Router)** в `myhive-next/`, хостится **Render Node web service**,
  на cutover заменяет текущий static-site.
- **Spring-бэкенд в роли не меняется** — Next зовёт его через `/api`. Публичные
  страницы фетчат **на сервере** (Server Components), а не в браузере.
- **Публичные SEO-страницы** — Server Components, **SSR/ISR** → HTML с контентом,
  метатегами, schema в исходнике.
- **Админка / vote / оплата** — Client Components (`"use client"`), Auth0 клиентский.

## 4. Режим рендеринга по типам страниц

| Страницы | Режим |
|---|---|
| `/`, `/about`, `/contact`, `/terms`, `/privacy-policy`, `/cookie-policy` | SSG (или ISR с длинным revalidate) |
| `/destinations`, `/destinations/[city]`, каталог, категории, карточки «активность×город», `/blog`, `/blog/[slug]` | **ISR + on-demand revalidate** (таймер + вебхук из админки при публикации/правке) |
| admin, vote, payment | CSR (client components) |

## 5. Структура URL v3 (файловый роутинг)

```
app/(public)/page.tsx                                           → главная
app/(public)/destinations/page.tsx                             → хаб городов
app/(public)/destinations/[city]/page.tsx                      → гео-лендинг
app/(public)/destinations/[city]/activities/page.tsx           → каталог города
app/(public)/destinations/[city]/activities/[slug]/page.tsx    → категория ИЛИ карточка (*)
app/(public)/destinations/[city]/packages/[slug]/page.tsx      → пакет
app/(public)/blog/page.tsx
app/(public)/blog/[slug]/page.tsx
app/(public)/about/page.tsx, contact, terms, privacy-policy, cookie-policy, refund-policy
app/admin/[[...slug]]/page.tsx    ("use client")
app/vote/...                      ("use client")
app/payment/success, payment/cancelled  ("use client")
app/api/revalidate/route.ts       → on-demand ISR revalidation
app/sitemap.ts (или проксирование бэкендового sitemap), app/robots.ts
```

(*) **Открытая развилка дизайна:** в v3 категория (`/…/activities/nightlife`) и
карточка (`/…/activities/beer-bike-tour`) лежат **на одном уровне**. Один
динамический сегмент `[slug]` с резолвом: если slug совпал с категорией города →
рендер страницы категории; иначе → карточка активности. Альтернатива —
разнести под разные префиксы (менее точно повторяет URL из документа). **Решение:**
единый сегмент с резолвом (категория проверяется первой).

## 6. Изменения на бэкенде (Spring)

Модель сущностей **не меняется** (Activity уже привязана к одному городу,
`destination_id NOT NULL`). Меняется **область уникальности slug** и добавляется
lookup по городу.

| # | Задача | Файлы (по состоянию на 2026-07-20) | Когда |
|---|---|---|---|
| B1 | Уникальность slug активности: глобальная → **составная `(destination_id + slug)`** + миграция БД | `entity/Activity.java:46` (`@Column(unique = true)`) | Ф1 (\*) |
| B2 | Генерация slug в пределах города, а не глобально | `util/SlugUtils.java`, `SlugAssigner` | Ф1 |
| B3 | Новый lookup карточки по `(city + slug)`, напр. `GET /destinations/{city}/activities/{slug}` | `controller/ActivityController.java:63-65` (сейчас `getActivityBySlug` по одному slug), сервис, репозиторий | Ф1 |
| B4 | SitemapController: пути v3 (`/destinations/`, `/activities/`), + URL каталогов, категорий, хаба `/destinations`, `/about`, `/contact` | `controller/SitemapController.java:48,56,64,71` | Ф1 |
| B5 | Починить публичную отдачу `/sitemap.xml` (сейчас отдаётся SPA-шелл) — Next `app/sitemap.ts` или проксирование бэкендового XML | edge + Next | Ф4 |
| B6 | Эндпоинт ревалидации: бэкенд/админка дёргает Next `POST /api/revalidate?path=…&secret=…` при изменении контента | новый вызов из admin-мутаций | Ф5 |
| B7 | EmailService: переименовать destination-путь в deep-link'ах | `service/EmailService.java:299` (`/destination/...`), `:198` | Ф1/Ф4 |
| B8 | Юнит-тесты на составную уникальность и lookup по городу | `src/test/...` | Ф1 |

Уже готово, менять не надо: каталог города (`getActivitiesByDestination`) и
категории в городе (`getActivitiesByDestinationAndCategorySlug`) —
`ActivityController.java:22-56`.

(\*) Строго обязателен B1 только когда появится **второй город с совпадающим
slug'ом**. Пока город один (Прага), можно временно обслуживать
`/destinations/prague/activities/{slug}` по глобально-уникальному slug. Но раз
проектируем мульти-гео с общими slug'ами — закладываем сразу, иначе позже редизайн
уже опубликованных (= незаменяемых) URL.

## 7. Работа на фронтенде (основной объём)

Текущие маршруты: `myhive-react-app/src/components/Layout.js:37-56`.
Зависимости к замене: `react-router-dom` → `next/navigation`+`next/link`;
`react-helmet-async` → Next Metadata API. Auth0 (`oidc-client-ts` +
`react-oidc-context`) — остаётся, но в client-провайдере.

**Роутинг/структура (v3):** переименование `destination`→`destinations`,
`activity`→`activities`; **новые страницы** — хаб `/destinations`, каталог
`/destinations/[city]/activities`, категории `/…/activities/[category]`.

**SEO-разметка (сейчас у всех страниц общие теги):**
- уникальные `title`/`description` на каждую страницу + `canonical` + один `H1`;
- OG/Twitter на каждую страницу;
- JSON-LD: Organization (главная), Article (посты), Product/Offer с
  `priceCurrency=EUR` (карточки), FAQPage, BreadcrumbList;
- хлебные крошки (Home › Destinations › Prague › Activities).

**Прочее:** меню + футер (Destinations, Activities → `/destinations/prague/activities`
пока город один, Blog, About, Contact, CTA «Build Your Trip»; города/pillar/legal в
футере); блог-хаб с рубриками-кластерами (7 кластеров); проброс `destination` в
Trip Builder с новых city-страниц; Core Web Vitals — WebP, lazy-load, мобайл.

## 8. SSR-подводные камни и как их закрыть

- **251 обращение к `window`/`document`/`localStorage`** в 57 файлах: держать только
  в client-компонентах или под guard `typeof window !== 'undefined'`; публичные
  страницы не трогают их при серверном рендере.
- `react-helmet-async` → `generateMetadata()`; зависимость удаляется.
- `react-router-dom` → `next/navigation` + `next/link`; зависимость удаляется.
- Env: `REACT_APP_*` → `NEXT_PUBLIC_*` (найдено 10 переменных: OIDC-набор,
  `API_URL`, `SITE_URL`, `TURNSTILE_SITE_KEY`); обновить конфиг на Render.
- GTM / CookieYes / Turnstile → через `next/script`; Consent Mode v2 сохранить.
- Auth0: добавить origin нового Render-сервиса в allowed redirect URIs.

## 9. Фазы (incremental strangler → один cutover)

- **Ф0. Каркас.** `myhive-next/` (App Router), Render web-service, layout/header/
  footer/провайдеры, env, GTM/consent/Auth0 как client. Деплой на preview-URL,
  домен пока на CRA.
- **Ф1. Бэкенд v3.** B1–B4, B7, B8 (составная уникальность slug + генератор +
  lookup по городу + sitemap-URL + письма + тесты). За обновлённым бэкендом могут
  работать обе версии фронта.
- **Ф2. Публичные страницы** в v3-структуре как Server Components: SSR/ISR +
  per-page метатеги + OG + JSON-LD + крошки. **Главный SEO-выигрыш.**
- **Ф3. Порт admin / vote / payment** как client-компонентов (свап роутинга/env,
  минимум изменений логики) — для паритета перед cutover.
- **Ф4. Cutover.** Домен → Next-сервис; **301** старые→новые URL (Cloudflare /
  Render); `robots.txt` (`Sitemap:` + disallow `/vote/*`, `/payment/*`, шаги
  Trip Builder); починка `/sitemap.xml` (B5); отправка sitemap в Google Search
  Console; вывод CRA static-site (держим готовым к откату).
- **Ф5. Полировка.** On-demand revalidate вебхук (B6); WebP/lazy/CWV; удаление
  `react-router`/`react-helmet` зависимостей.

## 10. Разделение задач: Frontend / Backend / Edge / Content

- **Backend (Spring):** B1–B8 — умеренный объём, не переписывание. Ядро — составная
  уникальность slug и lookup по городу.
- **Frontend (Next.js):** основной объём — файловый роутинг v3 + 4 новых типа
  страниц + вся SEO-разметка (которой сейчас нет) + порт admin/vote/payment.
- **Edge / Infra:** 301-редиректы, `robots.txt`, починка `/sitemap.xml`, новый
  Render web-service, Google Search Console.
- **Content / Ops (не код, но блокирует SEO-эффект):** 50 постов + рубрики;
  **уникальные описания 100–200 слов на каждую комбинацию город × активность**
  (не шаблон — иначе клоны); нейтральные slug/title для adult-категорий; правило
  «город открывается только с готовым уникальным каталогом».

## 11. Тестирование

- Порт Jest/RTL (router-моки → `next/navigation`).
- **SSR smoke-тесты:** `curl` каждого типа публичного маршрута → проверка, что
  контент + уникальные `title`/`description`/OG есть в **сыром HTML** (тот самый
  тест из документа «View Page Source»).
- Бэкенд юнит-тесты: составная уникальность slug + lookup по городу (по CLAUDE.md —
  бэкенд-тесты обязательны для нового/изменённого кода).

## 12. Риски и откат

- **Один cutover** → митигируем preview-деплоем и параллельным прогоном до
  переключения DNS; CRA держим деплоибельным для быстрого отката.
- **Hydration-mismatch** (251 browser-API) → ловим smoke-тестами и на preview.
- Cold start на дешёвом Render-плане; Auth0 redirect URIs.
- Бэкенд-миграция уникальности slug — в критическом пути Ф1.

## 13. Критерий готовности (acceptance)

- View-source каждого типа публичной страницы = контент + уникальные
  `title`/`description`/OG + JSON-LD.
- `/sitemap.xml` = настоящий XML с v3-URL; `robots.txt` с `Sitemap:` и disallow
  служебных путей.
- Публикация поста/карточки в админке отражается в HTML по вебхуку/таймеру.
- Ноль регрессий в admin / vote / payment / booking-флоу.

## 14. Открытые вопросы к согласованию

1. **Резолв `[slug]`** категория-vs-карточка на одном уровне (см. §5) — подтвердить
   единый сегмент с резолвом.
2. **Интервал ISR revalidate** по умолчанию (напр. 1 час) + перечень admin-мутаций,
   которые дёргают вебхук ревалидации.
3. **301-карта** старых URL (`/destination/...`) → новых (`/destinations/...`) —
   составить полный список перед cutover.
4. Делать **B1 (составная уникальность slug)** сразу или отложить до второго города
   (см. §6, сноска).
