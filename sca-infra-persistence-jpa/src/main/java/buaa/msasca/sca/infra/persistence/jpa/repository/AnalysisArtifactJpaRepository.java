package buaa.msasca.sca.infra.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import buaa.msasca.sca.infra.persistence.jpa.entity.artifact.AnalysisArtifactEntity;

public interface AnalysisArtifactJpaRepository extends JpaRepository<AnalysisArtifactEntity, Long> {}