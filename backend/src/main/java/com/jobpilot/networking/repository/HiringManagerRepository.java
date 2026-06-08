package com.jobpilot.networking.repository;

import com.jobpilot.networking.entity.HiringManager;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HiringManagerRepository extends JpaRepository<HiringManager, UUID> {
    
    @Query("SELECT hm FROM HiringManager hm WHERE " +
           "(:domain IS NULL OR hm.companyDomain = :domain) AND " +
           "(:function IS NULL OR hm.function = :function) AND " +
           "(:level IS NULL OR hm.managementLevel = :level) AND " +
           "(:country IS NULL OR hm.country = :country)")
    List<HiringManager> search(@Param("domain") String domain, 
                               @Param("function") String function, 
                               @Param("level") String level, 
                               @Param("country") String country);

    List<HiringManager> findByCompanyDomain(String companyDomain);
}
