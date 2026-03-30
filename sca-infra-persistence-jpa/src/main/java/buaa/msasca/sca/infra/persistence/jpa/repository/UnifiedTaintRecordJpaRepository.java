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
        left join u.codeqlFinding cf
        left join cf.codeqlRun crr
        left join crr.toolRun ctr
        left join ctr.analysisRun ar1
        left join u.mscanFinding mf
        left join mf.mscanRun mrr
        left join mrr.toolRun mtr
        left join mtr.analysisRun ar2
       where ar1.id = :analysisRunId
          or ar2.id = :analysisRunId
      """)
  List<Long> findIdsByAnalysisRunId(@Param("analysisRunId") Long analysisRunId);
}

