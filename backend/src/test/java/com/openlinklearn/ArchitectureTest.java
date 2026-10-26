package com.openlinklearn;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.openlinklearn", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainDependsOnNoOuterLayer = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..api..", "..application..", "..infrastructure..");

    @ArchTest
    static final ArchRule applicationDependsOnNoApiOrInfrastructure = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..api..", "..infrastructure..");

    @ArchTest
    static final ArchRule apiIsNotUsedByOtherLayers = noClasses()
            .that().resideOutsideOfPackage("..api..")
            .should().dependOnClassesThat()
            .resideInAPackage("..api..");

    @ArchTest
    static final ArchRule controllersLiveInApi = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("..api..");

    @ArchTest
    static final ArchRule entitiesLiveInDomain = classes()
            .that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage("..domain..");

    @ArchTest
    static final ArchRule repositoriesLiveInDomain = classes()
            .that().areAssignableTo(Repository.class)
            .should().resideInAPackage("..domain..");
}
