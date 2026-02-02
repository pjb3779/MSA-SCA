package buaa.msasca.sca.infra.persistence.jpa.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import buaa.msasca.sca.infra.persistence.jpa.entity.project.ServiceModuleEntity;

public interface ServiceModuleJpaRepository extends JpaRepository<ServiceModuleEntity, Long> {
    List<ServiceModuleEntity> findByProjectVersion_Id(Long projectVersionId);
}