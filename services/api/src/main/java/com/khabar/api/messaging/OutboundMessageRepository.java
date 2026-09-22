package com.khabar.api.messaging;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboundMessageRepository extends JpaRepository<OutboundMessage, Long> {

    List<OutboundMessage> findTop50ByOrderByIdDesc();
}
