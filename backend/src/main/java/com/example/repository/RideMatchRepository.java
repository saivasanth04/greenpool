// backend/src/main/java/com/example/repository/RideMatchRepository.java
package com.example.repository;

import com.example.entity.RideMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RideMatchRepository extends JpaRepository<RideMatch, Long> {
    List<RideMatch> findByRideId(Long rideId);
    void deleteByRideId(Long rideId);
}