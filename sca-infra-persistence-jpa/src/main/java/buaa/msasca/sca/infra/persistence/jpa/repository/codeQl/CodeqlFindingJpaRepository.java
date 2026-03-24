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

    /**
     * analysis_run에 속한 CodeQL finding 전체 조회.
     * Agent Sanitizer 파이프라인에서 SARIF 결과를 참조할 때 사용.
     */
    @Query("""
        select f from CodeqlFindingEntity f
        join fetch f.codeqlRun cr
        join fetch cr.toolRun tr
        join fetch cr.serviceModule sm
        where tr.analysisRun.id = :analysisRunId
        order by f.id
        """)
    List<CodeqlFindingEntity> findByCodeqlRun_ToolRun_AnalysisRun_Id(
        @Param("analysisRunId") Long analysisRunId
    );

    void deleteByCodeqlRun_ToolRunId(Long toolRunId);
}