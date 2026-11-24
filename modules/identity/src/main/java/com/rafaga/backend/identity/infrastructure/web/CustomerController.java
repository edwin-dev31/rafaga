package com.rafaga.backend.identity.infrastructure.web;

import com.rafaga.backend.identity.application.service.CustomerService;
import com.rafaga.backend.identity.application.service.CustomerView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @GetMapping
    public Page<CustomerView> list(
            @RequestParam(name = "q", required = false) String query,
            Pageable pageable) {
        return customers.searchCustomers(query, pageable);
    }

    @GetMapping("/{customerId}")
    public CustomerView get(@PathVariable("customerId") UUID customerId) {
        return customers.getCustomer(customerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerView create(@Valid @RequestBody CustomerRequest request) {
        return customers.createCustomer(request.fullName(), request.email(), request.phone(), request.notes());
    }

    @PutMapping("/{customerId}")
    public CustomerView update(
            @PathVariable("customerId") UUID customerId,
            @Valid @RequestBody CustomerRequest request) {
        return customers.updateCustomer(customerId, request.fullName(), request.email(), request.phone(), request.notes());
    }

    public record CustomerRequest(
            @NotBlank @Size(max = 200) String fullName,
            @Email @Size(max = 255) String email,
            @Size(max = 50) String phone,
            @Size(max = 1000) String notes) { }
}
