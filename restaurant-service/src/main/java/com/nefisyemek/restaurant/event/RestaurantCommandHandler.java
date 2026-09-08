package com.nefisyemek.restaurant.event;

import com.nefisyemek.contracts.Topics;
import com.nefisyemek.contracts.command.ReserveRestaurantCommand;
import com.nefisyemek.contracts.event.RestaurantAcceptedEvent;
import com.nefisyemek.contracts.event.RestaurantRejectedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "nefis.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class RestaurantCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(RestaurantCommandHandler.class);

    private final RestaurantDecisionPolicy policy;
    private final KafkaTemplate<String, Object> kafka;

    public RestaurantCommandHandler(RestaurantDecisionPolicy policy, KafkaTemplate<String, Object> kafka) {
        this.policy = policy;
        this.kafka = kafka;
    }

    @KafkaListener(topics = Topics.RESTAURANT_COMMANDS, groupId = "${spring.kafka.consumer.group-id}")
    public void onCommand(Object command) {
        if (!(command instanceof ReserveRestaurantCommand reserve)) {
            log.debug("RESTAURANT ignored command type={}",
                    command == null ? "null" : command.getClass().getSimpleName());
            return;
        }
        boolean reject = policy.shouldReject(reserve.forceReject(), reserve.forceAccept());
        if (reject) {
            String reason = policy.reason(reserve.forceReject(), reserve.forceAccept());
            kafka.send(Topics.RESTAURANT_EVENTS, reserve.orderId().toString(),
                    RestaurantRejectedEvent.of(reserve.orderId(), reserve.restaurantId(), reason));
            log.warn("RESTAURANT REJECT order={} restaurant={} reason={} (compensate tetiklenecek)",
                    reserve.orderId(), reserve.restaurantId(), reason);
            return;
        }
        kafka.send(Topics.RESTAURANT_EVENTS, reserve.orderId().toString(),
                RestaurantAcceptedEvent.of(reserve.orderId(), reserve.restaurantId()));
        log.info("RESTAURANT ACCEPT order={} restaurant={}", reserve.orderId(), reserve.restaurantId());
    }
}
