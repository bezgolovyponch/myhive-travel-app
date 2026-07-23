# Чеклист: инфра (cutover) + статус SEO-v3

**Дата:** 2026-07-23. Ф0+Ф1 реализованы, проверены локально (включая
админ-логин, полный vote-флоу и букинг против докер-бэкенда).
Ветка `feat/nextjs-foundation` (head `66fa978`) — готова к деплою как есть.
Спека: `docs/superpowers/specs/2026-07-20-nextjs-migration-design.md`.

---

## Часть 1. Инфра-чеклист (коллеге) — единственный критический путь

### A. Preview (сделать сейчас — после этого прогоняем смоуки и готовы к cutover)

1. **Render: новый web service для `myhive-next`**
   - Root directory: **пустой (корень репозитория)** — `prebuild` копирует
     `myhive-react-app/src` в `myhive-next/legacy-src`, а Render не даёт
     сервису файлы вне root directory ни на билде, ни в рантайме
   - Build: `cd myhive-next && npm ci && npm run build`
   - Start: `cd myhive-next && npm run start`
   - Node: из `.node-version` **в корне репо** (20.20.0, добавлен в этой ветке)
   - Health check path (опционально): `/robots.txt` — лёгкий, без похода в бэкенд
   - Env vars:
     - `BACKEND_URL=https://myhive-backend.onrender.com/api` — **обязательная**,
       билд намеренно падает без неё (защита от «тихого» localhost-фоллбэка)
     - `NEXT_PUBLIC_SITE_URL=<preview-url>`
     - `NEXT_PUBLIC_OIDC_AUTHORITY`, `NEXT_PUBLIC_OIDC_CLIENT_ID`,
       `NEXT_PUBLIC_OIDC_REDIRECT_URI=<preview-url>/admin`,
       `NEXT_PUBLIC_OIDC_AUDIENCE` — как у CRA, но с preview-origin
     - `NEXT_PUBLIC_TURNSTILE_SITE_KEY=<боевой ключ>`
     - `INTERNAL_API_TOKEN=<длинный случайный секрет>` — тот же секрет ставится
       на Render-бэкенде; SSR-трафик идёт с одного egress-IP и без него упирается
       в лимит 100 req/min (шлётся как `X-Internal-Token`)
     - `ALLOW_INDEXING` — **НЕ ставить** на preview (robots по умолчанию
       Disallow-all; включается только на проде при cutover)
2. **Backend: задеплоить ветки `fix/vote-suggestions-null-bind` и
   `feat/seo-gate-internal-token`** (мердж в main → деплой Render-бэкенда).
   Первая чинит 500 на `/vote/sessions/{token}/result` — воспроизводится на
   проде, когда группа налайкала всё, что предлагал квиз (боевой CRA-сайт
   подвержен так же). Вторая добавляет `INTERNAL_API_TOKEN`-исключение из
   rate-limit (env `INTERNAL_API_TOKEN` — тот же секрет, что на Render
   фронтенда). Колонки `seoIndexable` — **отложенная отдельная** бэкенд-правка
   (самодобавятся при деплое, prod `ddl-auto=update`); пока их нет, поле в
   ответах API отсутствует, и per-record SEO-гейт на фронте работает как
   «всё не индексируется» — до п. C12 их нужно доехать.
3. **Backend Render: `CORS_ALLOWED_ORIGINS` + preview-origin.**
   Браузер шлёт свой Origin даже через same-origin `/api`-rewrite; Spring
   отдаёт 403 на все write-запросы с неизвестного origin (проверено локально —
   это единственный жёсткий блокер смоук-тестов). Переменная **замещает**
   дефолтный список — перечислить все нужные origin'ы целиком.
4. **Auth0**: preview-URL в Allowed Callback (`…/admin`), Logout URLs, Web Origins.
5. Смоук на preview: `node myhive-next/scripts/smoke.mjs <preview-url>` +
   ручное: админ-логин, vote-флоу, Stripe test payment, контакт-форма
   (боевой Turnstile).

### B. До cutover (можно параллельно)

5. **Cloudflare: отключить managed-блок «content signals»** — блок **добавляется
   сверху** к нашему robots.txt (проверено 2026-07-23: наши правила и Sitemap
   видны ниже блока — это НЕ полный подмен и не блокер индексации). Отключаем,
   потому что блок навязывает свои правила (`Allow: /`, запреты AI-ботам),
   которые мы не выбирали.
6. **Google Search Console: верифицировать домен** (DNS TXT через Cloudflare).
   Домен верифицирован только в Meta; для Google — отдельно. Делается заранее.
7. Зафиксировать канонический хост: де-факто www (apex 301→www). Он же в
   `NEXT_PUBLIC_SITE_URL` прода.

### C. Cutover (день X)

8. Домен → Next-сервис; CRA-деплой держим готовым для отката.
9. Env прода: `ALLOW_INDEXING=true`, `NEXT_PUBLIC_SITE_URL=https://www.trivlu.com`
   (+ прод-redirect_uri в Auth0, прод-origin в CORS — если ещё не были).
   `ALLOW_INDEXING=true` открывает только записи с `seoIndexable=true`
   (глобальный аварийный выключатель + пер-записный гейт; новые записи по
   умолчанию не индексируются).
10. Проверить **живые ответы** (не файлы): view-source всех типов страниц,
    `robots.txt` (правила: Disallow /admin /vote /payment — без завершающего
    слэша, покрывает и голый `/admin` + Sitemap), `/sitemap.xml` (настоящий
    XML), 404 на неизвестный slug.
11. Отправить sitemap в GSC; браузерный прогон booking/vote/payment на проде.
12. Проставить `seoIndexable=true` в админке только на редакционно готовые
    записи (уникальные 100–200-слов описания, нейтральные slug/title).
13. Stripe: прод-домен в allowlist'ах (return-URL резолвится из
    `CORS_ALLOWED_ORIGINS` — общий список с CORS).

---

## Часть 2. Чеклист SEO-v3 (нам) — статус по документу

«Технический SEO (чек-лист разработчику)» из
`Trivlu_SEO_структура_сайта_v3.docx`:

| # | Пункт SEO-v3 | Статус |
|---|---|---|
| 1 | SSR публичных страниц | ✅ **Ф1 готово** — все публичные страницы Server Components, контент в исходном HTML (view-source-проверка проходит) |
| 2 | XML-sitemap автообновляемый | ✅ `app/sitemap.ts`, каталог+блог, обновление раз в час. Отправка в GSC — инфра (п. B6/C11) |
| 3 | robots.txt: открыть публичные, закрыть служебные | ✅ `app/robots.ts`: Disallow `/admin` `/vote` `/payment` (голые префиксы, без завершающего слэша — покрывает и точное совпадение); индексация opt-in через `ALLOW_INDEXING` + пер-записный `seoIndexable` (preview не утечёт, прод не деиндексируется по забывчивости, неготовые записи не индексируются по умолчанию). ⚠️ Cloudflare managed-блок добавляется сверху — не подменяет и не блокер, но отключаем как не наши правила — инфра п. B5 |
| 4 | Google Search Console | 🔲 инфра (п. B6) |
| 5 | Уникальные title/description, canonical, один H1 | ✅ на каждой странице; тексты — из таблицы SEO-v3 |
| 6 | Schema.org | ✅ Organization (главная), Article (посты), BreadcrumbList (вложенные). 🔲 FAQPage — когда появится FAQ-контент; 🔲 Product/Offer на карточках — после cutover (по спеке) |
| 7 | Хлебные крошки | ✅ видимые + JSON-LD на destination/activity/package |
| 8 | OG/Twitter на каждую страницу | ✅ уникальные OG per-page через общий `pageMetadata` (title/desc/url/**og:image/og:type всегда** — Next мерджит `openGraph` неглубоко, точечный merge на каждой странице терял их; теперь одна точка правды, включая правовые страницы); twitter наследует общие |
| 9 | Скорость/CWV: WebP, lazy-load, мобайл | ◐ lazy-load на карточках есть; WebP/оптимизация изображений — Ф4 (после cutover) |
| — | Перелинковка (внутренние ссылки, требование v3) | ✅ блог рендерит Markdown (заголовки, внутренние ссылки) — требование перелинковки v3 выполнимо контентом, код не блокирует |

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
