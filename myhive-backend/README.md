# MyHive Backend

## Dev Docker (rebuild from scratch)

### Windows

Double-click or run from `myhive-backend/`:

```
dev-restart.bat
```

### Manual (step by step)

Run from `myhive-backend/`:

```bash
docker compose -f docker-compose.dev.yml down -v
```

```bash
docker compose -f docker-compose.dev.yml build --no-cache
```

```bash
docker compose -f docker-compose.dev.yml up -d
```

```bash
docker compose -f docker-compose.dev.yml logs -f
```

Backend will be available at http://localhost:8080

## Content localization

Translatable content (destinations, activities, packages, categories, blog
posts) keeps English in the base columns and per-locale overrides in a
`translations` TEXT column holding JSON: `{"de": {"name": "...", ...}}`
(`util/TranslationsConverter`; schema in `db/migration/V2__content_translations.sql`).

- Public GET endpoints take `?locale=` (en/de/…): fields come back resolved for
  that locale, field-by-field English fallback, response shape unchanged. The
  frontends always send it.
- No `locale` param = raw/admin view: base fields plus the `translations` map,
  which the admin PUT endpoints also accept (`null` = leave unchanged, `{}` =
  clear).
- Adding a language needs no backend change — only content: fill `"<locale>"`
  keys. Prod fill for German: `prod-migration-translations-de.sql` (run against
  the prod database; base rows are matched by slug).
