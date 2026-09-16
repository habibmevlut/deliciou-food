package com.nefisyemek.payment.repository;

import com.nefisyemek.payment.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    List<ProcessedEvent> findTop50ByOrderByProcessedAtDesc();
}
