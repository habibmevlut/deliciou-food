# Kapı, sigorta, sayaç, iz — Milestone 4

Lab tıklamaları: [`observability-lab.md`](observability-lab.md)

Milestone 4 üç iş: **kim içeri girer** (JWT), **arka servis ölünce kapı ne yapar** (sigorta), **çalışırken nasıl bakarsın** (Prometheus / Grafana / Zipkin). Üçüncüsü sipariş saga’sını değiştirmez; seyircidir.

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

## 3) Prometheus + Grafana + Zipkin — bakmak

Tek cümle: **sipariş kodu değişmez; 6 servis varken “hasta mı, bu istek nerede takıldı?” sorusuna ekrandan bakarsın.**

### Bu adımın konusu ne?

M2 / M3 siparişin **doğru** ilerlemesiydi (öde → restoran → kurye, aynı komutu iki kez yeme).

Burada yeni iş kuralı yok. Soru şu: laboratuvarda 7 IntelliJ konsolu açık, bir sipariş takılınca log’a bakıyorsun. Gerçek hayatta o pencereler yok. Altı servisten biri yavaş veya ölüyse bunu **sayı** ve **bir isteğin yolu** ile görürsün. Milestone 4’ün bu parçası o bakışı kuruyor.

Kafka UI (`:8090`) başka iş: kuyrukta **mektup** var mı (`payment-commands` dolu mu).  
Bu üçlü kuyruğa bakmaz. Servislerin **nabzına** ve **tek bir HTTP isteğinin gezisine** bakar.

### Üç alet, üç soru

| Soru | Araç | Adres |
| --- | --- | --- |
| Servis ayakta mı? Dakikada kaç istek, kaç hata? | **Prometheus** sayıları toplar | http://localhost:9090 |
| Aynı sayıyı çizgi / panel olarak gör | **Grafana** Prometheus’u çizer | http://localhost:3000 (`admin` / `admin`) |
| **Şu** Get restaurants / Sipariş Ver hangi servislerden geçti, hangisi yavaş? | **Zipkin** bir isteğin filmini çizer | http://localhost:9411 |

Prometheus grafiği **kendisi çizmez**; depo gibidir. Grafana o depoyu okur. Zipkin sayı tutmaz; bir isteğe kimlik (`traceId`) takıp kapı → restoran (ve saga’da ödeme, kurye) karelerini yan yana koyar.

```
Sen (Postman) ──► api-gateway :8080 ──► restaurant-service :8083
                         │                      │
                         │  her 5 sn            │  her 5 sn
                         ▼                      ▼
                  /actuator/prometheus    /actuator/prometheus
                         │                      │
                         └──────────► Prometheus :9090
                                           │
                                           ▼
                                      Grafana :3000   (çizgi)

Aynı istek giderken gateway + restoran Zipkin’e
“ben bu izdeydim, şu kadar ms sürdüm” der ──► Zipkin :9411
```

Spring **Actuator** bu sayıları üretir. `/actuator/health` = sağ mıyım (JWT yok, Prometheus da buradan bakabilsin diye açık). `/actuator/prometheus` = sayaçların ham listesi.

### Prometheus — sayaç

Neden: “restaurant-service öldü mü?” sorusuna log okumadan evet/hayır. Her 5 saniyede `8080`–`8086` adreslerindeki `/actuator/prometheus` sayfasını çeker, sayıyı saklar.

IntelliJ servisleri ayaktaysa http://localhost:9090 → **Status → Targets** satırları **UP** olmalı. Restaurant-service’i durdurursan o hedef **DOWN** — Grafana’daki “ayakta mı” çizgisi de 0’a iner.

### Grafana — pano

Neden: Prometheus’un metnini (`http_server_requests_seconds_count{...}`) kimse ezberlemez. Grafana hazır dashboard’u açar: **Nefis Yemek — genel**. Postman’da birkaç istek at, “HTTP istek / saniye” çizgisi hareket eder. Sayıyı **görmek** için Grafana, sayıyı **tutmak** için Prometheus.

### Zipkin — bir isteğin filmi

Neden: Grafana “dakikada 20 istek, 3 hata” der; **hangi** isteğin **hangi** serviste takıldığını söylemez.

Zipkin bir isteğe bilet numarası takar (`traceId`). Gateway restoran’a giderken aynı numarayı header’da taşır. İkisi de Zipkin’e “ben bu numaradaydım” yazar. Ekranda kutu kutu: `api-gateway` → `restaurant-service`. Saga mutlu yolda sipariş birkaç servise dokunursa kutular çoğalır.

Aynı numara IntelliJ log’da da var: `[api-gateway,<traceId>]`. Log ile Zipkin’i bu id ile eşlersin.

Örnek: Login + **List restaurants**. Zipkin → Run query, servis `api-gateway`. Bir iz aç; iki kutu görmelisin. Restaurant-service kapalıysa iz kopuk kalır veya sadece kapı kutusu durur — “takıldığı yer kapının arkası” dersin.

### Ne değişmez

Sipariş saga’sı, outbox, JWT kuralı aynı. Bu üçlü **seyirci**. `docker compose up -d` onları Kafka UI ile birlikte açar.

---

## Postman sırası

1. Create customer  
2. **Login** (klasör 0b) — `token` dolar  
3. Eskisi gibi 2–10. Koleksiyon Bearer `{{token}}` gönderir.

Health ve Login biletsiz.
