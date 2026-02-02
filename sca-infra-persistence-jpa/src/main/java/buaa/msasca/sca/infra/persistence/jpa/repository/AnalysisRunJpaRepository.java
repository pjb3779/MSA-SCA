package buaa.msasca.sca.infra.persistence.jpa.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import buaa.msasca.sca.core.domain.enums.RunStatus;
import buaa.msasca.sca.infra.persistence.jpa.entity.run.AnalysisRunEntity;

import java.util.List;

public interface AnalysisRunJpaRepository extends JpaRepository<AnalysisRunEntity, Long> {
  List<AnalysisRunEntity> findByStatusOrderByCreatedAtAsc(RunStatus status, Pageable pageable);
}