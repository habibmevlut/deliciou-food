package com.nefisyemek.order.service;

import com.nefisyemek.order.controller.dto.CreateOrderRequest;
import com.nefisyemek.order.controller.dto.OrderResponse;
import com.nefisyemek.order.controller.dto.UpdateOrderRequest;
import com.nefisyemek.order.domain.Order;
import com.nefisyemek.order.domain.OrderStatus;
import com.nefisyemek.order.event.OrderSagaOrchestrator;
import com.nefisyemek.order.exception.IllegalOrderStateException;
import com.nefisyemek.order.exception.ResourceNotFoundException;
import com.nefisyemek.order.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderSagaOrchestrator saga;

    public OrderService(
            OrderRepository orderRepository,
            @Autowired(required = false) OrderSagaOrchestrator saga
    ) {
        this.orderRepository = orderRepository;
        this.saga = saga;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        OrderStatus status = request.status() == null ? OrderStatus.PENDING : request.status();
        Order order = new Order(
                request.customerId(),
                request.restaurantId(),
                request.totalAmount(),
                status
        );
        order.setForceRestaurantReject(Boolean.TRUE.equals(request.forceRestaurantReject()));
        order.setForceRestaurantAccept(Boolean.TRUE.equals(request.forceRestaurantAccept()));
        Order saved = orderRepository.save(order);
        // status elle verilmişse bu klasik CRUD; saga yalnızca "Sipariş Ver" (status yok) yolunda başlar.
        if (request.status() == null && saga != null) {
            saga.start(saved);
        }
        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findAll() {
        return orderRepository.findAll().stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(UUID id) {
        return OrderResponse.from(getOrder(id));
    }

    /**
     * Kafka komutunu o anki saga adımı için tekrar yayınlar. REST ile payment oluşturmaz.
     */
    @Transactional
    public OrderResponse replaySaga(UUID id) {
        if (saga == null) {
            throw new IllegalOrderStateException("Kafka saga kapalı; replay yok");
        }
        Order order = getOrder(id);
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT
                && order.getStatus() != OrderStatus.AWAITING_RESTAURANT
                && order.getStatus() != OrderStatus.AWAITING_DELIVERY) {
            throw new IllegalOrderStateException(
                    "Saga replay yalnızca AWAITING_* için. status=" + order.getStatus());
        }
        saga.replayCurrentStep(order);
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse update(UUID id, UpdateOrderRequest request) {
        Order order = getOrder(id);
        order.setCustomerId(request.customerId());
        order.setRestaurantId(request.restaurantId());
        order.setTotalAmount(request.totalAmount());
        order.setStatus(request.status());
        // save() managed entity'de @PreUpdate'i hemen çalıştırmaz; flush etmezsek
        // response eski updatedAt ile döner.
        Order saved = orderRepository.save(order);
        orderRepository.flush();
        return OrderResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Order order = getOrder(id);
        orderRepository.delete(order);
    }

    private Order getOrder(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }
}
