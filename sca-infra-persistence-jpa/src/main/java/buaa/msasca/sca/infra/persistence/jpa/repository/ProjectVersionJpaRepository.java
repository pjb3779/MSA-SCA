package buaa.msasca.sca.infra.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectVersionEntity;

public interface ProjectVersionJpaRepository extends JpaRepository<ProjectVersionEntity, Long> {}