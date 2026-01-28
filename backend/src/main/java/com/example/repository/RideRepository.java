// backend/src/main/java/com/example/repository/RideRepository.java (Added findByH3IndexInAndStatus)
package com.example.repository;

import com.example.entity.Ride;
import com.example.entity.Ride.RideStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RideRepository extends JpaRepository<Ride, Long> {
    List<Ride> findByStatus(Ride.RideStatus status);
    List<Ride> findByUserId(Long userId);  // Added for user-specific rides
    List<Ride> findByH3IndexAndStatus(String h3Index, Ride.RideStatus status);
    List<Ride> findByH3IndexInAndStatusAndIdNot(List<String> h3Indices, Ride.RideStatus status, Long excludedId);
    List<Ride> findByH3IndexInAndStatus(List<String> h3Indices, Ride.RideStatus pending);
    List<Ride> findByUserIdAndStatus(Long userId, RideStatus status);
    @Query("SELECT r FROM Ride r WHERE r.status = :status AND r.createdAt < :threshold")
List<Ride> findByStatusAndCreatedAtBefore(@Param("status") RideStatus status, @Param("threshold") LocalDateTime threshold);
}