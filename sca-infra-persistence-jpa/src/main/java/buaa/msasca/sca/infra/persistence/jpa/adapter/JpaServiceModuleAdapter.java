package buaa.msasca.sca.infra.persistence.jpa.adapter;

import java.util.List;

import buaa.msasca.sca.core.domain.model.ServiceModule;
import buaa.msasca.sca.core.port.out.persistence.ServiceModulePort;
import buaa.msasca.sca.infra.persistence.jpa.mapper.ServiceModuleMapper;
import buaa.msasca.sca.infra.persistence.jpa.repository.ServiceModuleJpaRepository;

public class JpaServiceModuleAdapter implements ServiceModulePort {

    private final ServiceModuleJpaRepository repo;
    private final ServiceModuleMapper mapper;

    public JpaServiceModuleAdapter(ServiceModuleJpaRepository repo, ServiceModuleMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @Override
    public List<ServiceModule> findByProjectVersionId(Long projectVersionId) {
        return repo.findAllByProjectVersionId(projectVersionId).stream()
            .map(mapper::toDomain)
            .toList();
    }
}