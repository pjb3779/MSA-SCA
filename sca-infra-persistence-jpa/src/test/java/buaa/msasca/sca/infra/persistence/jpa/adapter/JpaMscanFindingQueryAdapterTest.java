package buaa.msasca.sca.infra.persistence.jpa.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import buaa.msasca.sca.core.domain.enums.BuildTool;
import buaa.msasca.sca.core.domain.enums.SourceType;
import buaa.msasca.sca.core.domain.enums.ToolType;
import buaa.msasca.sca.infra.persistence.jpa.TestJpaBootConfig;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectVersionEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ServiceModuleEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.result.mscan.MscanFindingEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.run.AnalysisRunEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.run.ToolRunEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.tooldetail.MscanRunDetailEntity;
import buaa.msasca.sca.infra.persistence.jpa.repository.AnalysisRunJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.MscanRunDetailJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectVersionJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ServiceModuleJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ToolRunJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.Mscan.MscanFindingJpaRepository;

@DataJpaTest
@ContextConfiguration(classes = TestJpaBootConfig.class)
class JpaMscanFindingQueryAdapterTest {

  @Autowired ProjectJpaRepository projectRepo;
  @Autowired ProjectVersionJpaRepository pvRepo;
  @Autowired AnalysisRunJpaRepository runRepo;
  @Autowired ToolRunJpaRepository toolRunRepo;
  @Autowired MscanRunDetailJpaRepository mscanRunRepo;
  @Autowired ServiceModuleJpaRepository smRepo;
  @Autowired MscanFindingJpaRepository findingRepo;

  @Test
  void findByAnalysisRunId_mapsEntitiesAndOrdersByFlowIndex() {
    // 시나리오: analysis_run에 매달린 mscan finding들을 조회하면
    //         어댑터가 필요한 필드를 view로 매핑하고 flowIndex 오름차순을 보장해야 한다.

    ProjectEntity project = projectRepo.save(ProjectEntity.create("p1", null, null));
    ProjectVersionEntity pv = pvRepo.save(ProjectVersionEntity.create(project, "v1", SourceType.ZIP, null, null, null));
    AnalysisRunEntity run = runRepo.save(AnalysisRunEntity.create(pv, null, "test"));

    ServiceModuleEntity srcSm = smRepo.save(ServiceModuleEntity.create(pv, "sm-src", "sm-src", BuildTool.MAVEN, "17", false));
    ServiceModuleEntity sinkSm = smRepo.save(ServiceModuleEntity.create(pv, "sm-sink", "sm-sink", BuildTool.MAVEN, "17", true));

    ToolRunEntity toolRun = toolRunRepo.save(ToolRunEntity.create(run, ToolType.MSCAN, "mscan", null));
    MscanRunDetailEntity mscanRun = mscanRunRepo.save(MscanRunDetailEntity.create(toolRun));

    MscanFindingEntity f2 = MscanFindingEntity.create(mscanRun, 2, "S2", "K2", "V2", "raw2");
    f2.attachServices(srcSm, sinkSm);
    f2.attachSinkMeta("B.java", 20, 7, "invokevirtual", "target2");
    findingRepo.save(f2);

    MscanFindingEntity f1 = MscanFindingEntity.create(mscanRun, 1, "S1", "K1", "V1", "raw1");
    f1.attachServices(srcSm, sinkSm);
    f1.attachSinkMeta("A.java", 10, 3, "invokeinterface", "target1");
    findingRepo.save(f1);

    JpaMscanFindingQueryAdapter adapter = new JpaMscanFindingQueryAdapter(findingRepo);

    var views = adapter.findByAnalysisRunId(run.getId());
    assertThat(views).hasSize(2);
    assertThat(views.stream().map(v -> v.flowIndex()).toList()).containsExactly(1, 2);

    var first = views.get(0);
    assertThat(first.vulId()).isEqualTo("V1");
    assertThat(first.sourceServiceId()).isEqualTo(srcSm.getId());
    assertThat(first.sinkServiceId()).isEqualTo(sinkSm.getId());
    assertThat(first.sinkFilePath()).isEqualTo("A.java");
    assertThat(first.sinkLine()).isEqualTo(10);
    assertThat(first.rawFlowText()).isEqualTo("raw1");
  }
}

