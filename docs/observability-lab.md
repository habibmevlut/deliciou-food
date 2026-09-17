# Milestone 4 lab — JWT, sigorta, Grafana, Zipkin

Sade anlatım: [`m4-kapı-sigorta-sayaç.md`](m4-kapı-sigorta-sayaç.md)

```bash
git pull
docker compose up -d
docker compose ps
```

`nefis-prometheus`, `nefis-grafana`, `nefis-zipkin` UP olmalı. IntelliJ: **Nefis Yemek: tum servisler**. Postman Import → **Replace**.

---

## A) JWT

1. Klasör **1 → Create customer** (401 olmamalı).
2. Klasör **0b → Login**. Body’de aynı e-posta. `accessToken` koleksiyona yazılır.
3. **List restaurants** (veya Get order). Header’da Bearer görünür.
4. Login’deki token’ı sil / yanlış yaz → **401**.

Kapı log’unda token yoksa Spring Security `401 Unauthorized`.

---

## B) Circuit breaker

1. IntelliJ’de **restaurant-service** Stop.
2. Postman **List restaurants** 4–8 kez. İlk denemeler hata; sonra 503 JSON: `sigorta atmış`.
3. restaurant-service’i tekrar Run. ~8 sn bekle, List restaurants 200.

`GET :8080/actuator/health` içinde `circuitBreakers` görünebilir.

---

## C) Grafana

Tarayıcı: http://localhost:3000 — `admin` / `admin`.

Sol **Dashboards** → **Nefis Yemek — genel**. Postman’da birkaç istek at, grafikte çizgi.

Prometheus hedefleri: http://localhost:9090 → Status → Targets. IntelliJ servisleri ayaktaysa 8080–8086 **UP**.

---

## D) Zipkin

http://localhost:9411 → Run query (servis `api-gateway`).

Bir Get restaurants / Sipariş Ver’den sonra iz: `api-gateway` → `restaurant-service` (ve saga’da diğerleri). Aynı `traceId` IntelliJ log satırında `[api-gateway,<traceId>]`.
