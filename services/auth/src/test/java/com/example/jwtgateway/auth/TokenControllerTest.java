package com.example.jwtgateway.auth;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenControllerTest {
    private static final String PASSWORD_HASH =
            "$argon2id$v=19$m=65536,t=3,p=1$MDEyMzQ1Njc4OWFiY2RlZg$RilFZ9JhPjv45LCaxYQx0EV4EI5/cqp7uPW2nAC89Zk";
    private static final JsonMapper JSON = new JsonMapper();

    @TempDir
    Path tempDir;

    private TokenController controller;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        Path accountsFile = tempDir.resolve("accounts.json");
        Map<String, Object> account = Map.of(
                "username", "alice",
                "passwordHash", PASSWORD_HASH,
                "audiences", List.of("component-a"),
                "scopes", List.of("component-a.read"));
        JSON.writeValue(accountsFile.toFile(), Map.of("accounts", List.of(account)));

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        String privatePem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        Path keyFile = tempDir.resolve("jwt-private.pem");
        Files.writeString(keyFile, privatePem, StandardCharsets.US_ASCII);

        var encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        AccountStore store = new AccountStore(accountsFile, JSON, encoder);
        JwtIssuer issuer = new JwtIssuer(keyFile, "https://issuer.example.test/auth", "test-key-1", 600);
        controller = new TokenController(store, issuer);
    }

    @Test
    void issuesTokenOnlyForAllowedAudienceAndScope() throws Exception {
        ResponseEntity<?> response = controller.token(new TokenController.TokenRequest(
                "alice", "fixture-password", "component-a", List.of("component-a.read")));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        TokenController.TokenResponse body = (TokenController.TokenResponse) response.getBody();
        SignedJWT jwt = SignedJWT.parse(body.accessToken());
        assertTrue(jwt.verify(new RSASSAVerifier((RSAPublicKey) keyPair.getPublic())));
        assertEquals("alice", jwt.getJWTClaimsSet().getSubject());
        assertEquals(List.of("component-a"), jwt.getJWTClaimsSet().getAudience());

        JsonNode json = JSON.valueToTree(body);
        assertTrue(json.has("access_token"));
        assertTrue(json.has("token_type"));
        assertTrue(json.has("expires_in"));
        assertFalse(json.has("accessToken"));
    }

    @Test
    void returnsSameGenericErrorForUnknownUserOrWrongPassword() {
        ResponseEntity<?> wrongPassword = controller.token(new TokenController.TokenRequest(
                "alice", "wrong-password", "component-a", List.of("component-a.read")));
        ResponseEntity<?> missingUser = controller.token(new TokenController.TokenRequest(
                "nobody", "wrong-password", "component-a", List.of("component-a.read")));

        assertEquals(HttpStatus.UNAUTHORIZED, wrongPassword.getStatusCode());
        assertEquals(wrongPassword.getBody(), missingUser.getBody());
    }

    @Test
    void refusesUnauthorizedAudienceOrScope() {
        ResponseEntity<?> response = controller.token(new TokenController.TokenRequest(
                "alice", "fixture-password", "component-b", List.of("component-b.read")));
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
