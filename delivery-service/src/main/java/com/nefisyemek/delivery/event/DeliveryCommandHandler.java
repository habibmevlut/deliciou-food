package com.nefisyemek.delivery.event;

import com.nefisyemek.contracts.Topics;
import com.nefisyemek.contracts.command.AssignCourierCommand;
import com.nefisyemek.contracts.event.CourierAssignedEvent;
import com.nefisyemek.contracts.kafka.KafkaPayloads;
import com.nefisyemek.delivery.domain.Delivery;
import com.nefisyemek.delivery.service.DeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "nefis.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class DeliveryCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(DeliveryCommandHandler.class);
    private static final List<String> COURIERS = List.of("Kurye-Ada", "Kurye-Can", "Kurye-Ece");

    private final DeliveryService deliveries;
    private final KafkaTemplate<String, Object> kafka;

    public DeliveryCommandHandler(DeliveryService deliveries, KafkaTemplate<String, Object> kafka) {
        this.deliveries = deliveries;
        this.kafka = kafka;
    }

    @KafkaListener(topics = Topics.DELIVERY_COMMANDS, groupId = "${spring.kafka.consumer.group-id}")
    public void onCommand(@Payload Object incoming) {
        Object command = KafkaPayloads.unwrap(incoming);
        if (!(command instanceof AssignCourierCommand assign)) {
            log.warn("DELIVERY ignored command type={}",
                    command == null ? "null" : command.getClass().getName());
            return;
        }
        String courier = pickCourier(assign.orderId());
        Delivery delivery = deliveries.assignForSaga(assign.orderId(), courier);
        kafka.send(Topics.DELIVERY_EVENTS, assign.orderId().toString(),
                CourierAssignedEvent.of(assign.orderId(), delivery.getId(), delivery.getCourierName()));
        log.info("DELIVERY ASSIGNED order={} delivery={} courier={}",
                assign.orderId(), delivery.getId(), delivery.getCourierName());
    }

    static String pickCourier(UUID orderId) {
        return COURIERS.get(Math.floorMod(orderId.hashCode(), COURIERS.size()));
    }
}
