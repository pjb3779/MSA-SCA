package buaa.msasca.sca.infra.persistence.jpa.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import buaa.msasca.sca.infra.persistence.jpa.entity.unifiedresult.UnifiedTaintRecordEntity;

public interface UnifiedTaintRecordJpaRepository extends JpaRepository<UnifiedTaintRecordEntity, Long> {

  @Query("""
      select u.id
        from UnifiedTaintRecordEntity u
       where (u.codeqlFinding is not null and u.codeqlFinding.codeqlRun.toolRun.analysisRun.id = :analysisRunId)
          or (u.mscanFinding is not null and u.mscanFinding.mscanRun.toolRun.analysisRun.id = :analysisRunId)
      """)
  List<Long> findIdsByAnalysisRunId(@Param("analysisRunId") Long analysisRunId);
}

