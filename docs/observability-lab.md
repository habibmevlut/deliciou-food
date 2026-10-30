# Milestone 4 lab — JWT, sigorta, Grafana, Zipkin

Neden bakıyorsun (sayı vs bir isteğin filmi): [`m4-kapı-sigorta-sayaç.md`](m4-kapı-sigorta-sayaç.md)

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

## C) Prometheus + Grafana (sayı)

Neden: Kafka UI mektuba bakar; burası **servis ayakta mı, kaç istek geldi**. Prometheus sayıyı depoya koyar, Grafana çizer.

1. http://localhost:9090 → **Status → Targets**. IntelliJ’de tum servisler ayaktaysa `8080`–`8086` **UP**.
2. Restaurant-service’i Stop et, Targets’da `:8083` **DOWN** olsun. Tekrar Run, UP’a dönsün.
3. http://localhost:3000 — `admin` / `admin`.
4. Sol **Dashboards** → **Nefis Yemek — genel**.
5. Postman’da List restaurants / Sipariş Ver birkaç kez. “HTTP istek / saniye” çizgisi kımıldar. Servisi durdurunca “ayakta mı” çizgisi 0’a iner.

---

## D) Zipkin (bir isteğin yolu)

Neden: Grafana toplamı gösterir. Zipkin **şu an attığın tek isteğin** kapıdan hangi servise gittiğini kutu kutu çizer.

1. Login + **List restaurants** (200).
2. http://localhost:9411 → servis `api-gateway` → **Run query**.
3. Bir iz aç: `api-gateway` ve `restaurant-service` kutuları. Süre ms cinsinden.
4. IntelliJ gateway log’unda `[api-gateway,<traceId>]` — aynı numara Zipkin izinin id’si.
5. (İsteğe bağlı) restaurant-service Stop + List restaurants, sonra Zipkin: kopuk / sadece kapı kutusu = istek arkada düşmüş.
