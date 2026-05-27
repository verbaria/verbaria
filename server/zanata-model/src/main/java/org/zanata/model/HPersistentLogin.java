package org.zanata.model;

import java.io.Serializable;
import java.util.Date;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

/**
 * Persistent remember-me token. Schema matches Spring Security's
 * {@link org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl}
 * default — column names and the {@code persistent_logins} table name are not
 * negotiable since that class uses raw SQL with hardcoded identifiers.
 *
 * <p>Hibernate auto-creates the table on startup via {@code ddl-auto:update}.
 * Spring Security reads/writes it via {@code JdbcTokenRepositoryImpl} (raw
 * JdbcTemplate, not JPA).</p>
 */
@Entity
@Table(name = "persistent_logins")
public class HPersistentLogin implements Serializable {

    @Id
    @Column(name = "series", length = 64, nullable = false)
    private String series;

    @Column(name = "username", length = 64, nullable = false)
    private String username;

    @Column(name = "token", length = 64, nullable = false)
    private String token;

    @Column(name = "last_used", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastUsed;

    public String getSeries() { return series; }
    public void setSeries(String series) { this.series = series; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public Date getLastUsed() { return lastUsed; }
    public void setLastUsed(Date lastUsed) { this.lastUsed = lastUsed; }
}
