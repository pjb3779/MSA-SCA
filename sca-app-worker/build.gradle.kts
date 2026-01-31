plugins {
  id("org.springframework.boot")
  id("io.spring.dependency-management")
}

dependencies {
  implementation(project(":sca-core"))
  implementation(project(":sca-common"))

  implementation(project(":sca-infra-persistence-jpa"))
  implementation(project(":sca-infra-storage"))
  implementation(project(":sca-infra-runner"))
  implementation(project(":sca-tool-codeql"))
  implementation(project(":sca-tool-mscan"))
  implementation(project(":sca-tool-agent"))

  implementation("org.springframework.boot:spring-boot-starter")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-actuator")

  runtimeOnly("org.postgresql:postgresql")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
}
