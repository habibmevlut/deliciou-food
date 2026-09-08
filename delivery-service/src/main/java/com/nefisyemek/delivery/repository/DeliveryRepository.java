package com.nefisyemek.delivery.repository;
import com.nefisyemek.delivery.domain.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {}
