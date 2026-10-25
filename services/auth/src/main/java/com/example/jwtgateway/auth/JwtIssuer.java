package com.example.jwtgateway.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public final class JwtIssuer {
    private final RSAPrivateCrtKey privateKey;
    private final RSAPublicKey publicKey;
    private final String issuer;
    private final String keyId;
    private final long ttlSeconds;

    public JwtIssuer(
            @Value("${jwt.private-key-file}") Path privateKeyFile,
            @Value("${jwt.issuer}") String issuer,
            @Value("${jwt.key-id}") String keyId,
            @Value("${jwt.access-token-ttl-seconds}") long ttlSeconds) throws Exception {
        if (ttlSeconds < 60 || ttlSeconds > 3600) {
            throw new IllegalArgumentException("JWT access-token TTL must be between 60 and 3600 seconds");
        }
        this.privateKey = loadPrivateKey(privateKeyFile);
        if (privateKey.getModulus().bitLength() < 2048) {
            throw new IllegalStateException("JWT RSA key must be at least 2048 bits");
        }
        this.publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        this.issuer = issuer;
        this.keyId = keyId;
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(String subject, String audience, List<String> scopes) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(subject)
                .audience(audience)
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
                .jwtID(UUID.randomUUID().toString())
                .claim("scope", String.join(" ", scopes))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(keyId).build(), claims);
        try {
            jwt.sign(new RSASSASigner(privateKey));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign access token", e);
        }
    }

    public long expiresInSeconds() {
        return ttlSeconds;
    }

    public Map<String, Object> jwks() {
        RSAKey jwk = new RSAKey.Builder(publicKey)
                .keyID(keyId)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        return new JWKSet(jwk).toJSONObject();
    }

    private static RSAPrivateCrtKey loadPrivateKey(Path path) throws Exception {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalStateException("JWT signing private key is missing or unreadable: " + path);
        }
        String pem = Files.readString(path, StandardCharsets.US_ASCII);
        if (!pem.contains("-----BEGIN PRIVATE KEY-----")) {
            throw new IllegalStateException("JWT key must be an unencrypted PKCS#8 PEM (BEGIN PRIVATE KEY)");
        }
        String body = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(body);
        var key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        if (!(key instanceof RSAPrivateCrtKey rsaKey)) {
            throw new IllegalStateException("JWT signing key must be an RSA private key");
        }
        return rsaKey;
    }
}
