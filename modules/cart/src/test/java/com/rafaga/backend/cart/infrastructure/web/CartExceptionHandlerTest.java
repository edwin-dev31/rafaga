package com.rafaga.backend.cart.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class CartExceptionHandlerTest {
    @Test
    void mapsConcurrentActiveCartConstraintToConflict() {
        var response = new CartExceptionHandler()
                .dataConflict(new DataIntegrityViolationException("duplicate active cart"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("message", "Cart conflicts with existing data");
    }
}
