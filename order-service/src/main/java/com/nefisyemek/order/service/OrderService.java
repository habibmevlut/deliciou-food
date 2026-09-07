package com.nefisyemek.order.service;

import com.nefisyemek.order.controller.dto.CreateOrderRequest;
import com.nefisyemek.order.controller.dto.OrderResponse;
import com.nefisyemek.order.controller.dto.UpdateOrderRequest;
import com.nefisyemek.order.domain.Order;
import com.nefisyemek.order.domain.OrderStatus;
import com.nefisyemek.order.exception.ResourceNotFoundException;
import com.nefisyemek.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
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
        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findAll() {
        return orderRepository.findAll().stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(UUID id) {
        return OrderResponse.from(getOrder(id));
    }

    @Transactional
    public OrderResponse update(UUID id, UpdateOrderRequest request) {
        Order order = getOrder(id);
        order.setCustomerId(request.customerId());
        order.setRestaurantId(request.restaurantId());
        order.setTotalAmount(request.totalAmount());
        order.setStatus(request.status());
        return OrderResponse.from(orderRepository.save(order));
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
