package buaa.msasca.sca.core.application.support;

public class WorkspacePathResolver {

    private final String baseDir;

    public WorkspacePathResolver(String baseDir) {
        this.baseDir = baseDir;
    }

    /**
     * project/version에 해당하는 소스 루트 경로를 반환한다.
     *
     * @param projectId project PK
     * @param projectVersionId project_version PK
     * @return source root absolute path
     */
    public String sourceRoot(Long projectId, Long projectVersionId) {
        return baseDir + "/projects/" + projectId + "/versions/" + projectVersionId + "/source";
    }

    /**
     * project/version에 해당하는 업로드(zip) 저장 경로를 반환한다.
     *
     * @param projectId project PK
     * @param projectVersionId project_version PK
     * @param originalFilename 원본 파일명(옵션)
     * @return zip absolute path
     */
    public String uploadZipPath(Long projectId, Long projectVersionId, String originalFilename) {
        String fn = safeFileName(originalFilename, "upload.zip");
        return baseDir + "/projects/" + projectId + "/versions/" + projectVersionId + "/uploads/" + fn;
    }

    /**
     * 파일명에 경로가 섞여 들어오는 경우를 제거하고 기본값을 보장한다.
     *
     * @param original 원본
     * @param fallback 기본값
     * @return 안전한 파일명
     */
    private String safeFileName(String original, String fallback) {
        if (original == null || original.isBlank()) return fallback;
        String s = original.replace("\\", "/");
        int idx = s.lastIndexOf("/");
        String name = (idx >= 0) ? s.substring(idx + 1) : s;
        if (name.isBlank()) return fallback;
        return name;
    }
}