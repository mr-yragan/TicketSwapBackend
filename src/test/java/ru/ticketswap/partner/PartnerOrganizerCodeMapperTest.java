package ru.ticketswap.partner;

import org.junit.jupiter.api.Test;
import ru.ticketswap.organizer.Organizer;
import ru.ticketswap.organizer.OrganizerRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PartnerOrganizerCodeMapperTest {

    private final OrganizerRepository organizerRepository = mock(OrganizerRepository.class);
    private final PartnerOrganizerCodeMapper mapper = new PartnerOrganizerCodeMapper(organizerRepository);

    @Test
    void normalizeOrganizerNameTrimsAndLowercasesValue() {
        assertEquals("org1", mapper.normalizeOrganizerName("  OrG1  "));
        assertEquals("org2", mapper.normalizeOrganizerName("\tORG2\n"));
    }

    @Test
    void normalizeOrganizerNameReturnsNullForNullAndBlankValues() {
        assertNull(mapper.normalizeOrganizerName(null));
        assertNull(mapper.normalizeOrganizerName(""));
        assertNull(mapper.normalizeOrganizerName("   "));
    }

    @Test
    void resolveOrganizerCodeReturnsMappedCodesForSupportedOrganizers() {
        when(organizerRepository.findByApiKeyIgnoreCase("org1"))
                .thenReturn(Optional.of(new Organizer("Организатор 1", "org1", "org1@example.com")));
        when(organizerRepository.findByApiKeyIgnoreCase("org2"))
                .thenReturn(Optional.of(new Organizer("Организатор 2", "org2", "org2@example.com")));

        assertEquals(Optional.of("org1"), mapper.resolveOrganizerCode("org1"));
        assertEquals(Optional.of("org1"), mapper.resolveOrganizerCode("  ORG1  "));
        assertEquals(Optional.of("org2"), mapper.resolveOrganizerCode("org2"));
    }

    @Test
    void resolveOrganizerCodeReturnsEmptyForUnsupportedOrganizers() {
        when(organizerRepository.findByApiKeyIgnoreCase("org3")).thenReturn(Optional.empty());
        when(organizerRepository.findByApiKeyIgnoreCase("неизвестный организатор")).thenReturn(Optional.empty());

        assertEquals(Optional.empty(), mapper.resolveOrganizerCode(null));
        assertEquals(Optional.empty(), mapper.resolveOrganizerCode("   "));
        assertEquals(Optional.empty(), mapper.resolveOrganizerCode("org3"));
        assertEquals(Optional.empty(), mapper.resolveOrganizerCode("Неизвестный организатор"));
    }

    @Test
    void isSupportedOrganizerReflectsMappingResult() {
        when(organizerRepository.findByApiKeyIgnoreCase("org1"))
                .thenReturn(Optional.of(new Organizer("Организатор 1", "org1", "org1@example.com")));
        when(organizerRepository.findByApiKeyIgnoreCase("org2"))
                .thenReturn(Optional.of(new Organizer("Организатор 2", "org2", "org2@example.com")));
        when(organizerRepository.findByApiKeyIgnoreCase("org3")).thenReturn(Optional.empty());

        assertTrue(mapper.isSupportedOrganizer(" org1 "));
        assertTrue(mapper.isSupportedOrganizer("ORG2"));
        assertFalse(mapper.isSupportedOrganizer("org3"));
        assertFalse(mapper.isSupportedOrganizer(" "));
    }
}