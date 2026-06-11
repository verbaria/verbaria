package org.verbaria.server.ui.vaadin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import java.util.List;

import com.vaadin.flow.component.avatar.Avatar;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.zanata.model.HApplicationConfiguration;
import org.verbaria.server.headless.repository.AccountRepository;
import org.verbaria.server.headless.repository.ApplicationConfigurationRepository;
import org.verbaria.server.headless.settings.ServerSetting;

/**
 * Single source for user avatars across the UI (profile menu, activity feed,
 * translation history, …). Produces a Vaadin {@link Avatar} that shows the
 * Gravatar image when an email is on file and falls back to the initials of the
 * display name otherwise.
 */
@Component
public class AvatarService {

    private final ApplicationConfigurationRepository configRepository;
    private final AccountRepository accountRepository;

    public AvatarService(ApplicationConfigurationRepository configRepository,
            AccountRepository accountRepository) {
        this.configRepository = configRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Avatar for a username — resolves the person's display name + email so the
     * caller (e.g. the navbar) doesn't have to. Falls back to the username when
     * there's no person on file.
     */
    @Transactional(readOnly = true)
    public Avatar avatarForUsername(String username, int sizePx) {
        String name = username;
        String email = null;
        if (username != null) {
            List<Object[]> rows = accountRepository.findPersonNameEmail(username);
            if (!rows.isEmpty()) {
                Object[] r = rows.get(0);
                if (r[0] != null) {
                    name = (String) r[0];
                }
                email = (String) r[1];
            }
        }
        return avatar(name, email, sizePx);
    }

    /** Avatar sized to {@code sizePx}; initials fallback when no email. */
    public Avatar avatar(String displayName, String email, int sizePx) {
        Avatar avatar = new Avatar(displayName == null ? "" : displayName);
        avatar.setHeight(sizePx + "px");
        avatar.setWidth(sizePx + "px");
        // Request 2x pixels so the image stays crisp on HiDPI displays.
        String url = gravatarUrl(email, sizePx * 2);
        if (url != null) {
            avatar.setImage(url);
        }
        return avatar;
    }

    /** Gravatar URL for {@code email} at {@code sizePx}, or {@code null}. */
    public String gravatarUrl(String email, int sizePx) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String hash = md5LowerHex(email.trim().toLowerCase(Locale.ROOT));
        if (hash == null) {
            return null;
        }
        return "https://www.gravatar.com/avatar/" + hash
                + "?s=" + sizePx + "&d=identicon&r=" + gravatarRating();
    }

    /**
     * Configured Gravatar max rating ({@code gravatar.rating}); defaults to
     * {@code g}. Restricted to Gravatar's allowed values so a bad config can't
     * produce a broken URL.
     */
    private String gravatarRating() {
        return configRepository
                .findByKey(ServerSetting.GRAVATAR_RATING.key())
                .map(HApplicationConfiguration::getValue)
                .map(v -> v == null ? "" : v.trim().toLowerCase(Locale.ROOT))
                .filter(v -> v.equals("g") || v.equals("pg")
                        || v.equals("r") || v.equals("x"))
                .orElse("g");
    }

    private static String md5LowerHex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(
                    md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
