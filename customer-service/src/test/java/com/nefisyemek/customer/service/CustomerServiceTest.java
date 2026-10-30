package com.nefisyemek.customer.service;

import com.nefisyemek.customer.controller.dto.CreateCustomerRequest;
import com.nefisyemek.customer.domain.Customer;
import com.nefisyemek.customer.exception.ResourceNotFoundException;
import com.nefisyemek.customer.repository.AddressRepository;
import com.nefisyemek.customer.repository.CustomerRepository;
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
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private AddressRepository addressRepository;

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(customerRepository, addressRepository);
    }

    @Test
    void createPersistsCustomer() {
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        var response = customerService.create(new CreateCustomerRequest("Ali", "ali@example.com", "555"));
        assertThat(response.email()).isEqualTo("ali@example.com");
    }

    @Test
    void findByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(customerRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> customerService.findById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
