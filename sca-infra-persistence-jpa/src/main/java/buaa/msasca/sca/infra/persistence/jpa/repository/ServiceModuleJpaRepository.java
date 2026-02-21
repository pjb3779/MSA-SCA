package buaa.msasca.sca.infra.persistence.jpa.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import buaa.msasca.sca.infra.persistence.jpa.entity.project.ServiceModuleEntity;

public interface ServiceModuleJpaRepository extends JpaRepository<ServiceModuleEntity, Long> {
    /**
     * (project_version_id, name) 기준으로 service_module을 조회한다.
     *
     * @param projectVersionId project_version ID
     * @param name module name
     * @return optional entity
     */
    Optional<ServiceModuleEntity> findByProjectVersion_IdAndName(Long projectVersionId, String name);

    List<ServiceModuleEntity> findAllByProjectVersionId(Long projectVersionId);
}