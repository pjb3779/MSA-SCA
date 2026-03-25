package buaa.msasca.sca.infra.persistence.jpa.adapter;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import buaa.msasca.sca.core.port.out.persistence.UnifiedTaintRecordCommandPort;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ServiceModuleEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.result.codeql.CodeqlFindingEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.result.mscan.MscanFindingEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.unifiedresult.TaintStepEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.unifiedresult.UnifiedTaintRecordEntity;
import buaa.msasca.sca.infra.persistence.jpa.repository.ServiceModuleJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.TaintStepJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.UnifiedTaintRecordJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.Mscan.MscanFindingJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.codeQl.CodeqlFindingJpaRepository;

public class JpaUnifiedTaintRecordAdapter implements UnifiedTaintRecordCommandPort {

  private final UnifiedTaintRecordJpaRepository unifiedRepo;
  private final TaintStepJpaRepository stepRepo;
  private final CodeqlFindingJpaRepository codeqlFindingRepo;
  private final MscanFindingJpaRepository mscanFindingRepo;
  private final ServiceModuleJpaRepository serviceModuleRepo;

  public JpaUnifiedTaintRecordAdapter(
      UnifiedTaintRecordJpaRepository unifiedRepo,
      TaintStepJpaRepository stepRepo,
      CodeqlFindingJpaRepository codeqlFindingRepo,
      MscanFindingJpaRepository mscanFindingRepo,
      ServiceModuleJpaRepository serviceModuleRepo
  ) {
    this.unifiedRepo = unifiedRepo;
    this.stepRepo = stepRepo;
    this.codeqlFindingRepo = codeqlFindingRepo;
    this.mscanFindingRepo = mscanFindingRepo;
    this.serviceModuleRepo = serviceModuleRepo;
  }

  @Override
  @Transactional
  public void replaceByAnalysisRun(Long analysisRunId, List<UnifiedTaintUpsert> records) {
    List<Long> existingIds = unifiedRepo.findIdsByAnalysisRunId(analysisRunId);
    if (!existingIds.isEmpty()) {
      stepRepo.deleteByRecord_IdIn(existingIds);
      unifiedRepo.deleteAllById(existingIds);
    }

    for (UnifiedTaintUpsert r : records) {
      if (r == null) continue;
      UnifiedTaintRecordEntity e = UnifiedTaintRecordEntity.create(
          defaultText(r.vulnerabilityType(), "UNKNOWN"),
          defaultText(r.title(), "unknown finding")
      );
      CodeqlFindingEntity codeql = (r.codeqlFindingId() == null)
          ? null
          : codeqlFindingRepo.findById(r.codeqlFindingId()).orElse(null);
      MscanFindingEntity mscan = (r.mscanFindingId() == null)
          ? null
          : mscanFindingRepo.findById(r.mscanFindingId()).orElse(null);
      e.link(codeql, mscan);
      e.describe(r.description(), r.severity());
      e.setEndpoints(r.sourceFilePath(), r.sourceLine(), r.sinkFilePath(), r.sinkLine());
      UnifiedTaintRecordEntity saved = unifiedRepo.save(e);

      if (r.steps() != null) {
        for (TaintStepUpsert s : r.steps()) {
          if (s == null) continue;
          TaintStepEntity step = TaintStepEntity.create(saved, s.stepIndex());
          ServiceModuleEntity sm = (s.serviceModuleId() == null)
              ? null
              : serviceModuleRepo.findById(s.serviceModuleId()).orElse(null);
          step.attach(sm, s.role(), s.filePath(), s.lineNumber(), s.description());
          stepRepo.save(step);
        }
      }
    }
  }

  private String defaultText(String v, String def) {
    return (v == null || v.isBlank()) ? def : v;
  }
}

