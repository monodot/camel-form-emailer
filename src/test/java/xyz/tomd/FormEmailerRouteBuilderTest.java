package xyz.tomd;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.jvnet.mock_javamail.Mailbox;

import javax.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;

@QuarkusTest
//@QuarkusTestResource(SmtpServerResource.class) // See: https://www.morling.dev/blog/quarkus-and-testcontainers/
class FormEmailerRouteBuilderTest {

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate template;

    @ConfigProperty(name = "redirect.fail")
    String redirectFailUrl;

    @ConfigProperty(name = "redirect.success")
    String redirectSuccessUrl;

    @ConfigProperty(name = "antispam.answer")
    String antispamAnswer;

    @ConfigProperty(name = "mail.to")
    String mailToAddress;

    @Inject
    AgroalDataSource dataSource;

    /**
     * Make sure that the healthcheck exists at the root endpoint (should return "NOOP")
     * @throws Exception
     */
    @Test
    public void testHealthcheck() throws Exception {
        given()
                .when().get("/")
                .then()
                .statusCode(200)
                .body(is("NOOP"));
    }

    /**
     * A happy-path test. Use a mock Javamail, and assert that an email arrives in the inbox.
     * @throws Exception
     */
    @Test
    public void testSendsEmail() throws Exception {
        Mailbox.clearAll();

        // Possibly replace this with a Testcontainer in future

        Connection conn;
        Statement statement;

        conn = dataSource.getConnection();
        statement = conn.createStatement();

        int deleteResult = statement.executeUpdate("drop table if exists responses");
        int dbResult = statement.executeUpdate("create table responses (\n" +
                "    id integer primary key,\n" +
                "    sender_name text,\n" +
                "    sender_email text,\n" +
                "    message text,\n" +
                "    received text\n" +
                ");");

        conn.close(); // Tidy up before the Camel stuff runs

        given()
                .when()
                .redirects().follow(false)
                .formParam("name", "Dave Angel")
                .formParam("email", "dav@example.com")
                .formParam("message", "Great website!")
                .formParam("antispam", antispamAnswer)
                .post("/")
                .then()
                .statusCode(303)
                .header("Location", is(redirectSuccessUrl));

        // Check that the email arrived
        Mailbox box = Mailbox.get(mailToAddress);
        assertEquals(1, box.size());

        // Create a new connection
        conn = dataSource.getConnection();
        statement = conn.createStatement();

        // Check that the data was persisted to the sqlite DB
        ResultSet results = statement.executeQuery("select * from responses");
        String senderName = results.getString("sender_name"); // Get the value from the first row
        assertEquals("Dave Angel", senderName);
    }

    /**
     * If the user provides an incorrect antispam answer, then redirect to the failure page
     * @throws Exception
     */
    @Test
    public void testIncorrectAntispamAnswerShouldRedirectToFailureUrl() throws Exception {
        given()
                .when()
                .redirects().follow(false) // Don't follow any redirects to my website, etc...
                .formParam("name", "Dave Smith")
                .formParam("antispam", "XXX")
                .post("/")
                .then()
                .statusCode(303)
                .header("Location", is(redirectFailUrl));
    }

    /**
     * If the user omits a required field, don't crash, redirect to failure page
     * @throws Exception
     */
    @Test
    public void testMissingRequiredFieldShouldRedirectToFailureUrl() throws Exception {
        given()
                .when()
                .redirects().follow(false) // Don't follow any redirects to my website, etc...
                .formParam("name", "Dave Smith")
                // Missing the 'email' field
                .formParam("antispam", antispamAnswer)
                .post("/")
                .then()
                .statusCode(303)
                .header("Location", is(redirectFailUrl));

    }

    /**
     * If the user supplies an antispam answer with padding or in the wrong case,
     * allow it
     * @throws Exception
     */
    @Test
    public void testWrongCaseWithSpacesAntispamIsPermitted() throws Exception {
        given()
                .when()
                .redirects().follow(false)
                .formParam("name", "Dave Smith")
                .formParam("email", "user@example.com")
                .formParam("message", "Great website!")
                .formParam("antispam", "  CaMeLs  ")
                .post("/")
                .then()
                .statusCode(303) // Redirect 303 See Other
                .header("Location", is(redirectSuccessUrl)); // check for redirect to success URL
    }

}