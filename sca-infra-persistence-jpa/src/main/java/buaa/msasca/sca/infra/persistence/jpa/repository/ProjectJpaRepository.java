package buaa.msasca.sca.infra.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectEntity;

public interface ProjectJpaRepository extends JpaRepository<ProjectEntity, Long> {}