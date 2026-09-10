package com.nefisyemek.order.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nefisyemek.contracts.Topics;
import com.nefisyemek.contracts.command.ChargePaymentCommand;
import com.nefisyemek.order.domain.OutboxMessage;
import com.nefisyemek.order.repository.OutboxMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxMessageRepository repository;

    @Mock
    private KafkaTemplate<String, Object> kafka;

    private ObjectMapper mapper;
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        publisher = new OutboxPublisher(repository, kafka, mapper, 50);
    }

    @Test
    void publishesAndMarksPublishedAt() throws Exception {
        ChargePaymentCommand command = ChargePaymentCommand.of(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("149.90"));
        OutboxMessage message = unpublished(Topics.PAYMENT_COMMANDS, command);

        when(repository.lockUnpublished(50)).thenReturn(List.of(message));
        when(kafka.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishBatch();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(eq(Topics.PAYMENT_COMMANDS), eq(message.getMessageKey()), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(ChargePaymentCommand.class);
        assertThat(((ChargePaymentCommand) payload.getValue()).orderId()).isEqualTo(command.orderId());
        assertThat(message.getPublishedAt()).isNotNull();
        assertThat(message.getLastError()).isNull();
    }

    @Test
    void kafkaFailureIncrementsAttemptsAndLeavesUnpublished() {
        ChargePaymentCommand command = ChargePaymentCommand.of(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.00"));
        OutboxMessage message = unpublished(Topics.PAYMENT_COMMANDS, command);

        when(repository.lockUnpublished(50)).thenReturn(List.of(message));
        when(kafka.send(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        publisher.publishBatch();

        assertThat(message.getPublishedAt()).isNull();
        assertThat(message.getAttempts()).isEqualTo(1);
        assertThat(message.getLastError()).contains("broker down");
    }

    @Test
    void untrustedPayloadTypeIsNotSentToKafka() {
        OutboxMessage message = new OutboxMessage(
                Topics.PAYMENT_COMMANDS,
                UUID.randomUUID().toString(),
                "java.lang.RuntimeException",
                "{\"oops\":true}"
        );
        ReflectionTestUtils.setField(message, "id", UUID.randomUUID());

        when(repository.lockUnpublished(50)).thenReturn(List.of(message));

        publisher.publishBatch();

        verifyNoInteractions(kafka);
        assertThat(message.getPublishedAt()).isNull();
        assertThat(message.getAttempts()).isEqualTo(1);
        assertThat(message.getLastError()).contains("untrusted payload type");
    }

    @Test
    void emptyBatchDoesNothing() {
        when(repository.lockUnpublished(50)).thenReturn(List.of());

        publisher.publishBatch();

        verifyNoInteractions(kafka);
    }

    private OutboxMessage unpublished(String topic, ChargePaymentCommand command) {
        try {
            OutboxMessage message = new OutboxMessage(
                    topic,
                    command.orderId().toString(),
                    ChargePaymentCommand.class.getName(),
                    mapper.writeValueAsString(command)
            );
            ReflectionTestUtils.setField(message, "id", UUID.randomUUID());
            return message;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
