# Чеклист: инфра (cutover) + статус SEO-v3

**Дата:** 2026-07-23. Ф0+Ф1 реализованы и проверены локально
(ветка `feat/nextjs-foundation`, коммиты `f678db9`, `aa77182`).
Спека: `docs/superpowers/specs/2026-07-20-nextjs-migration-design.md`.

---

## Часть 1. Инфра-чеклист (коллеге) — единственный критический путь

### A. Preview (сделать сейчас — после этого прогоняем смоуки и готовы к cutover)

1. **Render: новый web service для `myhive-next`**
   - Root directory: `myhive-next`
   - Build: `npm ci && npm run build` · Start: `npm run start`
   - Node: из `.node-version` (20.20.0)
   - Env vars:
     - `BACKEND_URL=https://myhive-backend.onrender.com/api` — **обязательная**,
       билд намеренно падает без неё (защита от «тихого» localhost-фоллбэка)
     - `NEXT_PUBLIC_SITE_URL=<preview-url>`
     - `NEXT_PUBLIC_OIDC_AUTHORITY`, `NEXT_PUBLIC_OIDC_CLIENT_ID`,
       `NEXT_PUBLIC_OIDC_REDIRECT_URI=<preview-url>/admin`,
       `NEXT_PUBLIC_OIDC_AUDIENCE` — как у CRA, но с preview-origin
     - `NEXT_PUBLIC_TURNSTILE_SITE_KEY=<боевой ключ>`
     - `ALLOW_INDEXING` — **НЕ ставить** на preview (robots по умолчанию
       Disallow-all; включается только на проде при cutover)
2. **Backend Render: `CORS_ALLOWED_ORIGINS` + preview-origin.**
   Браузер шлёт свой Origin даже через same-origin `/api`-rewrite; Spring
   отдаёт 403 на все write-запросы с неизвестного origin (проверено локально —
   это единственный жёсткий блокер смоук-тестов). Переменная **замещает**
   дефолтный список — перечислить все нужные origin'ы целиком.
3. **Auth0**: preview-URL в Allowed Callback (`…/admin`), Logout URLs, Web Origins.
4. Смоук на preview: `python3 f1_smoke.py <preview-url>` (скрипт у Ольги) +
   ручное: админ-логин, vote-флоу, Stripe test payment, контакт-форма
   (боевой Turnstile).

### B. До cutover (можно параллельно)

5. **Cloudflare: отключить managed-блок «content signals»** — сейчас он
   **полностью подменяет** отдаваемый `robots.txt` (проверено 2026-07-21).
   Пока включён — наши robots/sitemap для Google невидимы. Блокер.
6. **Google Search Console: верифицировать домен** (DNS TXT через Cloudflare).
   Домен верифицирован только в Meta; для Google — отдельно. Делается заранее.
7. Зафиксировать канонический хост: де-факто www (apex 301→www). Он же в
   `NEXT_PUBLIC_SITE_URL` прода.

### C. Cutover (день X)

8. Домен → Next-сервис; CRA-деплой держим готовым для отката.
9. Env прода: `ALLOW_INDEXING=true`, `NEXT_PUBLIC_SITE_URL=https://www.trivlu.com`
   (+ прод-redirect_uri в Auth0, прод-origin в CORS — если ещё не были).
10. Проверить **живые ответы** (не файлы): view-source всех типов страниц,
    `robots.txt` (правила: Disallow /admin/ /vote/ /payment/ + Sitemap),
    `/sitemap.xml` (настоящий XML), 404 на неизвестный slug.
11. Отправить sitemap в GSC; браузерный прогон booking/vote/payment на проде.
12. Stripe: прод-домен в allowlist'ах (return-URL резолвится из
    `CORS_ALLOWED_ORIGINS` — общий список с CORS).

---

## Часть 2. Чеклист SEO-v3 (нам) — статус по документу

«Технический SEO (чек-лист разработчику)» из
`Trivlu_SEO_структура_сайта_v3.docx`:

| # | Пункт SEO-v3 | Статус |
|---|---|---|
| 1 | SSR публичных страниц | ✅ **Ф1 готово** — все публичные страницы Server Components, контент в исходном HTML (view-source-проверка проходит) |
| 2 | XML-sitemap автообновляемый | ✅ `app/sitemap.ts`, каталог+блог, обновление раз в час. Отправка в GSC — инфра (п. B6/C11) |
| 3 | robots.txt: открыть публичные, закрыть служебные | ✅ `app/robots.ts`: Disallow `/admin/` `/vote/` `/payment/`; индексация opt-in через `ALLOW_INDEXING` (preview не утечёт, прод не деиндексируется по забывчивости). ⚠️ Cloudflare-подмена — инфра п. B5 |
| 4 | Google Search Console | 🔲 инфра (п. B6) |
| 5 | Уникальные title/description, canonical, один H1 | ✅ на каждой странице; тексты — из таблицы SEO-v3 |
| 6 | Schema.org | ✅ Organization (главная), Article (посты), BreadcrumbList (вложенные). 🔲 FAQPage — когда появится FAQ-контент; 🔲 Product/Offer на карточках — после cutover (по спеке) |
| 7 | Хлебные крошки | ✅ видимые + JSON-LD на destination/activity/package |
| 8 | OG/Twitter на каждую страницу | ✅ уникальные OG per-page (title/desc/url/image); twitter наследует общие |
| 9 | Скорость/CWV: WebP, lazy-load, мобайл | ◐ lazy-load на карточках есть; WebP/оптимизация изображений — Ф4 (после cutover) |

**Структурные пункты v3 (не «технический чеклист», а архитектура):**

| Пункт | Когда |
|---|---|
| URL v3: `/destinations/[city]/activities/[slug]`, хаб `/destinations`, один набор 301 | **Ф3, после cutover** (решение §14.1 от 2026-07-21: сначала SSR на текущих URL) |
| Категорийные лендинги (nightlife, czech-beer, shooting, adult…) | Ф3 |
| Меню/футер v3 (Destinations · Activities · города · pillar-гайд) | Ф3 |
| Trip Builder: destination из path, не из hostname | Ф3 |
| Составная уникальность slug на бэке (B1–B3) | Триггер — второй город |

**Контент (не код, блокирует SEO-эффект — Content/Ops):**
50 постов по кластерам; уникальные описания 100–200 слов на каждую
комбинацию город×активность; нейтральные slug/title для adult-карточек;
город открывается только с готовым каталогом; перелинковка по карте
(120 ссылок).

**«Порядок внедрения» из SEO-v3 → факт:**
1. Фундамент (SSR+sitemap+robots) — ✅ код готов; GSC — инфра.
2. Лендинги (главная, город, about, contact) — ✅ на текущих URL; хаб
   `/destinations` — Ф3.
3. Блог-хаб + посты — ✅ хаб и страницы постов готовы; рубрики-кластеры —
   вместе с контентом.
4. Города-лендинги — по мере гео (+бэкенд B1–B3).
