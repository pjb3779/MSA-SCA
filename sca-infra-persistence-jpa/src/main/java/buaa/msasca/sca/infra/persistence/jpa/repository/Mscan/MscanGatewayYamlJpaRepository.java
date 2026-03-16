package buaa.msasca.sca.infra.persistence.jpa.repository.Mscan;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import buaa.msasca.sca.infra.persistence.jpa.entity.result.mscan.MscanGatewayYamlEntity;

public interface MscanGatewayYamlJpaRepository extends JpaRepository<MscanGatewayYamlEntity, Long> {
    Optional<MscanGatewayYamlEntity> findByProjectVersion_Id(Long projectVersionId);
}