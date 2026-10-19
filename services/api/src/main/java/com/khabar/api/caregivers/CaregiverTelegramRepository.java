package com.khabar.api.caregivers;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaregiverTelegramRepository extends JpaRepository<CaregiverTelegram, UUID> {

    Optional<CaregiverTelegram> findByLinkCodeHash(String linkCodeHash);

    List<CaregiverTelegram> findByChatIndex(String chatIndex);
}
