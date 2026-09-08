package com.nefisyemek.customer.service;

import com.nefisyemek.customer.controller.dto.AddressResponse;
import com.nefisyemek.customer.controller.dto.CreateAddressRequest;
import com.nefisyemek.customer.controller.dto.CreateCustomerRequest;
import com.nefisyemek.customer.controller.dto.CustomerResponse;
import com.nefisyemek.customer.controller.dto.UpdateCustomerRequest;
import com.nefisyemek.customer.domain.Address;
import com.nefisyemek.customer.domain.Customer;
import com.nefisyemek.customer.exception.ResourceNotFoundException;
import com.nefisyemek.customer.repository.AddressRepository;
import com.nefisyemek.customer.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;

    public CustomerService(CustomerRepository customerRepository, AddressRepository addressRepository) {
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        Customer customer = new Customer(request.fullName(), request.email(), request.phone());
        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> findAll() {
        return customerRepository.findAll().stream().map(CustomerResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse findById(UUID id) {
        return CustomerResponse.from(getCustomer(id));
    }

    @Transactional
    public CustomerResponse update(UUID id, UpdateCustomerRequest request) {
        Customer customer = getCustomer(id);
        customer.setFullName(request.fullName());
        customer.setEmail(request.email());
        customer.setPhone(request.phone());
        Customer saved = customerRepository.save(customer);
        customerRepository.flush();
        return CustomerResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Customer customer = getCustomer(id);
        addressRepository.findByCustomerId(id).forEach(addressRepository::delete);
        customerRepository.delete(customer);
    }

    @Transactional
    public AddressResponse addAddress(UUID customerId, CreateAddressRequest request) {
        getCustomer(customerId);
        Address address = new Address(customerId, request.title(), request.street(), request.city());
        return AddressResponse.from(addressRepository.save(address));
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> listAddresses(UUID customerId) {
        getCustomer(customerId);
        return addressRepository.findByCustomerId(customerId).stream().map(AddressResponse::from).toList();
    }

    private Customer getCustomer(UUID id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));
    }
}
