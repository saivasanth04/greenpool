// backend/src/main/java/com/example/repository/RideMatchRequestRepository.java (Fixed)
package com.example.repository;

import com.example.entity.Ride.RideStatus;
import com.example.entity.RideMatchRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RideMatchRequestRepository extends JpaRepository<RideMatchRequest, Long> {
    
    List<RideMatchRequest> findByToRideIdInAndStatus(List<Long> toRideIds, RideStatus pending);
    List<RideMatchRequest> findByFromRideIdInAndStatus(List<Long> fromRideIds, RideStatus status);
    RideMatchRequest findByFromRideIdAndToRideId(Long fromRideId, Long toRideId);
    List<RideMatchRequest> findByStatusAndStartConfirmedFromAndStartConfirmedTo(RideStatus status, boolean from, boolean to);
    List<RideMatchRequest> findByFromRideIdInAndStatusOrToRideIdInAndStatus(List<Long> rideIds, RideStatus status, List<Long> rideIds2, RideStatus status2);
    void deleteByFromRideIdOrToRideId(Long fromRideId, Long toRideId);
    
}
