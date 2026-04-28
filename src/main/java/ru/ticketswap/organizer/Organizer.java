package ru.ticketswap.organizer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "organizers")
public class Organizer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "organizer_code", nullable = false, unique = true, length = 64)
    private String organizerCode;

    @Column(name = "api_key_hash", length = 255)
    private String apiKeyHash;

    @Column(name = "api_key_last4", length = 4)
    private String apiKeyLast4;

    @Column(name = "api_key_created_at")
    private Instant apiKeyCreatedAt;

    @Column(name = "contact_email", nullable = false, unique = true, length = 255)
    private String contactEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_mode", nullable = false, length = 32)
    private OrganizerVerificationMode verificationMode = OrganizerVerificationMode.EXTERNAL_API;

    @Column(nullable = false)
    private boolean banned = false;

    protected Organizer() {
    }

    public Organizer(String name, String organizerCode, String contactEmail) {
        this(name, organizerCode, contactEmail, OrganizerVerificationMode.EXTERNAL_API);
    }

    public Organizer(String name, String organizerCode, String contactEmail, OrganizerVerificationMode verificationMode) {
        this.name = name;
        this.organizerCode = organizerCode;
        this.contactEmail = contactEmail;
        this.verificationMode = verificationMode == null ? OrganizerVerificationMode.EXTERNAL_API : verificationMode;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOrganizerCode() {
        return organizerCode;
    }

    public void setOrganizerCode(String organizerCode) {
        this.organizerCode = organizerCode;
    }

    /**
     * @deprecated use getOrganizerCode() for public routing; API keys are stored only as hashes.
     */
    @Deprecated
    public String getApiKey() {
        return organizerCode;
    }

    /**
     * @deprecated use setOrganizerCode() for public routing; API keys are stored only as hashes.
     */
    @Deprecated
    public void setApiKey(String apiKey) {
        this.organizerCode = apiKey;
    }

    public String getApiKeyHash() {
        return apiKeyHash;
    }

    public void setApiKeyHash(String apiKeyHash) {
        this.apiKeyHash = apiKeyHash;
    }

    public String getApiKeyLast4() {
        return apiKeyLast4;
    }

    public void setApiKeyLast4(String apiKeyLast4) {
        this.apiKeyLast4 = apiKeyLast4;
    }

    public Instant getApiKeyCreatedAt() {
        return apiKeyCreatedAt;
    }

    public void setApiKeyCreatedAt(Instant apiKeyCreatedAt) {
        this.apiKeyCreatedAt = apiKeyCreatedAt;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public OrganizerVerificationMode getVerificationMode() {
        return verificationMode;
    }

    public void setVerificationMode(OrganizerVerificationMode verificationMode) {
        this.verificationMode = verificationMode == null ? OrganizerVerificationMode.EXTERNAL_API : verificationMode;
    }

    public boolean isExternalApi() {
        return verificationMode == OrganizerVerificationMode.EXTERNAL_API;
    }

    public boolean isManual() {
        return verificationMode == OrganizerVerificationMode.MANUAL;
    }

    public boolean isBanned() {
        return banned;
    }

    public void setBanned(boolean banned) {
        this.banned = banned;
    }
}
