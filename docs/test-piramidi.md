# Test piramidi — Milestone 5

Lab komutları aşağıda. Amaç: her şeyi Docker’da 6 servis açmadan “bozuldu mu?” diye sormak.

---

## Bu adımın konusu ne?

M2–M4 siparişin **çalışmasıydı**. Burası **güven** : kod değişince hangi katman yakalar?

Üç katman, üç fiyat:

```
            /\
           /  \      az, yavaş: Testcontainers (gerçek Postgres / Kafka)
          /----\
         /      \    orta: WireMock sözleşme (karşı servisi taklit)
        /--------\
       /   unit   \  çok, hızlı: Mockito — DB/Kafka yok (zaten vardı)
      /------------\
```

**Neden hepsi Testcontainers değil?** Gerçek Postgres her testte 10+ saniye. 40 birim testi o kadar bekleyemezsin. Mock ucuzdur.

**Neden yalnız unit değil?** Mock repository Flyway’deki eksik kolonu görmez. `ChargePaymentCommand` JSON’u Kafka’dan çıkınca type header yanlışsa unit test susar; gerçek broker bağırır.

**Neden WireMock?** Gateway testinde restaurant-service’i ayağa kaldırmak istemezsin. “List restaurants JSON’u böyle durur” sözünü sahte sunucu verir; kapı alanı silmeden + JWT ile geçirir. Docker gerekmez.

Kafka UI / Grafana buraya karışmaz. Onlar çalışırken bakmak; piramit **commit’ten önce** yakalamak.

---

## Bu repoda hangisi hangisi?

| Katman | Sınıf | Ne kanıtlar | Docker |
| --- | --- | --- | --- |
| Unit | `*Test` (eski) | İş kuralı, mock repo | hayır |
| Sözleşme (tüketici) | `GatewayRestaurantContractIT` | Kapı, WireMock restoran JSON’unu + JWT ile geçirir | hayır |
| Sözleşme (üretici) | `RestaurantPostgresIT` | Gerçek restaurant-service **aynı alan adlarını** üretir | evet |
| IT | `CustomerPostgresIT` | Flyway + login gerçek Postgres’te | evet |
| IT | `OrderOutboxIT` | Sipariş Ver → `outbox_messages` aynı DB | evet |
| IT | `PaymentIdempotencyIT` | Aynı `commandId` iki Kafka mesajı → tek tahsilat | evet |

Sözleşme alanları: `id`, `name`, `address`, `cuisine`, `open`, `createdAt`, `updatedAt`. Üretici veya tüketici bunlardan birini silerse ilgili IT kırılır — “iki taraf anlaştı” budur. Spring Cloud Contract / Pact yok; WireMock + iki assert yeterli laboratuvar.

---

## Nasıl koşarsın?

IntelliJ’de Docker Desktop açık olsun. İlk sefer `postgres:16-alpine` ve `confluentinc/cp-kafka:7.7.1` iner (compose’daki `apache/kafka` değil; Testcontainers’ın bildiği imaj).

```bash
./mvnw test
```

Hepsi birlikte: birim + WireMock her zaman; `*IT` Docker yoksa **atlanır** (kırmızı değil, skipped). Log’da Docker ERROR görebilirsin — atlama kontrolü. Docker varsa IT de koşar.

Tek sınıf (IntelliJ: sınıfın üstüne sağ tık → Run):

- `GatewayRestaurantContractIT` — Docker’suz dene
- `CustomerPostgresIT` — Docker şart

Postman M5’te yeni klasör yok. Postman **elle** API dener; piramit **Maven** ile otomatik dener.
