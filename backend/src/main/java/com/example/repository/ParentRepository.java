// File: C:\Projects\EchoU\backend\src\main\java\com\example\repository\ParentRepository.java (new file)

package com.example.repository;

import com.example.entity.Parent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParentRepository extends JpaRepository<Parent, Long> {
    Parent findByUsername(String username);
}