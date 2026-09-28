# Nefis Yemek — mülakat soruları (sade dil)

Bu dosya ezber kitabı değil.  
Mülakatçı sorar → sen kendi projenle anlatırsın.

**Nasıl çalış:**
1. Soruyu oku.
2. Cevabı elinle kapat, kendi cümlelerinle söyle.
3. Sonra aşağıdaki cevaba bak. Eksik bıraktığını not al.

Acele etme. Günde 5–6 soru yeter.

Daha uzun lab’ler: [`hikaye.md`](hikaye.md) · [`kafka-outbox.md`](kafka-outbox.md) · [`cqrs-idempotency.md`](cqrs-idempotency.md)

---

## Önce şunu ezberle (projeni tanıt)

Bunu tek nefeste sıkıştırma. Üç parça halinde söyle:

**Parça 1 — ne yaptım?**  
“Nefis Yemek diye kurgusal bir yemek sipariş uygulaması yazdım. Amaç canlıya almak değil. Mikroservis fikirlerini gerçek Java ile denemek.”

**Parça 2 — nasıl duruyor?**  
“Altı servis var. Her birinin kendi Postgres’i var. Dışarıdan tek kapı var: gateway, port 8080. Giriş için JWT bileti kapıda kontrol ediliyor.”

**Parça 3 — zor kısım neydi?**  
“Sipariş verince ödeme, restoran, kurye sırayla ilerliyor. Buna saga diyoruz. Mesajlar Kafka’dan gidiyor. Aynı iş iki kez olmasın diye outbox ve idempotency kullandım. Testleri de yazdım.”

Bu üç parça yeter. Detayı sorarlarsa açarsın.

---

## Bölüm A — Java / Spring temeli

### Soru 1
**Entity’yi neden doğrudan API’den dönmüyorsun?**

Kısa cevap:  
Entity veritabanına bağlı. API’ye ayrı bir cevap tipi koyarım.

Senin projede:  
`OrderResponse` gibi record’lar var. Controller entity göndermez. İçeride tablo değişse bile dışarıdaki JSON aynı kalabilir.

---

### Soru 2
**Controller, service, repository ne işe yarar?**

- Controller = kapıdan gelen HTTP isteği  
- Service = iş kuralı  
- Repository = veritabanı  

Senin projede her serviste bu düzen aynı.  
Örnek: `OrderController` → `OrderService` → `OrderRepository`.

---

### Soru 3
**`@Transactional` nereye konur?**

Service tarafına.  
Controller’a koyma.

Neden?  
Bir iş ya hep olsun ya hiç olmasın istiyorsan transaction service’te başlar.

Senin örnek:  
Saga başlarken sipariş + outbox satırı aynı transaction’da yazılıyor. Biri yazılıp diğeri yazılmazsa sistem bozulur.

---

### Soru 4
**Flyway ne işe yarar? Hibernate validate ne demek?**

Flyway tabloları dosyadan kurar (`V1__...sql`).  
Hibernate `validate` der: “Ben tablo yaratmam. Uyumsuzluk varsa ayağa kalkmam.”

Yani şema sürpriz yapmaz. Sen yönetirsin.

---

### Soru 5
**Hata olunca ne yapıyorsun?**

Serviste `ResourceNotFoundException` fırlatıyorum.  
`RestExceptionHandler` bunu 404’e çeviriyor.

Her controller’da try/catch yazmıyorum. Hata formatı tek yerden çıkıyor.

---

**Bu bölümü bitince söyle:**  
“Her serviste aynı katman düzeni var. Şemayı Flyway yönetiyor. Hataları merkezi handler’a veriyorum.”

---

## Bölüm B — Neden ayrı veritabanları?

### Soru 6
**Database per Service ne demek?**

Her servis yalnız kendi veritabanına bakar.  
Order, payment tablosuna JOIN atamaz.

İyi tarafı: servisler birbirinin şemasını kırmaz.  
Zor tarafı: “her şeyi tek sorguda birleştir” diyemezsin.

---

### Soru 7
**O zaman sipariş dört servisi ilgilendirince ne oluyor?**

Tek büyük transaction yok.  
2PC (iki fazlı commit) de istemedim. Kilitli ve ağır.

Bunun yerine **saga** var:  
Adım adım ilerle. Bir yer bozulursa geri al.

Restoran reddederse ödeme iade edilir. Sipariş `CANCELLED` olur.

---

### Soru 8
**Servisler birbirine REST mi diyor, Kafka mı?**

Kullanıcı → gateway → servis: REST.  
Uzun sipariş filmi: Kafka.

Ödeme için order, payment’i senkron beklemiyor.  
“Parayı çek” komutunu kuyruğa koyuyor. Cevap event olarak geliyor.

---

### Soru 9
**`nefis-contracts` ne paylaşıyor?**

Sadece mesajın şeklini.  
“Şu JSON böyle görünür” dosyası.

Tablo / entity paylaşmıyoruz.  
Biri kolon değiştirince herkes kırılmasın diye.

---

**Bu bölümü bitince söyle:**  
“Her servisin kendi kasası var. Ortak transaction yok. Bu yüzden saga ile adım adım gidiyoruz.”

---

## Bölüm C — Kapı (Gateway) ve bilet (JWT)

### Soru 10
**Gateway ne yapar?**

Tek kapı.  
İstek `/api/orders` ise order-service’e yollar.

Ne yapmaz?  
Sipariş kuralı yazmaz. Veritabanına dokunmaz.

Bizde kütüphane: Spring Cloud Gateway.

---

### Soru 11
**JWT’yi neden her serviste değil de kapıda kontrol ettin?**

Kural tek yerde dursun diye.

Açık kalanlar:
- müşteri oluştur
- login
- health

Gerisi: `Authorization: Bearer ...` lazım.

Login’i `customer-service` yapıyor, bileti o basıyor.  
Kapı bileti okuyor.

---

### Soru 12
**Kullanıcı nasıl giriyor? (kısa anlat)**

1. Müşteri oluştur  
2. Login ol → token al  
3. Sonraki isteklerde header’a token koy  

Hepsi `localhost:8080` üzerinden.

---

**Bu bölümü bitince söyle:**  
“Dışarı tek kapı görüyor. Bilet kapıda bakılıyor. İçerideki servisler işine bakıyor.”

---

## Bölüm D — Saga, Kafka, outbox (en önemli kısım)

Bunu parçala. Bir oturuşta hepsini yutma.

### Soru 13
**Komut ile event farkı ne?**

Komut = “yap”  
Örnek: `ChargePaymentCommand` → parayı çek.

Event = “oldu”  
Örnek: `PaymentChargedEvent` → para çekildi.

Yönetmen komut yollar.  
Event gelince bir sonraki adıma geçer.

---

### Soru 14
**Yönetmen kim? Orchestration ne demek?**

Yönetmen: `OrderSagaOrchestrator` (order-service içinde).

Orchestration = sıra tek yerde belli.  
Payment, restaurant, delivery birbirini aramaz. Yönetmeni dinler.

(Diğer stil choreography: herkes birbirini tetikler. Biz onu seçmedik.)

---

### Soru 15
**Mutlu yol nasıl ilerliyor?**

Kabaca:

1. Sipariş oluştu  
2. `AWAITING_PAYMENT` — ödeme bekleniyor  
3. Ödeme oldu → restoran bekleniyor  
4. Restoran kabul → kurye bekleniyor  
5. Kurye atandı → `CONFIRMED`

Postman’da “Sipariş Ver” bunu başlatıyor.

---

### Soru 16
**Restoran reddederse ne oluyor?**

Compensate = geri alma.

Restoran red event’i gelir.  
Yönetmen “parayı iade et” komutu yazar.  
Sipariş `CANCELLED` olur.

Denemek için: `forceRestaurantReject: true`.

---

### Soru 17
**Dual-write problemi ne? (önemli)**

Kötü yol:
1. Siparişi Postgres’e yaz  
2. Sonra Kafka’ya gönder  

Arada process ölürse ne olur?  
Sipariş “ödeme bekliyor” kalır. Ama mesaj hiç gitmemiş olabilir. Film takılır.

---

### Soru 18
**Outbox bunu nasıl çözer?**

Aynı deftere, aynı anda iki satır yaz:
- sipariş
- outbox mesajı

İkisi aynı transaction. Ya ikisi de kalır, ya hiçbiri.

Sonra bir poller (`OutboxPublisher`) outbox’taki satırı Kafka’ya taşır.

Yani önce defter, sonra kuyruk.

---

### Soru 19
**Peki outbox yetmiyor mu? Neden `processed_events`?**

Kafka mesajı bazen birden fazla gelebilir.  
“En az bir kez” teslimat var.

Payment aynı `commandId`’yi ikinci kez görürse yutar.  
Tablo: `processed_events`.

Yoksa para iki kez çekilebilir.

Kısaca:
- Outbox → “mesaj kaybolmasın”  
- Idempotency → “mesaj iki gelirse iş iki olmasın”

---

### Soru 20
**Sipariş `AWAITING_PAYMENT`’te takılırsa ne yaparsın?**

`replayCurrentStep` var.  
Status aynı kalır. O adımın komutu outbox’a yeniden yazılır.

Ayrıca Kafka UI’dan topic’e ve consumer’a bakarsın: mesaj gitmiş mi, biri okumuş mu?

---

### Soru 21
**History / CQRS burada ne?**

`orders` tablosu = siparişin şu anki hali (`CONFIRMED` gibi).  
`order_history_view` = film şeridi (hangi adım ne zaman).

Güncel durum bir şey. Hikâye başka şey.  
İkisini ayırdık.

---

**Bu bölümü bitince söyle (ezber cümle):**  
“Siparişi yönetmen yönetiyor. Komutu önce outbox’a yazıyoruz, sonra Kafka’ya. Payment aynı komutu iki kez işlemiyor. Restoran reddederse parayı iade edip iptal ediyoruz.”

---

## Bölüm E — Sigorta ve gözlem

### Soru 22
**Circuit breaker ne işe yarar?**

Sigorta.

Bir servis art arda bozulunca kapı bir süre onu ezmez.  
İstekleri fallback’e düşürür.

Bizde bu gateway route’larında var.

---

### Soru 23
**Grafana, Zipkin, log farkı ne?**

Çok basit:

- **Log** → “bu istekte ne yazıldı?”  
- **Grafana** → “servis genel olarak nasıl? hata arttı mı?”  
- **Zipkin** → “şu bir sipariş hangi servislerden geçti?”

Sayı ayrı. Tek isteğin filmi ayrı.

---

**Bu bölümü bitince söyle:**  
“Kapıda sigorta var. Sayıyı Grafana’da, bir isteğin yolunu Zipkin’de bakıyorum.”

---

## Bölüm F — Test

### Soru 24
**Neden sadece Postman yetmez?**

Postman öğrenmek ve elle denemek için güzel.  
Ama her commit’te insan tıklamaz.

Otomatik test lazım: `./mvnw test`.

---

### Soru 25
**Testleri nasıl ayırdın?**

Üç kat:

1. **Unit** — Mockito ile hızlı, küçük  
2. **WireMock** — “karşı taraf böyle cevap verse”  
3. **Testcontainers** — gerçek Postgres ayağa kalkar  

Örnek: ödeme iki kez gelmesin diye IT yazdık.

---

**Bu bölümü bitince söyle:**  
“Öğrenirken Postman kullandım. Güvence için unit + WireMock + Testcontainers yazdım.”

---

## Bölüm G — Tahtada çiz / dürüst sorular

### Soru 26
**Sistemi tahtaya çiz deseler?**

Şunu çizmen yeter:

```
Kullanıcı → Gateway (bilet + sigorta)
              ├ customer
              ├ restaurant
              ├ order  → outbox → Kafka
              ├ payment (processed_events)
              ├ delivery
              └ notification
```

Sonra ağızdan ekle:  
“Restoran red olursa iade var. Mesaj iki gelirse payment yutar.”

---

### Soru 27
**Ne eksik bıraktın?** (bu soru artıdır)

Dürüst söyle:

- Restaurant ve delivery’de henüz `processed_events` yok  
- Her serviste outbox yok  
- Notification henüz saga’ya bağlı değil  
- Kubernetes yok; Docker Compose ile lab  
- Strangler Fig (M6) opsiyonel kaldı  

“Hepsini production sanmayın; bilinçli sadeleştirdim” demek seni güçlü gösterir.

---

### Soru 28
**“Ödeme iki kez çekildi” bug’ını nasıl ararsın?**

Sırayla:

1. `processed_events` içinde aynı `commandId` var mı?  
2. Kafka UI’da aynı komut iki kez görünüyor mu?  
3. Outbox aynı mesajı iki kez mi basmış?  
4. Replay yeni id mi üretmiş?

Tek cümleyle bitir:  
“Önce iş etkisinin iki olup olmadığına, sonra mesajın iki gelip gelmediğine bakarım.”

---

## Günde nasıl çalış?

| Gün | Ne bak |
| --- | --- |
| 1 | Bölüm A + B (Soru 1–9) |
| 2 | Bölüm C (kapı / JWT) |
| 3 | Bölüm D soru 13–16 (saga yolu) |
| 4 | Bölüm D soru 17–21 (outbox + idempotency) |
| 5 | Bölüm E + F |
| 6 | Bölüm G + sesli 5 dakika tanıtım |

Her günün sonunda sadece **bir** ezber cümlesini sesli tekrarla.

---

## 5 dakikalık sesli prova

Kronometre aç. Şunları sırayla söyle:

1. Üç parçalı proje tanıtımı  
2. Sipariş mutlu yol (status’ler)  
3. Outbox neden var  
4. `processed_events` neden var  
5. Restoran red olursa ne olur  
6. Ne eksik bıraktım  

Takılırsan normal. O soruya dön, bir tur daha söyle. Ezber yarışı değil.

---

Takıldığın bir soruyu seçip “bunu birlikte sesli cevaplayalım” dersen, o soruyu daha da kısa hale getiririz.
