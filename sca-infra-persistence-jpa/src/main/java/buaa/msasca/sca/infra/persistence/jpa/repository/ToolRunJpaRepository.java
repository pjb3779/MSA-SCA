package buaa.msasca.sca.infra.persistence.jpa.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import buaa.msasca.sca.infra.persistence.jpa.entity.run.ToolRunEntity;

public interface ToolRunJpaRepository extends JpaRepository<ToolRunEntity, Long> {
  List<ToolRunEntity> findByAnalysisRun_Id(Long analysisRunId);
}