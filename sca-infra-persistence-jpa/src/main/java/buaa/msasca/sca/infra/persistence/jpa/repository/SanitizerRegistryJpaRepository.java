package buaa.msasca.sca.infra.persistence.jpa.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.result.sanitizer.SanitizerRegistryEntity;

public interface SanitizerRegistryJpaRepository extends JpaRepository<SanitizerRegistryEntity, Long> {

    Optional<SanitizerRegistryEntity> findByProjectAndMethodSignature(
        ProjectEntity project,
        String methodSignature
    );
}
