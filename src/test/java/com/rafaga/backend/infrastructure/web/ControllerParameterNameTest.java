package com.rafaga.backend.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.MatrixVariable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;

class ControllerParameterNameTest {
    private static final List<Class<? extends Annotation>> NAMED_PARAMETER_ANNOTATIONS = List.of(
            PathVariable.class,
            RequestParam.class,
            RequestHeader.class,
            CookieValue.class,
            MatrixVariable.class,
            RequestPart.class);

    @Test
    void everyControllerParameterAnnotationDeclaresItsName() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

        List<String> violations = new ArrayList<>();
        int controllerCount = 0;

        for (var candidate : scanner.findCandidateComponents("com.rafaga.backend")) {
            if (candidate.getBeanClassName() == null) {
                continue;
            }
            Class<?> controller = Class.forName(candidate.getBeanClassName());
            controllerCount++;
            for (Method method : controller.getDeclaredMethods()) {
                Parameter[] parameters = method.getParameters();
                for (int index = 0; index < parameters.length; index++) {
                    for (Annotation annotation : parameters[index].getAnnotations()) {
                        if (!NAMED_PARAMETER_ANNOTATIONS.contains(annotation.annotationType())) {
                            continue;
                        }
                        if (hasBlankName(annotation)) {
                            violations.add("%s.%s(parameter %d)".formatted(
                                    controller.getSimpleName(), method.getName(), index));
                        }
                    }
                }
            }
        }

        assertThat(controllerCount).as("scanned MVC controllers").isPositive();
        assertThat(violations)
                .as("controller parameters must declare an explicit @PathVariable/@RequestParam/@RequestHeader name")
                .isEmpty();
    }

    private boolean hasBlankName(Annotation annotation) throws Exception {
        for (String attribute : List.of("name", "value")) {
            try {
                Object value = annotation.annotationType().getMethod(attribute).invoke(annotation);
                if (value instanceof String text && !text.isBlank()) {
                    return false;
                }
            } catch (NoSuchMethodException ignored) {
                // annotation does not expose this attribute
            }
        }
        return true;
    }
}
