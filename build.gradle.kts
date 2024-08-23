plugins {
    java
    alias(libs.plugins.shadow)
}

group = "gg.mew.lassy"
version = "1.1"

dependencies {
    compileOnly(libs.paper)

    implementation(libs.acf.paper)

    compileOnly(libs.plugin.annotations)
    annotationProcessor(libs.plugin.annotations)

    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")

    testCompileOnly("org.projectlombok:lombok:1.18.34")
    testAnnotationProcessor("org.projectlombok:1.18.34")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.shadowJar {
    archiveFileName = "lassy-${project.version}.jar"
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    enabled = false
}

tasks.compileJava {
    options.compilerArgs.add("-parameters")
    options.isFork = true
}