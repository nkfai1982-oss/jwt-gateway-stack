package com.example.jwtgateway.springapi;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@RestController
public class WhoAmIController {
    @GetMapping("/whoami")
    public ResponseEntity<?> whoami(
            @RequestHeader(value = "X-Authenticated-User", required = false) String user,
            @RequestHeader(value = "X-Authenticated-Audience", required = false) String audience,
            @RequestHeader(value = "X-Authenticated-Scopes", required = false) String scopesHeader) {
        if (user == null || user.isBlank() || !"component-a".equals(audience)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "gateway_identity_required"));
        }
        Set<String> scopes = Arrays.stream((scopesHeader == null ? "" : scopesHeader).split("\\s+"))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(TreeSet::new));
        if (!scopes.contains("component-a.read")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "insufficient_scope"));
        }
        return ResponseEntity.ok(Map.of(
                "component", "component-a",
                "user", user,
                "audience", audience,
                "scopes", scopes));
    }
}
