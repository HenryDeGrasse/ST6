package com.weekly.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * ArchUnit module-boundary tests (§9.2, §13.1).
 *
 * <p>These enforce that internal modules communicate through
 * defined interfaces and do not cross module boundaries directly.
 */
@AnalyzeClasses(
        packages = "com.weekly",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ModuleBoundaryTest {

    @ArchTest
    static final ArchRule PLAN_MUST_NOT_DEPEND_ON_NOTIFICATION =
            noClasses().that().resideInAPackage("..plan..")
                    .should().dependOnClassesThat().resideInAPackage("..notification..")
                    .because("Plan module must not depend on notification internals");

    @ArchTest
    static final ArchRule PLAN_MUST_NOT_DEPEND_ON_AI =
            noClasses().that().resideInAPackage("..plan..")
                    .should().dependOnClassesThat().resideInAPackage("..ai..")
                    .because("Plan module must not depend on AI internals");

    @ArchTest
    static final ArchRule NOTIFICATION_MUST_NOT_DEPEND_ON_PLAN =
            noClasses().that().resideInAPackage("..notification..")
                    .should().dependOnClassesThat().resideInAPackage("..plan..")
                    .because("Notification module must not depend on plan internals");

    @ArchTest
    static final ArchRule AI_MUST_NOT_DEPEND_ON_PLAN =
            noClasses().that().resideInAPackage("..ai..")
                    .should().dependOnClassesThat().resideInAPackage("..plan..")
                    .because("AI module must not depend on plan internals");

    @ArchTest
    static final ArchRule AUDIT_MUST_NOT_DEPEND_ON_PLAN =
            noClasses().that().resideInAPackage("..audit..")
                    .should().dependOnClassesThat().resideInAPackage("..plan..")
                    .because("Audit module must not depend on plan internals");

    @ArchTest
    static final ArchRule RCDO_MUST_NOT_DEPEND_ON_PLAN =
            noClasses().that().resideInAPackage("..rcdo..")
                    .should().dependOnClassesThat().resideInAPackage("..plan..")
                    .because("RCDO module must not depend on plan internals");

    @ArchTest
    static final ArchRule AUTH_MUST_NOT_DEPEND_ON_PLAN =
            noClasses().that().resideInAPackage("..auth..")
                    .should().dependOnClassesThat().resideInAPackage("..plan..")
                    .because("Auth module must not depend on plan internals");

    @ArchTest
    static final ArchRule SHARED_MUST_NOT_DEPEND_ON_ANY_MODULE =
            noClasses().that().resideInAPackage("..shared..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..plan..", "..auth..", "..ai..",
                            "..rcdo..", "..audit..", "..notification..",
                            "..config..", "..health.."
                    )
                    .because("Shared package must not depend on any specific module");

    @ArchTest
    static final ArchRule NO_CYCLES_BETWEEN_MODULES =
            slices().matching("com.weekly.(*)..")
                    .should().beFreeOfCycles()
                    .because("Module packages must not have cyclic dependencies");
}
