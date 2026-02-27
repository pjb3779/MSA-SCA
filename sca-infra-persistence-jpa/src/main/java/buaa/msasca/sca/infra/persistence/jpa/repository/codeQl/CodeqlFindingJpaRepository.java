package buaa.msasca.sca.infra.persistence.jpa.repository.codeQl;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import buaa.msasca.sca.infra.persistence.jpa.entity.result.codeql.CodeqlFindingEntity;

public interface CodeqlFindingJpaRepository extends JpaRepository<CodeqlFindingEntity, Long> {
    
    @Query("""
        select f.id
            from CodeqlFindingEntity f
        where f.codeqlRun.toolRunId = :toolRunId
        """)
    List<Long> findIdsByToolRunId(@Param("toolRunId") Long toolRunId);

    void deleteByCodeqlRun_ToolRunId(Long toolRunId);
}