# Cursor Master Prompt — "Nefis Yemek" Mikroservis Projesi

> **Nasıl kullanılır:** Bu dosyayı proje kök dizinine `PROJECT_BRIEF.md` olarak kaydet. Cursor'da yeni bir sohbet aç, bu dosyayı context'e ekle (`@PROJECT_BRIEF.md`) ve en alttaki "İlk Mesaj" bölümünü yapıştırarak başla. Her milestone'u ayrı ayrı, bir öncekini bitirip test ettikten sonra iste — hepsini birden "yap" demek, hem Cursor'un hem senin kontrolü kaybetmesine yol açar.

---

## 🎯 Proje Bağlamı (Cursor'a Ver)

Aşağıdaki metni olduğu gibi Cursor'a (Agent/Composer modunda) ver — bu, tüm projenin "anayasası" olacak.

```
Sen bana "Nefis Yemek" adında kurgusal bir yemek sipariş platformunu
mikroservis mimarisiyle, Java + Spring Boot + Kafka kullanarak adım
adım inşa etmemde yardım edeceksin. Amacım production'a atmak değil,
mikroservis pattern'lerini (Saga, CQRS, Outbox, Circuit Breaker,
API Gateway, Distributed Tracing vb.) gerçek kodla deneyimlemek.

TEKNOLOJİ YIĞINI:
- Java 21, Spring Boot 3.x
- Build: Maven, multi-module monorepo (her servis ayrı modül)
- Mesajlaşma: Apache Kafka (KRaft mode, Zookeeper'sız)
- Veritabanı: Her servis için ayrı PostgreSQL instance'ı
- API Gateway: Spring Cloud Gateway
- Resilience: Resilience4j (circuit breaker, retry)
- Observability: Spring Boot Actuator + Micrometer + Prometheus + Grafana,
  dağıtık tracing için Zipkin (Micrometer Tracing ile)
- Auth: Spring Security + JWT
- Test: JUnit 5, Testcontainers (gerçek Postgres/Kafka ile entegrasyon
  testleri), WireMock (contract test simülasyonu için)
- Orkestrasyon: Docker Compose (Kubernetes'e sonra geçebiliriz, önce
  Compose ile temelleri oturtalım)

SERVİSLER (business capability'ye göre bölünmüş):
1. customer-service   → kullanıcı kaydı, login, profil, adres
2. restaurant-service  → restoran, menü, açık/kapalı durumu
3. order-service     → sepet, sipariş oluşturma, sipariş durumu, saga
             orchestrator burada yaşayacak
4. payment-service    → ödeme tahsilatı, iade
5. delivery-service   → kurye atama, teslimat takibi
6. notification-service → SMS/push bildirim (mock olarak console'a yazsın,
              gerçek SMS/push entegrasyonu gerekmez)
7. api-gateway      → tüm dış trafiğin girdiği tek kapı

ÇALIŞMA PRENSİPLERİM:
- Her servis kendi PostgreSQL veritabanına sahip olacak — asla başka
 bir servisin veritabanına direkt erişim olmayacak (Database per Service).
- Servisler arası senkron iletişim sadece gerektiğinde REST ile,
 asenkron iletişim Kafka event'leriyle olacak.
- Bana kod yazarken NEDEN o pattern'i kullandığını kısaca açıkla —
 amacım sadece çalışan kod değil, "neden böyle" sorusunun cevabını
 da öğrenmek.
- Her milestone'un sonunda bana nasıl test edeceğimi (curl komutları,
 Postman koleksiyonu ya da hangi endpoint'i çağıracağımı) göster.
- Kod tabanını temiz tut: her serviste standart bir katman yapısı
 (controller / service / repository / domain / event) kullan.

Şimdi ilk milestone'dan başlayalım (aşağıda vereceğim).
```

---

## 🧱 Milestone'lar (Sırayla İste)

Her birini ayrı bir mesaj olarak Cursor'a ver. Bir öncekini çalıştırıp test ettikten sonra sonrakine geçme.

### Milestone 0 — İskelet ve Database per Service

Maven multi-module monorepo: `order-service`, `payment-service`, `restaurant-service`. Her biri kendi PostgreSQL instance'ı, Flyway, CRUD REST. Kafka yok.

### Milestone 1 — Kalan Servisler + API Gateway

`customer-service`, `delivery-service`, `notification-service`, `api-gateway`.

### Milestone 2 — Saga (Orchestration-based) + Kafka

"Sipariş Ver" akışı, `OrderSagaOrchestrator`, compensating transaction, restoran %20 reddetme.

### Milestone 2.5 — Transactional Outbox

order-service outbox tablosu + polling publisher.

### Milestone 3 — Idempotent Consumer + CQRS Read Model

payment-service processed_events; order-service `order_history_view`.

### Milestone 4 — Resilience, Security, Observability

Resilience4j, JWT, Actuator, Prometheus, Grafana, Zipkin.

### Milestone 5 — Test Piramidi

Testcontainers + WireMock + contract test.

### Milestone 6 (Opsiyonel) — Strangler Fig Simülasyonu

`review-service` ve gateway yönlendirmesi.
