plugins {
	java
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
	id("info.solidsoft.pitest") version "1.19.0"
}

group = "com.seatreservation"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

tasks.withType<JavaCompile> {
	options.compilerArgs.add("-parameters")
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("com.fasterxml.jackson.core:jackson-databind")
	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
	implementation("org.springframework.boot:spring-boot-starter-log4j2")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("org.mariadb.jdbc:mariadb-java-client")
	configurations.all {
		exclude("org.springframework.boot", "spring-boot-starter-logging")
	}
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-webmvc-test")
	testImplementation("org.mockito:mockito-core")
	testImplementation("org.mockito:mockito-junit-jupiter")
	testImplementation("com.h2database:h2")
	testImplementation("org.assertj:assertj-core:3.25.3")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.test {
	useJUnitPlatform {
		excludeTags("smoke")
	}
}

tasks.register<Test>("smokeTest") {
	description = "Runs only the smoke tests (tag \"smoke\")"
	group = "verification"

	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath

	useJUnitPlatform {
		includeTags("smoke")
	}
}

pitest {
	junit5PluginVersion.set("1.2.3")
	pitestVersion.set("1.25.5")
	targetClasses.set(listOf("com.example.demo.*"))
	targetTests.set(listOf("com.example.demo.*"))
	excludedClasses.set(listOf(
		"com.example.demo.DemoApplication",
		"com.example.demo.config.*",
		"com.example.demo.web.dto.*",
		"com.example.demo.domain.SeatStatus",
		"com.example.demo.domain.ReservationStatus",
		"com.example.demo.event.*",
		"com.example.demo.sse.SeatEventMessage",
		"com.example.demo.exception.*"
	))
	mutators.set(listOf("STRONGER"))
	threads.set(4)
	outputFormats.set(listOf("HTML", "XML"))
}
