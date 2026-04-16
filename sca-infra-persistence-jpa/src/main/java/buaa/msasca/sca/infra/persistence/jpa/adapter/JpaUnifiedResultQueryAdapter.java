package buaa.msasca.sca.infra.persistence.jpa.adapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import buaa.msasca.sca.core.domain.enums.RoleType;
import buaa.msasca.sca.core.domain.enums.Severity;
import buaa.msasca.sca.core.port.out.persistence.UnifiedResultQueryPort;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ServiceModuleEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.unifiedresult.TaintStepEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.unifiedresult.UnifiedTaintRecordEntity;
import buaa.msasca.sca.infra.persistence.jpa.repository.AnalysisRunJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ServiceModuleJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.TaintStepJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.UnifiedTaintRecordJpaRepository;

public class JpaUnifiedResultQueryAdapter implements UnifiedResultQueryPort {
  /** source/sink 시그니처 문자열에서 메서드명을 추출할 때 사용하는 정규식 */
  private static final Pattern METHOD_SIGNATURE_PATTERN =
      Pattern.compile("(?:source|sink)=([^,;\\s]+)");

  /** 스텝/경로로 모듈을 못 찾을 때; 그래프 엣지에는 넣지 않는다 */
  private static final String UNKNOWN_MODULE = "unknown-module";

  private final UnifiedTaintRecordJpaRepository unifiedRepo;
  private final TaintStepJpaRepository stepRepo;
  private final AnalysisRunJpaRepository analysisRunRepo;
  private final ServiceModuleJpaRepository serviceModuleRepo;

  public JpaUnifiedResultQueryAdapter(
      UnifiedTaintRecordJpaRepository unifiedRepo,
      TaintStepJpaRepository stepRepo,
      AnalysisRunJpaRepository analysisRunRepo,
      ServiceModuleJpaRepository serviceModuleRepo
  ) {
    this.unifiedRepo = unifiedRepo;
    this.stepRepo = stepRepo;
    this.analysisRunRepo = analysisRunRepo;
    this.serviceModuleRepo = serviceModuleRepo;
  }

  @Override
  public ServiceGraphView getServiceGraph(Long analysisRunId) {
    AnalysisResultSummaryView s = getSummary(analysisRunId);
    if (analysisRunId == null) {
      return new ServiceGraphView(null, List.of(), List.of(), List.of());
    }
    Optional<Long> pvId = analysisRunRepo.findById(analysisRunId).map(a -> a.getProjectVersion().getId());
    List<String> moduleNames = new ArrayList<>();
    if (pvId.isPresent()) {
      for (ServiceModuleEntity m : serviceModuleRepo.findAllByProjectVersionId(pvId.get())) {
        if (m.getName() != null && !m.getName().isBlank()) {
          moduleNames.add(m.getName());
        }
      }
    }
    Collections.sort(moduleNames);

    Map<String, Severity> nodeSeverity = new LinkedHashMap<>();
    Map<String, Boolean> nodeTainted = new LinkedHashMap<>();
    for (String m : moduleNames) {
      nodeSeverity.put(m, null);
      nodeTainted.put(m, false);
    }

    Map<String, FlowEdgeView> codeqlByPath = new LinkedHashMap<>();
    for (FlowEdgeView ce : s.codeqlFlowEdges()) {
      codeqlByPath.put(pathId(ce.sourceModule(), ce.sinkModule()), ce);
    }

    for (FlowEdgeView e : s.flowEdges()) {
      touchNodePair(nodeSeverity, nodeTainted, e, codeqlByPath);
    }
    for (FlowEdgeView e : s.mscanFlowEdges()) {
      touchNodePair(nodeSeverity, nodeTainted, e, codeqlByPath);
    }
    for (FlowEdgeView e : s.codeqlFlowEdges()) {
      touchNodePair(nodeSeverity, nodeTainted, e, codeqlByPath);
    }

    List<ServiceGraphNodeView> nodes = nodeSeverity.entrySet().stream()
        .map(e -> new ServiceGraphNodeView(
            e.getKey(),
            e.getKey(),
            e.getValue(),
            nodeTainted.getOrDefault(e.getKey(), false)
        ))
        .toList();

    Map<String, FlowEdgeView> flowByPath = indexEdgesByPathId(s.flowEdges());
    Map<String, FlowEdgeView> mscanByPath = indexEdgesByPathId(s.mscanFlowEdges());
    Map<String, FlowEdgeView> codeqlMap = indexEdgesByPathId(s.codeqlFlowEdges());

    Set<String> pathUnion = new LinkedHashSet<>();
    pathUnion.addAll(flowByPath.keySet());
    pathUnion.addAll(mscanByPath.keySet());
    pathUnion.addAll(codeqlMap.keySet());

    List<ServiceGraphEdgeView> edges = new ArrayList<>();
    for (String pid : pathUnion) {
      String[] sp = pid.split("->", 2);
      String src = sp.length > 0 ? sp[0] : "";
      String tgt = sp.length > 1 ? sp[1] : "";
      FlowEdgeView f = flowByPath.get(pid);
      FlowEdgeView ms = mscanByPath.get(pid);
      FlowEdgeView cq = codeqlMap.get(pid);
      if (f != null) {
        src = f.sourceModule();
        tgt = f.sinkModule();
      } else if (ms != null) {
        src = ms.sourceModule();
        tgt = ms.sinkModule();
      } else if (cq != null) {
        src = cq.sourceModule();
        tgt = cq.sinkModule();
      }
      if (isUnknownModuleName(src) || isUnknownModuleName(tgt)) {
        continue;
      }
      edges.add(new ServiceGraphEdgeView(
          pid,
          src,
          tgt,
          f != null ? f.count() : 0L,
          f != null ? f.maxSeverity() : null,
          ms != null ? ms.count() : 0L,
          ms != null ? ms.maxSeverity() : null,
          cq != null ? cq.count() : 0L,
          cq != null ? cq.maxSeverity() : null
      ));
    }

    List<ServiceWiringEdgeView> wiring = new ArrayList<>();
    for (int i = 0; i < moduleNames.size() - 1; i++) {
      wiring.add(new ServiceWiringEdgeView(moduleNames.get(i), moduleNames.get(i + 1)));
    }

    return new ServiceGraphView(analysisRunId, nodes, edges, wiring);
  }

  private void touchNodePair(
      Map<String, Severity> nodeSeverity,
      Map<String, Boolean> nodeTainted,
      FlowEdgeView e,
      Map<String, FlowEdgeView> codeqlByPath
  ) {
    if (isUnknownModuleName(e.sourceModule()) || isUnknownModuleName(e.sinkModule())) {
      return;
    }
    nodeSeverity.putIfAbsent(e.sourceModule(), null);
    nodeSeverity.putIfAbsent(e.sinkModule(), null);
    nodeTainted.put(e.sourceModule(), true);
    nodeTainted.put(e.sinkModule(), true);
    nodeSeverity.put(e.sourceModule(), moreSevereSeverity(nodeSeverity.get(e.sourceModule()), e.maxSeverity()));
    nodeSeverity.put(e.sinkModule(), moreSevereSeverity(nodeSeverity.get(e.sinkModule()), e.maxSeverity()));
    String pid = pathId(e.sourceModule(), e.sinkModule());
    FlowEdgeView cq = codeqlByPath.get(pid);
    if (cq != null) {
      nodeSeverity.put(e.sourceModule(), moreSevereSeverity(nodeSeverity.get(e.sourceModule()), cq.maxSeverity()));
      nodeSeverity.put(e.sinkModule(), moreSevereSeverity(nodeSeverity.get(e.sinkModule()), cq.maxSeverity()));
    }
  }

  private static Map<String, FlowEdgeView> indexEdgesByPathId(List<FlowEdgeView> edges) {
    Map<String, FlowEdgeView> m = new LinkedHashMap<>();
    for (FlowEdgeView e : edges) {
      m.putIfAbsent(pathId(e.sourceModule(), e.sinkModule()), e);
    }
    return m;
  }

  private static String pathId(String sourceModule, String sinkModule) {
    return (sourceModule == null ? "unknown" : sourceModule) + "->" + (sinkModule == null ? "unknown" : sinkModule);
  }

  private static boolean isUnknownModuleName(String m) {
    return m == null || m.isBlank() || UNKNOWN_MODULE.equals(m);
  }

  private Severity moreSevereSeverity(Severity base, Severity incoming) {
    if (base == null) {
      return incoming;
    }
    if (incoming == null) {
      return base;
    }
    return severityRank(base) <= severityRank(incoming) ? base : incoming;
  }

  @Override
  public SemanticDrilldownView getSemanticDrilldown(Long analysisRunId, String moduleName) {
    if (analysisRunId == null || moduleName == null || moduleName.isBlank()) {
      return new SemanticDrilldownView(analysisRunId, moduleName == null ? "" : moduleName, List.of());
    }
    List<Long> ids = unifiedRepo.findIdsByAnalysisRunId(analysisRunId);
    if (ids.isEmpty()) {
      return new SemanticDrilldownView(analysisRunId, moduleName, List.of());
    }
    List<TaintStepEntity> steps = stepRepo.findByRecord_IdIn(ids);
    ModuleContext moduleCtx = resolveModuleContext(analysisRunId);
    List<ModuleMatchRule> moduleRules = moduleCtx.rules();

    List<TaintStepEntity> forModule = new ArrayList<>();
    for (TaintStepEntity st : steps) {
      if (st.getRecord() == null) {
        continue;
      }
      String m = resolveModuleName(st, moduleRules);
      if (moduleName.equals(m)) {
        forModule.add(st);
      }
    }
    forModule.sort(Comparator
        .comparing((TaintStepEntity x) -> x.getFilePath() == null ? "" : x.getFilePath())
        .thenComparing(x -> x.getClassName() == null ? "" : x.getClassName())
        .thenComparing(x -> x.getMethodName() == null ? "" : x.getMethodName())
        .thenComparing(x -> x.getRecord().getId())
        .thenComparingInt(TaintStepEntity::getStepIndex));

    Map<String, Map<String, Map<String, List<TaintStepEntity>>>> tree = new TreeMap<>();
    for (TaintStepEntity st : forModule) {
      String file = firstNonBlank(st.getFilePath(), "(unknown file)");
      String cls = firstNonBlank(st.getClassName(), "(default)");
      String meth = firstNonBlank(st.getMethodName(), st.getMethodSignature(), "(unknown method)");
      tree.computeIfAbsent(file, k -> new TreeMap<>())
          .computeIfAbsent(cls, k -> new TreeMap<>())
          .computeIfAbsent(meth, k -> new ArrayList<>())
          .add(st);
    }

    List<SemanticDrilldownNodeView> fileChildren = new ArrayList<>();
    for (Map.Entry<String, Map<String, Map<String, List<TaintStepEntity>>>> fe : tree.entrySet()) {
      String filePath = fe.getKey();
      String fileId = "file:" + Integer.toHexString(filePath.hashCode());
      List<SemanticDrilldownNodeView> classChildren = new ArrayList<>();
      for (Map.Entry<String, Map<String, List<TaintStepEntity>>> ce : fe.getValue().entrySet()) {
        String cls = ce.getKey();
        String classId = fileId + "/c:" + Integer.toHexString(cls.hashCode());
        List<SemanticDrilldownNodeView> methodChildren = new ArrayList<>();
        for (Map.Entry<String, List<TaintStepEntity>> me : ce.getValue().entrySet()) {
          String meth = me.getKey();
          String methodId = classId + "/m:" + Integer.toHexString(meth.hashCode());
          List<SemanticDrilldownNodeView> stepLeaves = new ArrayList<>();
          for (TaintStepEntity st : me.getValue()) {
            UnifiedTaintRecordEntity r = st.getRecord();
            String role = st.getRole() == null ? null : st.getRole().name();
            stepLeaves.add(new SemanticDrilldownNodeView(
                methodId + "/s:" + r.getId() + ":" + st.getStepIndex(),
                "STEP",
                briefStepLabel(st),
                r.getId(),
                st.getStepIndex(),
                st.getFilePath(),
                st.getClassName(),
                st.getMethodName(),
                st.getLineNumber(),
                st.getCodeSnippet(),
                role,
                r.getVulnerabilityType(),
                r.getTitle(),
                List.of()
            ));
          }
          methodChildren.add(new SemanticDrilldownNodeView(
              methodId,
              "METHOD",
              meth,
              null,
              null,
              filePath,
              cls,
              meth,
              null,
              null,
              null,
              null,
              null,
              stepLeaves
          ));
        }
        classChildren.add(new SemanticDrilldownNodeView(
            classId,
            "CLASS",
            cls,
            null,
            null,
            filePath,
            cls,
            null,
            null,
            null,
            null,
            null,
            null,
            methodChildren
        ));
      }
      fileChildren.add(new SemanticDrilldownNodeView(
          fileId,
          "FILE",
          shortFileLabel(filePath),
          null,
          null,
          filePath,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          classChildren
      ));
    }

    SemanticDrilldownNodeView root = new SemanticDrilldownNodeView(
        "service:" + moduleName,
        "SERVICE",
        moduleName,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        fileChildren
    );
    return new SemanticDrilldownView(analysisRunId, moduleName, List.of(root));
  }

  private static String briefStepLabel(TaintStepEntity st) {
    Integer line = st.getLineNumber();
    String role = st.getRole() == null ? "?" : st.getRole().name();
    return role + (line != null ? " @" + line : "");
  }

  private static String shortFileLabel(String filePath) {
    if (filePath == null) {
      return "";
    }
    int i = filePath.lastIndexOf('/');
    int j = filePath.lastIndexOf('\\');
    int k = Math.max(i, j);
    return k >= 0 ? filePath.substring(k + 1) : filePath;
  }

  @Override
  public AnalysisResultSummaryView getSummary(Long analysisRunId) {
    if (analysisRunId == null) {
      return emptySummary(null);
    }

    List<Long> ids = unifiedRepo.findIdsByAnalysisRunId(analysisRunId);
    if (ids.isEmpty()) {
      return emptySummary(analysisRunId);
    }

    List<UnifiedTaintRecordEntity> records = unifiedRepo.findAllById(ids);
    List<TaintStepEntity> steps = stepRepo.findByRecord_IdIn(ids);
    ModuleContext moduleCtx = resolveModuleContext(analysisRunId);
    List<ModuleMatchRule> moduleRules = moduleCtx.rules();
    Map<Long, String> moduleNameById = moduleCtx.moduleNameById();

    List<TaintStepEntity> orderedSteps = new ArrayList<>(steps);
    orderedSteps.sort(Comparator
        .comparing((TaintStepEntity s) -> s.getRecord() != null && s.getRecord().getId() != null
            ? s.getRecord().getId() : 0L)
        .thenComparingInt(TaintStepEntity::getStepIndex));

    Map<Long, String> sourceModuleByRecordId = new HashMap<>();
    Map<Long, String> sinkModuleByRecordId = new HashMap<>();
    Map<Long, String> sourceMethodByRecordId = new HashMap<>();
    Map<Long, String> sinkMethodByRecordId = new HashMap<>();
    for (TaintStepEntity s : orderedSteps) {
      if (s.getRecord() == null || s.getRecord().getId() == null) continue;
      Long rid = s.getRecord().getId();
      String moduleName = resolveModuleName(s, moduleRules);
      String method = firstNonBlank(s.getMethodSignature(), s.getMethodName(), extractMethodName(s.getDescription()));

      if (s.getRole() == RoleType.SOURCE) {
        mergeRoleModule(sourceModuleByRecordId, sourceMethodByRecordId, rid, moduleName, method);
      } else if (s.getRole() == RoleType.SINK) {
        mergeRoleModule(sinkModuleByRecordId, sinkMethodByRecordId, rid, moduleName, method);
      }
    }

    long total = records.size();
    long codeqlOnly = records.stream().filter(r -> r.getCodeqlFinding() != null && r.getMscanFinding() == null).count();
    long mscanOnly = records.stream().filter(r -> r.getCodeqlFinding() == null && r.getMscanFinding() != null).count();
    long matched = records.stream().filter(r -> r.getCodeqlFinding() != null && r.getMscanFinding() != null).count();

    Map<Severity, Long> severityMap = new HashMap<>();
    Map<String, Long> typeMap = new HashMap<>();
    Map<String, Long> moduleMap = new HashMap<>();
    Map<String, FlowEdgeAccumulator> edgeMap = new HashMap<>();
    Map<String, FlowEdgeAccumulator> edgeMapMscan = new HashMap<>();
    Map<String, FlowEdgeAccumulator> edgeMapCodeql = new HashMap<>();
    List<FindingView> findings = new ArrayList<>();

    for (UnifiedTaintRecordEntity r : records) {
      Severity severity = r.getSeverity() == null ? Severity.MEDIUM : r.getSeverity();
      String vulnType = (r.getVulnerabilityType() == null || r.getVulnerabilityType().isBlank())
          ? "UNKNOWN"
          : r.getVulnerabilityType();
      String sourceModule = sourceModuleByRecordId.getOrDefault(r.getId(), UNKNOWN_MODULE);
      String sinkModule = sinkModuleByRecordId.getOrDefault(r.getId(), UNKNOWN_MODULE);
      if (UNKNOWN_MODULE.equals(sourceModule)) {
        String m = resolveModuleNameFromFilePath(r.getSourceFilePath(), moduleRules);
        if (m != null) {
          sourceModule = m;
        }
      }
      if (UNKNOWN_MODULE.equals(sinkModule)) {
        String m = resolveModuleNameFromFilePath(r.getSinkFilePath(), moduleRules);
        if (m != null) {
          sinkModule = m;
        }
      }

      Long scopeModuleId = r.getScopeServiceModule() == null ? null : r.getScopeServiceModule().getId();
      String scopeModuleName = scopeModuleId == null ? null : moduleNameById.get(scopeModuleId);
      if (UNKNOWN_MODULE.equals(sourceModule) && scopeModuleName != null && !scopeModuleName.isBlank()) {
        sourceModule = scopeModuleName;
      }
      if (UNKNOWN_MODULE.equals(sinkModule) && scopeModuleName != null && !scopeModuleName.isBlank()) {
        sinkModule = scopeModuleName;
      }

      final String sourceModuleFinal = sourceModule;
      final String sinkModuleFinal = sinkModule;
      boolean graphMappable = !UNKNOWN_MODULE.equals(sourceModuleFinal) && !UNKNOWN_MODULE.equals(sinkModuleFinal);

      severityMap.merge(severity, 1L, Long::sum);
      typeMap.merge(vulnType, 1L, Long::sum);
      if (graphMappable) {
        moduleMap.merge(sourceModuleFinal, 1L, Long::sum);
        moduleMap.merge(sinkModuleFinal, 1L, Long::sum);

        String edgeKey = sourceModuleFinal + " -> " + sinkModuleFinal;
        edgeMap.compute(edgeKey, (k, old) -> {
          if (old == null) return new FlowEdgeAccumulator(sourceModuleFinal, sinkModuleFinal, 1L, severity);
          return old.merge(severity);
        });
        if (r.getMscanFinding() != null) {
          edgeMapMscan.compute(edgeKey, (k, old) -> {
            if (old == null) return new FlowEdgeAccumulator(sourceModuleFinal, sinkModuleFinal, 1L, severity);
            return old.merge(severity);
          });
        }
        if (r.getCodeqlFinding() != null) {
          edgeMapCodeql.compute(edgeKey, (k, old) -> {
            if (old == null) return new FlowEdgeAccumulator(sourceModuleFinal, sinkModuleFinal, 1L, severity);
            return old.merge(severity);
          });
        }
      }

      findings.add(new FindingView(
          r.getId(),
          severity,
          vulnType,
          r.getTitle(),
          r.getSourceFilePath(),
          r.getSourceLine(),
          sourceMethodByRecordId.get(r.getId()),
          r.getSinkFilePath(),
          r.getSinkLine(),
          sinkMethodByRecordId.get(r.getId()),
          sourceModuleFinal,
          sinkModuleFinal,
          scopeModuleId,
          scopeModuleName
      ));
    }

    List<SeverityBucketView> severityBuckets = severityMap.entrySet().stream()
        .map(e -> new SeverityBucketView(e.getKey(), e.getValue()))
        .sorted(Comparator.comparingInt(e -> severityRank(e.severity())))
        .toList();

    List<VulnerabilityTypeBucketView> vulnerabilityTypeBuckets = typeMap.entrySet().stream()
        .map(e -> new VulnerabilityTypeBucketView(e.getKey(), e.getValue()))
        .sorted((a, b) -> Long.compare(b.count(), a.count()))
        .toList();

    List<ModuleBucketView> moduleBuckets = moduleMap.entrySet().stream()
        .map(e -> new ModuleBucketView(e.getKey(), e.getValue()))
        .sorted((a, b) -> Long.compare(b.count(), a.count()))
        .toList();

    List<FlowEdgeView> flowEdges = edgeMap.values().stream()
        .map(v -> new FlowEdgeView(v.sourceModule, v.sinkModule, v.count, v.maxSeverity))
        .sorted((a, b) -> Long.compare(b.count(), a.count()))
        .toList();

    List<FlowEdgeView> mscanFlowEdges = edgeMapMscan.values().stream()
        .map(v -> new FlowEdgeView(v.sourceModule, v.sinkModule, v.count, v.maxSeverity))
        .sorted((a, b) -> Long.compare(b.count(), a.count()))
        .toList();

    List<FlowEdgeView> codeqlFlowEdges = edgeMapCodeql.values().stream()
        .map(v -> new FlowEdgeView(v.sourceModule, v.sinkModule, v.count, v.maxSeverity))
        .sorted((a, b) -> Long.compare(b.count(), a.count()))
        .toList();

    findings.sort((a, b) -> {
      int sevCmp = Integer.compare(severityRank(a.severity()), severityRank(b.severity()));
      if (sevCmp != 0) return sevCmp;
      return Long.compare(
          b.unifiedRecordId() == null ? -1L : b.unifiedRecordId(),
          a.unifiedRecordId() == null ? -1L : a.unifiedRecordId()
      );
    });

    return new AnalysisResultSummaryView(
        analysisRunId,
        total,
        codeqlOnly,
        mscanOnly,
        matched,
        severityBuckets,
        vulnerabilityTypeBuckets,
        moduleBuckets,
        flowEdges,
        mscanFlowEdges,
        codeqlFlowEdges,
        findings
    );
  }

  private String resolveModuleName(TaintStepEntity step, List<ModuleMatchRule> moduleRules) {
    if (step.getServiceModule() != null
        && step.getServiceModule().getName() != null
        && !step.getServiceModule().getName().isBlank()) {
      return step.getServiceModule().getName();
    }
    String m = resolveModuleNameFromFilePath(step.getFilePath(), moduleRules);
    return m != null ? m : UNKNOWN_MODULE;
  }

  /**
   * 워크스페이스 절대경로 등 긴 경로에서도 동작하도록, service_module.root_path 가 경로 안에 포함되는지 본다.
   * 가장 긴 root_path 를 가진 모듈을 선택한다.
   */
  private String resolveModuleNameFromFilePath(String filePath, List<ModuleMatchRule> moduleRules) {
    String fp = normalizePath(filePath);
    if (fp == null || moduleRules.isEmpty()) {
      return null;
    }
    ModuleMatchRule best = null;
    int bestScore = -1;
    for (ModuleMatchRule r : moduleRules) {
      String root = r.rootPath();
      String name = r.moduleName();
      int score = -1;
      if (root != null && !root.isBlank()) {
        boolean ok =
            fp.equals(root)
                || fp.startsWith(root + "/")
                || fp.contains("/" + root + "/")
                || fp.endsWith("/" + root);
        if (ok) {
          score = Math.max(score, root.length());
        }
      }
      if (name != null && !name.isBlank()) {
        if (fp.contains("/" + name + "/") || fp.endsWith("/" + name) || fp.equals(name)) {
          score = Math.max(score, name.length());
        }
      }
      if (score > bestScore) {
        bestScore = score;
        best = r;
      }
    }
    return best != null ? best.moduleName() : null;
  }

  /**
   * 동일 레코드에 여러 SOURCE/SINK 스텝이 있을 때, 첫 스텝만 unknown이면 뒤 스텝의 모듈로 덮어쓴다.
   */
  private static void mergeRoleModule(
      Map<Long, String> moduleByRecord,
      Map<Long, String> methodByRecord,
      Long recordId,
      String moduleName,
      String method
  ) {
    String prev = moduleByRecord.get(recordId);
    boolean take =
        prev == null
            || (UNKNOWN_MODULE.equals(prev) && !UNKNOWN_MODULE.equals(moduleName));
    if (take) {
      moduleByRecord.put(recordId, moduleName);
      if (method != null) {
        methodByRecord.put(recordId, method);
      }
    }
  }

  /**
   * 프로젝트 버전의 service_module을 한 번 조회해 경로 매칭 규칙과 id→이름 맵을 함께 만든다.
   */
  private ModuleContext resolveModuleContext(Long analysisRunId) {
    if (analysisRunId == null) {
      return new ModuleContext(List.of(), Map.of());
    }
    Optional<Long> pvId = analysisRunRepo.findById(analysisRunId).map(v -> v.getProjectVersion().getId());
    if (pvId.isEmpty()) {
      return new ModuleContext(List.of(), Map.of());
    }
    Map<Long, String> nameById = new HashMap<>();
    List<ModuleMatchRule> rules = new ArrayList<>();
    for (ServiceModuleEntity v : serviceModuleRepo.findAllByProjectVersionId(pvId.get())) {
      if (v.getId() != null && v.getName() != null && !v.getName().isBlank()) {
        nameById.put(v.getId(), v.getName());
      }
      String rp = normalizePath(v.getRootPath());
      if (v.getName() != null && !v.getName().isBlank() && rp != null && !rp.isBlank()) {
        rules.add(new ModuleMatchRule(v.getName(), rp));
      }
    }
    rules.sort((a, b) -> Integer.compare(b.rootPath().length(), a.rootPath().length()));
    return new ModuleContext(rules, nameById);
  }

  private record ModuleContext(List<ModuleMatchRule> rules, Map<Long, String> moduleNameById) {}

  private String normalizePath(String path) {
    if (path == null || path.isBlank()) return null;
    String p = path.replace('\\', '/');
    if (p.startsWith("./")) p = p.substring(2);
    while (p.startsWith("/")) p = p.substring(1);
    while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
    return p;
  }

  private record ModuleMatchRule(String moduleName, String rootPath) {}

  private AnalysisResultSummaryView emptySummary(Long analysisRunId) {
    return new AnalysisResultSummaryView(
        analysisRunId,
        0L,
        0L,
        0L,
        0L,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of()
    );
  }

  private int severityRank(Severity s) {
    if (s == null) return 99;
    return switch (s) {
      case CRITICAL -> 0;
      case HIGH -> 1;
      case MEDIUM -> 2;
      case LOW -> 3;
    };
  }

  private static final class FlowEdgeAccumulator {
    final String sourceModule;
    final String sinkModule;
    final long count;
    final Severity maxSeverity;

    private FlowEdgeAccumulator(String sourceModule, String sinkModule, long count, Severity maxSeverity) {
      this.sourceModule = sourceModule;
      this.sinkModule = sinkModule;
      this.count = count;
      this.maxSeverity = maxSeverity;
    }

    private FlowEdgeAccumulator merge(Severity incoming) {
      return new FlowEdgeAccumulator(
          this.sourceModule,
          this.sinkModule,
          this.count + 1,
          moreSevere(this.maxSeverity, incoming)
      );
    }

    private Severity moreSevere(Severity a, Severity b) {
      int ra = rank(a);
      int rb = rank(b);
      return (ra <= rb) ? a : b;
    }

    private int rank(Severity s) {
      if (s == null) return 99;
      return switch (s) {
        case CRITICAL -> 0;
        case HIGH -> 1;
        case MEDIUM -> 2;
        case LOW -> 3;
      };
    }
  }

  /**
   * taint step의 설명(description)에서 메서드명을 추출한다.
   * - mscan 케이스: source=..., sink=... 패턴을 우선 사용
   * - 그 외 케이스: description 원문을 짧게 잘라 fallback
   */
  private String extractMethodName(String description) {
    if (description == null || description.isBlank()) return null;

    Matcher matcher = METHOD_SIGNATURE_PATTERN.matcher(description);
    if (matcher.find()) {
      String signature = matcher.group(1);
      if (signature != null && !signature.isBlank()) {
        return signature.trim();
      }
    }

    String text = description.trim();
    if (text.length() <= 120) return text;
    return text.substring(0, 120);
  }

  private String firstNonBlank(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) return v;
    }
    return null;
  }
}

