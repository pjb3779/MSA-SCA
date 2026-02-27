package buaa.msasca.sca.infra.persistence.jpa.repository.codeQl;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import buaa.msasca.sca.infra.persistence.jpa.entity.result.codeql.CodeqlFindingLocationEntity;

public interface CodeqlFindingLocationJpaRepository extends JpaRepository<CodeqlFindingLocationEntity, Long> {
    
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CodeqlFindingLocationEntity l where l.finding.id in ?1")
    void deleteByFindingIds(List<Long> findingIds);
}