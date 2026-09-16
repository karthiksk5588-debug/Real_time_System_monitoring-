package com.neurosys.backend.repository;

import com.neurosys.backend.entity.SoftwarePackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SoftwarePackageRepository extends JpaRepository<SoftwarePackage, String> {
    Optional<SoftwarePackage> findByNameAndVersion(String name, String version);
    List<SoftwarePackage> findByActiveTrue();
    boolean existsByNameAndVersion(String name, String version);
}
