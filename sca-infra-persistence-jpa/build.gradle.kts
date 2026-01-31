dependencies {
  api(project(":sca-core"))
  api(project(":sca-common"))

  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  runtimeOnly("org.postgresql:postgresql")

  compileOnly("org.projectlombok:lombok")
  annotationProcessor("org.projectlombok:lombok")
  testCompileOnly("org.projectlombok:lombok")
  testAnnotationProcessor("org.projectlombok:lombok")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("com.h2database:h2")
}
