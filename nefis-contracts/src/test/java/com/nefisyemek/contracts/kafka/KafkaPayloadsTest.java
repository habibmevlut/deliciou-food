package com.nefisyemek.contracts.kafka;

import com.nefisyemek.contracts.command.ChargePaymentCommand;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaPayloadsTest {

    @Test
    void unwrapsConsumerRecordValue() {
        ChargePaymentCommand command = ChargePaymentCommand.of(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.00"));
        ConsumerRecord<String, Object> record =
                new ConsumerRecord<>("payment-commands", 0, 0L, command.orderId().toString(), command);

        assertThat(KafkaPayloads.unwrap(record)).isSameAs(command);
    }

    @Test
    void leavesPlainPayloadUnchanged() {
        ChargePaymentCommand command = ChargePaymentCommand.of(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.00"));
        assertThat(KafkaPayloads.unwrap(command)).isSameAs(command);
    }
}
