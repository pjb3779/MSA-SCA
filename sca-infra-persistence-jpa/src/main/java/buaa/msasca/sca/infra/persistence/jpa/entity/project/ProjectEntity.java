package buaa.msasca.sca.infra.persistence.jpa.entity.project;

import buaa.msasca.sca.infra.persistence.jpa.entity.base.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
  name = "project",
  indexes = {
    @Index(name = "idx_project_name", columnList = "name")
  }
)
@SequenceGenerator(
  name = "project_seq_gen",
  sequenceName = "project_id_seq",
  allocationSize= 1
)
//일단 어록케이션 1로 설정후 추후에 수정!!
public class ProjectEntity extends AuditedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "progect_seq_gen")
  private Long id;

  @Column(length = 255, nullable = false)
  private String name;

  @Column(columnDefinition = "text")
  private String description;

  @Column(name = "repo_url", length = 1024)
  private String repoUrl;

  private ProjectEntity(String name, String description, String repoUrl) {
    this.name = name;
    this.description = description;
    this.repoUrl = repoUrl;
  }

  public static ProjectEntity create(String name, String description, String repoUrl) {
    return new ProjectEntity(name, description, repoUrl);
  }

  public void changeName(String newName) { this.name = newName; }
  public void changeDescription(String newDesc) { this.description = newDesc; }
  public void changeRepoUrl(String newRepoUrl) { this.repoUrl = newRepoUrl; }
}