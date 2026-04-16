package buaa.msasca.sca.core.application.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import buaa.msasca.sca.core.domain.enums.RoleType;
import buaa.msasca.sca.core.domain.enums.Severity;
import buaa.msasca.sca.core.domain.model.AnalysisRun;
import buaa.msasca.sca.core.domain.model.ProjectVersionSourceCache;
import buaa.msasca.sca.core.domain.model.ServiceModule;
import buaa.msasca.sca.core.port.out.persistence.AnalysisRunCommandPort;
import buaa.msasca.sca.core.port.out.persistence.CodeqlFindingPort;
import buaa.msasca.sca.core.port.out.persistence.MscanFindingQueryPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCachePort;
import buaa.msasca.sca.core.port.out.persistence.ServiceModulePort;
import buaa.msasca.sca.core.port.out.persistence.UnifiedTaintRecordCommandPort;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CodeQL + MScan 결과를 analysis_run 단위로 통합해 unified_taint_record/taint_step에 저장한다.
 */
public class UnifiedTaintMergeService {
  private static final Logger log = LoggerFactory.getLogger(UnifiedTaintMergeService.class);
  private static final Pattern CLASS_PATTERN = Pattern.compile("\\bclass\\s+([A-Za-z0-9_\\$]+)");
  private static final Pattern METHOD_PATTERN = Pattern.compile("([A-Za-z0-9_<>\\[\\]\\.,\\s]+)\\s+([A-Za-z0-9_]+)\\s*\\(([^)]*)\\)");

  private final CodeqlFindingPort codeqlFindingPort;
  private final MscanFindingQueryPort mscanFindingQueryPort;
  private final UnifiedTaintRecordCommandPort unifiedTaintRecordCommandPort;
  private final AnalysisRunCommandPort analysisRunCommandPort;
  private final ProjectVersionSourceCachePort sourceCachePort;
  private final ServiceModulePort serviceModulePort;

  public UnifiedTaintMergeService(
      CodeqlFindingPort codeqlFindingPort,
      MscanFindingQueryPort mscanFindingQueryPort,
      UnifiedTaintRecordCommandPort unifiedTaintRecordCommandPort,
      AnalysisRunCommandPort analysisRunCommandPort,
      ProjectVersionSourceCachePort sourceCachePort,
      ServiceModulePort serviceModulePort
  ) {
    this.codeqlFindingPort = codeqlFindingPort;
    this.mscanFindingQueryPort = mscanFindingQueryPort;
    this.unifiedTaintRecordCommandPort = unifiedTaintRecordCommandPort;
    this.analysisRunCommandPort = analysisRunCommandPort;
    this.sourceCachePort = sourceCachePort;
    this.serviceModulePort = serviceModulePort;
  }

  public void mergeAndStore(Long analysisRunId) {
    List<CodeqlFindingPort.CodeqlFindingView> codeql = codeqlFindingPort.findByAnalysisRunId(analysisRunId);
    List<MscanFindingQueryPort.MscanFindingView> mscan = mscanFindingQueryPort.findByAnalysisRunId(analysisRunId);

    Optional<AnalysisRun> runOpt = analysisRunCommandPort.findById(analysisRunId);
    Optional<String> sourceRootOpt = resolveSourceRootFromRun(runOpt, analysisRunId);
    List<ServiceModule> modules = runOpt
        .map(r -> serviceModulePort.findByProjectVersionId(r.projectVersionId()))
        .orElse(List.of());

    List<UnifiedTaintRecordCommandPort.UnifiedTaintUpsert> out = new ArrayList<>();
    boolean[] usedMscan = new boolean[mscan.size()];

    for (CodeqlFindingPort.CodeqlFindingView c : codeql) {
      int idx = bestMatchMscanIndex(c, mscan, usedMscan);
      MscanFindingQueryPort.MscanFindingView m = (idx >= 0) ? mscan.get(idx) : null;
      if (idx >= 0) usedMscan[idx] = true;
      out.add(toUnified(c, m, sourceRootOpt.orElse(null), modules));
    }

    for (int i = 0; i < mscan.size(); i++) {
      if (!usedMscan[i]) {
        out.add(toUnified(null, mscan.get(i), sourceRootOpt.orElse(null), modules));
      }
    }

    unifiedTaintRecordCommandPort.replaceByAnalysisRun(analysisRunId, out);
    log.info("[Unified] merged analysisRunId={} codeql={} mscan={} unified={}",
        analysisRunId, codeql.size(), mscan.size(), out.size());
  }

  private UnifiedTaintRecordCommandPort.UnifiedTaintUpsert toUnified(
      CodeqlFindingPort.CodeqlFindingView c,
      MscanFindingQueryPort.MscanFindingView m,
      String sourceRootPath,
      List<ServiceModule> modules
  ) {
    String vulnType = (c != null && c.ruleId() != null && !c.ruleId().isBlank())
        ? c.ruleId()
        : (m != null ? safe(m.vulId(), "UNKNOWN") : "UNKNOWN");
    String title = (c != null && c.message() != null && !c.message().isBlank())
        ? c.message()
        : ("MScan flow #" + (m != null ? m.flowIndex() : -1));

    String srcFile = firstCodeqlStepFile(c);
    Integer srcLine = firstCodeqlStepLine(c);
    String sinkFile = (c != null && c.primaryFile() != null && !c.primaryFile().isBlank())
        ? c.primaryFile()
        : (m != null ? m.sinkFilePath() : null);
    Integer sinkLine = (c != null && c.primaryLine() != null)
        ? c.primaryLine()
        : (m != null ? m.sinkLine() : null);

    Severity severity = decideSeverity(c, m);
    String description = buildDescription(c, m);
    Long scopeServiceModuleId = resolveScopeServiceModuleId(c, m, sourceRootPath, modules);

    List<UnifiedTaintRecordCommandPort.TaintStepUpsert> steps = new ArrayList<>();
    int stepIndex = 0;
    if (c != null && c.flowSteps() != null && !c.flowSteps().isEmpty()) {
      for (CodeqlFindingPort.FlowStepView s : c.flowSteps()) {
        StepEvidence ev = buildEvidence(sourceRootPath, s.filePath(), s.lineNumber(), s.label());
        Long stepModuleId = resolveStepServiceModuleId(c.serviceModuleId(), s.filePath(), sourceRootPath, modules);
        steps.add(new UnifiedTaintRecordCommandPort.TaintStepUpsert(
            stepIndex++,
            stepModuleId,
            roleForCodeqlStep(s, c.flowSteps().size()),
            s.filePath(),
            s.lineNumber(),
            s.label(),
            ev.className(),
            ev.methodName(),
            ev.methodSignature(),
            ev.codeSnippet()
        ));
      }
    } else if (m != null) {
      StepEvidence srcEv = buildEvidence(sourceRootPath, null, null, safe(m.sourceSignature(), ""));
      Long sourceModuleId = resolveStepServiceModuleId(m.sourceServiceId(), null, sourceRootPath, modules);
      steps.add(new UnifiedTaintRecordCommandPort.TaintStepUpsert(
          stepIndex++,
          sourceModuleId,
          RoleType.SOURCE,
          null,
          null,
          "source=" + safe(m.sourceSignature(), ""),
          srcEv.className(),
          srcEv.methodName(),
          srcEv.methodSignature(),
          srcEv.codeSnippet()
      ));
      StepEvidence sinkEv = buildEvidence(sourceRootPath, m.sinkFilePath(), m.sinkLine(), safe(m.sinkSignature(), ""));
      Long sinkModuleId = resolveStepServiceModuleId(m.sinkServiceId(), m.sinkFilePath(), sourceRootPath, modules);
      steps.add(new UnifiedTaintRecordCommandPort.TaintStepUpsert(
          stepIndex++,
          sinkModuleId,
          RoleType.SINK,
          m.sinkFilePath(),
          m.sinkLine(),
          "sink=" + safe(m.sinkSignature(), ""),
          sinkEv.className(),
          sinkEv.methodName(),
          sinkEv.methodSignature(),
          sinkEv.codeSnippet()
      ));
    }

    return new UnifiedTaintRecordCommandPort.UnifiedTaintUpsert(
        c != null ? c.findingId() : null,
        m != null ? m.findingId() : null,
        scopeServiceModuleId,
        vulnType,
        safe(title, "unknown finding"),
        description,
        severity,
        srcFile,
        srcLine,
        sinkFile,
        sinkLine,
        steps
    );
  }

  /**
   * 통합 레코드의 "분석 스코프" 서비스: CodeQL이면 run detail의 모듈, MScan-only면 source/sink 서비스,
   * ID가 없으면 파일 경로로 등록된 모듈과 매칭한다.
   */
  private Long resolveScopeServiceModuleId(
      CodeqlFindingPort.CodeqlFindingView c,
      MscanFindingQueryPort.MscanFindingView m,
      String sourceRootPath,
      List<ServiceModule> modules
  ) {
    if (c != null && c.serviceModuleId() != null) {
      return c.serviceModuleId();
    }
    if (m != null) {
      if (m.sourceServiceId() != null) {
        return m.sourceServiceId();
      }
      if (m.sinkServiceId() != null) {
        return m.sinkServiceId();
      }
    }
    if (c != null) {
      String fp = firstNonBlankPath(c.primaryFile(), firstCodeqlStepFile(c));
      Long id = resolveServiceModuleIdByPath(fp, sourceRootPath, modules);
      if (id != null) {
        return id;
      }
    }
    if (m != null) {
      Long id = resolveServiceModuleIdByPath(m.sinkFilePath(), sourceRootPath, modules);
      if (id != null) {
        return id;
      }
    }
    return null;
  }

  private static String firstNonBlankPath(String a, String b) {
    if (a != null && !a.isBlank()) {
      return a;
    }
    if (b != null && !b.isBlank()) {
      return b;
    }
    return null;
  }

  private int bestMatchMscanIndex(
      CodeqlFindingPort.CodeqlFindingView c,
      List<MscanFindingQueryPort.MscanFindingView> mscan,
      boolean[] used
  ) {
    int bestIdx = -1;
    int bestScore = -1;
    for (int i = 0; i < mscan.size(); i++) {
      if (used[i]) continue;
      MscanFindingQueryPort.MscanFindingView m = mscan.get(i);
      int score = 0;
      if (samePath(c.primaryFile(), m.sinkFilePath())) score += 4;
      if (closeLine(c.primaryLine(), m.sinkLine(), 5)) score += 3;
      if (containsAnyIgnoreCase(c.message(), m.vulId(), c.ruleId())) score += 1;
      if (score > bestScore) {
        bestScore = score;
        bestIdx = i;
      }
    }
    return bestScore >= 4 ? bestIdx : -1;
  }

  private boolean samePath(String a, String b) {
    if (a == null || b == null) return false;
    String na = a.replace("\\", "/");
    String nb = b.replace("\\", "/");
    return na.equals(nb) || na.endsWith("/" + nb) || nb.endsWith("/" + na);
  }

  private boolean closeLine(Integer a, Integer b, int delta) {
    if (a == null || b == null) return false;
    return Math.abs(a - b) <= delta;
  }

  private boolean containsAnyIgnoreCase(String text, String... keys) {
    if (text == null || text.isBlank()) return false;
    String t = text.toLowerCase(Locale.ROOT);
    for (String k : keys) {
      if (k != null && !k.isBlank() && t.contains(k.toLowerCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
  }

  private Severity decideSeverity(CodeqlFindingPort.CodeqlFindingView c, MscanFindingQueryPort.MscanFindingView m) {
    String level = c != null ? c.level() : null;
    if (level != null) {
      String l = level.toLowerCase(Locale.ROOT);
      if ("error".equals(l) || "high".equals(l)) return Severity.HIGH;
      if ("warning".equals(l) || "medium".equals(l)) return Severity.MEDIUM;
      if ("note".equals(l) || "low".equals(l)) return Severity.LOW;
    }
    if (m != null && m.vulId() != null) {
      String v = m.vulId().toUpperCase(Locale.ROOT);
      if (v.contains("RCE") || v.contains("SQLI") || v.contains("CMD")) return Severity.HIGH;
      if (v.contains("XSS") || v.contains("SSRF") || v.contains("PATH")) return Severity.MEDIUM;
    }
    return Severity.MEDIUM;
  }

  private RoleType roleForCodeqlStep(CodeqlFindingPort.FlowStepView step, int size) {
    if (step.stepIndex() <= 0) return RoleType.SOURCE;
    if (step.stepIndex() >= size - 1) return RoleType.SINK;
    return RoleType.INTERMEDIATE;
  }

  private String firstCodeqlStepFile(CodeqlFindingPort.CodeqlFindingView c) {
    if (c == null || c.flowSteps() == null || c.flowSteps().isEmpty()) return null;
    return c.flowSteps().get(0).filePath();
  }

  private Integer firstCodeqlStepLine(CodeqlFindingPort.CodeqlFindingView c) {
    if (c == null || c.flowSteps() == null || c.flowSteps().isEmpty()) return null;
    return c.flowSteps().get(0).lineNumber();
  }

  private String buildDescription(CodeqlFindingPort.CodeqlFindingView c, MscanFindingQueryPort.MscanFindingView m) {
    StringBuilder sb = new StringBuilder();
    if (c != null) {
      sb.append("codeqlRule=").append(safe(c.ruleId(), "UNKNOWN"));
      if (c.message() != null && !c.message().isBlank()) {
        sb.append(", codeqlMessage=").append(c.message());
      }
    }
    if (m != null) {
      if (sb.length() > 0) sb.append("; ");
      sb.append("mscanVulId=").append(safe(m.vulId(), "UNKNOWN"));
      if (m.rawFlowText() != null && !m.rawFlowText().isBlank()) {
        sb.append(", mscanFlow=").append(m.rawFlowText());
      }
    }
    return sb.toString();
  }

  private String safe(String s, String def) {
    return (s == null || s.isBlank()) ? def : s;
  }

  /**
   * 이미 조회한 run 기준으로 source cache 루트 경로를 얻는다.
   * 조회 실패 시 Optional.empty()를 반환하고 머지는 계속 진행한다.
   */
  private Optional<String> resolveSourceRootFromRun(Optional<AnalysisRun> runOpt, Long analysisRunId) {
    try {
      if (runOpt.isEmpty()) return Optional.empty();
      Optional<ProjectVersionSourceCache> cacheOpt =
          sourceCachePort.findValidByProjectVersionId(runOpt.get().projectVersionId());
      return cacheOpt.map(ProjectVersionSourceCache::storagePath);
    } catch (Exception e) {
      log.warn("[Unified] source root resolve failed analysisRunId={}", analysisRunId, e);
      return Optional.empty();
    }
  }

  /**
   * CodeQL/MScan이 넘긴 service_module_id가 없으면, 파일 경로와 등록된 모듈 root_path로 FK를 보강한다.
   * ({@code JpaUnifiedResultQueryAdapter#resolveModuleName} 과 동일한 longest-match 의도)
   */
  private Long resolveStepServiceModuleId(
      Long existing,
      String filePath,
      String sourceRootPath,
      List<ServiceModule> modules
  ) {
    if (existing != null) {
      return existing;
    }
    return resolveServiceModuleIdByPath(filePath, sourceRootPath, modules);
  }

  private Long resolveServiceModuleIdByPath(String filePath, String sourceRootPath, List<ServiceModule> modules) {
    if (modules == null || modules.isEmpty() || filePath == null || filePath.isBlank()) {
      return null;
    }
    String n = normalizePath(filePath);
    if (n == null || n.isBlank()) {
      return null;
    }
    String relNorm = null;
    String relativized = tryRelativizeBelowSourceRoot(sourceRootPath, filePath);
    if (relativized != null && !relativized.isBlank()) {
      relNorm = normalizePath(relativized);
    }

    ServiceModule best = null;
    int bestScore = -1;
    for (ServiceModule mod : modules) {
      if (mod == null || mod.id() == null) {
        continue;
      }
      int score = modulePathMatchScore(relNorm, n, mod);
      if (score > bestScore) {
        bestScore = score;
        best = mod;
      }
    }
    return best != null ? best.id() : null;
  }

  /**
   * root_path 포함 매칭과 Gradle 모듈명(경로 세그먼트) 매칭을 같은 점수 체계로 비교한다.
   * 가장 긴 일치(문자 수)를 가진 모듈을 선택한다.
   */
  private static int modulePathMatchScore(String relNorm, String fullNorm, ServiceModule mod) {
    String root = normalizePath(mod.rootPath());
    String name = mod.name();
    int score = -1;
    if (root != null && !root.isBlank()) {
      boolean match =
          matchesModuleRoot(relNorm, root)
              || matchesModuleRoot(fullNorm, root)
              || matchesModulePathInfix(fullNorm, root);
      if (match) {
        score = Math.max(score, root.length());
      }
    }
    if (name != null && !name.isBlank()) {
      boolean nameMatch =
          matchesModuleNameSegment(fullNorm, name)
              || (relNorm != null && matchesModuleNameSegment(relNorm, name));
      if (nameMatch) {
        score = Math.max(score, name.length());
      }
    }
    return score;
  }

  private static boolean matchesModuleNameSegment(String fp, String name) {
    if (fp == null || fp.isBlank()) {
      return false;
    }
    return fp.contains("/" + name + "/")
        || fp.endsWith("/" + name)
        || fp.equals(name);
  }

  private static boolean matchesModuleRoot(String fp, String root) {
    if (fp == null) {
      return false;
    }
    return fp.equals(root) || fp.startsWith(root + "/");
  }

  /**
   * 절대 경로에 모듈 상대 root가 중간에 포함되는 경우 (예: .../spring-cloud-skipper-server-core/src/...)
   */
  private static boolean matchesModulePathInfix(String fullNorm, String root) {
    if (fullNorm == null) {
      return false;
    }
    return fullNorm.contains("/" + root + "/")
        || fullNorm.endsWith("/" + root)
        || fullNorm.equals(root);
  }

  private static String normalizePath(String path) {
    if (path == null || path.isBlank()) {
      return null;
    }
    String p = path.replace('\\', '/');
    if (p.startsWith("./")) {
      p = p.substring(2);
    }
    while (p.startsWith("/")) {
      p = p.substring(1);
    }
    while (p.endsWith("/")) {
      p = p.substring(0, p.length() - 1);
    }
    return p;
  }

  private static String tryRelativizeBelowSourceRoot(String sourceRootPath, String filePath) {
    if (sourceRootPath == null || sourceRootPath.isBlank() || filePath == null || filePath.isBlank()) {
      return null;
    }
    Path root = toLocalPath(sourceRootPath);
    if (root == null) {
      return null;
    }
    try {
      Path absRoot = root.toAbsolutePath().normalize();
      Path file = Path.of(filePath);
      if (!file.isAbsolute()) {
        file = absRoot.resolve(filePath).normalize();
      } else {
        file = file.toAbsolutePath().normalize();
      }
      if (file.startsWith(absRoot)) {
        return absRoot.relativize(file).toString().replace('\\', '/');
      }
    } catch (Exception ignored) {
      // ignore
    }
    return null;
  }

  private static Path toLocalPath(String storagePath) {
    if (storagePath == null || storagePath.isBlank()) {
      return null;
    }
    String s = storagePath.trim();
    try {
      if (s.startsWith("file:")) {
        return Path.of(URI.create(s));
      }
      return Path.of(s);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * 파일/라인/설명을 기반으로 코드 증거를 생성한다.
   * - class/method는 간단한 정규식으로 추출
   * - codeSnippet은 대상 라인 주변 5줄을 저장
   */
  private StepEvidence buildEvidence(String rootPath, String filePath, Integer line, String fallbackSignature) {
    if (rootPath == null || rootPath.isBlank() || filePath == null || filePath.isBlank()) {
      return StepEvidence.fromSignature(fallbackSignature);
    }
    try {
      Path p = resolvePath(rootPath, filePath);
      if (p == null || !Files.exists(p)) {
        return StepEvidence.fromSignature(fallbackSignature);
      }
      List<String> lines = Files.readAllLines(p);
      int target = (line == null || line <= 0) ? 1 : line;
      int idx = Math.max(0, Math.min(target - 1, Math.max(0, lines.size() - 1)));

      String className = findClassName(lines, idx);
      String methodSig = findMethodSignature(lines, idx);
      String methodName = extractMethodName(methodSig);
      String snippet = extractSnippet(lines, idx, 2);

      return new StepEvidence(className, methodName, methodSig, snippet);
    } catch (Exception e) {
      return StepEvidence.fromSignature(fallbackSignature);
    }
  }

  private Path resolvePath(String rootPath, String filePath) {
    String normalized = filePath.replace("\\", "/");
    if (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    return Path.of(rootPath).resolve(normalized).normalize();
  }

  private String findClassName(List<String> lines, int from) {
    for (int i = from; i >= 0; i--) {
      Matcher m = CLASS_PATTERN.matcher(lines.get(i));
      if (m.find()) return m.group(1);
    }
    return null;
  }

  private String findMethodSignature(List<String> lines, int from) {
    for (int i = from; i >= Math.max(0, from - 120); i--) {
      String l = lines.get(i).trim();
      if (l.startsWith("//") || l.startsWith("*")) continue;
      Matcher m = METHOD_PATTERN.matcher(l);
      if (m.find() && l.contains("{")) return l;
    }
    return null;
  }

  private String extractMethodName(String signature) {
    if (signature == null || signature.isBlank()) return null;
    Matcher m = METHOD_PATTERN.matcher(signature);
    if (m.find()) return m.group(2);
    return null;
  }

  private String extractSnippet(List<String> lines, int center, int radius) {
    int from = Math.max(0, center - radius);
    int to = Math.min(lines.size(), center + radius + 1);
    StringBuilder sb = new StringBuilder();
    for (int i = from; i < to; i++) {
      sb.append(i + 1).append(": ").append(lines.get(i)).append('\n');
    }
    return sb.toString();
  }

  /**
   * step 증거 묶음.
   * 파일 기반 추출 실패 시 signature 기반 최소값으로 채운다.
   */
  private record StepEvidence(
      String className,
      String methodName,
      String methodSignature,
      String codeSnippet
  ) {
    private static StepEvidence fromSignature(String signature) {
      String sig = (signature == null || signature.isBlank()) ? null : signature;
      return new StepEvidence(null, null, sig, null);
    }
  }
}

