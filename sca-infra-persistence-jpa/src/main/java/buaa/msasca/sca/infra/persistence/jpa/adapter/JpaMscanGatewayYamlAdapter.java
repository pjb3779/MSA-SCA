package buaa.msasca.sca.infra.persistence.jpa.adapter;

import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

import buaa.msasca.sca.core.domain.enums.GatewayYamlProvidedBy;
import buaa.msasca.sca.core.domain.model.MscanGatewayYaml;
import buaa.msasca.sca.core.port.out.persistence.MscanGatewayYamlCommandPort;
import buaa.msasca.sca.core.port.out.persistence.MscanGatewayYamlPort;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectVersionEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.result.mscan.MscanGatewayYamlEntity;
import buaa.msasca.sca.infra.persistence.jpa.mapper.MscanGatewayYamlMapper;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectVersionJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.Mscan.MscanGatewayYamlJpaRepository;


public class JpaMscanGatewayYamlAdapter implements MscanGatewayYamlPort, MscanGatewayYamlCommandPort {

    private final MscanGatewayYamlJpaRepository repo;
    private final ProjectVersionJpaRepository pvRepo;
    private final MscanGatewayYamlMapper mapper;

    public JpaMscanGatewayYamlAdapter(
        MscanGatewayYamlJpaRepository repo,
        ProjectVersionJpaRepository pvRepo,
        MscanGatewayYamlMapper mapper
    ) {
        this.repo = repo;
        this.pvRepo = pvRepo;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MscanGatewayYaml> findByProjectVersionId(Long projectVersionId) {
        return repo.findByProjectVersion_Id(projectVersionId).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public MscanGatewayYaml ensureMissing(Long projectVersionId, String cacheRelPath) {
        return repo.findByProjectVersion_Id(projectVersionId)
            .map(mapper::toDomain)
            .orElseGet(() -> {
            ProjectVersionEntity pv = pvRepo.findById(projectVersionId)
                .orElseThrow(() -> new IllegalArgumentException("project_version not found: " + projectVersionId));

            MscanGatewayYamlEntity created = MscanGatewayYamlEntity.missing(pv, cacheRelPath);
            return mapper.toDomain(repo.save(created));
            });
    }

    @Override
    @Transactional
    public MscanGatewayYaml upsertReady(
        Long projectVersionId,
        GatewayYamlProvidedBy providedBy,
        String storagePath,
        String sha256,
        String originalFilename,
        String cacheRelPath,
        JsonNode metadataJson
    ) {
        ProjectVersionEntity pv = pvRepo.findById(projectVersionId)
            .orElseThrow(() -> new IllegalArgumentException("project_version not found: " + projectVersionId));

        MscanGatewayYamlEntity e = repo.findByProjectVersion_Id(projectVersionId)
            .orElseGet(() -> MscanGatewayYamlEntity.missing(pv, cacheRelPath));

        e.markReady(providedBy, storagePath, sha256, originalFilename, metadataJson);
        return mapper.toDomain(repo.save(e));
    }
}