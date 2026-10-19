package com.khabar.api.access;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FamilyGroupRepository extends JpaRepository<FamilyGroup, UUID> {

    Optional<FamilyGroup> findByLinkCodeHash(String linkCodeHash);

    List<FamilyGroup> findByChatIndex(String chatIndex);
}
