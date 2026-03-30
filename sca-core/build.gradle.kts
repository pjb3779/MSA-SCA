dependencies {
  api(project(":sca-common"))
  api("jakarta.validation:jakarta.validation-api")
  testImplementation("org.junit.jupiter:junit-jupiter")
  // 단위 테스트 지원(Mockito/AssertJ)
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
