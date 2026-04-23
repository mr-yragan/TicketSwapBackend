package ru.ticketswap;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashGenerator {
    public static void main(String[] args) {
        String hash = new BCryptPasswordEncoder()
                .encode("ticketswap_organizer_test_password");

        System.out.println(hash);
    }
}

