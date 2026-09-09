package com.nefisyemek.contracts.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nefisyemek.contracts.command.ChargePaymentCommand;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KafkaJsonDeserializerTrustTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void trustedContractsPackageDoesNotCoverCommandSubpackage() {
        ChargePaymentCommand command = ChargePaymentCommand.of(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.00"));
        RecordHeaders headers = new RecordHeaders();
        byte[] payload = serialize(command, headers);

        JsonDeserializer<Object> deserializer = new JsonDeserializer<>(Object.class, objectMapper, true);
        deserializer.addTrustedPackages("com.nefisyemek.contracts");
        deserializer.setUseTypeHeaders(true);

        assertThatThrownBy(() -> deserializer.deserialize("payment-commands", headers, payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in the trusted packages");
    }

    @Test
    void trustedContractsWildcardDeserializesChargePaymentCommand() {
        ChargePaymentCommand command = ChargePaymentCommand.of(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.00"));
        RecordHeaders headers = new RecordHeaders();
        byte[] payload = serialize(command, headers);

        Object result = KafkaJsonAutoConfiguration.jsonDeserializer(objectMapper)
                .deserialize("payment-commands", headers, payload);

        assertThat(result).isInstanceOf(ChargePaymentCommand.class);
        ChargePaymentCommand read = (ChargePaymentCommand) result;
        assertThat(read.orderId()).isEqualTo(command.orderId());
        assertThat(read.amount()).isEqualByComparingTo(command.amount());
    }

    private byte[] serialize(Object value, RecordHeaders headers) {
        JsonSerializer<Object> serializer = new JsonSerializer<>(objectMapper);
        serializer.setAddTypeInfo(true);
        return serializer.serialize("payment-commands", headers, value);
    }
}
