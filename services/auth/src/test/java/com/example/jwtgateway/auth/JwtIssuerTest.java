package com.example.jwtgateway.auth;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtIssuerTest {
    @TempDir
    Path tempDir;

    @Test
    void issuesShortLivedRs256TokenAndPublishesOnlyPublicJwk() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        Path keyFile = writePkcs8Key(keyPair, tempDir.resolve("jwt-private.pem"));
        JwtIssuer issuer = new JwtIssuer(keyFile, "https://issuer.example.test/auth", "test-key-1", 600);

        String encoded = issuer.issue("alice", "component-a", List.of("component-a.read"));
        SignedJWT jwt = SignedJWT.parse(encoded);
        assertEquals("RS256", jwt.getHeader().getAlgorithm().getName());
        assertEquals("test-key-1", jwt.getHeader().getKeyID());
        assertTrue(jwt.verify(new RSASSAVerifier((RSAPublicKey) keyPair.getPublic())));
        assertEquals("https://issuer.example.test/auth", jwt.getJWTClaimsSet().getIssuer());
        assertEquals(List.of("component-a"), jwt.getJWTClaimsSet().getAudience());
        assertEquals("alice", jwt.getJWTClaimsSet().getSubject());
        assertEquals("component-a.read", jwt.getJWTClaimsSet().getStringClaim("scope"));
        assertTrue(jwt.getJWTClaimsSet().getExpirationTime().after(jwt.getJWTClaimsSet().getIssueTime()));

        Map<String, Object> jwks = issuer.jwks();
        JWKSet parsed = JWKSet.parse(jwks);
        assertEquals(1, parsed.getKeys().size());
        Map<String, Object> publicKey = parsed.toJSONObject().get("keys") instanceof List<?> list
                ? (Map<String, Object>) list.getFirst()
                : Map.of();
        assertEquals("RSA", publicKey.get("kty"));
        assertEquals("RS256", publicKey.get("alg"));
        assertEquals("test-key-1", publicKey.get("kid"));
        assertFalse(publicKey.containsKey("d"));
        assertFalse(publicKey.containsKey("p"));
    }

    @Test
    void rejectsAccessTokenTtlOutsideConfiguredBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtIssuer(tempDir.resolve("unused.pem"), "issuer", "kid", 30));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtIssuer(tempDir.resolve("unused.pem"), "issuer", "kid", 3601));
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static Path writePkcs8Key(KeyPair keyPair, Path path) throws Exception {
        String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(keyPair.getPrivate().getEncoded());
        Files.writeString(path, "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n",
                StandardCharsets.US_ASCII);
        return path;
    }
}
