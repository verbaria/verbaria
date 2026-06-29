package org.verbaria.server.headless.web.rest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.verbaria.server.headless.changelog.ChangelogService;
import org.verbaria.server.headless.changelog.LockChangelog;
import org.verbaria.server.headless.changelog.VerbariaLock;
import org.verbaria.server.headless.changelog.VerbariaLockReaderWriter;
import org.verbaria.server.headless.repository.AccountRepository;

@RestController
@RequestMapping("/rest")
public class ChangelogController {

    private final AccountRepository accountRepository;
    private final ChangelogService changelogService;

    public ChangelogController(AccountRepository accountRepository,
            ChangelogService changelogService) {
        this.accountRepository = accountRepository;
        this.changelogService = changelogService;
    }

    @PostMapping(value = "/changelog",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> changelog(
            @RequestParam(value = "old", required = false) MultipartFile oldLock,
            @RequestParam("new") MultipartFile newLock,
            @RequestParam(value = "format", required = false) String format,
            @RequestParam(value = "excludeAuthors", required = false)
                    List<String> excludeAuthors) {
        if (currentUserName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        LockChangelog.Format fmt;
        try {
            fmt = LockChangelog.Format.parse(format);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
        try {
            VerbariaLock before = parse(oldLock);
            VerbariaLock after = parse(newLock);
            if (after == null) {
                return ResponseEntity.badRequest().body("Missing new lock");
            }
            String rendered = changelogService.render(before, after, fmt,
                    split(excludeAuthors));
            return ResponseEntity.ok()
                    .contentType(new MediaType(MediaType.TEXT_PLAIN,
                            StandardCharsets.UTF_8))
                    .body(rendered);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(
                    e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private static VerbariaLock parse(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }
        return VerbariaLockReaderWriter.parse(file.getBytes());
    }

    private static List<String> split(List<String> raw) {
        List<String> out = new ArrayList<>();
        if (raw != null) {
            for (String entry : raw) {
                if (entry == null) {
                    continue;
                }
                for (String part : entry.split("[,\\s]+")) {
                    if (!part.isBlank()) {
                        out.add(part.trim());
                    }
                }
            }
        }
        return out;
    }

    private String currentUserName() {
        Authentication auth =
                SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        String name = auth.getName();
        if (name == null || "anonymousUser".equals(name)) {
            return null;
        }
        return accountRepository.findByUsername(name).map(a -> name).orElse(null);
    }
}
