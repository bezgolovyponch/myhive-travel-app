# Lead-capture batch — follow-ups & manual QA (2026-07-28)

Branch: fixes-main-flow, batch range 81d1047..dee5120 (14 tasks + final review, all clean).

## Deferred follow-ups

- **Photo swaps (user to supply):** replace placeholder images in How It Works — `src/assets/home/steak-and-tits.jpg` for TinderMomentCard (currently the old swipe screenshot, card-within-card look) and a limo photo for the "Review & confirm" step. Import sites are commented in `HowItWorksSection.js`.
- **WhatsApp FAB on participant quiz:** `/vote/:shareToken/quiz` still shows the FAB while the identical organizer quiz route hides it — add its regex to FULL_SCREEN_ROUTES in `WhatsAppWidget.js` in a future pass.
- **Before flipping `STICKY_VOTE_CTA_ENABLED`:** verify the sticky bar hides behind ALL modals (auto-hide currently wired only to the vote-setup modal); see the precondition note in `services/config.js`.
- **Dead code:** `ChatPanel.js`/`.css` are imported nowhere — candidate for deletion.
- Assorted cosmetic deferrals from per-task reviews (vestigial test mock in TripSetupModal.test.js, inert wrapper div in DateRangePicker non-popover modes, etc.) — none merge-blocking per final review triage.

## MANUAL QA CHECKLIST (для ручного тестирования)

Выполнить вручную на `npm start` (порт 3000+backend) в браузере. Ниже — пункты из шага 2 брифа плюс отложенные визуальные проверки.

### A. Воронка группового голосования (Vote funnel)

1. Открыть главную страницу → нажать **"Start Group Vote"**.
2. Убедиться, что открывается **компактное модальное окно**: поля "путешественники" + даты (попап-календарь), **email отсутствует**.
3. Закрыть модалку, открыть снова — убедиться, что **введённые значения сохранились** (persist).
4. Перейти к квизу — убедиться, что **прогресс-бар предзаполнен** в соответствии с уже введёнными данными.
5. Пройти этап "curate swipe" (свайп активностей).
6. Перейти в Trip Builder.
7. Нажать **"Let your mates vote"** — должна появиться модалка с запросом email и **новым микротекстом** (проверить текст на актуальность/отсутствие опечаток).
8. Ввести email — убедиться, что создаётся lead: в **Network tab** должен пройти запрос **`POST /leads`**.
9. Нажать "Create vote" → должна открыться страница ожидания (waiting page).
10. Проверить в **DevTools → Application → Session/Local Storage**, что создана именно **QUIZ-сессия**, и **отсутствует** ключ `myhive-trip-vote-session` (старое поведение).

### B. Воронка "Browse" (самостоятельный подбор активностей)

1. Открыть главную страницу → сетка активностей должна быть **сразу под hero-блоком**.
2. Добавить активность в подборку.
3. Должна появиться **компактная модалка setup** (без лишних полей).
4. Перейти в Trip Builder → нажать **"Complete Booking"**.
5. В форме чекаута должна отображаться **consent-плашка** (текст согласия на обработку данных).
6. Ввести **валидный email** — примерно через **2 секунды** должен уйти запрос **`POST /leads`** (проверить Network tab, debounce).
7. Отправить бронирование — убедиться, что **бронирование проходит успешно**.

### C. Общие проверки по всему приложению

1. **WhatsApp FAB** (плавающая кнопка):
   - присутствует на главной странице и странице направления (destination);
   - **отсутствует** на странице `/vote/<token>/activities`.
2. **Sticky CTA** (липкий призыв к действию при скролле):
   - в текущей поставке флаг `STICKY_VOTE_CTA_ENABLED = false` → CTA **не должен появляться** ни при каком скролле;
   - (опционально) локально переключить флаг в `src/services/config.js` на `true`, проскроллить страницу мимо hero-блока — CTA должен появиться, расположен **выше** FAB WhatsApp (не перекрывать её); после проверки **обязательно вернуть флаг обратно в `false`**.
3. **Событие аналитики `modal_abandoned`**:
   - открыть модалку (setup или group vote), закрыть её **не отправляя форму**;
   - в консоли браузера проверить `window.dataLayer` — должно появиться событие `modal_abandoned`.

### D. Отложенные визуальные проверки (не блокируют релиз, зафиксировать отдельно)

(a) **VoteDemoCard / TinderMomentCard** в блоке "How It Works": в смешанных рядах эти карточки могут визуально быть **выше**, чем карточки-шаги с фото — проверить, не ломает ли это верстку/выравнивание рядов на разных брейкпоинтах.

(b) **tinder-block placeholder**: пока не подключены настоящие фотографии, плейсхолдер выглядит как "карточка в карточке" (card-within-card) — визуально не финальный вид, но ожидаемо до появления реальных фото.

(c) **Модальный popover-календарь на мобильных**: проверить позиционирование/скролл попапа с датами внутри маленькой setup-модалки на мобильных экранах (не обрезается ли, не уезжает ли за пределы экрана).

(d) **Sticky CTA флаг**: см. пункт C.2 — для проверки визуального поведения sticky CTA нужно локально включить флаг `STICKY_VOTE_CTA_ENABLED`, т.к. по умолчанию в проде он выключен.
