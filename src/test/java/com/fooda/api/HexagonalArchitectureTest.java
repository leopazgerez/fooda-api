package com.fooda.api;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.fooda.api", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domainDependsOnlyOnJava = classes()
            .that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..domain..");

    @ArchTest
    static final ArchRule applicationIsFrameworkFree = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..infrastructure..", "..config..",
                    "org.springframework..", "jakarta..", "org.hibernate..", "lombok..",
                    "tools.jackson..", "com.fasterxml..");

    @ArchTest
    static final ArchRule catalogIsIsolated = noClasses()
            .that().resideInAPackage("com.fooda.api.catalog..")
            .should().dependOnClassesThat().resideInAnyPackage("com.fooda.api.production..", "com.fooda.api.stock..", "com.fooda.api.sales..", "com.fooda.api.finance..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule productionIsIsolated = noClasses()
            .that().resideInAPackage("com.fooda.api.production..")
            .should().dependOnClassesThat().resideInAnyPackage("com.fooda.api.catalog..", "com.fooda.api.stock..", "com.fooda.api.sales..", "com.fooda.api.finance..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule stockIsIsolated = noClasses()
            .that().resideInAPackage("com.fooda.api.stock..")
            .should().dependOnClassesThat().resideInAnyPackage("com.fooda.api.catalog..", "com.fooda.api.production..", "com.fooda.api.sales..", "com.fooda.api.finance..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule salesIsIsolated = noClasses()
            .that().resideInAPackage("com.fooda.api.sales..")
            .should().dependOnClassesThat().resideInAnyPackage("com.fooda.api.catalog..", "com.fooda.api.production..", "com.fooda.api.stock..", "com.fooda.api.finance..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule financeIsIsolated = noClasses()
            .that().resideInAPackage("com.fooda.api.finance..")
            .should().dependOnClassesThat().resideInAnyPackage("com.fooda.api.catalog..", "com.fooda.api.production..", "com.fooda.api.stock..", "com.fooda.api.sales..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule coreIsIndependent = noClasses()
            .that().resideInAPackage("com.fooda.api.core..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.fooda.api.catalog..", "com.fooda.api.production..", "com.fooda.api.stock..",
                    "com.fooda.api.sales..", "com.fooda.api.finance..", "com.fooda.api.user..", "com.fooda.api.auth..")
            .allowEmptyShould(true);
}
