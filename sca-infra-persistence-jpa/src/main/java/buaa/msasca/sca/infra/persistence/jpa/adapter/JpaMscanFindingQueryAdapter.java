package buaa.msasca.sca.infra.persistence.jpa.adapter;

import java.util.ArrayList;
import java.util.List;

import buaa.msasca.sca.core.port.out.persistence.MscanFindingQueryPort;
import buaa.msasca.sca.infra.persistence.jpa.entity.result.mscan.MscanFindingEntity;
import buaa.msasca.sca.infra.persistence.jpa.repository.Mscan.MscanFindingJpaRepository;

public class JpaMscanFindingQueryAdapter implements MscanFindingQueryPort {

  private final MscanFindingJpaRepository findingRepo;

  public JpaMscanFindingQueryAdapter(MscanFindingJpaRepository findingRepo) {
    this.findingRepo = findingRepo;
  }

  @Override
  public List<MscanFindingView> findByAnalysisRunId(Long analysisRunId) {
    if (analysisRunId == null) return List.of();
    List<MscanFindingEntity> entities = findingRepo.findByAnalysisRunId(analysisRunId);
    List<MscanFindingView> out = new ArrayList<>(entities.size());
    for (MscanFindingEntity f : entities) {
      out.add(new MscanFindingView(
          f.getId(),
          f.getFlowIndex(),
          f.getSourceSignature(),
          f.getSinkSignature(),
          f.getVulId(),
          f.getSourceService() != null ? f.getSourceService().getId() : null,
          f.getSinkService() != null ? f.getSinkService().getId() : null,
          f.getSinkFilePath(),
          f.getSinkLine(),
          f.getRawFlowText()
      ));
    }
    return out;
  }
}

