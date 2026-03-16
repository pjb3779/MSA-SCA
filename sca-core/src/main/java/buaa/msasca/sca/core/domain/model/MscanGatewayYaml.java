package buaa.msasca.sca.core.domain.model;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

import buaa.msasca.sca.core.domain.enums.GatewayYamlProvidedBy;
import buaa.msasca.sca.core.domain.enums.GatewayYamlStatus;

public record MscanGatewayYaml(
    Long id,
    Long projectVersionId,
    GatewayYamlStatus status,
    GatewayYamlProvidedBy providedBy, // MISSING일 때는 null 가능
    String storagePath,               
    String sha256,                    
    String originalFilename,          // 업로드면 파일명
    String cacheRelPath,              // 고정 위치 ex) ".msasca/mscan/gateway.yml"
    JsonNode metadataJson,
    Instant createdAt,
    Instant updatedAt
) {}