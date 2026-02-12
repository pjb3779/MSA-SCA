package buaa.msasca.sca.app.worker.config.props;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sca.build.images")
public record BuildImagesProperties(
    String defaultImage,
    List<Rule> rules
) {
  public record Rule(
      String buildTool,
      String jdkVersion,
      String image
  ) {}
}