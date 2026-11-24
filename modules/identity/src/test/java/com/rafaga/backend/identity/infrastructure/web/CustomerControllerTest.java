package com.rafaga.backend.identity.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rafaga.backend.identity.application.exception.IdentityResourceNotFoundException;
import com.rafaga.backend.identity.application.service.CustomerService;
import com.rafaga.backend.identity.application.service.CustomerView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CustomerControllerTest {
    private CustomerService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(CustomerService.class);
        mvc = MockMvcBuilders.standaloneSetup(new CustomerController(service))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new IdentityExceptionHandler())
                .build();
    }

    @Test
    void listsGetsCreatesAndUpdatesCustomers() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-01T12:00:00Z");
        CustomerView ana = new CustomerView(id, null, "Ana Pérez", "ana@example.com", "3001234567", null, now, now);

        when(service.searchCustomers(eq("ana"), any()))
                .thenReturn(new PageImpl<>(List.of(ana), PageRequest.of(0, 20), 1));
        when(service.getCustomer(id)).thenReturn(ana);
        when(service.createCustomer("Ana Pérez", "ana@example.com", "3001234567", null)).thenReturn(ana);
        when(service.updateCustomer(id, "Ana Gómez", "ana@example.com", "3001234567", "VIP"))
                .thenReturn(new CustomerView(id, null, "Ana Gómez", "ana@example.com", "3001234567", "VIP", now, now));

        mvc.perform(get("/api/customers").param("q", "ana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].fullName").value("Ana Pérez"));

        mvc.perform(get("/api/customers/{customerId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));

        mvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Ana Pérez\",\"email\":\"ana@example.com\",\"phone\":\"3001234567\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()));

        mvc.perform(put("/api/customers/{customerId}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Ana Gómez\",\"email\":\"ana@example.com\",\"phone\":\"3001234567\",\"notes\":\"VIP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ana Gómez"));
    }

    @Test
    void returnsNotFoundWhenCustomerDoesNotExist() throws Exception {
        UUID missing = UUID.randomUUID();
        when(service.getCustomer(missing)).thenThrow(new IdentityResourceNotFoundException("Customer", missing));

        mvc.perform(get("/api/customers/{customerId}", missing))
                .andExpect(status().isNotFound());
    }
}
