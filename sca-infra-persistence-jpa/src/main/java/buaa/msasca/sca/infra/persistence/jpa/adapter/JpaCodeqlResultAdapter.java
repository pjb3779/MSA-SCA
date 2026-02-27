package buaa.msasca.sca.infra.persistence.jpa.adapter;

import java.time.Instant;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import buaa.msasca.sca.core.domain.enums.CodeqlSummaryStatus;
import buaa.msasca.sca.core.port.out.persistence.CodeqlResultPort;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ServiceModuleEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.result.codeql.CodeqlFindingEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.result.codeql.CodeqlFindingLocationEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.result.codeql.CodeqlFlowEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.result.codeql.CodeqlFlowStepEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.result.codeql.CodeqlRunSummaryEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.run.ToolRunEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.tooldetail.CodeqlRunDetailEntity;
import buaa.msasca.sca.infra.persistence.jpa.repository.ServiceModuleJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ToolRunJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.codeQl.CodeqlFindingJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.codeQl.CodeqlFindingLocationJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.codeQl.CodeqlFlowJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.codeQl.CodeqlFlowStepJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.codeQl.CodeqlRunDetailJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.codeQl.CodeqlRunSummaryJpaRepository;

public class JpaCodeqlResultAdapter implements CodeqlResultPort {

    private final ToolRunJpaRepository toolRunRepo;
    private final ServiceModuleJpaRepository serviceModuleRepo;
    private final CodeqlRunDetailJpaRepository codeqlDetailRepo;

    private final CodeqlRunSummaryJpaRepository summaryRepo;

    private final CodeqlFindingJpaRepository findingRepo;
    private final CodeqlFindingLocationJpaRepository locationRepo;
    private final CodeqlFlowJpaRepository flowRepo;
    private final CodeqlFlowStepJpaRepository stepRepo;

    public JpaCodeqlResultAdapter(
        ToolRunJpaRepository toolRunRepo,
        ServiceModuleJpaRepository serviceModuleRepo,
        CodeqlRunDetailJpaRepository codeqlDetailRepo,
        CodeqlRunSummaryJpaRepository summaryRepo,
        CodeqlFindingJpaRepository findingRepo,
        CodeqlFindingLocationJpaRepository locationRepo,
        CodeqlFlowJpaRepository flowRepo,
        CodeqlFlowStepJpaRepository stepRepo
    ) {
        this.toolRunRepo = toolRunRepo;
        this.serviceModuleRepo = serviceModuleRepo;
        this.codeqlDetailRepo = codeqlDetailRepo;
        this.summaryRepo = summaryRepo;
        this.findingRepo = findingRepo;
        this.locationRepo = locationRepo;
        this.flowRepo = flowRepo;
        this.stepRepo = stepRepo;
    }

    @Override
    @Transactional
    public void upsertRunSummary(
        Long toolRunId,
        Long serviceModuleId,
        CodeqlSummaryStatus status,
        int resultCount,
        String sarifStoragePath,
        String sarifSha256,
        Instant ingestedAt
    ) {
        ToolRunEntity tr = toolRunRepo.findById(toolRunId)
            .orElseThrow(() -> new IllegalArgumentException("tool_run not found: " + toolRunId));

        CodeqlRunDetailEntity detail = codeqlDetailRepo.findById(toolRunId)
            .orElseThrow(() -> new IllegalArgumentException("codeql_run_detail not found: " + toolRunId));

        ServiceModuleEntity sm = null;
        if (serviceModuleId != null) {
        sm = serviceModuleRepo.findById(serviceModuleId)
            .orElseThrow(() -> new IllegalArgumentException("service_module not found: " + serviceModuleId));
        } else {
        sm = detail.getServiceModule();
        }

        CodeqlRunSummaryEntity existing = summaryRepo.findById(toolRunId).orElse(null);
        if (existing == null) {
        summaryRepo.save(CodeqlRunSummaryEntity.create(
            tr, sm, status, resultCount, sarifStoragePath, sarifSha256, ingestedAt
        ));
        return;
        }

        existing.update(status, resultCount, sarifStoragePath, sarifSha256, ingestedAt);
        summaryRepo.save(existing);
    }

    @Override
    @Transactional
    public void replaceAll(Long toolRunId, List<CodeqlFindingIngest> findings) {
        CodeqlRunDetailEntity detail = codeqlDetailRepo.findById(toolRunId)
            .orElseThrow(() -> new IllegalArgumentException("codeql_run_detail not found: " + toolRunId));

        // 1) 기존 결과 삭제(자식부터)
        List<Long> findingIds = findingRepo.findIdsByToolRunId(toolRunId);
        if (!findingIds.isEmpty()) {
            List<Long> flowIds = flowRepo.findFlowIdsByFindingIds(findingIds);
        if (!flowIds.isEmpty()) stepRepo.deleteByFlowIds(flowIds);
            flowRepo.deleteByFindingIds(findingIds);
            locationRepo.deleteByFindingIds(findingIds);
            findingRepo.deleteByCodeqlRun_ToolRunId(toolRunId);
        }

        // 2) 새 결과 insert
        for (CodeqlFindingIngest f : findings) {
        CodeqlFindingEntity fe = CodeqlFindingEntity.create(detail, f.ruleId(), f.message());

        // level/tags/helpText
        if (f.level() != null || f.tagsJson() != null || f.helpText() != null) {
            fe.attachHelp(f.level(), f.tagsJson(), f.helpText());
        }

        // primary
        if (f.primaryFile() != null || f.primaryLine() != null) {
            fe.attachPrimary(f.primaryFile(), f.primaryLine());
        }

        CodeqlFindingEntity savedFinding = findingRepo.save(fe);

        // locations
        if (f.locations() != null) {
            for (LocationRow l : f.locations()) {
            locationRepo.save(CodeqlFindingLocationEntity.create(
                savedFinding, l.locationIndex(), l.filePath(), l.lineNumber()
            ));
            }
        }

        // flows + steps
        if (f.flows() != null) {
            for (FlowRow flow : f.flows()) {
            CodeqlFlowEntity savedFlow = flowRepo.save(CodeqlFlowEntity.create(savedFinding, flow.flowIndex()));

            if (flow.steps() != null) {
                for (FlowStepRow s : flow.steps()) {
                CodeqlFlowStepEntity step = CodeqlFlowStepEntity.create(savedFlow, s.stepIndex());
                step.attachLocation(s.filePath(), s.lineNumber(), s.label());
                stepRepo.save(step);
                }
            }
            }
            }
        }
    }
}