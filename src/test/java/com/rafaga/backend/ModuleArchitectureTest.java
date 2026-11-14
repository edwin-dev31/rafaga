package com.rafaga.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
class ModuleArchitectureTest {

    private final ApplicationModules modules = ApplicationModules.of(BackendApplication.class);

    @Test
    void verifiesModuleBoundariesAndAbsenceOfCycles() {
        assertDoesNotThrow(() -> modules.verify());
    }

    @Test
    void documentsCrossModuleEventCommunication() {
        var inventory = modules.getModuleByName("inventory").orElseThrow();
        var listenedEvents = inventory.getEventsListenedTo(modules).stream()
                .map(event -> event.getName())
                .toList();

        assertThat(listenedEvents)
                .contains("com.rafaga.backend.catalog.domain.event.ProductCreatedEvent",
                        "com.rafaga.backend.common.domain.event.OrderConfirmedEvent",
                        "com.rafaga.backend.common.domain.event.OrderCancelledEvent");
        System.out.println(modules);
    }
}
