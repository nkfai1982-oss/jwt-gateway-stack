package com.example.jwtgateway.auth;

import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public final class AccountStore {
    private static final Pattern ACCOUNT_NAME = Pattern.compile("[A-Za-z0-9._@-]{1,128}");
    private static final Pattern SCOPE = Pattern.compile("[A-Za-z0-9:._/-]{1,128}");

    private final Map<String, StoredAccount> accounts;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    public AccountStore(
            @Value("${auth.accounts-file}") Path file,
            JsonMapper jsonMapper,
            PasswordEncoder passwordEncoder) throws IOException {
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new IllegalStateException("Static accounts file is missing or unreadable: " + file);
        }
        try {
            var posix = Files.getPosixFilePermissions(file);
            if (posix.contains(java.nio.file.attribute.PosixFilePermission.GROUP_WRITE)
                    || posix.contains(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE)) {
                throw new IllegalStateException("Static accounts file must not be group- or world-writable");
            }
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX filesystems are allowed; deployment must still protect this file.
        }

        AccountsDocument document = jsonMapper.readValue(file.toFile(), AccountsDocument.class);
        if (document == null || document.accounts() == null || document.accounts().isEmpty()) {
            throw new IllegalStateException("Static accounts file must contain at least one account");
        }
        Map<String, StoredAccount> loaded = new HashMap<>();
        for (StoredAccount account : document.accounts()) {
            validate(account);
            if (loaded.putIfAbsent(account.username(), account) != null) {
                throw new IllegalStateException("Duplicate username in static accounts file");
            }
        }
        this.accounts = Map.copyOf(loaded);
    }

    public StoredAccount authenticate(String username, String password) {
        StoredAccount account = accounts.get(username);
        String hash = account == null ? dummyPasswordHash : account.passwordHash();
        boolean matches = passwordEncoder.matches(password, hash);
        return account != null && matches ? account : null;
    }

    private static void validate(StoredAccount account) {
        if (account == null
                || account.username() == null
                || !ACCOUNT_NAME.matcher(account.username()).matches()
                || account.passwordHash() == null
                || !account.passwordHash().startsWith("$argon2id$")
                || account.audiences() == null
                || account.audiences().isEmpty()
                || account.scopes() == null
                || account.scopes().isEmpty()) {
            throw new IllegalStateException("Each static account needs a username, Argon2id hash, audiences, and scopes");
        }
        if (account.audiences().stream().anyMatch(value -> value == null || !SCOPE.matcher(value).matches())
                || account.scopes().stream().anyMatch(value -> value == null || !SCOPE.matcher(value).matches())) {
            throw new IllegalStateException("Audience and scope values must use the documented safe identifier format");
        }
    }

    public record AccountsDocument(List<StoredAccount> accounts) {}
    public record StoredAccount(String username, String passwordHash, List<String> audiences, List<String> scopes) {}
}
