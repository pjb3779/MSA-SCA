package buaa.msasca.sca.infra.persistence.jpa.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import buaa.msasca.sca.infra.persistence.jpa.entity.unifiedresult.TaintStepEntity;

public interface TaintStepJpaRepository extends JpaRepository<TaintStepEntity, Long> {
  void deleteByRecord_IdIn(List<Long> recordIds);
}

