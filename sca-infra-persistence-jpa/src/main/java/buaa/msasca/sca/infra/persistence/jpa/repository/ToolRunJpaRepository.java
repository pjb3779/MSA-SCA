package buaa.msasca.sca.infra.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import buaa.msasca.sca.infra.persistence.jpa.entity.run.ToolRunEntity;

public interface ToolRunJpaRepository extends JpaRepository<ToolRunEntity, Long> {
}