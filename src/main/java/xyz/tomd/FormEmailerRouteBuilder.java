package xyz.tomd;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class FormEmailerRouteBuilder extends RouteBuilder {

    @Inject
    SubmissionValidPredicate submissionIsValid;

    @Override
    public void configure() throws Exception {

        from("platform-http:/?httpMethodRestrict=GET,POST")
                .choice()
                    .when(header(Exchange.HTTP_METHOD).isEqualTo(constant("POST")))
                    .log("Received POST submission")

                    .choice()
                        .when(submissionIsValid)
                            .log("Passed validation and antispam challenge")
                            .to("direct:process-valid-response")

                        .otherwise()
                            //Redirect to an 'invalid' page if the user hasn't passed the antispam challenge
                            .log("Submission failed validation or antispam challenge")
                            .removeHeaders("*")
                            .setHeader("Location", simple("{{redirect.fail}}"))
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(303)) // Redirect 303 'See Other'
                            .transform(constant(""))
                        .endChoice()

                .otherwise()
                    .transform(constant("NOOP"));


        from("direct:process-valid-response")
                .setHeader("timestamp", simple("${date:now:yyyy-MM-dd'T'HH:mm:ss.SSSXXX}"))

                // insert into SQLite here in case the email doesn't send
                .wireTap("seda:insert-to-db")

                // Prepare the email content
                .to("velocity:email.vm")

                // Send mail
                .removeHeaders("*", "email", "timestamp")
                .setHeader("To", simple("{{mail.to}}"))
                .setHeader("From", simple("{{mail.from}}"))
                .setHeader("Reply-To", simple("${header.email}"))
                .setHeader("Subject", simple("{{mail.subject}}"))
                .to("{{smtp.uri}}")

                .log("Sent email to {{mail.to}}")

                // Prepare the response
                .removeHeaders("*")
                .setHeader("Location", simple("{{redirect.success}}"))
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(303)) // Redirect 303 See Other after form submission
                .transform(constant(""));

        from("seda:insert-to-db") // Do this asynchronously away from the main route
                .log("Putting into the DB")

                // insert into SQLite here in case the email doesn't send
                .to("sql:insert into responses (sender_name, sender_email, message, received) values (:#name, :#email, :#message, :#timestamp)")
                .log("Saved into DB");


    }
}
