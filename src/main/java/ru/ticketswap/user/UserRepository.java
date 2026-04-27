package ru.ticketswap.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findAllByEmailIgnoreCase(String email);

    Optional<User> findByLogin(String login);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByLogin(String login);
}
