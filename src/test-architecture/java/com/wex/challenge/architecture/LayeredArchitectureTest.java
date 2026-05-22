package com.wex.challenge.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.wex.challenge.WexChallengeApplication;

@AnalyzeClasses(
        packagesOf = WexChallengeApplication.class,
        importOptions = ImportOption.DoNotIncludeTests.class)
class LayeredArchitectureTest {

    @ArchTest
    static final ArchRule layered_architecture_is_respected =
            layeredArchitecture().consideringAllDependencies()
                    .layer("Web").definedBy("com.wex.challenge.web..")
                    .layer("Service").definedBy("com.wex.challenge.service..")
                    .layer("Domain").definedBy("com.wex.challenge.domain..")
                    .layer("Repository").definedBy("com.wex.challenge.repository..")
                    .layer("Config").definedBy("com.wex.challenge.config..")

                    .whereLayer("Web").mayNotBeAccessedByAnyLayer()
                    .whereLayer("Service").mayOnlyBeAccessedByLayers("Web", "Config")
                    .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service");

    @ArchTest
    static final ArchRule domain_has_no_framework_dependencies =
            noClasses().that().resideInAPackage("com.wex.challenge.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.web..",
                            "org.springframework.stereotype..",
                            "org.springframework.beans..",
                            "com.wex.challenge.web..",
                            "com.wex.challenge.service..",
                            "com.wex.challenge.repository..")
                    .because("the domain model must stay framework-agnostic and "
                            + "must not know about layers above it");

    @ArchTest
    static final ArchRule controllers_live_in_web_package =
            classes().that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                    .should().resideInAPackage("com.wex.challenge.web..");

    @ArchTest
    static final ArchRule services_live_in_service_package =
            classes().that().areAnnotatedWith(org.springframework.stereotype.Service.class)
                    .should().resideInAPackage("com.wex.challenge.service..");

    @ArchTest
    static final ArchRule repositories_live_in_repository_package =
            classes().that().areAssignableTo(org.springframework.data.repository.Repository.class)
                    .and().areInterfaces()
                    .should().resideInAPackage("com.wex.challenge.repository..");

    @ArchTest
    static final ArchRule jpa_entities_live_in_domain_package =
            classes().that().areAnnotatedWith(jakarta.persistence.Entity.class)
                    .should().resideInAPackage("com.wex.challenge.domain..");

    @ArchTest
    static final ArchRule no_field_injection =
            noClasses().should().beAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class)
                    .because("constructor injection only — field injection hides "
                            + "dependencies and breaks testability");
}
