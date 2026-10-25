package com.example.jwtgateway.auth;

import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AccountStoreTest {
    private static final String PYTHON_ARGON2ID_HASH =
            "$argon2id$v=19$m=65536,t=3,p=1$MDEyMzQ1Njc4OWFiY2RlZg$RilFZ9JhPjv45LCaxYQx0EV4EI5/cqp7uPW2nAC89Zk";

    @TempDir
    Path tempDir;

    @Test
    void authenticatesPythonGeneratedArgon2idHashAndRejectsWrongPassword() throws Exception {
        Path accountsFile = tempDir.resolve("accounts.json");
        Map<String, Object> account = Map.of(
                "username", "alice",
                "passwordHash", PYTHON_ARGON2ID_HASH,
                "audiences", List.of("component-a"),
                "scopes", List.of("component-a.read"));
        new JsonMapper().writeValue(accountsFile.toFile(), Map.of("accounts", List.of(account)));

        AccountStore store = new AccountStore(
                accountsFile,
                new JsonMapper(),
                Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());

        AccountStore.StoredAccount authenticated = store.authenticate("alice", "fixture-password");
        assertNotNull(authenticated);
        assertEquals(List.of("component-a"), authenticated.audiences());
        assertNull(store.authenticate("alice", "wrong-password"));
        assertNull(store.authenticate("missing-user", "wrong-password"));
    }

    @Test
    void rejectsGroupOrWorldWritableAccountFiles() throws Exception {
        Path accountsFile = tempDir.resolve("writable-accounts.json");
        Files.writeString(accountsFile, "{\"accounts\":[]}");
        try {
            var permissions = Files.getPosixFilePermissions(accountsFile);
            permissions.add(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE);
            Files.setPosixFilePermissions(accountsFile, permissions);
        } catch (UnsupportedOperationException ignored) {
            return;
        }

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new AccountStore(
                        accountsFile,
                        new JsonMapper(),
                        Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    }
}
