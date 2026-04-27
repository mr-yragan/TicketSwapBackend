package ru.ticketswap.organizer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "organizers")
public class Organizer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "api_key", unique = true, length = 64)
    private String apiKey;

    @Column(name = "contact_email", nullable = false, unique = true, length = 255)
    private String contactEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_mode", nullable = false, length = 32)
    private OrganizerVerificationMode verificationMode = OrganizerVerificationMode.EXTERNAL_API;

    @Column(nullable = false)
    private boolean banned = false;

    protected Organizer() {
    }

    public Organizer(String name, String apiKey, String contactEmail) {
        this(name, apiKey, contactEmail, OrganizerVerificationMode.EXTERNAL_API);
    }

    public Organizer(String name, String apiKey, String contactEmail, OrganizerVerificationMode verificationMode) {
        this.name = name;
        this.apiKey = apiKey;
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

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
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
