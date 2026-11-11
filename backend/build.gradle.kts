plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "7.0.2"
}

group = "org.grit"
version = "0.0.1-SNAPSHOT"
description = "daynomy"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("software.amazon.awssdk:bom:2.54.7"))
    implementation("software.amazon.awssdk:s3")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    implementation("org.jsoup:jsoup:1.21.2")
    implementation("org.apache.pdfbox:pdfbox:3.0.8")
    implementation("org.apache.httpcomponents.client5:httpclient5")
    implementation("dev.langchain4j:langchain4j:1.22.0")
    implementation("dev.langchain4j:langchain4j-open-ai:1.22.0")
    implementation("dev.langchain4j:langchain4j-http-client-jdk:1.22.0")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.3.2")
    testImplementation("io.rest-assured:rest-assured:6.0.1")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.5")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("spring.test.context.cache.maxSize", "8")
    if (System.getenv("CI_DIAGNOSTICS") == "true") {
        systemProperty("logging.level.org.springframework.test.context.cache", "DEBUG")
    }
    maxHeapSize = "2g"
    if (System.getenv("LEAGUE_BENCHMARK") == "true") maxHeapSize = "3g"
}

// CI and development CD partition the existing test task; builds without the option run every test.
val ciTestSuite = providers.gradleProperty("ciTestSuite").orElse("all")
val heavyCiTests = listOf(
    "org.grit.daynomy.loadtest.Portfolio1000LoadtestToolTest",
    "org.grit.daynomy.league.acceptance.LeagueRankingsLoadtestSeedTest",
)
tasks.named<Test>("test") {
    when (val suite = ciTestSuite.get()) {
        "all" -> Unit
        "main" -> filter { heavyCiTests.forEach { excludeTestsMatching(it) } }
        "heavy" -> filter { heavyCiTests.forEach { includeTestsMatching(it) } }
        else -> throw GradleException("Unknown ciTestSuite: $suite (expected all, main, or heavy)")
    }
}

// Manual dev load-test preparation only; no Spring server or schedulers are started.
tasks.register<JavaExec>("portfolioLoadtest") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("org.grit.daynomy.loadtest.PortfolioLoadtestTool")
}

tasks.register<JavaExec>("portfolio1000Loadtest") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("org.grit.daynomy.loadtest.Portfolio1000LoadtestTool")
    maxHeapSize = "512m"
}

spotless {
    java {
        googleJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
