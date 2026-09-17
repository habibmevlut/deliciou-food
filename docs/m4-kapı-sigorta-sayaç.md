# Kapı, sigorta, sayaç, iz — Milestone 4

Lab tıklamaları: [`observability-lab.md`](observability-lab.md)

Üç ayrı alet. Hepsi “mikroservis production hissi” için; sipariş saga’sını değiştirmez.

---

## 1) JWT — kapıda kimlik

Tek cümle: **biletin yoksa kapı içeri almaz.**

Eskiden `http://localhost:8080/api/orders` herkese açıktı. Şimdi:

1. Müşteri oluştur (bilet **gerekmez**): `POST /api/customers`
2. Login (bilet **gerekmez**): `POST /api/auth/login` `{ "email": "ali@example.com" }`
3. Cevaptaki `accessToken` bir JWT — imzalı bilet. Şifre yok; bu laboratuvar, e-posta kimlik.
4. Sonraki her istek: header `Authorization: Bearer <token>`

Bileti **yalnızca gateway** kontrol eder. order-service JWT bilmez. Neden: altı servise Security kopyalamamak.

Biletsiz Get order → **401**. Health (`/actuator/health`) açık kalır — Prometheus ve Docker healthcheck bozulmasın.

---

## 2) Circuit breaker — sigorta

Tek cümle: **arka servis peş peşe ölürse kapı bekletmez, hemen “kapalı” der.**

Restaurant-service’i IntelliJ’de durdur. Birkaç kez Get restaurants at. Önce connection error; sonra gateway **503** + `"sigorta atmış"`. 8 sn sonra sigorta yavaşça tekrar dener (half-open).

GET isteklerinde ayrıca **Retry**: 5xx olursa kapı 2 kez daha dener. POST’a retry yok (siparişi iki kez yazmasın).

---

## 3) Prometheus + Grafana + Zipkin

| Araç | Adres | Ne işe yarar |
| --- | --- | --- |
| Prometheus | http://localhost:9090 | Sayı toplar (`/actuator/prometheus`) |
| Grafana | http://localhost:3000 (`admin` / `admin`) | Sayıyı grafik yapar |
| Zipkin | http://localhost:9411 | Bir HTTP isteğinin hangi servislerden geçtiğini çizer |

`docker compose up -d` bunları Kafka UI ile birlikte açar.

Zipkin’de bir iz görmek için: login + Get restaurants. Search → servis `api-gateway`. Trace’de gateway → restaurant-service kareleri.

---

## Postman sırası

1. Create customer  
2. **Login** (klasör 0b) — `token` dolar  
3. Eskisi gibi 2–10. Koleksiyon Bearer `{{token}}` gönderir.

Health ve Login biletsiz.
