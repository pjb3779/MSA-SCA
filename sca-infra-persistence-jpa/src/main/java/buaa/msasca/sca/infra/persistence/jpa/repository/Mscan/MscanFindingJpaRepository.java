package buaa.msasca.sca.infra.persistence.jpa.repository.Mscan;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import buaa.msasca.sca.infra.persistence.jpa.entity.result.mscan.MscanFindingEntity;

public interface MscanFindingJpaRepository extends JpaRepository<MscanFindingEntity, Long> {

    @Query("""
        select f.id
          from MscanFindingEntity f
         where f.mscanRun.toolRunId = :toolRunId
        """)
    List<Long> findIdsByToolRunId(@Param("toolRunId") Long toolRunId);

    void deleteByMscanRun_ToolRunId(Long toolRunId);

    @Query("""
        select f
          from MscanFindingEntity f
         where f.mscanRun.toolRun.analysisRun.id = :analysisRunId
         order by f.flowIndex asc
        """)
    List<MscanFindingEntity> findByAnalysisRunId(@Param("analysisRunId") Long analysisRunId);
}

