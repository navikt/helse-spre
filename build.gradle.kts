plugins {
    kotlin("jvm") version "2.3.20"
}

val junitJupiterVersion = "6.0.3"
val rapidsAndRiversVersion = "2026011411051768385145.e8ebad1177b4"
val ktorVersion = "3.2.3" // should be set to same value as rapids and rivers
val mockkVersion = "1.13.17"
val hikariCPVersion = "6.3.0"
val kotliqueryVersion = "1.9.1"
val postgresqlVersion = "42.7.7"
val flywayCoreVersion = "11.5.0"
val tbdLibsVersion = "2026.01.22-09.16-1d3f6039"

allprojects {
    group = "no.nav.helse.spre"
    version = properties["version"] ?: "local-build"

    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        if (!erFellesmodul()) implementation(project(":felles"))

        testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    // Sett opp repositories basert på om vi kjører i CI eller ikke
    // Jf. https://github.com/navikt/utvikling/blob/main/docs/teknisk/Konsumere%20biblioteker%20fra%20Github%20Package%20Registry.md
    repositories {
        mavenCentral()
        if (providers.environmentVariable("GITHUB_ACTIONS").orNull == "true") {
            maven {
                url = uri("https://maven.pkg.github.com/navikt/maven-release")
                credentials {
                    username = "token"
                    password = providers.environmentVariable("GITHUB_TOKEN").orNull!!
                }
            }
        } else {
            maven("https://repo.adeo.no/repository/github-package-registry-navikt/")
        }
    }

    kotlin {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of("21"))
        }
    }

    tasks {
        withType<Test> {
            useJUnitPlatform()
            testLogging {
                events("skipped", "failed")
            }
        }
    }
}

subprojects {
    ext {
        set("ktorVersion", ktorVersion)
        set("rapidsAndRiversVersion", rapidsAndRiversVersion)
        set("mockkVersion", mockkVersion)
        set("hikariCPVersion", hikariCPVersion)
        set("kotliqueryVersion", kotliqueryVersion)
        set("postgresqlVersion", postgresqlVersion)
        set("flywayCoreVersion", flywayCoreVersion)
        set("tbdLibsVersion", tbdLibsVersion)
    }
    tasks {
        if (!project.erFellesmodul()) {
            named<Jar>("jar") {
                archiveBaseName.set("app")

                val mainClass = project.mainClass()

                doLast {
                    val mainClassFound = this.project.sourceSets.findByName("main")?.let { sourceSet ->
                        sourceSet.output.classesDirs.asFileTree.any { it.path.contains(mainClass.replace(".", File.separator)) }
                    } ?: false

                    if (!mainClassFound) throw RuntimeException("Kunne ikke finne main class: $mainClass")
                }

                manifest {
                    attributes["Main-Class"] = mainClass
                    attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") { it.name }
                }

                doLast {
                    configurations.runtimeClasspath.get().forEach {
                        val file = File("${layout.buildDirectory.get()}/libs/${it.name}")
                        if (!file.exists())
                            it.copyTo(file)
                    }
                }
            }
        }
    }
}

fun Project.mainClass() =
    "$group.${name.replace("-", "")}.AppKt"

fun Project.erFellesmodul() = name == "felles"
