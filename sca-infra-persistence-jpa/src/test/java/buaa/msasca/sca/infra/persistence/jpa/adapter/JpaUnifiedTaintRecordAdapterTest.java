package buaa.msasca.sca.infra.persistence.jpa.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import jakarta.persistence.EntityManager;
import buaa.msasca.sca.core.domain.enums.BuildTool;
import buaa.msasca.sca.core.domain.enums.RoleType;
import buaa.msasca.sca.core.domain.enums.Severity;
import buaa.msasca.sca.core.domain.enums.SourceType;
import buaa.msasca.sca.core.domain.enums.ToolType;
import buaa.msasca.sca.core.port.out.persistence.UnifiedTaintRecordCommandPort;
import buaa.msasca.sca.infra.persistence.jpa.TestJpaBootConfig;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectVersionEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ServiceModuleEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.result.mscan.MscanFindingEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.run.AnalysisRunEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.run.ToolRunEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.tooldetail.MscanRunDetailEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.unifiedresult.TaintStepEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.unifiedresult.UnifiedTaintRecordEntity;
import buaa.msasca.sca.infra.persistence.jpa.repository.AnalysisRunJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.MscanRunDetailJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectVersionJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ServiceModuleJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.TaintStepJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ToolRunJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.UnifiedTaintRecordJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.Mscan.MscanFindingJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.codeQl.CodeqlFindingJpaRepository;

@DataJpaTest
@ContextConfiguration(classes = TestJpaBootConfig.class)
class JpaUnifiedTaintRecordAdapterTest {

  @Autowired EntityManager em;
  @Autowired ProjectJpaRepository projectRepo;
  @Autowired ProjectVersionJpaRepository pvRepo;
  @Autowired AnalysisRunJpaRepository runRepo;
  @Autowired ToolRunJpaRepository toolRunRepo;
  @Autowired MscanRunDetailJpaRepository mscanRunRepo;
  @Autowired ServiceModuleJpaRepository smRepo;
  @Autowired MscanFindingJpaRepository mscanFindingRepo;
  @Autowired UnifiedTaintRecordJpaRepository unifiedRepo;
  @Autowired TaintStepJpaRepository stepRepo;
  @Autowired CodeqlFindingJpaRepository codeqlFindingRepo;

  @Test
  void replaceByAnalysisRun_deletesOldRecordsAndSteps_thenInsertsNewOnes() {
    // 시나리오: 동일 analysis_run에 대해 통합 결과를 replace하면
    //         기존 unified_taint_record + taint_step을 모두 삭제하고,
    //         새 레코드/스텝을 저장해야 한다.

    ProjectEntity project = projectRepo.save(ProjectEntity.create("p1", null, null));
    ProjectVersionEntity pv = pvRepo.save(ProjectVersionEntity.create(project, "v1", SourceType.ZIP, null, null, null));
    AnalysisRunEntity run = runRepo.save(AnalysisRunEntity.create(pv, null, "test"));

    ServiceModuleEntity sm = smRepo.save(ServiceModuleEntity.create(pv, "sm1", "sm1", BuildTool.MAVEN, "17", true));

    ToolRunEntity toolRun = toolRunRepo.save(ToolRunEntity.create(run, ToolType.MSCAN, "mscan", null));
    MscanRunDetailEntity mscanRun = mscanRunRepo.save(MscanRunDetailEntity.create(toolRun));
    MscanFindingEntity mscanFinding = mscanFindingRepo.save(
        MscanFindingEntity.create(mscanRun, 1, "SRC", "SINK", "CMD", "raw")
    );

    // 기존 unified record + step 1개를 미리 만들어둔다.
    UnifiedTaintRecordEntity oldRecord = UnifiedTaintRecordEntity.create("OLD", "old-title");
    oldRecord.link(null, mscanFinding);
    UnifiedTaintRecordEntity oldSaved = unifiedRepo.save(oldRecord);
    stepRepo.save(TaintStepEntity.create(oldSaved, 0));

    JpaUnifiedTaintRecordAdapter adapter = new JpaUnifiedTaintRecordAdapter(
        unifiedRepo,
        stepRepo,
        codeqlFindingRepo,
        mscanFindingRepo,
        smRepo
    );

    UnifiedTaintRecordCommandPort.TaintStepUpsert s0 = new UnifiedTaintRecordCommandPort.TaintStepUpsert(
        0,
        sm.getId(),
        RoleType.SOURCE,
        "A.java",
        10,
        "source",
        "A",
        "sourceMethod",
        "A.sourceMethod()",
        "10: source()"
    );
    UnifiedTaintRecordCommandPort.TaintStepUpsert s1 = new UnifiedTaintRecordCommandPort.TaintStepUpsert(
        1,
        sm.getId(),
        RoleType.SINK,
        "B.java",
        20,
        "sink",
        "B",
        "sinkMethod",
        "B.sinkMethod()",
        "20: sink()"
    );

    UnifiedTaintRecordCommandPort.UnifiedTaintUpsert newUpsert = new UnifiedTaintRecordCommandPort.UnifiedTaintUpsert(
        null,
        mscanFinding.getId(),
        sm.getId(),
        "",                 // vulnerabilityType 비면 기본값으로 채워져야 함
        "",                 // title 비면 기본값으로 채워져야 함
        "desc",
        Severity.HIGH,
        null,
        null,
        "B.java",
        20,
        List.of(s0, s1)
    );

    adapter.replaceByAnalysisRun(run.getId(), List.of(newUpsert));

    // 시나리오: replace 동작이 끝난 뒤 조회를 위해 flush/clear 해서 DB 기준으로 확인한다.
    em.flush();
    em.clear();

    // 기존 record는 삭제되고, 새 record만 남아야 한다.
    List<Long> ids = unifiedRepo.findIdsByAnalysisRunId(run.getId());
    assertThat(ids).hasSize(1);
    assertThat(ids.get(0)).isNotEqualTo(oldSaved.getId());

    // step은 새 record 기준으로 2개만 남아야 한다.
    assertThat(stepRepo.findAll()).hasSize(2);
    assertThat(stepRepo.findAll().stream().map(s -> s.getRecord().getId()).distinct().toList())
        .containsExactly(ids.get(0));

    UnifiedTaintRecordEntity saved = unifiedRepo.findById(ids.get(0)).orElseThrow();
    assertThat(saved.getVulnerabilityType()).isEqualTo("UNKNOWN");
    assertThat(saved.getTitle()).isEqualTo("unknown finding");
    assertThat(saved.getSeverity()).isEqualTo(Severity.HIGH);
    assertThat(saved.getScopeServiceModule()).isNotNull();
    assertThat(saved.getScopeServiceModule().getId()).isEqualTo(sm.getId());
  }
}

