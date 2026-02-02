package buaa.msasca.sca.infra.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import buaa.msasca.sca.infra.persistence.jpa.entity.tooldetail.AgentRunDetailEntity;

public interface AgentRunDetailJpaRepository extends JpaRepository<AgentRunDetailEntity, Long> {}