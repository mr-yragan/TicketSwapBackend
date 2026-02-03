package ru.ticketswap.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByLogin(String login);

    boolean existsByEmail(String email);

    boolean existsByLogin(String login);
}
