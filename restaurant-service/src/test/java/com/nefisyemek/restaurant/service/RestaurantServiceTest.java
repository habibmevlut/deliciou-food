package com.nefisyemek.restaurant.service;

import com.nefisyemek.restaurant.controller.dto.CreateRestaurantRequest;
import com.nefisyemek.restaurant.controller.dto.RestaurantResponse;
import com.nefisyemek.restaurant.controller.dto.UpdateRestaurantRequest;
import com.nefisyemek.restaurant.domain.Restaurant;
import com.nefisyemek.restaurant.exception.ResourceNotFoundException;
import com.nefisyemek.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    private RestaurantService restaurantService;

    @BeforeEach
    void setUp() {
        restaurantService = new RestaurantService(restaurantRepository);
    }

    @Test
    void createPersistsRestaurant() {
        CreateRestaurantRequest request = new CreateRestaurantRequest(
                "Adana Sofrası", "Kadıköy", "Türk", true);
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RestaurantResponse response = restaurantService.create(request);

        assertThat(response.name()).isEqualTo("Adana Sofrası");
        assertThat(response.open()).isTrue();
        assertThat(response.cuisine()).isEqualTo("Türk");
    }

    @Test
    void updateCanCloseRestaurant() {
        UUID id = UUID.randomUUID();
        Restaurant existing = new Restaurant("Adana Sofrası", "Kadıköy", "Türk", true);
        when(restaurantRepository.findById(id)).thenReturn(Optional.of(existing));
        when(restaurantRepository.save(existing)).thenReturn(existing);

        RestaurantResponse response = restaurantService.update(
                id, new UpdateRestaurantRequest("Adana Sofrası", "Kadıköy", "Türk", false));

        assertThat(response.open()).isFalse();
    }

    @Test
    void findByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(restaurantRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantService.findById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }
}
