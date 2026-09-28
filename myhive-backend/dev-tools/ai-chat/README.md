# AI planner playground

A dependency-free browser client for the AI planner API (`docs/api/ai-planner-api.md`), for
manual testing and as a reference for the frontend integration. Dev only — nothing here is
served by the backend or shipped.

```
node dev-tools/ai-chat/check-key.mjs                 # one tiny real call per model, verifies QWEN_* in .env
./gradlew bootRun --args='--spring.profiles.active=dev --server.port=8081'
BACKEND_URL=http://127.0.0.1:8081 node dev-tools/ai-chat/server.mjs   # then open http://localhost:4173
```

- `server.mjs` serves `index.html` and forwards `/ai/*` and `/destinations` to `BACKEND_URL`
  (default `http://localhost:8080`; use `.../api` against a prod-profile run), so the browser sees
  one origin and CORS never comes into it.
- `index.html` — chat with quick prompts, brief and limits, the three packages by day with prices
  and a **Choose** button, the last edit report, and the raw JSON of the last response. It polls a
  running generation every 2 s and can resume the last session kept in `localStorage`.
- `check-key.mjs` reads `myhive-backend/.env`, never prints the key, and exits 1 unless both
  `qwen3.7-plus` and `qwen3.8-max` answer. A model list from `GET /models` is not proof — some
  DashScope hosts return 200 for any key; only a chat completion counts.

Backend needs `AI_ENABLED=true` and a Model Studio key (`QWEN_API_KEY`, and `QWEN_BASE_URL` for a
workspace-scoped region such as Frankfurt) in `.env`, which `bootRun` loads.
