package buaa.msasca.sca.core.application.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import buaa.msasca.sca.core.domain.enums.GatewayYamlProvidedBy;
import buaa.msasca.sca.core.domain.model.MscanGatewayYaml;
import buaa.msasca.sca.core.port.in.UploadMscanGatewayYamlUseCase;
import buaa.msasca.sca.core.port.out.persistence.MscanGatewayYamlCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCachePort;
import buaa.msasca.sca.core.port.out.tool.StoragePort;

public class UploadMscanGatewayYamlService implements UploadMscanGatewayYamlUseCase {

    public static final String DEFAULT_CACHE_REL_PATH = ".msasca/mscan/gateway.yml";

    private final StoragePort storagePort;
    private final MscanGatewayYamlCommandPort gatewayYamlCommandPort;
    private final ProjectVersionSourceCachePort sourceCachePort;

    private final ObjectMapper om = new ObjectMapper();

    public UploadMscanGatewayYamlService(
        StoragePort storagePort,
        MscanGatewayYamlCommandPort gatewayYamlCommandPort,
        ProjectVersionSourceCachePort sourceCachePort
    ) {
        this.storagePort = storagePort;
        this.gatewayYamlCommandPort = gatewayYamlCommandPort;
        this.sourceCachePort = sourceCachePort;
    }

    @Override
    public MscanGatewayYaml upload(Request req) {
        try {

            byte[] bytes = req.content().readAllBytes();
            String sha256 = sha256(bytes);

            String filename = (req.originalFilename() == null || req.originalFilename().isBlank())
                    ? "gateway.yml"
                    : req.originalFilename();

            String key = "projectVersions/" + req.projectVersionId() + "/mscan/gateway/" + filename;

            var stored = storagePort.put(key, new ByteArrayInputStream(bytes));

            if (stored == null || stored.uri() == null) {
                throw new IllegalStateException("Storage put failed for gateway.yml");
            }

            ObjectNode meta = om.createObjectNode();
            meta.put("originalFilename", filename);
            meta.put("size", bytes.length);
            meta.put("sha256", sha256);

            MscanGatewayYaml saved = gatewayYamlCommandPort.upsertReady(
                    req.projectVersionId(),
                    req.providedBy(),
                    stored.uri(),
                    sha256,
                    filename,
                    DEFAULT_CACHE_REL_PATH,
                    meta
            );

            // source cache 존재 시 즉시 재물질화
            sourceCachePort.findValidByProjectVersionId(req.projectVersionId()).ifPresent(cache -> {
                try {
                    Path target = Path.of(cache.storagePath())
                            .resolve(DEFAULT_CACHE_REL_PATH)
                            .normalize();

                    Files.createDirectories(target.getParent());
                    Files.write(target, bytes);

                } catch (Exception e) {
                    throw new IllegalStateException(
                            "Failed to materialize gateway.yml into source cache: " + cache.storagePath(),
                            e
                    );
                }
            });

            return saved;

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Upload gateway.yml failed: pv=" + req.projectVersionId(),
                    e
            );
        }
    }

    private String sha256(byte[] bytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(bytes);
        return HexFormat.of().formatHex(md.digest());
    }
}