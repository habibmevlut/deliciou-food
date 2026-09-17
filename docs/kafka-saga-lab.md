# Kafka Saga Lab — büyük pencereden akış

Bu dosya Postman + Kafka UI ile **üç senaryoyu** sırayla denemen için. README özet tutar; burası adım adım laboratuvar.

Kodda yönetmen / oyuncu / compensate: [`kafka-orchestration.md`](kafka-orchestration.md).  
Neden outbox (Postgres önce, Kafka sonra): [`kafka-outbox.md`](kafka-outbox.md).  
Neden processed_events / history: [`cqrs-idempotency.md`](cqrs-idempotency.md).

Kafka UI: [http://localhost:8090](http://localhost:8090)  
Gateway (Postman): [http://localhost:8080](http://localhost:8080)  
Koleksiyon: `http/Nefis-Yemek.postman_collection.json` → klasör **8. Saga**, **9. Outbox**, **10. Idempotency / History**

---

## 0) Büyük resim (okumadan tıklama)

Dış dünya yalnızca gateway’e konuşur. Kafka **servislerin kendi arasında** konuştuğu kuyruk. Orchestrator (`order-service`) yönetmen: “şunu yap” komutu atar, “oldu” event’ini bekler, bir sonraki komutu atar.

```
Sen ──HTTP──► :8080 ──► order-service
                              │
                              │  0. aynı TX: orders + outbox_messages
                              │  poller ~250 ms sonra Kafka
                              │  1. ChargePaymentCommand
                              ▼
                     payment-commands          payment-service çeker, DB'ye CAPTURED yazar
                              │
                              │  2. PaymentChargedEvent
                              ▼
                     payment-events            orchestrator dinler
                              │
                              │  3. ReserveRestaurantCommand
                              ▼
                     restaurant-commands       restaurant-service kabul veya red
                              │
                    ┌─────────┴─────────┐
                    ▼                   ▼
         RestaurantAccepted    RestaurantRejected
         restaurant-events     restaurant-events
                    │                   │
                    │ 4. AssignCourier  │ 4'. RefundPaymentCommand
                    ▼                   ▼
         delivery-commands     payment-commands (iade)
                    │                   │
                    │ 5. CourierAssigned│ 5'. PaymentRefundedEvent
                    ▼                   ▼
         delivery-events       payment-events
                    │                   │
                    ▼                   ▼
              Order CONFIRMED     Order CANCELLED
```

**Komut topic’i** = işi yap (`*-commands`).  
**Event topic’i** = iş bitti (`*-events`).  
**Key** her mesajda `orderId`. Aynı siparişi altı topic’te bu UUID ile takip edersin.

Yönetmen `order-service`. Payment/restaurant/delivery birbirini doğrudan aramaz; sadece kendi komutunu tüketir, event üretir.

---

## 1) Sahneyi kur (bir kez)

1. Terminal:

   ```bash
   git pull
   docker compose up -d
   docker compose ps
   ```

   `nefis-kafka` (9092) ve `nefis-kafka-ui` (8090) **UP** olmalı.
   M4 ile Prometheus (:9090), Grafana (:3000), Zipkin (:9411) de ayağa kalkar.

2. IntelliJ: **Nefis Yemek: tum servisler**. Konsolda `Started *Application` gör.

3. Postman: koleksiyonu **Import → Replace**. `{{gateway}}` = `http://localhost:8080`.

4. Tarayıcı: [http://localhost:8090](http://localhost:8090)  
   Sol üstte cluster adı **nefis-yemek**. Brokers yeşil olmalı.

5. Kafka UI’da **Topics**. İlk açılışta yalnızca Kafka’nın iç topic’leri (`__consumer_offsets` gibi) durabilir. Saga topic’leri **ilk mesaj gelince** oluşur (`auto.create.topics`). Panik yok.

Hazırlık isteği (saga’dan önce, bir kez):

| Sıra | Postman | Neden |
| --- | --- | --- |
| 1 | **0. Gateway → Health** | 8080 ayakta mı (JWT yok) |
| 2 | **1. customer → Create customer** | `customerId` + `email` dolar (JWT yok) |
| 3 | **0b. Auth → Login** | `token` dolar; sonrası Bearer |
| 4 | **2. restaurant → Create restaurant** | `restaurantId` dolar |

Bu iki id + Login token olmadan klasör 8 **401** verir.

---

## 2) Kafka UI’da mesaj nasıl okunur (her senaryoda aynı)

1. Sol menü **Topics**.
2. Topic adına tıkla (ör. `payment-commands`).
3. Üstteki **Messages** sekmesi.
4. Önemli: varsayılan çoğu zaman **Latest** (sadece yeni mesaj). Eski mesajı görmek için:
   - **Seek** / **from** → **Earliest** (veya offset `0`)
   - **Produce / consume** tarafında **JSON**
5. Tabloda **Key** sütunu = `orderId`. Postman’daki sipariş id’si ile eşleştir.
6. Satıra tıkla: JSON body + header. Spring `JsonSerializer` bazen `__TypeId__` header koyar (`ChargePaymentCommand` vs `RefundPaymentCommand` aynı topic’te bununla ayrılır).

**Consumers** menüsü: group adı servis adıdır (`order-service`, `payment-service`, …). **Lag = 0** → mesaj okundu. Lag büyüyorsa o servis ayakta değildir.

İç topic `__consumer_offsets`’e dokunma.

---

## Senaryo A — Mutlu yol (hepsi yeşil)

Amaç: bir siparişin Kafka’da **soldan sağa** nasıl ilerlediğini görmek. Restoran kasten kabul eder (`forceRestaurantAccept`).

### A1. Siparişi at

Postman: **8. Saga — Sipariş Ver → Sipariş Ver (mutlu yol)** → Send.

Cevap hemen gelir, status çoğu zaman `AWAITING_PAYMENT`. Bu normal: HTTP bitti, saga Kafka’da devam ediyor.

`id` değerini kopyala. Buna **ORDER_ID** de.

### A2. HTTP tarafını doğrula

Aynı klasör: **Get order (saga sonucu)** 2–3 kez (arada ~1 sn).

- `CONFIRMED` → A3’e geç.
- `AWAITING_PAYMENT` (senin ekranın) → saga ödeme adımında durdu. **Get’i tekrar tekrar basmak yetmez.** Aşağıdaki sırayı izle; her adımı bitirmeden sonrakine geçme.

#### A2b. `AWAITING_PAYMENT` takıldıysa (şimdi senin durumun)

Sipariş örneği: `84071403-aecc-473c-9d5f-92ae352536d0`. HTTP doğru; Kafka’daki payment tüketicisi komutu işlememiş.

1. IntelliJ Run penceresinde **payment-service** seçili mi, kırmızı stop değil mi? `Started PaymentServiceApplication` satırı var mı?
2. payment-service konsolunda bu sipariş id’sini ara:
   - `PAYMENT CAPTURED` → ödeme oldu; sorun order-service (adım 6).
   - `trusted packages` / `SerializationException` → eski deserialize hatası; adım 3 şart.
   - Hiçbiri yok, log durgun → consumer takılı veya servis Kafka’ya bağlı değil; adım 3 + 5.
3. Terminal:

   ```bash
   git pull
   ```

   IntelliJ: Maven **Reload All**. Sonra **Stop** ve tekrar **Nefis Yemek: tum servisler** (en azından order + payment + restaurant + delivery).
4. payment-service ayağa kalkınca konsolda `PAYMENT command type=...ChargePaymentCommand` ve `PAYMENT CAPTURED` beklenir. `LinkedHashMap` / `String` / `null` görürsen hâlâ deserialize yanlış — bu mesajı not et.
5. Kafka UI → **Consumers** → `payment-service`. `payment-commands` **lag** büyükse eski bozuk kayıt kuyruğu tıkıyor. Temizlemek (öğrenme ortamı; mesaj silinmez, group ileri sarılır):

   ```bash
   docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
     --bootstrap-server localhost:9092 \
     --group payment-service \
     --reset-offsets --to-latest --execute --topic payment-commands
   ```

   Sonra payment-service’i bir kez daha restart et.
6. Postman: **Saga adımını tekrarla (takılı AWAITING_*)** → Send.  
   Bu, `POST /api/orders/84071403-aecc-473c-9d5f-92ae352536d0/saga/replay` — REST ile payment açmaz; `ChargePaymentCommand`’ı yeniden yazar.
7. 2 sn bekle → **Get order**.  
   - `AWAITING_RESTAURANT` / `AWAITING_DELIVERY` / `CONFIRMED` → düzeldi; A3.  
   - hâlâ `AWAITING_PAYMENT` → **List payments**. Bu order id ile `CAPTURED` satır var mı? Yoksa payment hâlâ komutu yemiyor (adım 1–4 log).
8. Elle denemek istersen (öğrenme; saga bunu zaten yapmalı): **yapma** `POST /api/payments` bu sipariş için — orchestrator event bekler, REST satırı saga’yı ilerletmez. İlerletmek replay’dir.

### A3. Kafka UI — aynı ORDER_ID’yi altı topic’te izle

Aşağıdaki sırayı **akış sırası** olarak oku. Her topic’te Seek → Earliest, key = senin ORDER_ID.

| Sıra | Topic | Kim yazdı | Kim okudu | Body’de ne arayacaksın |
| --- | --- | --- | --- | --- |
| 1 | `payment-commands` | order-service | payment-service | `ChargePaymentCommand`, `amount`, `customerId` |
| 2 | `payment-events` | payment-service | order-service | `PaymentChargedEvent`, `paymentId` |
| 3 | `restaurant-commands` | order-service | restaurant-service | `ReserveRestaurantCommand`, `forceAccept: true` |
| 4 | `restaurant-events` | restaurant-service | order-service | `RestaurantAcceptedEvent` |
| 5 | `delivery-commands` | order-service | delivery-service | `AssignCourierCommand` |
| 6 | `delivery-events` | delivery-service | order-service | `CourierAssignedEvent`, `courierName` |

`payment-commands` içinde bu senaryoda **Refund yok**. İade yalnızca kırmızı yolda.

**Sadece payment tarafında mesaj görmek normal değil** (Senaryo A tamamlandıysa). Tek `payment-*` topic’i doluysa saga orada durmuştur; restoran/kurye komutu hiç yazılmamıştır. GET order status’una bak:

| GET `/api/orders/{id}` | Kafka’da dolu olması gerekenler | Boşsa kim takıldı |
| --- | --- | --- |
| `AWAITING_PAYMENT` | çoğu zaman yalnız `payment-commands` | payment-service komutu okumadı (log: `PAYMENT CAPTURED` yok) |
| `AWAITING_PAYMENT` ama `payment-events` dolu | `payment-commands` + `payment-events` | **order-service** event’i okumadı; `restaurant-commands` oluşmaz |
| `AWAITING_RESTAURANT` | yukarıdakiler + `restaurant-commands` | restaurant-service |
| `AWAITING_DELIVERY` | + `restaurant-events` + `delivery-commands` | delivery-service |
| `CONFIRMED` | **altı topic** de dolu | Kafka UI yanlış bakıyor (aşağıdaki tuzaklar) |

Kafka UI tuzakları (önce bunları ele):

1. Topic adı `payment-topic` değil: `payment-commands` ve `payment-events` ayrı.
2. Messages’ta **Newest / Latest** eski kaydı gizler → **Oldest / Earliest**.
3. Üstteki arama kutusunu **temizle** (kısmi UUID diğer topic’lerde “No messages found” üretir).
4. Sol **Topics** listesini yenile. `restaurant-commands` hiç oluşmadıysa listede yoktur; Kafka topic’i ilk mesajda yaratır.
5. **Consumers** → `order-service` → `payment-events` **lag > 0** ise yönetmen event’i yutmuyor: order-service’i restart et.

Takılı sipariş: `POST /api/orders/{id}/saga/replay` (Postman: **Saga adımını tekrarla**). Sonra GET order tekrar.

### A4. Consumers

**Consumers** → `order-service`: `payment-events`, `restaurant-events`, `delivery-events` dinler (yönetmen event bekler).  
`payment-service` yalnız `payment-commands`.  
`restaurant-service` yalnız `restaurant-commands`.  
`delivery-service` yalnız `delivery-commands`.

Bu tablo “kim kime REST atıyor?” değil; “kim hangi kuyruğu tüketiyor?”.

### A5. IntelliJ log (aynı an)

```
order-service        SAGA start ... -> payment-commands
payment-service      PAYMENT CAPTURED ...
order-service        SAGA payment charged ... -> restaurant-commands
restaurant-service   RESTAURANT ACCEPT ...
order-service        SAGA restaurant accepted ... -> delivery-commands
delivery-service     DELIVERY ASSIGNED ...
order-service        SAGA confirmed ...
```

Log zaman çizelgesi, Kafka UI’daki topic sırası ile aynıdır. İkisini yan yana tut.

---

## Senaryo B — Restoran red + compensate (geri alma)

Amaç: mutlu yolun **tersine** nasıl sarıldığını görmek. Ödeme çekilmişti; restoran reddedince iade komutu gider.

### B1. Siparişi at

Postman: **Sipariş Ver (restoran red / compensate)** → Send.  
Yeni bir `orderId` gelir; A’dakini ezmiş olur. Bu yeni id’yi kopyala.

### B2. HTTP

**Get order** → `CANCELLED`, `cancelReason` = `FORCE_REJECT`.  
**List payments** → bu order için `REFUNDED` (önce CAPTURED olmuştu, sonra iade).  
**List deliveries** → bu order için kurye **yok**. Red, kurye adımına gelmeden kesildi.

### B3. Kafka UI — kırılma noktası

Aynı Seek / key yöntemi. A’dan farklar kalın:

| Sıra | Topic | Beklenen |
| --- | --- | --- |
| 1 | `payment-commands` | yine `ChargePaymentCommand` |
| 2 | `payment-events` | yine `PaymentChargedEvent` |
| 3 | `restaurant-commands` | `forceReject: true` |
| 4 | `restaurant-events` | **`RestaurantRejectedEvent`**, `reason: FORCE_REJECT` |
| 5 | `payment-commands` | **ikinci mesaj:** `RefundPaymentCommand` (aynı key, farklı type) |
| 6 | `payment-events` | **`PaymentRefundedEvent`** |
| — | `delivery-commands` / `delivery-events` | bu ORDER_ID **yok** |

`payment-commands` aynı topic, iki farklı komut. Header `__TypeId__` veya JSON’daki record alanları ile ayırırsın.

### B4. Log

```
restaurant-service   RESTAURANT REJECT ... reason=FORCE_REJECT (compensate tetiklenecek)
order-service        SAGA COMPENSATE ...
order-service        SAGA cancelled ... -> RefundPaymentCommand
payment-service      PAYMENT REFUNDED ...
```

Compensate = “ileri adımı geri al”. Burada geri alınan adım ödeme. Restoran rezervasyonu ayrı bir tablo değil; red event’i yeterli.

---

## Senaryo C — CRUD, Kafka yok (karşılaştırma)

Amaç: saga ile eski CRUD’un farkını hissetmek.

Postman klasör **3. order-service → Create order**. Body’ye `"status": "PENDING"` ekliyse (veya zaten varsa) orchestrator **başlamaz**.

Kafka UI’da **yeni mesaj gelmez**. `GET /api/payments` bu order için satır açmaz. Ödemeyi sen klasör 4’ten elle atarsın — tek transaction yok, sen yönetmensin.

Büyük ders: `status` yok = “Sipariş Ver” (dağıtık akış). `status` var = “sadece satır kaydet”.

---

## Senaryo D — Bayraksız, %20 rastgele red (isteğe bağlı)

Yeni bir POST `/api/orders` at; `forceRestaurantAccept` / `forceRestaurantReject` **yok**.

Beş kez dene. Çoğu `CONFIRMED`, bazısı `RANDOM_REJECT` + `CANCELLED`. Kafka UI’da `restaurant-events` içinde `reason` bazen `RANDOM_REJECT` olur. B ve A’nın karışımı; hangisi çıktıysa o senaryonun topic tablosunu kullan.

---

## Senaryo E — Outbox’ı görmek (M2.5)

Amaç: komutun Kafka’dan **önce** Postgres’te durduğunu hissetmek.

1. Klasör **8 → Sipariş Ver (mutlu yol)** → Send. `orderId` kopyala.
2. **Hemen** klasör **9 → List outbox**. Aynı `messageKey` = sipariş id, `topic` = `payment-commands`. `publishedAt` henüz `null` olabilir (poller 250 ms).
3. 1 sn bekle → **List outbox** tekrar (veya `unpublishedOnly=true`: boşalmalı). `publishedAt` dolu.
4. Kafka UI → `payment-commands` → aynı key. Outbox satırı “kasa defteri”, topic “yola çıkan kamyon”.
5. GET order `CONFIRMED` olunca aynı `orderId` için outbox’ta üç komut vardır: `payment-commands`, `restaurant-commands`, `delivery-commands`. Hepsi `publishedAt` dolu.

IntelliJ order-service log:

```
SAGA start ... outbox -> payment-commands
OUTBOX enqueue topic=payment-commands ...
OUTBOX published ... topic=payment-commands
```

`SAGA start` var, `OUTBOX published` yoksa poller/Kafka; `published` var, Kafka UI boşsa Seek **Earliest**.

---

## Senaryo F — History + processed_events (M3)

Amaç: “şu anki status” ile “neler oldu”nun ayrı tablolar olduğunu görmek; payment’in aynı commandId’yi deftere yazdığını görmek.

1. Klasör 8 → mutlu yol, Get order = `CONFIRMED`.
2. Klasör **10 → Get order history**. Sıra: `ORDER_CREATED`, `SAGA_STARTED`, `PAYMENT_CHARGED`, `RESTAURANT_ACCEPTED`, `COURIER_ASSIGNED`.
3. **List processed events**. `eventType=ChargePaymentCommand`, `orderId` senin sipariş. Bu, outbox’ın ikinci kopyasını yutacak defter.
4. Restoran red siparişinde history’de `RESTAURANT_REJECTED` + `PAYMENT_REFUNDED`; processed-events’te iki satır (charge + refund).

GET order = tek satır (yazma). GET history = film (okuma). Ayrıntı: [`cqrs-idempotency.md`](cqrs-idempotency.md).

---

## Tek bakışta “bu sipariş nerede?”

Postman’dan ORDER_ID’yi kopyala. Kafka UI Topics’te her saga topic’ine gir, key ile süz.

| Sipariş durumu (GET /api/orders) | Kafka’da en son gördüğün event |
| --- | --- |
| `AWAITING_PAYMENT` | henüz `payment-events` yok veya çok yeni |
| `AWAITING_RESTAURANT` | `PaymentChargedEvent` var, restaurant event yok |
| `AWAITING_DELIVERY` | `RestaurantAcceptedEvent` var |
| `CONFIRMED` | `CourierAssignedEvent` var |
| `CANCELLED` + `FORCE_REJECT` / `RANDOM_REJECT` | `RestaurantRejectedEvent` + `PaymentRefundedEvent` |
| `CANCELLED` + `PAYMENT_FAILED` | `PaymentFailedEvent`, iade yok |

HTTP status, orchestrator’ın Kafka event’lerinden türetilmiş özetidir. Asıl film Kafka UI’dadır.

---

## Sık takılmalar

| Ne gördün | Ne yap |
| --- | --- |
| Topics boş / saga topic’leri yok | Henüz senaryo A atılmadı, veya UI cluster `nefis-yemek` değil |
| Messages boş | Seek = **Earliest**. Latest yalnızca “bundan sonra” gösterir |
| Topic var, senin ORDER_ID yok | Yanlış siparişi (A vs B) veya eski id’yi arıyorsun |
| Consumers lag > 0 | O Java servisi IntelliJ’de kapalı |
| `8090` açılmıyor | `docker compose ps` → `nefis-kafka-ui` |
| `8080` down | Gateway çalışmıyor; Postman’ın hepsi 8080 |
| `AWAITING_PAYMENT` takılı, `payment-events` boş | Payment REST’i atlamadın. Orchestrator `payment-commands`’a yazdı; event henüz yok. Kafka UI’da **Topics → payment-commands** (Events değil), Seek **Oldest**, arama kutusunu temizle, Key = tam UUID. `Newest` + kısmi UUID araması boş gösterebilir. payment-service deserializer hatası yaşadıysa `git pull`, servisleri restart, sonra `POST /api/orders/{id}/saga/replay`. |
| `PAYMENT ignored command type=...ConsumerRecord` | Listener `Object` parametresi payload değil tüm Kafka kaydı oluyor. `@Payload` + unwrap düzeltmesi. `git pull`, Maven Reload, 4 servisi restart, sonra **Saga adımını tekrarla**. |
| `GET /api/orders/outbox` 404 veya tablo yok | order-service Flyway **V3** (`outbox_messages`). `git pull`, order-service restart. Path literal `/outbox`, UUID değil. |
| `GET /api/orders/{id}/history` 404 tablo | Flyway **V4** `order_history_view`. order-service restart. |
| `GET /api/payments/processed-events` 404 tablo | payment-service Flyway **V2**. payment-service restart. |

---

## Bu lab’de bilinçli olarak yok

- Notification Kafka dinlemiyor; SMS REST ile ayrı.
- payment / restaurant / delivery outbox’ı yok; yalnızca order-service komutları tablodan geçer.
- restaurant / delivery `processed_events` yok; yalnızca payment.
- JWT yok; UI’da auth yok.
- Prometheus/Grafana yok; Kafka UI metrik dashboard’u değil.
