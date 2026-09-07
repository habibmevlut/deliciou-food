package com.nefisyemek.order.service;

import com.nefisyemek.order.controller.dto.CreateOrderRequest;
import com.nefisyemek.order.controller.dto.OrderResponse;
import com.nefisyemek.order.controller.dto.UpdateOrderRequest;
import com.nefisyemek.order.domain.Order;
import com.nefisyemek.order.domain.OrderStatus;
import com.nefisyemek.order.exception.ResourceNotFoundException;
import com.nefisyemek.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository);
    }

    @Test
    void createDefaultsStatusToPending() {
        UUID customerId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest(
                customerId, restaurantId, new BigDecimal("149.90"), null);

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.create(request);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.restaurantId()).isEqualTo(restaurantId);
        assertThat(response.totalAmount()).isEqualByComparingTo("149.90");
    }

    @Test
    void findByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.findById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void updateReplacesMutableFields() {
        UUID id = UUID.randomUUID();
        Order existing = new Order(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.00"), OrderStatus.PENDING);
        when(orderRepository.findById(id)).thenReturn(Optional.of(existing));
        when(orderRepository.save(existing)).thenReturn(existing);

        UUID newCustomer = UUID.randomUUID();
        UUID newRestaurant = UUID.randomUUID();
        UpdateOrderRequest request = new UpdateOrderRequest(
                newCustomer, newRestaurant, new BigDecimal("20.00"), OrderStatus.CONFIRMED);

        OrderResponse response = orderService.update(id, request);

        assertThat(response.customerId()).isEqualTo(newCustomer);
        assertThat(response.restaurantId()).isEqualTo(newRestaurant);
        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.totalAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    void findAllMapsEntities() {
        when(orderRepository.findAll()).thenReturn(List.of(
                new Order(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("5.00"), OrderStatus.PENDING)
        ));

        assertThat(orderService.findAll()).hasSize(1);
    }
}
