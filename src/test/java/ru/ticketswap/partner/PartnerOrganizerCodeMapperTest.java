package ru.ticketswap.partner;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartnerOrganizerCodeMapperTest {

    private final PartnerOrganizerCodeMapper mapper = new PartnerOrganizerCodeMapper();

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
        assertEquals(Optional.of("org1"), mapper.resolveOrganizerCode("org1"));
        assertEquals(Optional.of("org1"), mapper.resolveOrganizerCode("  ORG1  "));
        assertEquals(Optional.of("org2"), mapper.resolveOrganizerCode("org2"));
    }

    @Test
    void resolveOrganizerCodeReturnsEmptyForUnsupportedOrganizers() {
        assertEquals(Optional.empty(), mapper.resolveOrganizerCode(null));
        assertEquals(Optional.empty(), mapper.resolveOrganizerCode("   "));
        assertEquals(Optional.empty(), mapper.resolveOrganizerCode("org3"));
        assertEquals(Optional.empty(), mapper.resolveOrganizerCode("Amazing Organizer"));
    }

    @Test
    void isSupportedOrganizerReflectsMappingResult() {
        assertTrue(mapper.isSupportedOrganizer(" org1 "));
        assertTrue(mapper.isSupportedOrganizer("ORG2"));
        assertFalse(mapper.isSupportedOrganizer("org3"));
        assertFalse(mapper.isSupportedOrganizer(" "));
    }
}
