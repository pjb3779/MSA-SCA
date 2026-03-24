package buaa.msasca.sca.infra.persistence.jpa.repository.codeQl;

import java.util.List;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import buaa.msasca.sca.infra.persistence.jpa.entity.result.codeql.CodeqlFlowEntity;

public interface CodeqlFlowJpaRepository extends JpaRepository<CodeqlFlowEntity, Long> {

    /** finding에 속한 flow 목록 (step 로딩용) */
    List<CodeqlFlowEntity> findByFinding_IdOrderByFlowIndexAsc(Long findingId);

    
    @Query("select f.id from CodeqlFlowEntity f where f.finding.id in ?1")
    List<Long> findFlowIdsByFindingIds(List<Long> findingIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CodeqlFlowEntity f where f.finding.id in ?1")
    void deleteByFindingIds(List<Long> findingIds);
}