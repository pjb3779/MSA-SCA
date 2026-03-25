package buaa.msasca.sca.core.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import buaa.msasca.sca.core.domain.enums.RoleType;
import buaa.msasca.sca.core.domain.enums.Severity;
import buaa.msasca.sca.core.port.out.persistence.CodeqlFindingPort;
import buaa.msasca.sca.core.port.out.persistence.MscanFindingQueryPort;
import buaa.msasca.sca.core.port.out.persistence.UnifiedTaintRecordCommandPort;

/**
 * CodeQL + MScan 결과를 analysis_run 단위로 통합해 unified_taint_record/taint_step에 저장한다.
 */
public class UnifiedTaintMergeService {
  private static final Logger log = LoggerFactory.getLogger(UnifiedTaintMergeService.class);

  private final CodeqlFindingPort codeqlFindingPort;
  private final MscanFindingQueryPort mscanFindingQueryPort;
  private final UnifiedTaintRecordCommandPort unifiedTaintRecordCommandPort;

  public UnifiedTaintMergeService(
      CodeqlFindingPort codeqlFindingPort,
      MscanFindingQueryPort mscanFindingQueryPort,
      UnifiedTaintRecordCommandPort unifiedTaintRecordCommandPort
  ) {
    this.codeqlFindingPort = codeqlFindingPort;
    this.mscanFindingQueryPort = mscanFindingQueryPort;
    this.unifiedTaintRecordCommandPort = unifiedTaintRecordCommandPort;
  }

  public void mergeAndStore(Long analysisRunId) {
    List<CodeqlFindingPort.CodeqlFindingView> codeql = codeqlFindingPort.findByAnalysisRunId(analysisRunId);
    List<MscanFindingQueryPort.MscanFindingView> mscan = mscanFindingQueryPort.findByAnalysisRunId(analysisRunId);

    List<UnifiedTaintRecordCommandPort.UnifiedTaintUpsert> out = new ArrayList<>();
    boolean[] usedMscan = new boolean[mscan.size()];

    for (CodeqlFindingPort.CodeqlFindingView c : codeql) {
      int idx = bestMatchMscanIndex(c, mscan, usedMscan);
      MscanFindingQueryPort.MscanFindingView m = (idx >= 0) ? mscan.get(idx) : null;
      if (idx >= 0) usedMscan[idx] = true;
      out.add(toUnified(c, m));
    }

    for (int i = 0; i < mscan.size(); i++) {
      if (!usedMscan[i]) {
        out.add(toUnified(null, mscan.get(i)));
      }
    }

    unifiedTaintRecordCommandPort.replaceByAnalysisRun(analysisRunId, out);
    log.info("[Unified] merged analysisRunId={} codeql={} mscan={} unified={}",
        analysisRunId, codeql.size(), mscan.size(), out.size());
  }

  private UnifiedTaintRecordCommandPort.UnifiedTaintUpsert toUnified(
      CodeqlFindingPort.CodeqlFindingView c,
      MscanFindingQueryPort.MscanFindingView m
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

    List<UnifiedTaintRecordCommandPort.TaintStepUpsert> steps = new ArrayList<>();
    int stepIndex = 0;
    if (c != null && c.flowSteps() != null && !c.flowSteps().isEmpty()) {
      for (CodeqlFindingPort.FlowStepView s : c.flowSteps()) {
        steps.add(new UnifiedTaintRecordCommandPort.TaintStepUpsert(
            stepIndex++,
            c.serviceModuleId(),
            roleForCodeqlStep(s, c.flowSteps().size()),
            s.filePath(),
            s.lineNumber(),
            s.label()
        ));
      }
    } else if (m != null) {
      steps.add(new UnifiedTaintRecordCommandPort.TaintStepUpsert(
          stepIndex++,
          m.sourceServiceId(),
          RoleType.SOURCE,
          null,
          null,
          "source=" + safe(m.sourceSignature(), "")
      ));
      steps.add(new UnifiedTaintRecordCommandPort.TaintStepUpsert(
          stepIndex++,
          m.sinkServiceId(),
          RoleType.SINK,
          m.sinkFilePath(),
          m.sinkLine(),
          "sink=" + safe(m.sinkSignature(), "")
      ));
    }

    return new UnifiedTaintRecordCommandPort.UnifiedTaintUpsert(
        c != null ? c.findingId() : null,
        m != null ? m.findingId() : null,
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
}

