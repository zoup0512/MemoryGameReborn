package com.memorygames.pk.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUuid(String uuid);

    Optional<UserAccount> findByToken(String token);
}
