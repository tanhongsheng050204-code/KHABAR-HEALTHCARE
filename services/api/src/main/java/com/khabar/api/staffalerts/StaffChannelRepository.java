package com.khabar.api.staffalerts;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffChannelRepository extends JpaRepository<StaffChannel, UUID> {

    Optional<StaffChannel> findByLinkCodeHash(String linkCodeHash);

    List<StaffChannel> findByAlertsOnTrue();
}
