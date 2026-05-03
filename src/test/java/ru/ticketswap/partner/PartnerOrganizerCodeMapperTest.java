package ru.ticketswap.partner;

import org.junit.jupiter.api.Test;
import ru.ticketswap.organizer.Organizer;
import ru.ticketswap.organizer.OrganizerRepository;
import ru.ticketswap.organizer.OrganizerVerificationMode;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PartnerOrganizerCodeMapperTest {

    private final OrganizerRepository organizerRepository = mock(OrganizerRepository.class);
    private final PartnerOrganizerCodeMapper mapper = new PartnerOrganizerCodeMapper(organizerRepository);

    @Test
    void normalizeOrganizerNameTrimsValue() {
        assertEquals("org1", mapper.normalizeOrganizerName("  OrG1  "));
        assertEquals("org2", mapper.normalizeOrganizerName("\tORG2\n"));
    }

    @Test
    void normalizeOrganizerNameReturnsNullForNullAndEmptyStringForBlankValues() {
        assertNull(mapper.normalizeOrganizerName(null));
        assertEquals("", mapper.normalizeOrganizerName(""));
        assertEquals("", mapper.normalizeOrganizerName("   "));
    }

    @Test
    void resolveOrganizerCodeReturnsCodeByOrganizerCode() {
        Organizer org1 = new Organizer("Организатор 1", "org1", "org1@example.com");
        Organizer org2 = new Organizer("Организатор 2", "org2", "org2@example.com");

        when(organizerRepository.findByOrganizerCodeIgnoreCase("org1"))
                .thenReturn(Optional.of(org1));
        when(organizerRepository.findByOrganizerCodeIgnoreCase("ORG1"))
                .thenReturn(Optional.of(org1));
        when(organizerRepository.findByOrganizerCodeIgnoreCase("org2"))
                .thenReturn(Optional.of(org2));

        assertEquals(Optional.of("org1"), mapper.resolveOrganizerCode("org1"));
        assertEquals(Optional.of("org1"), mapper.resolveOrganizerCode("  ORG1  "));
        assertEquals(Optional.of("org2"), mapper.resolveOrganizerCode("org2"));
    }

    @Test
    void resolveOrganizerCodeReturnsCodeByOrganizerNameIfCodeNotFound() {
        Organizer organizer = new Organizer("Организатор 1", "org1", "org1@example.com");

        when(organizerRepository.findByOrganizerCodeIgnoreCase("Организатор 1"))
                .thenReturn(Optional.empty());
        when(organizerRepository.findByNameIgnoreCase("Организатор 1"))
                .thenReturn(Optional.of(organizer));

        assertEquals(Optional.of("org1"), mapper.resolveOrganizerCode("Организатор 1"));
    }

    @Test
    void resolveOrganizerCodeReturnsEmptyForNullAndBlankValues() {
        assertEquals(Optional.empty(), mapper.resolveOrganizerCode(null));
        assertEquals(Optional.empty(), mapper.resolveOrganizerCode(""));
        assertEquals(Optional.empty(), mapper.resolveOrganizerCode("   "));
    }

    @Test
    void resolveOrganizerCodeReturnsEmptyForUnknownOrganizer() {
        when(organizerRepository.findByOrganizerCodeIgnoreCase("unknown"))
                .thenReturn(Optional.empty());
        when(organizerRepository.findByNameIgnoreCase("unknown"))
                .thenReturn(Optional.empty());

        assertEquals(Optional.empty(), mapper.resolveOrganizerCode("unknown"));
    }

    @Test
    void resolveOrganizerCodeReturnsEmptyForBannedOrganizer() {
        Organizer organizer = new Organizer("Организатор 1", "org1", "org1@example.com");
        organizer.setBanned(true);

        when(organizerRepository.findByOrganizerCodeIgnoreCase("org1"))
                .thenReturn(Optional.of(organizer));

        assertEquals(Optional.empty(), mapper.resolveOrganizerCode("org1"));
    }

    @Test
    void resolveOrganizerCodeReturnsEmptyForManualOrganizer() {
        Organizer organizer = new Organizer("Организатор 1", "org1", "org1@example.com");
        organizer.setVerificationMode(OrganizerVerificationMode.MANUAL);

        when(organizerRepository.findByOrganizerCodeIgnoreCase("org1"))
                .thenReturn(Optional.of(organizer));

        assertEquals(Optional.empty(), mapper.resolveOrganizerCode("org1"));
    }
}