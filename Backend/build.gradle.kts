plugins {
	java
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.panwar2001.orgagent"
version = "0.0.1-SNAPSHOT"

// Versions the Spring Boot BOM does not manage. Declared as plain values rather than as extra
// properties so the IDE can resolve them by reading the script, with no property lookup involved.
val springAiVersion = "2.0.1"
val springdocVersion = "3.1.1"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

// Every dependency, including the Spring Boot Gradle plugin, is a released artifact on Maven
// Central, so no snapshot repository is declared: nothing churns under IntelliJ's feet.
repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")
	implementation("org.springframework.ai:spring-ai-markdown-document-reader")
	implementation("org.springframework.ai:spring-ai-pdf-document-reader")
	implementation("org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc")
	implementation("org.springframework.ai:spring-ai-starter-model-google-genai")
	implementation("org.springframework.ai:spring-ai-starter-model-google-genai-embedding")
	implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")
	implementation("org.springframework.ai:spring-ai-vector-store-advisor")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.flywaydb:flyway-database-postgresql")
	compileOnly("org.projectlombok:lombok")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("org.postgresql:postgresql")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testCompileOnly("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testAnnotationProcessor("org.projectlombok:lombok")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.ai:spring-ai-bom:$springAiVersion")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// Only the executable Spring Boot fat jar is produced, so the container image has
// exactly one artifact to copy.
tasks.jar {
	enabled = false
}
