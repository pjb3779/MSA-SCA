plugins {
    id("org.springframework.boot") version "3.3.2" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
    id("org.jetbrains.kotlin.jvm") version "1.9.24" apply false
}

allprojects {
    group = "buaa.msasca"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.apply("java-library")
    plugins.apply("io.spring.dependency-management")

    extensions.configure<JavaPluginExtension>("java") {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    // Spring dependency management DSL
    extensions.configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>("dependencyManagement") {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.2")
        }
    }

    dependencies {
        "compileOnly"("org.projectlombok:lombok")
        "annotationProcessor"("org.projectlombok:lombok")
        "testCompileOnly"("org.projectlombok:lombok")
        "testAnnotationProcessor"("org.projectlombok:lombok")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
