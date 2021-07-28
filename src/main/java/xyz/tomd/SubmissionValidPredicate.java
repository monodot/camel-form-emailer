package xyz.tomd;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import java.util.Arrays;
import java.util.List;

/**
 * Predicate class to check whether a submission includes all of the mandatory fields
 */
@ApplicationScoped
public class SubmissionValidPredicate implements Predicate {

    public final static String ANTISPAM_FIELD = "antispam";

    @ConfigProperty(name = "fields.required")
    String fieldsRequired;

    @ConfigProperty(name = "antispam.answer")
    String antispamAnswer;

    @Override
    public boolean matches(Exchange exchange) {
        boolean isValid = true;

        // Loop through all required fields. If any are missing, then it's invalid.
        List<String> required = Arrays.asList(fieldsRequired.split(","));
        for (String field : required) {
            if (exchange.getMessage().getHeader(field, "").equals("")) {
                isValid = false;
            }
        }

        String antispam = exchange.getMessage().getHeader(ANTISPAM_FIELD, String.class).trim();

        // Also check that the antispam field is correct (ignoring spaces and upper/lower case)
        if (!(antispam.equalsIgnoreCase(antispamAnswer))) {
            isValid = false;
        }

        return isValid;
    }
}
