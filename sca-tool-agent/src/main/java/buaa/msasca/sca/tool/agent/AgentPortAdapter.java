package buaa.msasca.sca.tool.agent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import buaa.msasca.sca.core.port.out.persistence.CodeqlFindingPort;
import buaa.msasca.sca.core.port.out.persistence.SanitizerResultCommandPort;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import buaa.msasca.sca.core.domain.model.ServiceModule;
import buaa.msasca.sca.core.port.out.tool.AgentPort;
import buaa.msasca.sca.tool.agent.pipeline.AgentOptionsLlmWriter;
import buaa.msasca.sca.tool.agent.pipeline.GatewayEntryLlmExtractor;
import buaa.msasca.sca.tool.agent.pipeline.JudgedSanitizerCandidate;
import buaa.msasca.sca.tool.agent.pipeline.ModulePrefilter;
import buaa.msasca.sca.tool.agent.pipeline.SanitizerCandidate;
import buaa.msasca.sca.tool.agent.pipeline.SanitizerCandidateJudge;
import buaa.msasca.sca.tool.agent.pipeline.SanitizerCandidateMiner;
import buaa.msasca.sca.tool.agent.pipeline.SanitizerCandidateVerifier;
import buaa.msasca.sca.tool.agent.pipeline.SanitizerStatus;
import buaa.msasca.sca.tool.agent.pipeline.VerifiedSanitizerCandidate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Agent 포트 어댑터(오케스트레이터).
 *
 * <p><b>책임:</b></p>
 * <ul>
 *   <li>1) 모듈 경량 선별 (ModulePrefilter)</li>
 *   <li>2) Sanitizer 지식 파이프라인: Miner → Judge → Verifier (CodeQL SARIF 활용)</li>
 *   <li>3) Gateway entry 추출 (LLM)</li>
 *   <li>4) MScan 주입용 산출물 파일 (registry/entries/options) 생성</li>
 * </ul>
 *
 * <p><b>Sanitizer 파이프라인 단계 요약:</b></p>
 * <ul>
 *   <li><b>Miner:</b> 패턴 스캔 + CodeQL flow에서 후보 수집. LLM 호출 없음.</li>
 *   <li><b>Judge:</b> 후보 10개 단위 배치 LLM 판정 (actionType, confidence, vulnTypes).</li>
 *   <li><b>Verifier:</b> Judge 결과를 규칙으로 재검증해 CONFIRMED/CONDITIONAL/REJECTED 등 최종 상태 부여. LLM 호출 없음.</li>
 * </ul>
 */
public class AgentPortAdapter implements AgentPort {
  private static final Logger log = LoggerFactory.getLogger(AgentPortAdapter.class);
  private static final ObjectMapper OM = new ObjectMapper();
  private final CodeqlFindingPort codeqlFindingPort;
  private final String llmApiKey;
  private final String llmBaseUrl;
  private final String llmModel;
  private final ModulePrefilter modulePrefilter = new ModulePrefilter();
  private final SanitizerCandidateMiner candidateMiner = new SanitizerCandidateMiner();
  private final SanitizerCandidateVerifier candidateVerifier = new SanitizerCandidateVerifier();

  public AgentPortAdapter(
      CodeqlFindingPort codeqlFindingPort,
      String llmApiKey,
      String llmBaseUrl,
      String llmModel
  ) {
    this.codeqlFindingPort = codeqlFindingPort;
    this.llmApiKey = llmApiKey;
    this.llmBaseUrl = llmBaseUrl;
    this.llmModel = llmModel;
  }

  @Override
  public List<ServiceModule> prefilterModules(
      Long toolRunId,
      Long projectVersionId,
      String sourcePath,
      List<ServiceModule> modules
  ) {
    List<ServiceModule> kept = modulePrefilter.filter(modules, sourcePath);

    log.info(
        "[Agent] prefilter done toolRunId={} pv={} kept={} excluded={}",
        toolRunId, projectVersionId, kept.size(), Math.max(0, modules.size() - kept.size()));
    return kept;
  }

  @Override
  public List<PrefilterDecision> prefilterDecisions(
      Long toolRunId,
      Long projectVersionId,
      String sourcePath,
      List<ServiceModule> modules
  ) {
    List<ModulePrefilter.PrefilterDecision> decisions = modulePrefilter.decide(modules, sourcePath);
    List<PrefilterDecision> out = new java.util.ArrayList<>();
    for (ModulePrefilter.PrefilterDecision d : decisions) {
      out.add(new PrefilterDecision(
          d.module().id(),
          d.selected(),
          d.reason()
      ));
    }
    return out;
  }

  @Override
  public AgentKnowledge buildKnowledge(
      Long toolRunId,
      Long projectVersionId,
      Long analysisRunId,
      String sourcePath,
      String gatewayYamlPathOnHost
  ) {
    try {
      Path root = Path.of(sourcePath);
      Path agentDir = root.resolve(".msasca/agent");
      Files.createDirectories(agentDir);

      Path sanitizerFile = agentDir.resolve("sanitizer-registry.json");
      Path reviewedFile = agentDir.resolve("sanitizer-reviewed.json");
      Path pipelineFile = agentDir.resolve("agent-candidates-pipeline.json");
      Path gatewayFile = agentDir.resolve("gateway-entries.json");
      Path optionsFile = agentDir.resolve("options-agent.yml");

      // CodeQL SARIF 결과 조회 (MSCAN_ONLY 모드 등 CodeQL 미실행 시 빈 리스트)
      var codeqlFindings = (codeqlFindingPort != null && analysisRunId != null)
          ? codeqlFindingPort.findByAnalysisRunId(analysisRunId)
          : List.<CodeqlFindingPort.CodeqlFindingView>of();

      // ========== 1단계: Miner - sanitizer 후보 수집 ==========
      // 소스 패턴(메서드명 키워드) + CodeQL flow step에서 후보 추출. LLM 호출 없음.
      List<SanitizerCandidate> candidates = candidateMiner.mine(root, codeqlFindings);
      writeCandidatePipelineProgress(pipelineFile, candidates, null, null);

      // ========== 2단계: Judge - 후보별 LLM 판정 ==========
      // 후보를 10개 단위로 묶어 LLM 배치 판정 (actionType, confidence, vulnTypes, reasoning).
      SanitizerCandidateJudge candidateJudge = new SanitizerCandidateJudge(llmApiKey, llmBaseUrl, llmModel);
      List<JudgedSanitizerCandidate> judged = candidateJudge.judge(candidates);
      writeCandidatePipelineProgress(pipelineFile, candidates, judged, null);

      // ========== 3단계: Verifier - 보수적 최종 검증 ==========
      // Judge 결과를 규칙으로 재검증해 CONFIRMED/CONDITIONAL/NEEDS_REVIEW/REJECTED 부여. LLM 호출 없음.
      List<VerifiedSanitizerCandidate> verified = candidateVerifier.verify(judged);
      writeCandidatePipelineProgress(pipelineFile, candidates, judged, verified);
      // CONFIRMED만 MScan이 사용할 sanitizer-registry.json에 기록
      writeSanitizerRegistry(sanitizerFile, verified);
      // 전체 판정 결과(모든 status)를 연구/리뷰용 sanitizer-reviewed.json에 기록
      writeReviewedCandidates(reviewedFile, verified);

      // Gateway 규칙: LLM으로 external/internal entry 분류 (gateway.yml 필수)
      GatewayEntryLlmExtractor gatewayExtractor = new GatewayEntryLlmExtractor(llmApiKey, llmBaseUrl, llmModel);
      GatewayEntryLlmExtractor.GatewayEntries entries = gatewayExtractor.extract(gatewayYamlPathOnHost);
      writeGatewayEntries(gatewayFile, entries);

      // MScan 실행 시 참조할 sidecar 옵션 파일: LLM으로 YAML 생성
      AgentOptionsLlmWriter optionsWriter = new AgentOptionsLlmWriter(llmApiKey, llmBaseUrl, llmModel);
      String optionsYaml = optionsWriter.write(
          verified,
          entries,
          toContainerPath(root, sanitizerFile),
          toContainerPath(root, gatewayFile)
      );
      Files.writeString(optionsFile, optionsYaml, StandardCharsets.UTF_8);

      long confirmed = verified.stream().filter(v -> v.status() == SanitizerStatus.CONFIRMED).count();
      long conditional = verified.stream().filter(v -> v.status() == SanitizerStatus.CONDITIONAL).count();
      long review = verified.stream().filter(v -> v.status() == SanitizerStatus.NEEDS_REVIEW).count();
      String summary = "candidates=" + candidates.size()
          + ", confirmedSanitizers=" + confirmed
          + ", conditional=" + conditional
          + ", needsReview=" + review
          + ", externalEntries=" + entries.externalEntries().size()
          + ", internalEntries=" + entries.internalEntries().size();

      log.info(
          "[Agent] knowledge built toolRunId={} pv={} sanitizer={} gateway={} options={} {}",
          toolRunId, projectVersionId, sanitizerFile, gatewayFile, optionsFile, summary
      );

      List<SanitizerResultCommandPort.SanitizerResultRow> sanitizerResults = new ArrayList<>();
      for (VerifiedSanitizerCandidate v : verified) {
        sanitizerResults.add(new SanitizerResultCommandPort.SanitizerResultRow(
            v.candidate().methodName(),
            v.candidate().filePath(),
            v.status().name(),
            v.actionType(),
            v.vulnTypes(),
            v.reasoning()
        ));
      }

      return new AgentKnowledge(
          sanitizerFile.toString(),
          gatewayFile.toString(),
          optionsFile.toString(),
          summary,
          sanitizerResults
      );
    } catch (Exception e) {
      String detail = buildFailureDetail(e);
      log.error("[Agent] knowledge build failed toolRunId={} pv={} detail={}", toolRunId, projectVersionId, detail, e);
      throw new IllegalStateException(
          "Agent knowledge build failed: toolRunId=" + toolRunId + ", pv=" + projectVersionId + ", detail=" + detail,
          e
      );
    }
  }

  /** MScan용 sanitizer registry. CONFIRMED 상태만 기록한다. */
  private void writeSanitizerRegistry(Path out, List<VerifiedSanitizerCandidate> verified) throws Exception {
    ObjectNode root = OM.createObjectNode();
    ArrayNode arr = root.putArray("confirmed_sanitizers");
    for (VerifiedSanitizerCandidate v : verified) {
      // registry에는 고신뢰(CONFIRMED) 상태만 반영 (MScan이 참조)
      if (v.status() != SanitizerStatus.CONFIRMED) {
        continue;
      }
      ObjectNode n = arr.addObject();
      n.put("method", v.candidate().methodName());
      n.put("status", "CONFIRMED");
      n.put("action", v.actionType());
      ArrayNode vulns = n.putArray("vuln_types");
      for (String vt : v.vulnTypes()) {
        vulns.add(vt);
      }
      n.put("reasoning", v.reasoning());
      n.put("file", v.candidate().filePath());
    }
    Files.writeString(out, OM.writerWithDefaultPrettyPrinter().writeValueAsString(root), StandardCharsets.UTF_8);
  }

  /** gateway YAML에서 추출한 external/internal entry 목록을 JSON으로 저장 */
  private void writeGatewayEntries(Path out, GatewayEntryLlmExtractor.GatewayEntries entries) throws Exception {
    // gateway-entries.json은 사람이 바로 리뷰하기 쉽도록 고정 포맷으로 기록한다.
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append("  \"external_entries\": [\n");
    for (int i = 0; i < entries.externalEntries().size(); i++) {
      String s = entries.externalEntries().get(i);
      sb.append("    ").append(OM.writeValueAsString(s));
      if (i < entries.externalEntries().size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append("  ],\n");
    sb.append("  \"internal_entries\": [\n");
    for (int i = 0; i < entries.internalEntries().size(); i++) {
      String s = entries.internalEntries().get(i);
      sb.append("    ").append(OM.writeValueAsString(s));
      if (i < entries.internalEntries().size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append("  ]\n");
    sb.append("}\n");
    Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
  }

  /** 연구/리뷰용 전체 판정 결과 (CONFIRMED/CONDITIONAL/NEEDS_REVIEW/REJECTED 모두) 보관 */
  private void writeReviewedCandidates(Path out, List<VerifiedSanitizerCandidate> verified) throws Exception {
    ObjectNode root = OM.createObjectNode();
    ArrayNode arr = root.putArray("sanitizer_candidates");
    for (VerifiedSanitizerCandidate v : verified) {
      ObjectNode n = arr.addObject();
      n.put("method", v.candidate().methodName());
      n.put("file", v.candidate().filePath());
      n.put("status", v.status().name());
      n.put("action", v.actionType());
      ArrayNode vulns = n.putArray("vuln_types");
      for (String vt : v.vulnTypes()) {
        vulns.add(vt);
      }
      ArrayNode signals = n.putArray("signals");
      for (String sg : v.candidate().signals()) {
        signals.add(sg);
      }
      n.put("reasoning", v.reasoning());
    }
    Files.writeString(out, OM.writerWithDefaultPrettyPrinter().writeValueAsString(root), StandardCharsets.UTF_8);
  }

  private String toUnixPath(Path p) {
    return p.toString().replace("\\", "/");
  }

  /**
   * 호스트 경로를 Docker 컨테이너 경로(/work/src/...)로 변환한다.
   * mscan validator는 /work/src 접두 경로를 기준으로 실제 파일 존재를 검증한다.
   */
  private String toContainerPath(Path sourceRoot, Path filePath) {
    Path absRoot = sourceRoot.toAbsolutePath().normalize();
    Path absFile = filePath.toAbsolutePath().normalize();
    Path rel = absRoot.relativize(absFile);
    String unixRel = rel.toString().replace("\\", "/");
    return "/work/src/" + unixRel;
  }

  /**
   * tool-error.log에 원인을 더 잘 남기기 위해 예외 체인을 한 줄 요약한다.
   * 예: IllegalStateException: xx <- ConfigException: yy <- UnrecognizedPropertyException: zz
   */
  private String buildFailureDetail(Throwable t) {
    if (t == null) return "unknown";
    StringBuilder sb = new StringBuilder();
    Throwable cur = t;
    int depth = 0;
    while (cur != null && depth < 5) {
      if (depth > 0) sb.append(" <- ");
      sb.append(cur.getClass().getSimpleName());
      String msg = cur.getMessage();
      if (msg != null && !msg.isBlank()) {
        sb.append(": ").append(msg.replace('\n', ' ').replace('\r', ' '));
      }
      cur = cur.getCause();
      depth++;
    }
    return sb.toString();
  }

  /**
   * Miner/Judge/Verifier 단계별 후보 진행 현황을 agent-candidates-pipeline.json에 기록한다.
   * 각 단계 완료 직후 호출되어 동일 파일을 최신 상태로 덮어쓴다.
   *
   * @param out 출력 파일 경로
   * @param candidates Miner 단계 결과 (null이면 stages.miner는 비움)
   * @param judged Judge 단계 결과 (null이면 stages.judge는 비움)
   * @param verified Verifier 단계 결과 (null이면 stages.verifier는 비움)
   */
  private void writeCandidatePipelineProgress(
      Path out,
      List<SanitizerCandidate> candidates,
      List<JudgedSanitizerCandidate> judged,
      List<VerifiedSanitizerCandidate> verified
  ) {
    try {
      ObjectNode root = OM.createObjectNode();
      root.put("generatedAt", Instant.now().toString());

      ObjectNode stages = root.putObject("stages");

      // Miner 단계: methodName, filePath, signals, codeql 연관 정보
      ObjectNode miner = stages.putObject("miner");
      ArrayNode minerArr = miner.putArray("candidates");
      int minerCount = 0;
      if (candidates != null) {
        for (SanitizerCandidate c : candidates) {
          ObjectNode n = minerArr.addObject();
          n.put("methodName", c.methodName());
          n.put("filePath", c.filePath());
          ArrayNode signals = n.putArray("signals");
          for (String s : c.signals()) {
            signals.add(s);
          }
          n.put("codeqlFindingId", c.codeqlFindingId());
          n.put("codeqlRuleId", c.codeqlRuleId());
          minerCount++;
        }
      }
      miner.put("count", minerCount);

      // Judge 단계: actionType, confidence, vulnTypes
      ObjectNode judgeNode = stages.putObject("judge");
      ArrayNode judgeArr = judgeNode.putArray("candidates");
      int judgeCount = 0;
      if (judged != null) {
        for (JudgedSanitizerCandidate j : judged) {
          ObjectNode n = judgeArr.addObject();
          n.put("methodName", j.candidate().methodName());
          n.put("filePath", j.candidate().filePath());
          n.put("actionType", j.actionType());
          n.put("confidence", j.confidence());
          ArrayNode vulns = n.putArray("vulnTypes");
          for (String vt : j.vulnTypes()) {
            vulns.add(vt);
          }
          judgeCount++;
        }
      }
      judgeNode.put("count", judgeCount);

      // Verifier 단계: 최종 status, actionType, reasoning
      ObjectNode verifierNode = stages.putObject("verifier");
      ArrayNode verifyArr = verifierNode.putArray("candidates");
      int verifyCount = 0;
      if (verified != null) {
        for (VerifiedSanitizerCandidate v : verified) {
          ObjectNode n = verifyArr.addObject();
          n.put("methodName", v.candidate().methodName());
          n.put("filePath", v.candidate().filePath());
          n.put("status", v.status().name());
          n.put("actionType", v.actionType());
          n.put("reasoning", v.reasoning());
          verifyCount++;
        }
      }
      verifierNode.put("count", verifyCount);

      Files.writeString(out, OM.writerWithDefaultPrettyPrinter().writeValueAsString(root), StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.warn("[Agent] failed to write candidate pipeline progress: {}", e.getMessage());
    }
  }
}
