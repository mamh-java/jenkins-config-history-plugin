package hudson.plugins.jobConfigHistory;

import hudson.XmlFile;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import hudson.model.FreeStyleProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
@Execution(ExecutionMode.SAME_THREAD)
class JobConfigHistoryProjectActionIT {

    // we need to sleep between saves so we don't overwrite the history
    // directories
    // (which are saved with a granularity of one second)
    private static final int SLEEP_TIME = 1100;
    private JenkinsRule.WebClient webClient;
    private JenkinsRule rule;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        this.rule = rule;
        webClient = rule.createWebClient();
    }

    @Issue("SECURITY-3742")
    @Test
    @LocalData()
    void testGetLinesWithRedactSecrets(JenkinsRule j) throws IOException {
        JobConfigHistoryProjectAction sut = new JobConfigHistoryProjectAction(null);
        File clearFile = new File(getClass().getResource("configWithClearTextAuthToken.xml").getPath());
        File encodedFile = new File(getClass().getResource("configWithEncodedAuthToken.xml").getPath());
        XmlFile left = new XmlFile(clearFile);
        XmlFile right = new XmlFile(encodedFile);

        List<SideBySideView.Line> lines = sut.getLines(left, right, false, true);
        final List<SideBySideView.Line> redacted = JobConfigHistoryProjectAction.redactAuthTokensInLines(lines);

        // Check that no line contains the actual authToken values
        for (SideBySideView.Line line : redacted) {
            String leftText = line.getLeft().getText();
            String rightText = line.getRight().getText();
            assertFalse(leftText != null && leftText.contains("abcdefghi"), "Left side should not contain clear text authToken:" + leftText );
            assertFalse(rightText != null && rightText.contains("{AQAAABAAAAAQNtUC7oOeACRYWRxPnadbUP7sylp1gIy+WZ79FTDQVv0=}"), "Right side should not contain encoded authToken");
            // Check that redacted authToken is present
            if (leftText != null && leftText.contains("<authToken>")) {
                assertTrue(leftText.contains("<authToken>******</authToken>"), "Left side authToken should be redacted");
            }
            if (rightText != null && rightText.contains("<authToken>")) {
                assertTrue(rightText.contains("<authToken>******</authToken>"), "Right side authToken should be redacted");
            }
        }
    }

    @Issue("SECURITY-3742")
    @Test
    @LocalData()
    void testGetStringFromXmlFileWithRedactSecrets() throws IOException {
        File encodedFile = new File(getClass().getResource("configWithEncodedAuthToken.xml").getPath());
        XmlFile xmlFile = new XmlFile(encodedFile);

        final String redacted = JobConfigHistoryProjectAction.getStringFromXmlFile(xmlFile, true);
        final String[] strings = redacted.split("\n");

        // Check that no line contains the actual authToken values
        for (String line : strings) {
            assertFalse(line != null && line.contains("{AQAAABAAAAAQNtUC7oOeACRYWRxPnadbUP7sylp1gIy+WZ79FTDQVv0=}"), "Right side should not contain encoded authToken");
            // Check that redacted authToken is present
            if (line != null && line.contains("<authToken>")) {
                assertTrue(line.contains("<authToken>********</authToken>"), "authToken should be redacted");
            }
        }
    }

    /**
     * Tests restore link on job config history page.
     */
    @Test
    void testRestore() throws Exception {
        final String firstDescription = "first test";
        final String secondDescription = "second test";
        final String projectName = "Test1";

        final FreeStyleProject project = rule.createFreeStyleProject(
                projectName);
        Thread.sleep(SLEEP_TIME);
        project.setDescription(firstDescription);
        Thread.sleep(SLEEP_TIME);
        project.setDescription(secondDescription);
        Thread.sleep(SLEEP_TIME);

        assertEquals(secondDescription, project.getDescription());

        final HtmlPage htmlPage = webClient.goTo("job/" + projectName + "/"
                + JobConfigHistoryConsts.URLNAME);
        final HtmlAnchor restoreLink = (HtmlAnchor) htmlPage
            .getElementById("restore2");
        final HtmlPage reallyRestorePage = restoreLink.click();
        final HtmlForm restoreForm = reallyRestorePage
            .getFormByName("restore");
        final HtmlPage jobPage = rule.submit(restoreForm, "Submit");

        assertTrue(
                jobPage.asNormalizedText().contains(firstDescription),
                "Verify return to job page and changed description.");
        assertEquals(firstDescription, project.getDescription(), "Verify changed description.");
    }

    /**
     * Tests restore button on "Really restore?" page.
     */
    @Test
    void testRestoreFromDiffFiles() throws Exception {
        final String firstDescription = "first test";
        final String secondDescription = "second test";
        final String projectName = "Test1";

        final FreeStyleProject project = rule.createFreeStyleProject(projectName);
        Thread.sleep(SLEEP_TIME);
        project.setDescription(firstDescription);
        Thread.sleep(SLEEP_TIME);
        project.setDescription(secondDescription);
        Thread.sleep(SLEEP_TIME);

        assertEquals(secondDescription, project.getDescription());

        final HtmlPage htmlPage = webClient.goTo("job/" + projectName + "/"
                + JobConfigHistoryConsts.URLNAME);
        final HtmlPage diffPage = rule.submit(
                htmlPage.getFormByName("diffFiles"), "Submit");
        final HtmlPage reallyRestorePage = rule.submit(
                diffPage.getFormByName("forward"), "Submit");
        final HtmlPage jobPage = rule.submit(
                reallyRestorePage.getFormByName("restore"), "Submit");

        assertTrue(
                jobPage.asNormalizedText().contains(firstDescription),
                "Verify return to job page and changed description.");
        assertEquals(firstDescription, project.getDescription(), "Verify changed description.");
    }
}
