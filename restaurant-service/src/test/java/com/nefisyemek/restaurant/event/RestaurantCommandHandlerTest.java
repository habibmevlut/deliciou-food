package com.nefisyemek.restaurant.event;

import com.nefisyemek.contracts.Topics;
import com.nefisyemek.contracts.command.ReserveRestaurantCommand;
import com.nefisyemek.contracts.event.RestaurantAcceptedEvent;
import com.nefisyemek.contracts.event.RestaurantRejectedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RestaurantCommandHandlerTest {

    @Mock
    private KafkaTemplate<String, Object> kafka;

    @Test
    void forceRejectPublishesRejectedEvent() {
        RestaurantCommandHandler handler = new RestaurantCommandHandler(
                new RestaurantDecisionPolicy(0.0, () -> 0.99), kafka);
        ReserveRestaurantCommand command = ReserveRestaurantCommand.of(
                UUID.randomUUID(), UUID.randomUUID(), true, false);

        handler.onCommand(command);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(eq(Topics.RESTAURANT_EVENTS), eq(command.orderId().toString()), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(RestaurantRejectedEvent.class);
        assertThat(((RestaurantRejectedEvent) payload.getValue()).reason()).isEqualTo("FORCE_REJECT");
    }

    @Test
    void forceAcceptPublishesAcceptedEvent() {
        RestaurantCommandHandler handler = new RestaurantCommandHandler(
                new RestaurantDecisionPolicy(1.0, () -> 0.0), kafka);
        ReserveRestaurantCommand command = ReserveRestaurantCommand.of(
                UUID.randomUUID(), UUID.randomUUID(), false, true);

        handler.onCommand(command);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(eq(Topics.RESTAURANT_EVENTS), eq(command.orderId().toString()), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(RestaurantAcceptedEvent.class);
    }
}
