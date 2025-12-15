package com.learning.learning.repository;



import com.learning.learning.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {

    List<Location> findByIsActiveTrue();

    List<Location> findByCity(String city);

    List<Location> findByState(String state);

    List<Location> findByIsActiveTrueOrderByLocationNameAsc();
}