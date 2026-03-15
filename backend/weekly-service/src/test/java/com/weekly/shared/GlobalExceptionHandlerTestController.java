package com.weekly.shared;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only controller used to exercise {@link GlobalExceptionHandler}
 * outside the plan controller package.
 */
@RestController
@Profile("test")
@RequestMapping("/api/v1/test/errors")
class GlobalExceptionHandlerTestController {

    @PostMapping("/validation")
    ResponseEntity<Void> validate(@Valid @RequestBody ValidationRequest request) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/boom")
    ResponseEntity<Void> boom() {
        throw new RuntimeException("Synthetic failure from test controller");
    }

    record ValidationRequest(@NotBlank String name) { }
}
