package com.nefisyemek.restaurant.service;

import com.nefisyemek.restaurant.controller.dto.CreateRestaurantRequest;
import com.nefisyemek.restaurant.controller.dto.RestaurantResponse;
import com.nefisyemek.restaurant.controller.dto.UpdateRestaurantRequest;
import com.nefisyemek.restaurant.domain.Restaurant;
import com.nefisyemek.restaurant.exception.ResourceNotFoundException;
import com.nefisyemek.restaurant.repository.RestaurantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;

    public RestaurantService(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    @Transactional
    public RestaurantResponse create(CreateRestaurantRequest request) {
        Restaurant restaurant = new Restaurant(
                request.name(),
                request.address(),
                request.cuisine(),
                request.open()
        );
        return RestaurantResponse.from(restaurantRepository.save(restaurant));
    }

    @Transactional(readOnly = true)
    public List<RestaurantResponse> findAll() {
        return restaurantRepository.findAll().stream().map(RestaurantResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RestaurantResponse findById(UUID id) {
        return RestaurantResponse.from(getRestaurant(id));
    }

    @Transactional
    public RestaurantResponse update(UUID id, UpdateRestaurantRequest request) {
        Restaurant restaurant = getRestaurant(id);
        restaurant.setName(request.name());
        restaurant.setAddress(request.address());
        restaurant.setCuisine(request.cuisine());
        restaurant.setOpen(request.open());
        Restaurant saved = restaurantRepository.save(restaurant);
        restaurantRepository.flush();
        return RestaurantResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Restaurant restaurant = getRestaurant(id);
        restaurantRepository.delete(restaurant);
    }

    private Restaurant getRestaurant(UUID id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + id));
    }
}
