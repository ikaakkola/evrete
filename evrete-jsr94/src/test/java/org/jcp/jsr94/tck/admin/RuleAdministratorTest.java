/* * JAVA COMMUNITY PROCESS * * J S R  9 4 * * Test	Compatibility Kit * */package org.jcp.jsr94.tck.admin;import org.jcp.jsr94.tck.util.TestCaseUtil;import org.junit.jupiter.api.Test;import javax.rules.RuleServiceProvider;import javax.rules.admin.LocalRuleExecutionSetProvider;import javax.rules.admin.RuleAdministrator;import javax.rules.admin.RuleExecutionSet;import javax.rules.admin.RuleExecutionSetProvider;import java.io.InputStream;import java.util.HashMap;/** * Test	the javax.rules.admin.RuleAdministrator	class. * * <p> * * <b>Performs the following tests:</b><br> * * <ul> * * <li>Basic API tests. {@link #testRuleAdministrator} * * <ul> * * <li>Create Instance * * <li>Get the LocalRuleExecutionSetProvider * * <li>Get the RuleExecutionSetProvider * * <li>Register and Deregister a RuleExecutionSet. * * </ul> * * </ul> * * @version 1.0 * @see javax.rules.admin.RuleAdministrator * @since JSR-94 1.0 */class RuleAdministratorTest {    /**     * Test the compliance	for javax.rules.admin.RuleAdministrator.     * <p>     * Get a RuleAdministrator from a RuleServiceProvider. Get the     * <p>     * RuleExecutionSetProvider as well as the     * <p>     * LocalRuleExecutionSetProvider. Create a     * <p>     * RuleExecutionSet via the LocalRuleExecutionSetProvider and try     * <p>     * to register and deregister the RuleExecutionSet. An input stream     * <p>     * to the tck_res_1.xml rule execution set is used to construct the     * <p>     * rule execution set.     *     * <p>     *     * <b>Description:</b><br>     *     * <ul>     *     * <li>Create Instance     *     * <ul>     *     * <li>Fail: If a RuleAdministrator instance cannot be retrieved.     *     * <li>Succeed: If the RuleAdministrator can successfully be     * <p>     * retrieved from the RuleServiceProvider.     *     * </ul>     *     * <li>Get the LocalRuleExecutionSetProvider     *     * <ul>     *     * <li>Fail: If any error occurs while retrieving the     * <p>     * LocalRuleExecutionSetProvider.     *     * <li>Succeed: If a non null LocalRuleExecutionSetProvider is     * <p>     * retrieved.     *     * </ul>     *     * <li>Get the RuleExecutionSetProvider     *     * <ul>     *     * <li>Fail: If any error occurs while retrieving the     * <p>     * RuleExecutionSetProvider.     *     * <li>Succeed: If a non null RuleExecutionSetProvider is     * <p>     * retrieved.     *     * </ul>     *     * <li>Register and Deregister a RuleExecutionSet.     *     * <ul>     *     * <li>Fail: If any error occurs during the registration and     * <p>     * un-registration of a rule execution set.     *     * <li>Succeed: If a rule execution set can successfully be     * <p>     * registered and de-registered.     *     * </ul>     *     * </ul>     *     * @see TestCaseUtil#getRuleServiceProvider     * @see TestCaseUtil#getRuleExecutionSetInputStream     */    @Test    void testRuleAdministrator() {        try {            // Get the RuleServiceProvider            RuleServiceProvider serviceProvider = TestCaseUtil.getRuleServiceProvider("ruleAdministratorTest");            assert serviceProvider != null;            // Get the RuleAdministrator            RuleAdministrator ruleAdministrator = serviceProvider.getRuleAdministrator();            assert ruleAdministrator != null;            // Test the LocalRuleExecutionSetProvider API            LocalRuleExecutionSetProvider localProvider = ruleAdministrator.getLocalRuleExecutionSetProvider(null);            assert localProvider != null;            // Test the RuleExecutionSetProvider API            RuleExecutionSetProvider provider = ruleAdministrator.getRuleExecutionSetProvider(null);            assert provider != null;            InputStream inStream = TestCaseUtil.getRuleExecutionSetInputStream("src/test/resources/TckRes1.java");            // Create the rule execution set.            HashMap<Object, Object> props = new HashMap<>();            props.put("org.evrete.jsr94.dsl-name", "JAVA-SOURCE");            RuleExecutionSet res = localProvider.createRuleExecutionSet(inStream, props);            assert res != null;            inStream.close();            // register the RuleExecutionSet            ruleAdministrator.registerRuleExecutionSet("testAdmin", res, null);            // deregister the RuleExecutionSet            ruleAdministrator.deregisterRuleExecutionSet("testAdmin", null);        } catch (Exception e) {            throw new IllegalStateException(e);        }    }}