package com.example.jwtgateway.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class TokenController {
    private final AccountStore accounts;
    private final JwtIssuer issuer;

    public TokenController(AccountStore accounts, JwtIssuer issuer) {
        this.accounts = accounts;
        this.issuer = issuer;
    }

    @PostMapping("/auth/token")
    public ResponseEntity<?> token(@Valid @RequestBody TokenRequest request) {
        AccountStore.StoredAccount account = accounts.authenticate(request.username(), request.password());
        if (account == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid_credentials"));
        }
        if (!account.audiences().contains(request.audience())
                || !account.scopes().containsAll(request.scopes())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "unauthorized_audience_or_scope"));
        }
        String accessToken = issuer.issue(account.username(), request.audience(), request.scopes());
        return ResponseEntity.ok(new TokenResponse("Bearer", accessToken, issuer.expiresInSeconds()));
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return issuer.jwks();
    }

    public record TokenRequest(
            @NotBlank String username,
            @NotBlank String password,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9:._/-]{1,128}") String audience,
            @NotEmpty List<@Pattern(regexp = "[A-Za-z0-9:._/-]{1,128}") String> scopes) {}

    public record TokenResponse(
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn) {}
}
