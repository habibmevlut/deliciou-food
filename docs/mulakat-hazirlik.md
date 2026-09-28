# Nefis Yemek — Java Developer Mülakat Hazırlık

Bu dosya, projede öğrendiklerinden yola çıkarak **mülakat tadında soru + örnek cevap** setidir.
Amacın ezber değil: her cevabı kendi koduna bağlamak.

Nasıl çalış:

1. Soruyu oku, cevabı kapat, 1–2 dk sesli anlat.
2. Sonra örnek cevabı oku; eksik bıraktığın “neden?” cümlesini not al.
3. Her bölüm sonunda **1 dakikalık STAR** pratik cümlesini ezberle.

İlgili lab’ler: [`hikaye.md`](hikaye.md) · [`kafka-orchestration.md`](kafka-orchestration.md) · [`kafka-outbox.md`](kafka-outbox.md) · [`cqrs-idempotency.md`](cqrs-idempotency.md) · [`m4-kapı-sigorta-sayaç.md`](m4-kapı-sigorta-sayaç.md) · [`test-piramidi.md`](test-piramidi.md)

---

## 0) 60 saniyelik proje tanıtımı (ezberle)

> “Nefis Yemek adında kurgusal bir yemek sipariş platformu yazdım. Amaç production değil; mikroservis pattern’lerini gerçek Java ile denemek.
> Altı iş servisi var: customer, restaurant, order, payment, delivery, notification. Her birinin kendi PostgreSQL’i var — Database per Service.
> Dış dünya yalnızca Spring Cloud Gateway `:8080`. Auth JWT ile kapıda.
> ‘Sipariş Ver’ akışı Kafka üzerinde orchestration-based saga: ödeme → restoran → kurye. Restoran reddederse compensate ile iade ve `CANCELLED`.
> Dual-write’ı transactional outbox ile, tekrar mesajı payment’te `processed_events` ile çözdüm. Order history bir CQRS okuma modeli.
> Resilience4j circuit breaker, Prometheus/Grafana/Zipkin, test piramidinde Mockito + WireMock + Testcontainers kullandım.”

---

## 1) Java temeli

### S1. Entity’yi API’den neden doğrudan dönmüyorsun?

**Örnek cevap:**
Entity JPA’ya bağlıdır: lazy ilişkiler, persistence context, veritabanı kolon isimleri sızabilir. API sözleşmesi için DTO/`record` kullanırım.
Nefis Yemek’te `CreateOrderRequest` / `OrderResponse` record’ları var; controller entity değil response döner. Böylece iç model değişince dış JSON kırılmaz.

### S2. `Optional` ne zaman, ne zaman değil?

**Örnek cevap:**
Repository `findById` gibi “belki yok” dönüşlerinde iyi. Alan tipi olarak entity içinde veya collection elemanı olarak kullanmam.
Service’te `orElseThrow(() -> new ResourceNotFoundException(...))` ile 404’e map ederim; controller’da `Optional` gezinmem.

### S3. Checked vs unchecked exception — sen ne yaptın?

**Örnek cevap:**
Spring/REST tarafında çoğunlukla unchecked (`RuntimeException`) kullanırım. `ResourceNotFoundException`, `UnauthorizedException` gibi domain hataları runtime.
`@RestExceptionHandler` bunları HTTP status’e çevirir. Checked exception REST katmanında gürültü yaratır; nadiren library zorlarsa sararım.

### S4. Immutability neden önemli?

**Örnek cevap:**
Request/response ve Kafka mesajlarında immutability race ve yan etkiyi azaltır.
Projede request DTO’ları `record`. Kafka contract’ları (`ChargePaymentCommand` vb.) da immutable mesaj şekli; “yap” komutunun içeriği yolda değişmesin diye.

### S5. `equals` / `hashCode` entity’de nasıl?

**Örnek cevap:**
JPA entity’de business field’larla equals tehlikeli olabilir (henüz id yokken Set’e koyma). Genelde id bazlı veya equals override etmeden dikkatli kullanırım.
Mülakatta dürüst cevap: “Entity’yi Set key yapmaktan kaçınırım; identity için UUID id kullanırım.”

**1 dk STAR:** “Katmanda entity ile API’yi ayırdım; record DTO + merkezi exception handler ile sözleşmeyi temiz tuttum.”

---

## 2) Spring Boot & katmanlar (M0)

### S6. Controller / Service / Repository ayrımı neden?

**Örnek cevap:**
- Controller: HTTP, validation, DTO mapping
- Service: iş kuralı + transaction sınırı
- Repository: veri erişimi

Nefis Yemek’te her serviste aynı iskelet var. Örn. `OrderController` → `OrderService` → `OrderRepository`. Saga da service/event katmanında; controller “Sipariş Ver” deyip işi service’e bırakır.

### S7. `@Transactional` nereye konur?

**Örnek cevap:**
Genelde **service** metoduna. Controller’a koymazsın; transaction HTTP’ye bağlanmasın.
`OrderSagaOrchestrator.start` `@Transactional`: order status + history + outbox satırı **aynı** Postgres transaction’ında yazılır. Read-only sorgulara `readOnly = true` veririm.

### S8. Constructor injection neden field injection’dan iyi?

**Örnek cevap:**
Zorunlu bağımlılıklar final olur, testte mock geçmek kolay, döngüsel bağımlılık erken görünür.
Projede orchestrator ve service’ler constructor ile alınıyor; field `@Autowired` ile gizli bağımlılık yok.

### S9. Flyway + Hibernate `validate` ne demek?

**Örnek cevap:**
Şemayı Flyway migration yönetir (`V1__create_orders_table.sql`). Hibernate `ddl-auto: validate` ile entity ile tablo uyumsuzsa ayağa kalkmaz.
Böylece “Hibernate sessizce kolon ekledi” sürprizi olmaz. Database-per-service’te her DB kendi migration’ını çalıştırır.

### S10. Global exception handler ne işe yarar?

**Örnek cevap:**
`RestExceptionHandler` ile `ResourceNotFoundException` → 404, validation → 400 gibi tek format.
Her controller’da try/catch kopyalamam. İstemciye tutarlı JSON hata gövdesi gider.

**1 dk STAR:** “M0’da üç servis, üç Postgres, Flyway + validate; klasik Spring katmanı ile CRUD iskeletini oturttum.”

---

## 3) Database per Service & dağıtık veri

### S11. Database per Service nedir? Neden?

**Örnek cevap:**
Her servis yalnızca kendi DB’sine yazar/okur. Order, payment’in `payments` tablosuna JOIN atamaz.
Neden: bağımsız deploy, şema sahipliği, bir servisin schema change’inin diğerini kırmaması.
Bedeli: çapraz sorgu yok → rapor/history için CQRS veya event’lerden türetilmiş okuma modeli gerekir.

### S12. Neden 2PC (iki fazlı commit) kullanmadın?

**Örnek cevap:**
“Sipariş Ver” order + payment + restaurant + delivery’yi ilgilendirir; dört ayrı Postgres. Tek `BEGIN/COMMIT` yok.
2PC kilit tutar, coordinator SPOF olur, bulutta pratikte kabus. Bunun yerine **saga**: adım adım ilerler, bozulursa **compensate** (örn. `RefundPaymentCommand`).

### S13. Senkron REST mi, asenkron Kafka mi?

**Örnek cevap:**
Gateway → servis senkron REST (istemci bekler). Servisler arası uzun iş akışı Kafka komut/event.
Saga’da payment’i senkron çağırmadım; `ChargePaymentCommand` outbox → Kafka. Böylece restoran yavaşsa order thread’i kilitlenmez; durum makinesi event ile ilerler.

### S14. Shared library’de entity paylaşmak neden antifpattern?

**Örnek cevap:**
`nefis-contracts` yalnızca **mesaj şeması** (command/event + topic adları). Domain entity paylaşmıyoruz.
Entity paylaşmak gizli coupling’tir; bir servisin kolon değişimi herkesi kırar. Contract JAR = “JSON’un şekli”, tablo değil.

**1 dk STAR:** “Dört kasa, tek transaction yok; bu yüzden saga + compensate seçtim, entity değil mesaj contract paylaştım.”

---

## 4) API Gateway & JWT (M1 + M4)

### S15. Gateway ne yapar, ne yapmaz?

**Örnek cevap:**
Yapar: tek giriş (`:8080`), path’e göre route, JWT doğrulama, circuit breaker, tracing header.
Yapmaz: sipariş iş kuralı, Postgres’e yazma, JSON’u business olarak yorumlama.
Bizde `spring-cloud-starter-gateway` (WebFlux/Netty). İçeride Tomcat yok — WebMVC gateway ile karıştırılmaz.

### S16. JWT’yi neden her serviste değil kapıda doğruladın?

**Örnek cevap:**
Policy tek yerde kalsın diye. `POST /api/customers` ve `POST /api/auth/login` açık; gerisi `Authorization: Bearer`.
`customer-service` içindeki `JwtIssuer` token üretir; gateway validate eder. Backend’ler JWT parse etmez (lab basitleştirmesi).
Prod’da defense-in-depth için servis içi auth da tartışılır; ben trade-off’u bilerek seçtim.

### S17. Login akışını anlat

**Örnek cevap:**
1. `POST /api/customers` → müşteri oluşur  
2. `POST /api/auth/login` `{ "email": "..." }` → `accessToken`  
3. Sonraki istekler `Authorization: Bearer <token>`  
4. Gateway Security config public path dışındakileri reddeder  

Health biletsiz: `GET :8080/actuator/health`.

### S18. Route tablosunda prefix strip yok — neden?

**Örnek cevap:**
Backend zaten `/api/orders/**` dinliyor. Gateway path’i olduğu gibi iletir; rewrite yok, hata ayıklaması kolay.
URI IDE’de `localhost:8081`, Docker’da `http://order-service:8081` — config ile değişir, istemci bilmez.

**1 dk STAR:** “Altı URL ezberletmemek ve JWT’yi tek kapıda tutmak için Spring Cloud Gateway koydum; iş kuralları içeride kaldı.”

---

## 5) Kafka, Saga, Outbox, Idempotency (kritik tur)

### S19. Komut ile event farkı?

**Örnek cevap:**
- **Komut** = “yap” → `ChargePaymentCommand`, `ReserveRestaurantCommand`
- **Event** = “oldu” → `PaymentChargedEvent`, `RestaurantRejectedEvent`

Orchestrator komut üretir, event dinler, bir sonraki adımı yazar.
Topic’ler: `payment-commands` / `payment-events`, aynı pattern restaurant ve delivery’de.

### S20. Orchestration vs choreography?

**Örnek cevap:**
**Orchestration:** tek yönetmen sırayı bilir — bizde `OrderSagaOrchestrator`.
**Choreography:** her servis bir event görüp sonrakini tetikler; dağıtık ama akış okuması zor.
Lab’te orchestration seçtim: compensate ve status geçişleri tek sınıfta görünür.

### S21. Mutlu yol status makinesi?

**Örnek cevap:**
`ORDER_CREATED` / saga start → `AWAITING_PAYMENT` → (charged) `AWAITING_RESTAURANT` → (accepted) `AWAITING_DELIVERY` → (courier) `CONFIRMED`.

Restoran red: `SAGA COMPENSATE` → `RefundPaymentCommand` → `CANCELLED` + `cancelReason`.

Postman’da `forceRestaurantReject: true` veya `forceRestaurantAccept: true` ile deterministik test.

### S22. Dual-write problemi nedir?

**Örnek cevap:**
“Önce DB’ye yaz, sonra `kafka.send`” dersen process arada ölürse: sipariş `AWAITING_PAYMENT` kalır, komut hiç gitmez — saga takılır.
Ya da Kafka gitti DB rollback oldu — hayalet mesaj.

### S23. Transactional Outbox nasıl çözer?

**Örnek cevap:**
Aynı transaction’da `orders` + `outbox_messages` yazılır (`OutboxEnqueuer`). COMMIT sonrası `OutboxPublisher` poll edip Kafka’ya taşır, `published_at` işaretler.
Process send’den önce ölürse satır tabloda kalır; poller tekrar dener.
Bizde outbox **order-service** tarafında (orchestrator komutları). Payment/restaurant/delivery hâlâ doğrudan send — bilerek scope.

### S24. Outbox yetmez mi? Neden idempotent consumer?

**Örnek cevap:**
Kafka **at-least-once**: ACK olup `published_at` yazılmadan ölüm → aynı komut tekrar gider.
Payment `processed_events` tablosunda `commandId` unique. İkinci kez gelirse yutulur, çift çekim olmaz.
`IdempotentConsumer` + `PaymentCommandHandler` bunu uygular.

### S25. Saga takılırsa (`AWAITING_PAYMENT`) ne yaparsın?

**Örnek cevap:**
`replayCurrentStep`: status aynı kalır, o adımın komutu outbox’a **yeni commandId** ile yeniden yazılır.
Eski kopya aynı `commandId` ile gelirse payment processed_events yutar; yeni id ise bilinçli yeniden deneme.
Operasyonel olarak Kafka UI’dan topic/consumer lag’e de bakarım.

### S26. Topic key neden `orderId`?

**Örnek cevap:**
Aynı siparişin komut/event’leri aynı partition’a düşsün diye sıralı işlensin.
Farklı order’lar parallel gidebilir. Key = partition affinity.

### S27. CQRS burada ne?

**Örnek cevap:**
Write model: `orders` güncel durum (`CONFIRMED` vb.).
Read model: `order_history_view` — film şeridi (hangi adım ne zaman).
`GET /api/orders/{id}/history` rapor için; her seferinde dört servisi JOIN’lemezsin (zaten yapamazsın).

**1 dk STAR:** “Saga yönetmeni order’da; komutları outbox’a yazdım, payment aynı commandId’yi iki kez yemedi; history ile akışı sonradan okudum.”

---

## 6) Resilience & observability (M4)

### S28. Circuit breaker ne zaman açılır?

**Örnek cevap:**
Downstream (örn. restaurant-service) art arda hata/timeout verince Resilience4j devre açar; istekler bir süre direkt fail/fallback’e gider, hasta servisi ezmez.
Gateway route’larında `CircuitBreaker` filter + `fallbackUri: forward:/fallback/...`.
`FallbackController` kullanıcıya kontrollü cevap döner.

### S29. Retry ile circuit breaker farkı?

**Örnek cevap:**
Retry: geçici hatayı tekrar dene (dikkat: ödemede idempotency şart).
Circuit breaker: “yeter, bir süre çağırma” — cascading failure’ı keser.
İkisini körlemesine birlikte kullanmak fırtına yaratır; policy bilinçli olmalı.

### S30. Metrics vs logs vs traces?

**Örnek cevap:**
| Araç | Soru |
| --- | --- |
| Log | Bu istekte ne oldu? (metin) |
| Prometheus/Grafana | Servis nabzı, hata oranı, latency (sayı) |
| Zipkin | Bu tek sipariş isteği hangi servislerden geçti? (film) |

Actuator + Micrometer metrik üretir; Zipkin dağıtık span birleştirir.

### S31. Observability’yi mülakatta nasıl kanıtlarsın?

**Örnek cevap:**
“Compose’da Prometheus `:9090`, Grafana `:3000`, Zipkin `:9411`. Sipariş attım, Zipkin’de gateway → order → … zincirini gördüm; Grafana’da hata olunca panel kırmızıya döndü.”
Lab dokümanı: `observability-lab.md`.

**1 dk STAR:** “Kapıda sigorta (circuit breaker) ve sayaç/iz (Prometheus + Zipkin) koydum; fail olunca kör retry yerine görünür fallback.”

---

## 7) Test piramidi (M5)

### S32. Neden sadece Postman yetmez?

**Örnek cevap:**
Postman smoke insan bağımlı ve regresyonu garanti etmez. CI’da `./mvnw test` şart.
Postman öğrenme UI’si; otomatik güvence test piramidi.

### S33. Üç katmanı anlat

**Örnek cevap:**
1. **Unit** (`*Test`): Mockito ile service/orchestrator — hızlı, DB yok  
2. **Contract / WireMock**: gateway’in beklediği HTTP şekli; gerçek servis ayakta olmadan  
3. **IT / Testcontainers**: gerçek Postgres (ve gerektiğinde Kafka) — örn. `PaymentIdempotencyIT`, `OrderOutboxIT`

### S34. Testcontainers neden embedded H2’den iyi?

**Örnek cevap:**
Prod Postgres; H2 SQL diyalekti ve tip farkı gizler. Flyway migration’ların gerçek motorunda geçmesi önemli.
Trade-off: IT daha yavaş — bu yüzden piramit: çoğu birim, az ama kritik IT.

### S35. Idempotency’yi nasıl test edersin?

**Örnek cevap:**
Aynı `commandId` ile komutu iki kez uygula; tek payment satırı / `processed_events` unique constraint.
`PaymentIdempotencyIT` bunu container Postgres ile doğrular.

**1 dk STAR:** “Öğrenirken Postman, güvence için Mockito + WireMock + Testcontainers; çift çekimi IT ile kilitledim.”

---

## 8) System design mini sorular

### S36. Bu sistemi sıfırdan tahtaya çiz

**Çizim iskeleti (söyleyerek çiz):**

```
Client → API Gateway (JWT, CB, trace)
           ├ customer  → PG
           ├ restaurant → PG
           ├ order → PG + outbox → Kafka ← payment/restaurant/delivery
           ├ payment → PG + processed_events
           ├ delivery → PG
           └ notification → PG (mock SMS)
```

Sonra ağızdan failure mode: restoran red, outbox poller gecikmesi, duplicate message.

### S37. Notification neden saga’ya bağlı değil?

**Örnek cevap:**
Lab scope: notification CRUD + konsola `[MOCK SMS]`. Kafka consumer / saga adımı yok.
İleride `OrderConfirmed` event’ini dinleyen consumer eklenebilir — bilerek bırakılmış boşluk.

### S38. Ne eksik bıraktın? (dürüstlük sorusu)

**Örnek cevap:**
- Restaurant/delivery’de henüz `processed_events` yok (ders payment’te)
- Her yerde outbox yok
- Servis-içi JWT yok
- Strangler Fig / `review-service` (M6) opsiyonel kaldı
- Spring Cloud Contract / Pact yok; WireMock ile simülasyon var
- K8s yok; Docker Compose lab

Eksik = yalan değil; **scope bilinci** artıdır.

### S39. Consistency model’in ne?

**Örnek cevap:**
Saga boyunca **eventual consistency**. Kullanıcı kısa süre `AWAITING_*` görür; sonunda `CONFIRMED` veya `CANCELLED`.
Strong consistency tek DB transaction’ı ile sınırlı (outbox + order aynı TX).

### S40. “Ödeme iki kez çekildi” bug’ını nasıl debug edersin?

**Örnek cevap:**
1. `processed_events`’te aynı `commandId` var mı?  
2. Kafka UI: `payment-commands`’ta duplicate mesaj / farklı `commandId` mi?  
3. Outbox’ta aynı payload birden fazla mı publish olmuş?  
4. Replay yeni `commandId` üretmiş olabilir — bu bilinçli yeniden deneme; idempotency key iş kuralına göre seçilmeli  

---

## 9) Hızlı “neden böyle?” kartları

Ezber kartı gibi oku:

| Konu | Tek cümle |
| --- | --- |
| DB per service | JOIN yok, sahiplik var |
| Gateway | Tek kapı; iş kuralı değil |
| JWT kapıda | Policy tek yerde |
| Saga | Dağıtık işi adım + compensate |
| Orchestration | Yönetmen tek sınıf |
| Outbox | Önce aynı TX’e yaz, sonra Kafka |
| Idempotency | At-least-once’ı yut |
| CQRS history | Güncel durum ≠ film şeridi |
| Circuit breaker | Hasta servisi ezme |
| Zipkin | Bir isteğin yolu |
| Testcontainers | Gerçek Postgres’te migration |

---

## 10) Mock mülakat — 8 dakika script

Kronometre tut, sesli söyle:

1. **(60 sn)** Proje tanıtımı (§0)  
2. **(90 sn)** Sipariş mutlu yol + status’ler  
3. **(90 sn)** Dual-write → outbox → neden processed_events  
4. **(60 sn)** Restoran red / compensate  
5. **(60 sn)** Gateway + JWT  
6. **(60 sn)** Test + observability + “ne eksik?”  

Bitince kendi kaydını dinle: “eee”, “şey”, boş kavram var mı? Her boşluğu bir dosya adına bağla (`OrderSagaOrchestrator`, `OutboxPublisher`, `IdempotentConsumer`).

---

## 11) Kendini sınama — cevapları kapat

Aşağıdakileri kağıda 3–5 cümle yaz, sonra bu dosyayla karşılaştır:

1. Database per Service’in bedeli nedir?  
2. Outbox ile idempotent consumer aynı problemi mi çözer?  
3. Gateway’e iş kuralı koyarsan ne bozulur?  
4. `AWAITING_PAYMENT`’te takılı siparişi nasıl kurtarırsın?  
5. Neden 2PC değil saga?  
6. Unit test ile Testcontainers’ı ne zaman seçersin?  
7. Komut/event ayrımı olmadan ne kaybedersin?  
8. Metrics’te görünen ama Zipkin’de görünmeyen problem nasıl olur?  

---

## 12) İleride sorulursa — kısa ek notlar

Bunları projede derin işlemedin; “biliyorum / bakıyorum” seviyesi yeterli:

- **Choreography saga** trade-off’ları  
- **Inbox pattern** (consumer tarafı outbox benzeri)  
- **Exactly-once** miti vs idempotent business effect  
- **Kafka consumer group / rebalance**  
- **Virtual threads (Java 21)** gateway WebFlux ile farkı  
- **Strangler Fig** ile monolith’ten parçalama (M6)  

Cevap kalıbı: “Lab’te X’i yaptım; Y’yi bilerek basitleştirdim; production’da Z’yi eklerdim.”

---

İyi çalışmalar. Takıldığın soruyu “şunu mock mülakat gibi sor” diye açman yeterli; cevabı birlikte sesli formatta sıkılaştırırız.
