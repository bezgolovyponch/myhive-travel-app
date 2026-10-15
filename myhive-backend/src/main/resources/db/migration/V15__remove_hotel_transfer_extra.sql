-- Transfers are not sold as an extra: "Transfer from your hotel" comes off every activity that offers it
-- (on 2026-10-14: ak-47-glock-17-shooting, beer-spa, bubble-football). The other extras keep their order.
-- By code, so it is safe on a database that has none of them; an activity left with no extras gets NULL.
UPDATE activities a
SET extras = (
    SELECT CASE WHEN count(*) = 0 THEN NULL ELSE jsonb_agg(e.value ORDER BY e.ord)::text END
    FROM jsonb_array_elements(a.extras::jsonb) WITH ORDINALITY AS e(value, ord)
    WHERE e.value ->> 'code' <> 'transfer-from-your-hotel'
)
WHERE a.extras LIKE '%"transfer-from-your-hotel"%';
