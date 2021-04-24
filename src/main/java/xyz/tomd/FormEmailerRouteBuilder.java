package xyz.tomd;

import org.apache.camel.Exchange;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;

public class FormEmailerRouteBuilder extends EndpointRouteBuilder {

    @Override
    public void configure() throws Exception {
        //Redirect to an 'invalid' page if the user hasn't passed the antispam challenge

        from("platform-http:/?httpMethodRestrict=GET,POST")
                .choice()
                    .when(header(Exchange.HTTP_METHOD).isEqualTo(constant("POST")))
                    .log("Received POST submission")

                    .choice()
                        .when(simple("${header.antispam} == {{antispam.answer}}"))
                            .log("Passed anti-spam challenge")
                            .to("direct:process-valid-response")

                        .otherwise()
                            .log("Submission failed anti-spam challenge")
                            .removeHeaders("*")
                            .setHeader("Location", simple("{{redirect.fail}}"))
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(303)) // Redirect 303 See Other
                            .transform(constant(""))
                        .endChoice()

                .otherwise()
                    .transform(constant("NOOP"));

        from("direct:process-valid-response")
                .setHeader("timestamp", simple("${date:now:yyyy-MM-dd'T'HH:mm:ss.SSSXXX}"))

                // insert into SQLite here in case the email doesn't send
                .to("sql:insert into responses (sender_name, sender_email, message, received) values (:#name, :#email, :#message, :#timestamp)")

                .log("Saved into DB")

                .to("velocity:email.vm")

                .removeHeaders("*", "email", "timestamp")

                // Send mail
                .setHeader("To", simple("{{mail.to}}"))
                .setHeader("From", simple("{{mail.from}}"))
                .setHeader("Reply-To", simple("${header.email}"))
                .setHeader("Subject", simple("{{mail.subject}}"))
                .to(smtps("{{smtps.host}}")
                        .username("{{smtps.username}}")
                        .password("{{smtps.password}}"))

                .log("Sent email to {{mail.to}}")

                .removeHeader("*")

                .setHeader("Location", simple("{{redirect.success}}"))
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(303)) // Redirect 303 See Other after form submission
                .transform(constant(""));

    }
}
