package org.evrete.dsl;

import org.evrete.KnowledgeService;
import org.evrete.api.ActivationMode;
import org.evrete.api.Knowledge;
import org.evrete.api.StatefulSession;
import org.evrete.util.NextIntSupplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.File;

class StatefulJavaJarTests extends CommonTestMethods {
    private static KnowledgeService service;

    @BeforeAll
    static void setUpClass() {
        service = new KnowledgeService();
    }

    @AfterAll
    static void shutDownClass() {
        service.shutdown();
    }


    private static StatefulSession session(Knowledge knowledge, ActivationMode mode) {
        return knowledge.newStatefulSession().setActivationMode(mode);
    }

    @ParameterizedTest
    @EnumSource(ActivationMode.class)
    void test1(ActivationMode mode) {
        Knowledge knowledge = applyToRuntimeAsURLs(service, AbstractDSLProvider.PROVIDER_JAVA_J, new File("src/test/resources/jars/jar2/jar2-tests.jar"));
        StatefulSession session = session(knowledge, mode);
        assert session.getRules().size() == 2;
        for (int i = 2; i < 100; i++) {
            session.insert(i);
        }
        session.fire();

        NextIntSupplier primeCounter = new NextIntSupplier();
        session.forEachFact((h, o) -> primeCounter.next());

        assert primeCounter.get() == 25;

    }

    @ParameterizedTest
    @EnumSource(ActivationMode.class)
    void test2(ActivationMode mode) {

        Knowledge knowledge = applyToRuntimeAsURLs(service, service.newTypeResolver(), AbstractDSLProvider.PROVIDER_JAVA_J, new File("src/test/resources/jars/jar2/jar2-tests.jar"));
        StatefulSession session = session(knowledge, mode);
        assert session.getRules().size() == 2;
        for (int i = 2; i < 100; i++) {
            session.insert(i);
        }
        session.fire();

        NextIntSupplier primeCounter = new NextIntSupplier();
        session.forEachFact((h, o) -> primeCounter.next());

        assert primeCounter.get() == 25;

    }


    @ParameterizedTest
    @EnumSource(ActivationMode.class)
    void testWithRecords(ActivationMode mode) {
        int version = TestUtils.getJavaVersion();
        if (version < 16) {
            System.out.println("Skipping test of Java Records for JVM version " + version);
            return;
        }

        Knowledge knowledge = applyToRuntimeAsURLs(service, AbstractDSLProvider.PROVIDER_JAVA_J, new File("src/test/resources/jars/jar-records1/jar-records1-tests.jar"));
        StatefulSession session = session(knowledge, mode);
        assert session.getRules().size() == 2;
        for (int i = 2; i < 100; i++) {
            session.insert(i);
        }
        session.fire();

        NextIntSupplier primeCounter = new NextIntSupplier();
        session.forEachFact((h, o) -> primeCounter.next());

        assert primeCounter.get() == 25;

    }
}
