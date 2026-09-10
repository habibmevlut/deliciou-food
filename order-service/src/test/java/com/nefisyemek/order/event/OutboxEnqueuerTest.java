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

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEnqueuerTest {

    @Mock
    private OutboxMessageRepository repository;

    private ObjectMapper mapper;
    private OutboxEnqueuer enqueuer;

    @BeforeEach
    void setUp() {
        mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        enqueuer = new OutboxEnqueuer(repository, mapper);
        when(repository.save(any(OutboxMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void enqueuePersistsTopicKeyTypeAndJson() throws Exception {
        UUID orderId = UUID.randomUUID();
        ChargePaymentCommand command = ChargePaymentCommand.of(orderId, UUID.randomUUID(), new BigDecimal("149.90"));

        enqueuer.enqueue(Topics.PAYMENT_COMMANDS, orderId.toString(), command);

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(repository).save(captor.capture());
        OutboxMessage row = captor.getValue();
        assertThat(row.getTopic()).isEqualTo(Topics.PAYMENT_COMMANDS);
        assertThat(row.getMessageKey()).isEqualTo(orderId.toString());
        assertThat(row.getPayloadType()).isEqualTo(ChargePaymentCommand.class.getName());
        ChargePaymentCommand roundTrip = mapper.readValue(row.getPayload(), ChargePaymentCommand.class);
        assertThat(roundTrip.orderId()).isEqualTo(orderId);
        assertThat(roundTrip.amount()).isEqualByComparingTo("149.90");
        assertThat(row.getPublishedAt()).isNull();
    }
}
